package doom.despair.client

import com.google.gson.Gson
import doom.despair.core.Packet
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.io.Closeable
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue

class ServerHandler(
    private val address: String,
    private val gameId: String?,
    private val client: Client
) : Closeable {
    private val gson = Gson()
    private val responseQueue = LinkedBlockingQueue<Packet>()
    @Volatile
    private var closed = false
    @Volatile
    private var stateUpdateHandler: ((Packet) -> Unit)? = null
    private var wsClient: WebSocketClient? = null

    private companion object {
        val LOBBY_HOST = System.getProperty("lobby.host") ?: "example.com:25567"
    }

    init {
        var connected = false
        val directUriStr = if (address.contains(":")) "ws://$address" else "ws://$address:25567"
        try {
            val directClient = createWsClient(URI(directUriStr))
            if (directClient.connectBlocking()) {
                wsClient = directClient
                connected = true
            }
        } catch (_: Exception) {
        }

        if (!connected && gameId != null) {
            // Debug: show which scheme will be used for relay
            val scheme = if (LOBBY_HOST.startsWith("localhost") || LOBBY_HOST.startsWith("127.0.0.1")) "ws" else "wss"
            println("[DEBUG] Relay scheme: $scheme")
            val relayUriStr = "$scheme://$LOBBY_HOST/relay?role=client&gameId=$gameId"
            println("[DEBUG] Relay URI: $relayUriStr")
            try {
                val relayClient = createWsClient(URI(relayUriStr))
                if (relayClient.connectBlocking()) {
                    wsClient = relayClient
                    connected = true
                }
            } catch (e: Exception) {
                throw RuntimeException("Direct connection failed, and fallback to relay failed", e)
            }
        }

        if (!connected) {
            throw RuntimeException("Could not connect to $address")
        }
    }

    private fun createWsClient(uri: URI): WebSocketClient {
        // Create client and configure proxy if system properties are set
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPortStr = System.getProperty("http.proxyPort")
        val proxy = if (proxyHost != null && proxyPortStr != null) {
            java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(proxyHost, proxyPortStr.toInt()))
        } else null

        val client = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake) {}

            override fun onMessage(message: String) {
                val packet = Packet.deserialize(message)
                if (packet.type == "STATE_UPDATE") {
                    stateUpdateHandler?.invoke(packet)
                } else {
                    responseQueue.put(packet)
                }
            }

            override fun onClose(code: Int, reason: String, remote: Boolean) {
                if (!closed) {
                    responseQueue.offer(Packet("ERROR", """{"ok":false,"message":"Connection closed"}"""))
                }
            }

            override fun onError(ex: Exception) {}
        }
        // Apply proxy if available
        if (proxy != null) {
            try {
                client.setProxy(proxy)
                println("[DEBUG] WebSocket proxy set to $proxyHost:$proxyPortStr")
            } catch (e: Exception) {
                println("[DEBUG] Failed to set WebSocket proxy: ${e.message}")
            }
        }
        return client
    }

    @Synchronized
    fun request(type: String, payload: Any? = null): Packet {
        try {
            val packet = Packet(type = type, payload = payload?.let { gson.toJson(it) })
            val ws = wsClient ?: throw IllegalStateException("Not connected")
            ws.send(packet.serialize())
            return responseQueue.take()
        } catch (e: Exception) {
            throw RuntimeException("Network request failed", e)
        }
    }

    fun onStateUpdate(handler: ((Packet) -> Unit)?) {
        stateUpdateHandler = handler
    }

    override fun close() {
        closed = true
        wsClient?.close()
    }
}
