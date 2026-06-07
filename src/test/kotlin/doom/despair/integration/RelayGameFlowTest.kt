package doom.despair.integration

import com.google.gson.Gson
import com.google.gson.JsonParser
import doom.despair.core.CreateGameResponse
import doom.despair.core.JoinGameResponse
import doom.despair.core.Packet
import doom.despair.ships.ShipType
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.server.WebSocketServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Tests the full relay path:
 *   client A → lobby relay → battleship server
 *   client B → lobby relay → battleship server
 *
 * An in-process mini lobby server handles /control and /relay upgrades.
 */
class RelayGameFlowTest {

    private val gson = Gson()

    // Ports chosen to avoid conflicts with other tests
    private val LOBBY_PORT = 25570
    private val GAME_PORT = 25571

    private lateinit var lobbyServer: MiniLobbyServer
    private lateinit var battleshipServer: doom.despair.server.BattleshipServer

    private data class Placement(val type: ShipType, val x: Int, val y: Int, val horizontal: Boolean)

    @BeforeEach
    fun setUp() {
        lobbyServer = MiniLobbyServer(LOBBY_PORT)
        lobbyServer.start()
        lobbyServer.waitForStart(3, TimeUnit.SECONDS)

        // Point the game server at the in-process lobby
        System.setProperty("lobby.host", "localhost:$LOBBY_PORT")
        battleshipServer = doom.despair.server.BattleshipServer(autoStart = false, port = GAME_PORT)
        battleshipServer.start()
        Thread.sleep(300) // give server time to fully bind
    }

    @AfterEach
    fun tearDown() {
        battleshipServer.stop()
        lobbyServer.stopServer()
        System.clearProperty("lobby.host")
    }

    @Test
    fun `two clients can play through the relay`() {
        // ── Step 1: host connects directly and creates the game ─────────────────
        val directHostConn = DirectWsClient(GAME_PORT)
        directHostConn.connectBlocking()

        val createResp = directHostConn.request(Packet("CREATE_GAME", gson.toJson(mapOf("playerName" to "Host"))))
        val created = gson.fromJson(createResp.payload, CreateGameResponse::class.java)
        assertTrue(created.ok, "CREATE_GAME should succeed")
        val hostId = created.playerId!!

        // ── Step 2: wait for game server to advertise to lobby ──────────────────
        Thread.sleep(600)
        val games = lobbyServer.listGames()
        assertTrue(games.isNotEmpty(), "Lobby should list at least one game")
        val gameId = games.first().gameId

        // ── Step 3: guest connects via relay ────────────────────────────────────
        val guestConn = RelayClient("localhost:$LOBBY_PORT")
        guestConn.connectViaRelay(gameId)
        // Wait for game server to connect back as relay-server and get wired
        Thread.sleep(600)

        // ── Step 4: guest joins through the relay ───────────────────────────────
        val joinResp = guestConn.request(Packet("JOIN_GAME", gson.toJson(mapOf("playerName" to "Guest"))))
        val joined = gson.fromJson(joinResp.payload, JoinGameResponse::class.java)
        assertTrue(joined.ok, "JOIN_GAME should succeed via relay: ${joined.message}")
        val guestId = joined.playerId!!

        // ── Step 5: place ships (host direct, guest via relay) ──────────────────
        listOf(
            Placement(ShipType.AIRCRAFT_CARRIER, 0, 0, true),
            Placement(ShipType.DESTROYER, 0, 2, false),
            Placement(ShipType.SUBMARINE, 2, 4, true)
        ).forEach { (type, x, y, horizontal) ->
            val hostResp = directHostConn.request(Packet("PLACE_SHIP", gson.toJson(
                mapOf("playerId" to hostId, "type" to type.name, "x" to x, "y" to y, "horizontal" to horizontal)
            )))
            assertTrue(gson.fromJson(hostResp.payload, doom.despair.core.ActionResponse::class.java).ok,
                "Host PLACE_SHIP $type should succeed")

            val guestResp = guestConn.request(Packet("PLACE_SHIP", gson.toJson(
                mapOf("playerId" to guestId, "type" to type.name, "x" to x, "y" to y, "horizontal" to horizontal)
            )))
            assertTrue(gson.fromJson(guestResp.payload, doom.despair.core.ActionResponse::class.java).ok,
                "Guest PLACE_SHIP $type should succeed via relay")
        }

        // ── Step 6: host fires a shot (direct); verify hit ──────────────────────
        val shotResp = directHostConn.request(Packet("FIRE_SHOT", gson.toJson(
            mapOf("playerId" to hostId, "x" to 0, "y" to 0)
        )))
        val shot = gson.fromJson(shotResp.payload, doom.despair.core.FireShotResponse::class.java)
        assertTrue(shot.ok,  "FIRE_SHOT should succeed: ${shot.message}")
        assertTrue(shot.hit, "Shot at (0,0) should hit AIRCRAFT_CARRIER")

        // ── Step 7: guest fetches state via relay ───────────────────────────────
        val stateResp = guestConn.request(Packet("GET_STATE", gson.toJson(mapOf("playerId" to guestId))))
        val stateWrapper = gson.fromJson(stateResp.payload, doom.despair.core.GameStateResponse::class.java)
        assertTrue(stateWrapper.ok, "GET_STATE should succeed via relay")
        assertNotNull(stateWrapper.state, "State must be present")
        // After host fires a hit, turn stays with host (hits keep the turn)
        assertEquals(hostId, stateWrapper.state!!.currentTurnPlayerId)

        val stateJson = JsonParser.parseString(stateResp.payload).asJsonObject
        val state = stateJson.getAsJsonObject("state")
        val playerBoard = state.getAsJsonArray("playerBoard")
        val opponentBoard = state.getAsJsonArray("opponentBoard")
        for (cellElement in playerBoard) {
            val cell = cellElement.asJsonObject
            if (cell.get("state")?.asString == "SHIP") {
                assertTrue(cell.has("shipType"), "Player ship cells must include shipType")
                assertTrue(cell.has("segment"), "Player ship cells must include segment")
            }
        }
        for (cellElement in opponentBoard) {
            val cell = cellElement.asJsonObject
            assertTrue(!cell.has("shipType"), "Opponent cells must not include shipType")
            assertTrue(!cell.has("segment"), "Opponent cells must not include segment")
        }

        directHostConn.close()
        guestConn.close()
    }


