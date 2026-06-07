package doom.despair.integration

import doom.despair.Player
import doom.despair.client.Client
import doom.despair.core.CellView
import doom.despair.server.BattleshipServer
import doom.despair.ships.ShipType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalGameFlowTest {
    @Test
    fun `host and guest can complete a local game`() {
        val server = BattleshipServer(autoStart = false, port = 25568)
        server.start()

        val hostClient = Client(Player("Host"))
        val guestClient = Client(Player("Guest"))

        try {
            val hostRemote = hostClient.connect("127.0.0.1:25568")
            val guestRemote = guestClient.connect("127.0.0.1:25568")

            val created = hostRemote.createGame("Host")
            val hostId = created.playerId!!

            val joined = guestRemote.joinGame("Guest")
            val guestId = joined.playerId!!

            hostRemote.placeShip(hostId, ShipType.AIRCRAFT_CARRIER, 0, 0, true)
            hostRemote.placeShip(hostId, ShipType.DESTROYER, 0, 2, false)
            hostRemote.placeShip(hostId, ShipType.SUBMARINE, 2, 4, true)
            guestRemote.placeShip(guestId, ShipType.AIRCRAFT_CARRIER, 0, 0, true)
            guestRemote.placeShip(guestId, ShipType.DESTROYER, 0, 2, false)
            guestRemote.placeShip(guestId, ShipType.SUBMARINE, 2, 4, true)

            val initialState = hostRemote.getState(hostId)
            assertEquals(hostId, initialState.currentTurnPlayerId)
            assertShipSegment(initialState.playerBoard, 0, 0, ShipType.AIRCRAFT_CARRIER, 1)
            assertShipSegment(initialState.playerBoard, 0, 3, ShipType.DESTROYER, 2)
            assertShipSegment(initialState.playerBoard, 3, 4, ShipType.SUBMARINE, 2)

            hostRemote.fireShot(hostId, 0, 0)
            assertThrows(IllegalStateException::class.java) {
                guestRemote.fireShot(guestId, 9, 9)
            }
            hostRemote.fireShot(hostId, 9, 9)
            guestRemote.fireShot(guestId, 9, 9)
            hostRemote.fireShot(hostId, 1, 0)
            hostRemote.fireShot(hostId, 2, 0)
            hostRemote.fireShot(hostId, 3, 0)
            hostRemote.fireShot(hostId, 0, 2)
            hostRemote.fireShot(hostId, 0, 3)
            hostRemote.fireShot(hostId, 0, 4)
            hostRemote.fireShot(hostId, 2, 4)
            val finalShot = hostRemote.fireShot(hostId, 3, 4)

            assertTrue(finalShot.won)
            val finalState = hostRemote.getState(hostId)
            assertEquals(hostId, finalState.winnerPlayerId)

            hostRemote.close()
            guestRemote.close()
        } finally {
            hostClient.disconnect()
            guestClient.disconnect()
            server.stop()
        }
    }

    private fun assertShipSegment(
        board: List<CellView>,
        x: Int,
        y: Int,
        shipType: ShipType,
        segment: Int
    ) {
        val cell = board.firstOrNull { it.x == x && it.y == y }
        assertNotNull(cell, "Expected cell at ($x,$y)")
        assertEquals(shipType, cell!!.shipType)
        assertEquals(segment, cell.segment)
    }
}

