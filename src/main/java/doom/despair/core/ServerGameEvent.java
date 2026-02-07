package doom.despair.core;

import doom.despair.Player;

public non-sealed abstract class ServerGameEvent extends GameEvent {
    public Player player;
    public abstract void handle(ServerContext context);
}
