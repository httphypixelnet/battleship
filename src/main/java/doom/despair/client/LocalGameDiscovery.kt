package doom.despair.client

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.jmdns.JmDNS

class LocalGameDiscovery : Closeable {
    data class LocalGame(val name: String, val host: String, val port: Int, val gameId: String? = null) {
        val address: String
            get() = "$host:$port"

        override fun toString(): String = if (gameId != null) "$name (Lobby)" else "$name ($address)"
    }

    private companion object {
        const val SERVICE_TYPE = "_battleship._tcp.local."
        val LOBBY_HOST = System.getProperty("lobby.host") ?: "54.213.93.141"
        val LOBBY_GAMES_URL = if (LOBBY_HOST.startsWith("localhost") || LOBBY_HOST.startsWith("127.0.0.1")) {
            "http://$LOBBY_HOST/games"
        } else {
            "https://$LOBBY_HOST/games"
        }
    }

    private data class LobbyGame(val gameId: String, val hostName: String, val address: String)

//    private val jmDNS: JmDNS = JmDNS.create()

    fun discover(timeoutMs: Long = 800): List<LocalGame> {
//        val lanGames = jmDNS.list(SERVICE_TYPE, timeoutMs).mapNotNull { info ->
//            val hostAddress = info.inet4Addresses.firstOrNull()?.hostAddress
//                ?: info.inetAddresses.firstOrNull()?.hostAddress
//                ?: return@mapNotNull null
//            val gameId = info.propertyNames.toList().firstOrNull { it.lowercase() == "gameid" }?.let { info.getPropertyString(it) }
//            LocalGame(info.name, hostAddress, info.port, gameId)
//        }

        val lobbyGames = try {
            val connection = java.net.URI(LOBBY_GAMES_URL).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 500
            connection.readTimeout = 500
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<LobbyGame>>() {}.type
            Gson().fromJson<List<LobbyGame>>(json, type).map {
                val parts = it.address.split(":")
                val host = parts.getOrNull(0) ?: "localhost"
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 25567
                LocalGame(it.hostName, host, port, it.gameId)
            }
        } catch (_: Exception) {
            emptyList()
        }

//        val lanGameIds = lanGames.mapNotNull { it.gameId }.toSet()
//        val filteredLobbyGames = lobbyGames.filter { it.gameId !in lanGameIds }
//        return (lanGames + filteredLobbyGames).sortedBy { it.name.lowercase() }
        return lobbyGames.sortedBy { it.name }
    }

    override fun close() {
        try {
//            jmDNS.close()
        } catch (e: IOException) {
            throw RuntimeException("Failed to close local game discovery", e)
        }
    }
}
