package doom.despair

import java.util.*

class Player(@JvmField val displayName: String?) {
    @JvmField
    val uuid: UUID = UUID.randomUUID()

    @JvmField
    val ships: MutableList<Ship> = ArrayList<Ship>()

    fun addShip(ship: Ship) {
        ships.add(ship)
    }

    val shipsRemaining: Int
        get() = ships.size

    fun hasLost(): Boolean {
        return this.shipsRemaining == 0
    }

    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) return true
        if (other !is Player) return false
        return uuid == other.uuid
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }
}
