package doom.despair;

import doom.despair.core.PlayerManagedObject;

import java.util.UUID;

public abstract class Ship extends PlayerManagedObject {
    private UUID uuid;
    private boolean sunk = false;
    private int health;
    public Ship(int size) {
        this.health = size;
    }
    public void tick() {
        if (health == 0) {
            sunk = true;
        }
    }
}
