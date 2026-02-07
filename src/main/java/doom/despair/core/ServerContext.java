package doom.despair.core;

import doom.despair.Player;
import doom.despair.server.BattleshipServer;

public class ServerContext {
    private final BattleshipServer server;
    public BattleshipServer getServer() { return server; }
    public ServerContext(BattleshipServer server) {
        this.server = server;
    }
}
