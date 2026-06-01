package doom.despair.core.events

import doom.despair.Player
import doom.despair.client.Client
import doom.despair.core.ClientContext
import doom.despair.core.GameEvent
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ClientImplementationTest {
    @Test
    fun `client context exposes provided player`() {
        val player = Player("client-player")

        val context = ClientContext(Client(player))

        assertSame(player, context.client.player)
    }

    @Test
    fun `register events includes client ship create event`() {
        ValidEvents.CLIENT_EVENTS.clear()
        ValidEvents.SERVER_EVENTS.clear()

        ValidEvents.registerEvents()

        assertEquals(ShipCreateEvent.ClientShipCreateEvent::class.java, ValidEvents.CLIENT_EVENTS[0])
    }

    @Test
    fun `client ship create event deserializes and handles without exception`() {
        val payload = """{"message":"hello-from-server"}"""
        val event = GameEvent.deserialize(payload, ShipCreateEvent.ClientShipCreateEvent::class.java)
        val context = ClientContext(Client(Player("client-player")))

        assertEquals("hello-from-server", event.message)
        assertDoesNotThrow { event.handle(context) }
    }
}
