package doom.despair.core.events

import doom.despair.core.ClientContext
import doom.despair.core.ClientGameEvent
import doom.despair.core.ServerContext
import doom.despair.core.ServerGameEvent


class PingEvent {
    class ServerPingEvent : ServerGameEvent() {
        override fun handle(context: ServerContext) {
            // no-op
        }
    }

    class ClientPingEvent : ClientGameEvent() {
        override fun handle(context: ClientContext) {
            // no-op
        }
    }
}
