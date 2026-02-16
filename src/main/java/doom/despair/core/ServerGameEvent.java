package doom.despair.core;

import doom.despair.Player;

public non-sealed abstract class ServerGameEvent extends GameEvent {
    public Player player;
    private String innerInfo;
    public abstract void handle(ServerContext context);
}
