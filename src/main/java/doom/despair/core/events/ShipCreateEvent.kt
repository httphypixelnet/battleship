package doom.despair.core.events

import com.google.gson.*;
import doom.despair.core.ClientContext
import doom.despair.core.ClientGameEvent
import doom.despair.core.ServerContext
import doom.despair.core.ServerGameEvent
import doom.despair.server.Board
import doom.despair.ships.ShipType

class ShipCreateEvent {
    private class ShipCreateContext {
        val type: ShipType = ShipType.AIRCRAFT_CARRIER
        val start: Board.Coordinate = Board.Coordinate(0, 0)
        val horizontal = false
    }

    class ServerShipCreateEvent : ServerGameEvent() {
        override fun handle(context: ServerContext) {
            val playerBoard: Board = context.server.getBoardForPlayer(this.player)
            val scc: ShipCreateContext = Gson().fromJson(this.nested, ShipCreateContext::class.java)
            playerBoard.placeShip(scc.type, scc.start, scc.horizontal)
        }
    }

    class ClientShipCreateEvent : ClientGameEvent() {
        override fun handle(context: ClientContext) {

        }
    }
}
