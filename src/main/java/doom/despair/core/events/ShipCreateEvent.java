package doom.despair.core.events;

import com.google.gson.Gson;
import doom.despair.core.*;
import doom.despair.server.Board;
import doom.despair.ships.ShipType;

public class ShipCreateEvent {
    private static class ShipCreateContext {
        private ShipType type;
    }

    public static class ServerShipCreateEvent extends ServerGameEvent {
        @Override
        public void handle(ServerContext ctx) {
            Board playerBoard = ctx.server().getBoardForPlayer(this.player);
            ShipCreateContext shipCreateContext = new Gson().fromJson(this.nested, ShipCreateContext.class);
            playerBoard.placeShip(shipCreateContext.type);
        }
    }

    public static class ClientShipCreateEvent extends ClientGameEvent {
        @Override
        public void handle(ClientContext context) {
        }
    }
}
