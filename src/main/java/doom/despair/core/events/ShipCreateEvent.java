package doom.despair.core.events;

import doom.despair.core.ClientContext;
import doom.despair.core.ClientGameEvent;
import doom.despair.core.ServerContext;
import doom.despair.core.ServerGameEvent;


public class ShipCreateEvent {
    public static class ServerShipCreateEvent extends ServerGameEvent {
        @Override
        public void handle(ServerContext context) {

        }
    }
    public static class ClientShipCreateEvent extends ClientGameEvent {
        @Override
        public void handle(ClientContext context) {

        }
    }
}
