package doom.despair.core;

import doom.despair.Player;

public non-sealed abstract class ServerGameEvent extends GameEvent {
    protected Player player;
    protected String nested;
    public abstract void handle(ServerContext context);
}
