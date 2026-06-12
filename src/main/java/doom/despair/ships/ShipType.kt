package doom.despair.ships

enum class ShipType {
    DESTROYER,
    AIRCRAFT_CARRIER,
    SUBMARINE;
    companion object {
         fun shipLength(type: ShipType): Int {
            return type.shipLength()
        }
    }
    fun shipLength(): Int {
        return when (this) {
            AIRCRAFT_CARRIER -> 5
            DESTROYER -> 3
            SUBMARINE -> 2
        }
    }
}
