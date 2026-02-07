package doom.despair.core;

import doom.despair.Ship;
import doom.despair.server.Board;

public class ShipFactory {
    public static <T extends Ship> T createShip(Board.Coordinate startingCoord, Class<T> clazz) {
        try {
            return clazz.getConstructor(Board.Coordinate.class).newInstance(startingCoord);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
