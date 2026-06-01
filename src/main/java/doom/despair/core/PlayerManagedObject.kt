package doom.despair.core

import doom.despair.Player

open class PlayerManagedObject : GameManagedObject() {
    @JvmField
    val player: Player? = null
}
