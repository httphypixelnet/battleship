package doom.despair.core;

import doom.despair.Player;

public class ClientContext {
    private final Player player;
    public Player getPlayer() { return player; }
    public ClientContext(Player player) {
        this.player = player;
    }
}
