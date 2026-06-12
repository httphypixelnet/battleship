package doom.despair.server

import com.google.gson.Gson
import doom.despair.Player
import doom.despair.core.ActionResponse
import doom.despair.core.CreateGameRequest
import doom.despair.core.CreateGameResponse
import doom.despair.core.FireShotRequest
import doom.despair.core.FireShotResponse
import doom.despair.core.GameStateDto
import doom.despair.core.GameStateResponse
import doom.despair.core.GetStateRequest
import doom.despair.core.JoinGameRequest
import doom.despair.core.JoinGameResponse
import doom.despair.core.Packet
import doom.despair.core.PlaceShipRequest
import doom.despair.core.ServerContext
import doom.despair.core.ServerGameEvent
import doom.despair.core.events.ValidEvents
import doom.despair.ships.ShipType
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.Collections
import java.util.LinkedHashSet
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.server.WebSocketServer
import kotlin.concurrent.thread
import kotlin.concurrent.Volatile

class BattleshipServer @JvmOverloads constructor(autoStart: Boolean = true, private val port: Int = 25567) {
    private companion object {
        val REQUIRED_SHIPS = listOf(ShipType.AIRCRAFT_CARRIER, ShipType.DESTROYER, ShipType.SUBMARINE)
    }

    private data class SessionPlayer(
        val id: String,
        val name: String,
        val board: Board = Board(),
        val placedShips: MutableSet<ShipType> = LinkedHashSet()
    )

    private data class GameSession(
        val gameId: String = UUID.randomUUID().toString(),
        val host: SessionPlayer,
        var guest: SessionPlayer? = null,
        var currentTurnPlayerId: String? = null,
        var winnerPlayerId: String? = null
    )

    private val gson = Gson()
    private val debugRelay = (System.getProperty("debug.relay") ?: "false").equals("true", ignoreCase = true)
    private val eventQueue: ConcurrentLinkedQueue<ServerGameEvent> = ConcurrentLinkedQueue<ServerGameEvent>()
    @Volatile
    private var currentSession: GameSession? = null
    private val subscribers = Collections.synchronizedMap(HashMap<String, (Packet) -> Unit>())
    private val lanAdvertiser = LanGameAdvertiser(port)
    private val running: AtomicBoolean = AtomicBoolean(false)
    private val workerPool: ExecutorService = Executors.newCachedThreadPool()

    @Volatile
    private var webSocketServer: WebSocketServer? = null
    private var lobbyControlClient: WebSocketClient? = null

    private fun debug(msg: String) {
        if (debugRelay) {
            println("[RelayDebug][Server] $msg")
        }
    }

    fun queueEvent(event: ServerGameEvent) {
        eventQueue.add(event)
    }

    private val playerBoards = Collections.synchronizedMap(HashMap<Player?, Board>())

    val isRunning: Boolean
        get() = running.get()

    init {
        ValidEvents.registerEvents()
        Runtime.getRuntime().addShutdownHook(Thread { stop() })

        if (autoStart) {
            start()
        }
    }

    fun getBoardForPlayer(player: Player?): Board {
        return playerBoards.computeIfAbsent(player) { `_`: Player? -> Board() }
    }

    @Synchronized
    fun start() {
        if (running.get()) {
            return
        }
        val server = object : WebSocketServer(InetSocketAddress(port)) {
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                debug("Game WS open remote=${conn.remoteSocketAddress}")
            }

            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                debug("Game WS close remote=${conn.remoteSocketAddress} code=$code reason='$reason' remote=$remote")
                handleConnectionClose(conn)
            }

            override fun onMessage(conn: WebSocket, message: String) {
                debug("Game WS message remote=${conn.remoteSocketAddress} bytes=${message.length}")
                handleIncomingMessage(conn, message)
            }

            override fun onError(conn: WebSocket?, ex: Exception) {
                debug("Game WS error remote=${conn?.remoteSocketAddress} error=${ex.message}")
            }

