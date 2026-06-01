package doom.despair.core.events

import doom.despair.core.ClientGameEvent
import doom.despair.core.ServerGameEvent
import doom.despair.core.events.ShipCreateEvent.ClientShipCreateEvent
import doom.despair.core.events.ShipCreateEvent.ServerShipCreateEvent

object ValidEvents {
    val CLIENT_EVENTS: MutableMap<Int?, Class<out ClientGameEvent>> = HashMap()
    val SERVER_EVENTS: MutableMap<Int?, Class<out ServerGameEvent>> = HashMap()
    fun registerEvents() {
        CLIENT_EVENTS[0] = ClientShipCreateEvent::class.java
        SERVER_EVENTS[0] = ServerShipCreateEvent::class.java
    }
}
