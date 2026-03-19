package doom.despair.server;

import doom.despair.Ship;
import doom.despair.core.ShipFactory;
import doom.despair.ships.ShipType;

import java.util.HashMap;
import java.util.Map;

public class Board {
    public record Coordinate(int x, int y) {}
    private final Map<Coordinate, Ship> grid = new HashMap<>();
    public Ship getShipAt(Coordinate coord) { return grid.get(coord); }
    public boolean placeShip(ShipType type, Coordinate start, boolean horizontal) {
        Class<? extends Ship> ship = ShipFactory.get(type);

        for (int i = 0; i < size; i++) {
            Coordinate coord = horizontal ? new Coordinate(start.x() + i, start.y()) : new Coordinate(start.x(), start.y() + i);
            if (grid.containsKey(coord)) return false; // Collision
        }
        for (int i = 0; i < size; i++) {
            Coordinate coord = horizontal ? new Coordinate(start.x() + i, start.y()) : new Coordinate(start.x(), start.y() + i);
            grid.put(coord, ship);
        }
        return true;
    }

}