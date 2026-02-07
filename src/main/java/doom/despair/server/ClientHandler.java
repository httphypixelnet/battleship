package doom.despair.server;

import doom.despair.core.GameEvent;
import doom.despair.core.ServerGameEvent;
import doom.despair.core.events.ValidEvents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler {
    private final BattleshipServer server;
    private final Socket socket;
    public ClientHandler(BattleshipServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }
    public void handle() {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                try {
                    String[] s = line.split(";;");
                    int eventType = Integer.parseInt(s[0]);
                    Class<? extends ServerGameEvent> c = ValidEvents.SERVER_EVENTS.get(eventType);
                    if (c != null) {
                        ServerGameEvent event = GameEvent.deserialize(s[1], c);
                        server.queueEvent(event);
                    }
                } catch (NumberFormatException n) {
                    n.printStackTrace();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
