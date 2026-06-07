package doom.despair.ships

enum class ShipType {
    DESTROYER,
    AIRCRAFT_CARRIER,
    SUBMARINE;
    companion object {
         fun shipLength(type: ShipType): Int {
            return when (type) {
                AIRCRAFT_CARRIER -> 4
                DESTROYER -> 3
                SUBMARINE -> 2
            }
        }
    }
    public fun shipLength(): Int {
        return when (this) {
            AIRCRAFT_CARRIER -> 4
            DESTROYER -> 3
            SUBMARINE -> 2
        }
    }
}
