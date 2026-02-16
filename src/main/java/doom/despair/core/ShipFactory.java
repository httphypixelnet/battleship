package doom.despair.core;

import doom.despair.Ship;
import doom.despair.server.Board;
import doom.despair.ships.Carrier;
import doom.despair.ships.ShipType;

public class ShipFactory {
    public static <T extends Ship> T createShip(ShipType type) {
        try {
            Ship ship;
            switch (type) {
                case AIRCRAFT_CARRIER: {
                    ship = new Carrier();
                }
                default: {
                    ship = null;
                    throw new Exception("Invalid ship type");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
