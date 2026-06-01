package doom.despair.core

abstract class ClientGameEvent : GameEvent() {
    abstract fun handle(context: ClientContext)
}
