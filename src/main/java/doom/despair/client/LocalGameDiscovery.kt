package doom.despair.client

import com.google.gson.Gson
import java.io.Closeable
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.jmdns.JmDNS
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake

class LocalGameDiscovery : Closeable {
    data class LocalGame(val name: String, val host: String, val port: Int, val gameId: String? = null) {
        val address: String
            get() = "$host:$port"

        override fun toString(): String = if (gameId != null) "$name (Lobby)" else "$name ($address)"
    }

    private companion object {
        const val SERVICE_TYPE = "_battleship._tcp.local."
        val LOBBY_HOST = System.getProperty("lobby.host") ?: "54.213.93.141:25565"
        val LOBBY_GAMES_WS_URL = "ws://$LOBBY_HOST/games"
    }

    private data class LobbyGame(val gameId: String, val hostName: String, val address: String)
    private data class LobbyGamesMessage(
        val type: String? = null,
        val init: List<LobbyGame>? = null,
        val game: LobbyGame? = null,
        val delete: String? = null
    )

    private val jmDNS: JmDNS = JmDNS.create()
    private val gson = Gson()

    fun discover(timeoutMs: Long = 800): List<LocalGame> {
        val lanGames = jmDNS.list(SERVICE_TYPE, timeoutMs).mapNotNull { info ->
            val hostAddress = info.inet4Addresses.firstOrNull()?.hostAddress
                ?: info.inetAddresses.firstOrNull()?.hostAddress
                ?: return@mapNotNull null
            val gameId = info.propertyNames.toList().firstOrNull { it.lowercase() == "gameid" }?.let { info.getPropertyString(it) }
            LocalGame(info.name, hostAddress, info.port, gameId)
        }

        val lobbyGames = discoverLobbyGames(timeoutMs)

        val lanGameIds = lanGames.mapNotNull { it.gameId }.toSet()
        val filteredLobbyGames = lobbyGames.filter { it.gameId !in lanGameIds }
        return (lanGames + filteredLobbyGames).sortedBy { it.name.lowercase() }
    }

    private fun discoverLobbyGames(timeoutMs: Long): List<LocalGame> {
        val lobbyTimeoutMs = timeoutMs.coerceIn(250, 2_000)
        return try {
            val connectLatch = CountDownLatch(1)
            val openSucceeded = AtomicBoolean(false)
            val discovered = mutableListOf<LobbyGame>()

            val wsClient = object : WebSocketClient(URI(LOBBY_GAMES_WS_URL)) {
                override fun onOpen(handshakedata: ServerHandshake) {
                    println("opened gamnes")
                    openSucceeded.set(true)
                    connectLatch.countDown()
                }

                override fun onMessage(message: String) {
                    try {
                        val parsed = gson.fromJson(message, LobbyGamesMessage::class.java)
                        println(message)
                        if (parsed.type != "games") return
                        parsed.init?.let { init ->
                            synchronized(discovered) {
                                discovered.clear()
                                discovered.addAll(init)
                            }
                        }
                        parsed.game?.let { game ->
                            synchronized(discovered) {
                                val idx = discovered.indexOfFirst { it.gameId == game.gameId }
                                if (idx >= 0) {
                                    discovered[idx] = game
                                } else {
                                    discovered.add(game)
                                }
                            }
                        }
                        parsed.delete?.let { gameId ->
                            synchronized(discovered) {
                                discovered.removeAll { it.gameId == gameId }
                            }
                        }
                    } catch (_: Exception) { }
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    connectLatch.countDown()
                }

                override fun onError(ex: Exception) {
                    connectLatch.countDown()
                }
            }

            wsClient.connect()
            connectLatch.await(lobbyTimeoutMs, TimeUnit.MILLISECONDS)
            if (!openSucceeded.get()) {
                wsClient.close()
                return emptyList()
            }
            // stay connected for the remainder of the timeout to accumulate messages
            Thread.sleep(lobbyTimeoutMs)
            wsClient.closeBlocking()
            synchronized(discovered) {
                discovered.map {
                    val parts = it.address.split(":")
                    val host = parts.getOrNull(0) ?: "localhost"
                    val port = parts.getOrNull(1)?.toIntOrNull() ?: 25567
                    LocalGame(it.hostName, host, port, it.gameId)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun close() {
        try {
            jmDNS.close()
        } catch (e: IOException) {
            throw RuntimeException("Failed to close local game discovery", e)
        }
    }
}
