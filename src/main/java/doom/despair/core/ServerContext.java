package doom.despair.core;

import doom.despair.Player;
import doom.despair.server.BattleshipServer;

public class ServerContext {
    private final BattleshipServer server;
    private final Player player;
    public BattleshipServer getServer() { return server; }
    public ServerContext(BattleshipServer server, Player player) {
        this.server = server;
        this.player = player;
    }
    public Player getPlayer() {
        return player;
    }
}
