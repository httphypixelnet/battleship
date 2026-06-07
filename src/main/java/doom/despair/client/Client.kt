package doom.despair.client

import doom.despair.Player
import doom.despair.core.ClientContext
import doom.despair.core.ClientGameEvent
import java.util.concurrent.ConcurrentLinkedQueue

class Client(val player: Player) {
    init {
        // Debug proxy settings
        println("[DEBUG] http.proxyHost=" + System.getProperty("http.proxyHost"))
        println("[DEBUG] http.proxyPort=" + System.getProperty("http.proxyPort"))
        println("[DEBUG] https.proxyHost=" + System.getProperty("https.proxyHost"))
        println("[DEBUG] https.proxyPort=" + System.getProperty("https.proxyPort"))
    }
    private val eventQueue: ConcurrentLinkedQueue<ClientGameEvent> = ConcurrentLinkedQueue<ClientGameEvent>()
    private var remoteServer: RemoteServer? = null



    fun connect(address: String, gameId: String? = null): RemoteServer {
        println("[DEBUG] Direct URI: " + (if (address.contains(":")) "ws://$address" else "ws://$address:25567"))
        val handler = ServerHandler(address, gameId, this)
        val remote = RemoteServer(handler)
        remoteServer = remote
        return remote
    }

    fun disconnect() {
        remoteServer?.close()
        remoteServer = null
    }

    fun queueEvent(event: ClientGameEvent) {
        eventQueue.add(event)
    }

    fun gameLoop() {
        val ev = eventQueue.poll() ?: // nothing to process
        return

        val ctx = ClientContext(this)
        ev.handle(ctx)
    }
}
