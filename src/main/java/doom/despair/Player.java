package doom.despair;

import java.util.HashMap;
import java.util.UUID;

public class Player {
    public String displayName;
    public UUID uuid;
    public int shipsRemaining;
    public HashMap<UUID, Ship> ships;
    public Player(String displayName) {
        this.displayName = displayName;
        this.uuid = UUID.randomUUID();
        this.shipsRemaining = 5;
        this.ships = new HashMap<>(shipsRemaining);
    }
}
