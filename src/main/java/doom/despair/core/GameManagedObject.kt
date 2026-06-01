package doom.despair.core

import java.util.*

abstract class GameManagedObject {
    val uUID: UUID = UUID.randomUUID()
    override fun hashCode(): Int {
        return uUID.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameManagedObject

        return uUID == other.uUID
    }
}
