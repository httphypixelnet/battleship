package doom.despair.core.events;

import doom.despair.Ship;
import doom.despair.core.*;
import doom.despair.server.Board;
import doom.despair.ships.ShipType;

public class ShipCreateEvent {
    public static class ShipCreateContext {
        public ShipType type;
        public
    }
    public static class ServerShipCreateEvent extends ServerGameEvent {
        @Override
        public void handle(ServerContext ctx) {
            Board playerBoard = ctx.getServer().getBoardForPlayer(ctx.getPlayer());

        }
    }
    public static class ClientShipCreateEvent extends ClientGameEvent {
        @Override
        public void handle(ClientContext context) {

        }
    }
}
