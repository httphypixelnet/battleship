package doom.despair.core;

public non-sealed abstract class ClientGameEvent extends GameEvent {

    public abstract void handle(ClientContext context);
}
