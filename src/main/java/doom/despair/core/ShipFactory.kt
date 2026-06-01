package doom.despair.core

import doom.despair.Ship
import doom.despair.ships.Carrier
import doom.despair.ships.Destroyer
import doom.despair.ships.ShipType
import doom.despair.ships.Submarine

object ShipFactory {
    fun createShip(type: ShipType): Ship {
        return when (type) {
            ShipType.AIRCRAFT_CARRIER -> Carrier()
            ShipType.DESTROYER -> Destroyer()
            ShipType.SUBMARINE -> Submarine()
        }
    }

    fun get(type: ShipType?): Class<out Ship?> {
        return when (type) {
            ShipType.AIRCRAFT_CARRIER -> Carrier::class.java
            ShipType.DESTROYER -> Destroyer::class.java
            ShipType.SUBMARINE -> Submarine::class.java
            null -> throw RuntimeException("Invalid ship type: null")
        }
    }
}