    // ── Mini lobby relay server ───────────────────────────────────────────────

    data class LobbyGame(val gameId: String, val hostName: String, val address: String)

    data class LobbyGameEntry(
        val gameId: String,
        val hostName: String,
        val address: String,
        val control: WebSocket
    )

    inner class MiniLobbyServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

        private val games = mutableMapOf<String, LobbyGameEntry>()
        private val pendingClients = mutableMapOf<String, WebSocket>()
        private val startLatch = CountDownLatch(1)

        fun waitForStart(timeout: Long, unit: TimeUnit) {
            check(startLatch.await(timeout, unit)) { "MiniLobbyServer did not start in time" }
        }

        fun listGames(): List<LobbyGame> = synchronized(games) {
            games.values.map { LobbyGame(it.gameId, it.hostName, it.address) }
        }

        fun stopServer() = try { stop(1000) } catch (_: Exception) {}

        override fun onStart() { startLatch.countDown() }
        override fun onError(conn: WebSocket?, ex: Exception) {}

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            val path = handshake.resourceDescriptor
            val params = parseQuery(path)
            when {
                path.startsWith("/control") -> {
                    val gameId = params["gameId"] ?: return conn.close(4000, "missing gameId")
                    val hostName = params["hostName"] ?: "Unknown"
                    val address = params["address"] ?: ""
                    conn.setAttachment(mapOf("role" to "control", "gameId" to gameId))
                    synchronized(games) {
                        games[gameId] = LobbyGameEntry(gameId, hostName, address, conn)
                    }
                }
                path.startsWith("/relay") -> {
                    val gameId = params["gameId"] ?: return conn.close(4000, "missing gameId")
                    val role = params["role"] ?: return conn.close(4000, "missing role")
                    if (role == "client") {
                        val clientId = UUID.randomUUID().toString()
                        conn.setAttachment(mapOf("role" to "relay-client", "gameId" to gameId, "clientId" to clientId))
                        synchronized(pendingClients) { pendingClients[clientId] = conn }
                        val controlWs = synchronized(games) { games[gameId]?.control }
                        if (controlWs == null || !controlWs.isOpen) {
                            conn.close(4001, "game not found")
                            return
                        }
                        controlWs.send(gson.toJson(mapOf("type" to "CONNECT_RELAY", "clientId" to clientId)))
                    } else if (role == "server") {
                        val clientId = params["clientId"] ?: return conn.close(4000, "missing clientId")
                        val clientWs = synchronized(pendingClients) { pendingClients.remove(clientId) }
                        if (clientWs == null) { conn.close(4002, "client not found"); return }
                        // Wire the pair
                        conn.setAttachment(mapOf("role" to "relay-server", "peer" to clientWs))
                        val data = clientWs.getAttachment<Map<String, Any?>>()?.toMutableMap() ?: mutableMapOf()
                        data["peer"] = conn
                        clientWs.setAttachment(data)
                    }
                }
            }
        }

        override fun onMessage(conn: WebSocket, message: String) {
            val data = conn.getAttachment<Map<String, Any?>>() ?: return
            val peer = data["peer"] as? WebSocket ?: return
            if (peer.isOpen) peer.send(message)
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            val data = conn.getAttachment<Map<String, Any?>>() ?: return
            when (data["role"]) {
                "control" -> synchronized(games) { games.remove(data["gameId"] as? String) }
                "relay-client" -> synchronized(pendingClients) { pendingClients.remove(data["clientId"] as? String) }
            }
            val peer = data["peer"] as? WebSocket
            if (peer != null && peer.isOpen) peer.close(code, reason)
        }

        private fun parseQuery(resource: String): Map<String, String> {
            val q = resource.substringAfter('?', "")
            return q.split('&').mapNotNull {
                val kv = it.split('=')
                if (kv.size == 2) kv[0] to kv[1] else null
            }.toMap()
        }
    }

    // ── Relay client helper ───────────────────────────────────────────────────

    inner class RelayClient(private val lobbyHost: String) {
        private var ws: WebSocketClient? = null
        private val queue = LinkedBlockingQueue<Packet>()

        fun connectViaRelay(gameId: String) {
            val uri = URI("ws://$lobbyHost/relay?role=client&gameId=$gameId")
            val latch = CountDownLatch(1)
            ws = object : WebSocketClient(uri) {
                override fun onOpen(handshakedata: ServerHandshake) { latch.countDown() }
                override fun onMessage(message: String) {
                    val p = Packet.deserialize(message)
                    if (p.type != "STATE_UPDATE") queue.put(p)
                }
                override fun onClose(code: Int, reason: String, remote: Boolean) {}
                override fun onError(ex: Exception) {}
            }
            ws!!.connect()
            check(latch.await(5, TimeUnit.SECONDS)) { "Relay connection timed out" }
        }

        fun request(packet: Packet): Packet {
            ws!!.send(packet.serialize())
            return queue.poll(5, TimeUnit.SECONDS)
                ?: error("No response received within 5s for ${packet.type}")
        }

        fun close() = ws?.close()
    }

    // ── Direct WS client (used only for CREATE_GAME on first connection) ─────

    inner class DirectWsClient(port: Int) : WebSocketClient(URI("ws://localhost:$port")) {
        private val queue = LinkedBlockingQueue<Packet>()

        override fun onOpen(handshakedata: ServerHandshake) {}
        override fun onMessage(message: String) {
            val p = Packet.deserialize(message)
            if (p.type != "STATE_UPDATE") queue.put(p)
        }
        override fun onClose(code: Int, reason: String, remote: Boolean) {}
        override fun onError(ex: Exception) {}

        fun request(packet: Packet): Packet {
            send(packet.serialize())
            return queue.poll(5, TimeUnit.SECONDS)
                ?: error("No response for ${packet.type}")
        }
    }
}
