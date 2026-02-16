package doom.despair.server;

import doom.despair.Player;
import doom.despair.core.GameEvent;
import doom.despair.core.ServerContext;
import doom.despair.core.ServerGameEvent;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BattleshipServer {
    private final ConcurrentLinkedQueue<ServerGameEvent> eventQueue;
    private int tick;
    private int maxTicks;
    private final int MAX_THREADS = 4;
    private int activeThreads = 0;
    private volatile boolean running = true;

    public BattleshipServer() {
        this.eventQueue = new ConcurrentLinkedQueue<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> running = false));

        start();
    }
    public void queueEvent(ServerGameEvent event) {
        eventQueue.add(event);
    }
    private final HashMap<Player, Board> playerBoards = new HashMap<>();
    public Board getBoardForPlayer(Player player) { return playerBoards.computeIfAbsent(player, _ -> new Board()); }
    public void start() {
        ServerSocket serverSocket = null;

        try {
            serverSocket = new ServerSocket(25567);
        }
        catch (Exception _) {
            System.out.println("Could not listen on port: 25567");
            running = false;
        }

        Thread socketListenerThread = getThread(serverSocket);
        socketListenerThread.start();

        // main server loop
        final double NS_PER_TICK = 1_000_000_000.0 / 20.0;
        long lastTime = System.nanoTime();
        double delta = 0;

        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / NS_PER_TICK;
            lastTime = now;

            while (delta >= 1) {
                gameLoop();
                delta--;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private Thread getThread(ServerSocket serverSocket) {
        final ServerSocket finalServerSocket = serverSocket;
        return new Thread(() -> {
            while (running && finalServerSocket != null) {
                try {
                    Socket clientSocket = finalServerSocket.accept();
                    if (activeThreads < MAX_THREADS) {
                        activeThreads++;
                        new Thread(() -> {
                            try {
                                ClientHandler clientHandler = new ClientHandler(this, clientSocket);
                                clientHandler.handle();
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                activeThreads--;
                            }
                        }).start();
                    } else {
                        clientSocket.close();
                    }
                } catch (Exception e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void gameLoop() {
        ServerGameEvent ev = eventQueue.poll();
        if (ev == null) {
            // nothing to process
            return;
        }

        ServerContext ctx = new ServerContext(this, ev.player);
        ev.handle(ctx);
    }
}
