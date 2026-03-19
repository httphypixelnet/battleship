package doom.despair;

import doom.despair.core.PlayerManagedObject;

import java.util.UUID;

public abstract class Ship extends PlayerManagedObject {
    private UUID uuid;
    private final int size;
    public Ship(int size) {
        this.size = size;
    }
}