            override fun onStart() {}
        }
        webSocketServer = server
        try {
            server.start()
        } catch (e: Exception) {
            throw RuntimeException("Could not start WebSocket server on port $port", e)
        }
        lanAdvertiser.start()
        running.set(true)
        thread(name = "battleship-event-loop", isDaemon = true) {
            while (running.get()) {
                gameLoop()
                Thread.sleep(10)
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false)) {
            return
        }
        try {
            webSocketServer?.stop(1000)
        } catch (_: Exception) {
        }
        webSocketServer = null
        lobbyControlClient?.close()
        lobbyControlClient = null
        lanAdvertiser.stop()
    }

    internal fun processPacket(packet: Packet, sendToCaller: (Packet) -> Unit): Packet {
        return try {
            when (packet.type) {
                "PING" -> Packet("PING_RESPONSE", gson.toJson(ActionResponse(ok = true, message = "pong")))
                "CREATE_GAME" -> {
                    val request = gson.fromJson(packet.payload, CreateGameRequest::class.java)
                    val response = createGame(request)
                    if (response.ok && response.playerId != null) {
                        registerSubscriber(response.playerId, sendToCaller)
                        pushStateToSubscribers()
                    }
                    Packet("CREATE_GAME_RESPONSE", gson.toJson(response))
                }

                "JOIN_GAME" -> {
                    val request = gson.fromJson(packet.payload, JoinGameRequest::class.java)
                    val response = joinGame(request)
                    if (response.ok && response.playerId != null) {
                        registerSubscriber(response.playerId, sendToCaller)
                        pushStateToSubscribers()
                    }
                    Packet("JOIN_GAME_RESPONSE", gson.toJson(response))
                }

                "PLACE_SHIP" -> {
                    val request = gson.fromJson(packet.payload, PlaceShipRequest::class.java)
                    val response = placeShip(request)
                    if (response.ok) {
                        registerSubscriber(request.playerId, sendToCaller)
                        pushStateToSubscribers()
                    }
                    Packet("PLACE_SHIP_RESPONSE", gson.toJson(response))
                }

                "FIRE_SHOT" -> {
                    val request = gson.fromJson(packet.payload, FireShotRequest::class.java)
                    val response = fireShot(request)
                    if (response.ok) {
                        registerSubscriber(request.playerId, sendToCaller)
                        pushStateToSubscribers()
                    }
                    Packet("FIRE_SHOT_RESPONSE", gson.toJson(response))
                }

                "GET_STATE" -> {
                    val request = gson.fromJson(packet.payload, GetStateRequest::class.java)
                    registerSubscriber(request.playerId, sendToCaller)
                    Packet("GET_STATE_RESPONSE", gson.toJson(getState(request)))
                }

                else -> Packet("ERROR", gson.toJson(ActionResponse(ok = false, message = "Unknown packet type '${packet.type}'")))
            }
        } catch (e: Exception) {
            Packet("ERROR", gson.toJson(ActionResponse(ok = false, message = e.message ?: "Request failed")))
        }
    }

    internal fun extractPlayerId(packet: Packet): String? {
        return try {
            when (packet.type) {
                "CREATE_GAME_RESPONSE" -> gson.fromJson(packet.payload, CreateGameResponse::class.java).playerId
                "JOIN_GAME_RESPONSE" -> gson.fromJson(packet.payload, JoinGameResponse::class.java).playerId
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun extractPlayerIdFromRequest(packet: Packet): String? {
        return try {
            when (packet.type) {
                "PLACE_SHIP" -> gson.fromJson(packet.payload, PlaceShipRequest::class.java).playerId
                "FIRE_SHOT" -> gson.fromJson(packet.payload, FireShotRequest::class.java).playerId
                "GET_STATE" -> gson.fromJson(packet.payload, GetStateRequest::class.java).playerId
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun unregisterSubscriber(playerId: String) {
        subscribers.remove(playerId)
    }

    private fun registerSubscriber(playerId: String, sender: (Packet) -> Unit) {
        subscribers[playerId] = sender
    }

    private fun createGame(request: CreateGameRequest): CreateGameResponse {
        synchronized(this) {
            val active = currentSession
            if (active != null && active.winnerPlayerId == null) {
                return CreateGameResponse(ok = false, message = "A game is already running on this server")
            }

            val playerId = UUID.randomUUID().toString()
            val host = SessionPlayer(id = playerId, name = request.playerName.ifBlank { "Host" })
            currentSession = GameSession(host = host)
            updateLanAdvertisement()
            updateLobbyAdvertisement()
            return CreateGameResponse(ok = true, playerId = playerId)
        }
    }

    private fun joinGame(request: JoinGameRequest): JoinGameResponse {
        val session = currentSession ?: return JoinGameResponse(ok = false, message = "No running game to join")
        synchronized(session) {
            if (session.winnerPlayerId != null) {
                return JoinGameResponse(ok = false, message = "Current game is over. Host must create a new game")
            }
            if (session.guest != null) {
                return JoinGameResponse(ok = false, message = "Game already has 2 players")
            }
            val playerId = UUID.randomUUID().toString()
            session.guest = SessionPlayer(id = playerId, name = request.playerName.ifBlank { "Guest" })
            updateLanAdvertisement()
            updateLobbyAdvertisement()
            return JoinGameResponse(ok = true, playerId = playerId)
        }
    }

    private fun placeShip(request: PlaceShipRequest): ActionResponse {
        val session = currentSession ?: return ActionResponse(ok = false, message = "No running game")
        synchronized(session) {
            val player = findPlayer(session, request.playerId) ?: return ActionResponse(ok = false, message = "Player not found")
            if (request.type !in REQUIRED_SHIPS) {
                return ActionResponse(ok = false, message = "Unknown ship type")
            }
            if (request.type in player.placedShips) {
                return ActionResponse(ok = false, message = "Ship already placed: ${request.type}")
            }

            val placed = player.board.placeShip(request.type, Board.Coordinate(request.x, request.y), request.horizontal)
            if (!placed) {
                return ActionResponse(ok = false, message = "Invalid ship placement")
            }
            player.placedShips.add(request.type)
            if (allShipsPlaced(session.host) && session.guest?.let { allShipsPlaced(it) } == true && session.currentTurnPlayerId == null) {
                session.currentTurnPlayerId = session.host.id
            }
            return ActionResponse(ok = true, message = "Ship placed")
        }
    }

    private fun fireShot(request: FireShotRequest): FireShotResponse {
        val session = currentSession ?: return FireShotResponse(ok = false, message = "No running game")
        synchronized(session) {
            val player = findPlayer(session, request.playerId) ?: return FireShotResponse(ok = false, message = "Player not found")
            val opponent = findOpponent(session, request.playerId)
                ?: return FireShotResponse(ok = false, message = "Waiting for opponent")

            if (!allShipsPlaced(player) || !allShipsPlaced(opponent)) {
                return FireShotResponse(ok = false, message = "Both players must place ships first")
            }
            if (session.winnerPlayerId != null) {
                return FireShotResponse(ok = false, message = "Game is already over")
            }
            if (session.currentTurnPlayerId != request.playerId) {
                return FireShotResponse(ok = false, message = "Not your turn")
            }

            val shot = opponent.board.fireAt(Board.Coordinate(request.x, request.y))
            if (!shot.valid) {
                return FireShotResponse(ok = false, message = if (shot.alreadyTried) "Coordinate already targeted" else "Invalid target")
            }

            if (shot.won) {
                session.winnerPlayerId = player.id
            } else if (!shot.hit) {
                session.currentTurnPlayerId = opponent.id
            }

            return FireShotResponse(ok = true, hit = shot.hit, sunk = shot.sunk, won = shot.won)
        }
    }

    private fun getState(request: GetStateRequest): GameStateResponse {
        val session = currentSession ?: return GameStateResponse(ok = false, message = "No running game")
        synchronized(session) {
            return buildStateForPlayer(session, request.playerId)
        }
    }

    private fun buildStateForPlayer(session: GameSession, playerId: String): GameStateResponse {
        val player = findPlayer(session, playerId) ?: return GameStateResponse(ok = false, message = "Player not found")
        val opponent = findOpponent(session, playerId)
        val dto = GameStateDto(
            playerId = player.id,
            playerName = player.name,
            opponentName = opponent?.name,
            waitingForOpponent = opponent == null,
            currentTurnPlayerId = session.currentTurnPlayerId,
            winnerPlayerId = session.winnerPlayerId,
            playerShipsPlaced = allShipsPlaced(player),
            opponentShipsPlaced = opponent?.let { allShipsPlaced(it) } ?: false,
            playerShipsRemainingToPlace = REQUIRED_SHIPS.filter { it !in player.placedShips },
            playerBoard = player.board.toView(revealShips = true),
            opponentBoard = opponent?.board?.toView(revealShips = session.winnerPlayerId != null) ?: emptyList()
        )
        return GameStateResponse(ok = true, state = dto)
    }

    private fun allShipsPlaced(player: SessionPlayer): Boolean {
        return player.placedShips.containsAll(REQUIRED_SHIPS)
    }

    private fun updateLanAdvertisement() {
        val session = currentSession
        if (session != null && session.guest == null && session.winnerPlayerId == null) {
            lanAdvertiser.advertiseOpenGame(session.host.name, session.gameId)
        } else {
            lanAdvertiser.clearOpenGame()
        }
    }

    private fun pushStateToSubscribers() {
        val session = currentSession ?: return
        synchronized(session) {
            val targets = ArrayList<Pair<String, (Packet) -> Unit>>()
            val hostSender = subscribers[session.host.id]
            if (hostSender != null) {
                targets.add(session.host.id to hostSender)
            }
            val guest = session.guest
            if (guest != null) {
                val guestSender = subscribers[guest.id]
                if (guestSender != null) {
                    targets.add(guest.id to guestSender)
                }
            }

            for ((playerId, sender) in targets) {
                val state = buildStateForPlayer(session, playerId)
                try {
                    sender(Packet("STATE_UPDATE", gson.toJson(state)))
                } catch (_: Exception) {
                    subscribers.remove(playerId)
                }
            }
        }
    }

    private fun findPlayer(session: GameSession, playerId: String): SessionPlayer? {
        if (session.host.id == playerId) {
            return session.host
        }
        val guest = session.guest
        if (guest != null && guest.id == playerId) {
            return guest
        }
        return null
    }

    private fun findOpponent(session: GameSession, playerId: String): SessionPlayer? {
        if (session.host.id == playerId) {
            return session.guest
        }
        val guest = session.guest
        return if (guest != null && guest.id == playerId) session.host else null
    }

    private fun gameLoop() {
        val ev = eventQueue.poll() ?: // nothing to process
        return

        val ctx = ServerContext(this)
        ev.handle(ctx)
    }

    @Synchronized
    private fun updateLobbyAdvertisement() {
        val session = currentSession
        if (session != null && session.winnerPlayerId == null) {
            if (lobbyControlClient == null || lobbyControlClient?.isOpen == false) {
                val gameId = session.gameId
                val hostName = session.host.name
                val localIp = try {
                    java.net.InetAddress.getLocalHost().hostAddress
                } catch (_: Exception) {
                    "127.0.0.1"
                }
                val lobbyHost = System.getProperty("lobby.host") ?: "54.213.93.141:25565"
                val scheme = if (lobbyHost.startsWith("localhost") || lobbyHost.startsWith("127.0.0.1")) "ws" else "ws"
                val lobbyUri = "$scheme://$lobbyHost/control?gameId=$gameId&hostName=$hostName&address=$localIp:$port"
                try {
                    val client = object : WebSocketClient(URI(lobbyUri)) {
                        override fun onOpen(handshakedata: ServerHandshake) {
                            debug("Lobby control open uri=$lobbyUri")
                        }
                        override fun onMessage(message: String) {
                            debug("Lobby control message=$message")
                            try {
                                val msg = gson.fromJson(message, Map::class.java)
                                if (msg["type"] == "CONNECT_RELAY") {
                                    val clientId = msg["clientId"] as? String ?: return
                                    connectToRelay(gameId, clientId)
                                }
                            } catch (_: Exception) {}
                        }
                        override fun onClose(code: Int, reason: String, remote: Boolean) {
                            debug("Lobby control close code=$code reason='$reason' remote=$remote")
                        }
                        override fun onError(ex: Exception) {
                            debug("Lobby control error=${ex.message}")
                        }
                    }
                    applyProxyIfConfigured(client)
                    client.connect()
                    lobbyControlClient = client
                } catch (_: Exception) {}
            }
        } else {
            lobbyControlClient?.close()
            lobbyControlClient = null
        }
    }

    private fun connectToRelay(gameId: String, clientId: String) {
        val lobbyHost = System.getProperty("lobby.host") ?: "54.213.93.141:25565"
        val scheme = if (lobbyHost.startsWith("localhost") || lobbyHost.startsWith("127.0.0.1")) "ws" else "ws"
        val relayUri = "$scheme://$lobbyHost/relay?gameId=$gameId&role=server&clientId=$clientId"
        try {
            val relayClient = object : WebSocketClient(URI(relayUri)) {
                override fun onOpen(handshakedata: ServerHandshake) {
                    debug("Relay server open clientId=$clientId uri=$relayUri")
                }
                override fun onMessage(message: String) {
                    debug("Relay server message clientId=$clientId bytes=${message.length}")
                    handleIncomingMessage(this, message)
                }
                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    debug("Relay server close clientId=$clientId code=$code reason='$reason' remote=$remote")
                    handleConnectionClose(this)
                }
                override fun onError(ex: Exception) {
                    debug("Relay server error clientId=$clientId error=${ex.message}")
                }
            }
            applyProxyIfConfigured(relayClient)
            relayClient.connect()
        } catch (_: Exception) {}
    }

    private fun applyProxyIfConfigured(client: WebSocketClient) {
        val proxyHost = System.getProperty("https.proxyHost") ?: System.getProperty("http.proxyHost")
        val proxyPortRaw = System.getProperty("https.proxyPort") ?: System.getProperty("http.proxyPort")
        val proxyPort = proxyPortRaw?.toIntOrNull()
        if (proxyHost != null && proxyPort != null) {
            client.setProxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
        }
    }

    private fun handleIncomingMessage(conn: WebSocket, message: String) {
        workerPool.submit {
            val inputPacket = try {
                Packet.deserialize(message)
            } catch (_: Exception) {
                Packet(type = "ERROR", payload = """{"ok":false,"message":"Malformed packet"}""")
            }
            debug("Process packet type=${inputPacket.type} conn=${conn.remoteSocketAddress}")
            val sendPacket: (Packet) -> Unit = { packet ->
                if (conn.isOpen) {
                    debug("Send packet type=${packet.type} conn=${conn.remoteSocketAddress}")
                    conn.send(packet.serialize())
                }
            }
            val response = processPacket(inputPacket, sendPacket)
            val currentRegisteredPlayerId = conn.getAttachment<String>()
            if (currentRegisteredPlayerId == null) {
                val registeredPlayerId = extractPlayerId(response) ?: extractPlayerIdFromRequest(inputPacket)
                if (registeredPlayerId != null) {
                    conn.setAttachment(registeredPlayerId)
                }
            }
            sendPacket(response)
        }
    }

    private fun handleConnectionClose(conn: WebSocket) {
        val registeredPlayerId = conn.getAttachment<String>()
        debug("Connection close cleanup conn=${conn.remoteSocketAddress} playerId=$registeredPlayerId")
        if (registeredPlayerId != null) {
            unregisterSubscriber(registeredPlayerId)
        }
    }
}
