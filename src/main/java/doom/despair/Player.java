package doom.despair;

import java.util.*;

public class Player {

    private final String displayName;
    private final UUID uuid;
    final List<Ship> ships;

    public Player(String displayName) {
        this.displayName = displayName;
        this.uuid = UUID.randomUUID();
        this.ships = new ArrayList<>();
    }

    public String getDisplayName() {
        return displayName;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void addShip(Ship ship) {
        ships.add(ship);
    }

    public int getShipsRemaining() {
        return ships.size();
    }

    public boolean hasLost() {
        return getShipsRemaining() == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Player other)) return false;
        return uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
