package doom.despair;

import doom.despair.server.BattleshipServer;

public class Main {
    void main() {
        BattleshipServer server = new BattleshipServer();
        server.start();
    }
}