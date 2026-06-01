package doom.despair.core

import doom.despair.Player

abstract class ServerGameEvent : GameEvent() {
    @JvmField
    protected var player: Player? = null
    @JvmField
    protected var nested: String? = null
    abstract fun handle(context: ServerContext)
}
