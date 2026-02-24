package doom.despair.core;

import doom.despair.Ship;
import doom.despair.server.Board;
import doom.despair.ships.Carrier;
import doom.despair.ships.ShipType;

public class ShipFactory {
    public static Ship createShip(ShipType type) {
        try {
            switch (type) {
                case AIRCRAFT_CARRIER: {
                    return new Carrier();
                }
                default: {
                    throw new Exception("Invalid ship type");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static  Class<? extends Ship> get(ShipType type) {
        try {
            switch (type) {
                case AIRCRAFT_CARRIER: {
                    return Carrier.class;
                }
                default: {
                    throw new Exception("Invalid ship type");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
