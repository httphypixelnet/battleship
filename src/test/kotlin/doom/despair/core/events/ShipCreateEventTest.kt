package doom.despair.core.events

import doom.despair.Player
import doom.despair.core.ServerContext
import doom.despair.server.BattleshipServer
import doom.despair.server.Board
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ShipCreateEventTest {
    @Test
    fun `server event places a ship using nested payload`() {
        val event = ShipCreateEvent.ServerShipCreateEvent()
        val player = Player("tester")
        setField(event, "player", player)
        setField(
            event,
            "nested",
            """{"type":"AIRCRAFT_CARRIER","start":{"x":2,"y":3},"horizontal":true}"""
        )

        val server = BattleshipServer(false)
        event.handle(ServerContext(server))

        val board = server.getBoardForPlayer(player)
        assertNotNull(board.getShipAt(Board.Coordinate(2, 3)))
        assertNotNull(board.getShipAt(Board.Coordinate(3, 3)))
        assertNotNull(board.getShipAt(Board.Coordinate(4, 3)))
        assertNotNull(board.getShipAt(Board.Coordinate(5, 3)))
    }

    @Test
    fun `server event propagates invalid ship type errors`() {
        val event = ShipCreateEvent.ServerShipCreateEvent()
        val player = Player("tester")
        setField(event, "player", player)
        setField(
            event,
            "nested",
            """{"type":"INVALID_SHIP","start":{"x":0,"y":0},"horizontal":false}"""
        )

        val server = BattleshipServer(false)

        assertThrows(RuntimeException::class.java) {
            event.handle(ServerContext(server))
        }
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException(fieldName)
    }
}
