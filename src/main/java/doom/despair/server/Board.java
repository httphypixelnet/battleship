package doom.despair.server;

import doom.despair.Ship;

import java.util.HashMap;
import java.util.Map;

public class Board {
    public record Coordinate(int x, int y) {}
    private final Map<Coordinate, Ship> grid = new HashMap<>();
    public Ship getShipAt(Coordinate coord) { return grid.get(coord); }
    public void placeShip(Ship ship) {

    }

}