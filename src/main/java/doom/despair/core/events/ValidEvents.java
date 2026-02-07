package doom.despair.core.events;

import doom.despair.core.ClientGameEvent;
import doom.despair.core.ServerGameEvent;

import java.util.HashMap;
import java.util.Map;

public class ValidEvents {
    public static final Map<Integer, Class<? extends ClientGameEvent>> CLIENT_EVENTS = new HashMap<>();
    public static final Map<Integer, Class<? extends ServerGameEvent>> SERVER_EVENTS = new HashMap<>();
    public static void registerEvents() {
        CLIENT_EVENTS.put(0, ShipCreateEvent.ClientShipCreateEvent.class);



        SERVER_EVENTS.put(0, ShipCreateEvent.ServerShipCreateEvent.class);
    }
}
