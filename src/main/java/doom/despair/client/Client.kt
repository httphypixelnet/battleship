package doom.despair.client

import doom.despair.Player
import doom.despair.core.ClientContext
import doom.despair.core.ClientGameEvent
import java.util.concurrent.ConcurrentLinkedQueue

class Client(val player: Player) {
    private val eventQueue: ConcurrentLinkedQueue<ClientGameEvent> = ConcurrentLinkedQueue<ClientGameEvent>()
    private var remoteServer: RemoteServer? = null

    fun connect(address: String): RemoteServer {
        val handler = ServerHandler(address, this)
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
