# Codebase Review Notes

- `src/main/java/doom/despair/Main.java`: `main()` is not `public static void main(String[] args)`, so it will not be a valid JVM entry point.
- `src/main/java/doom/despair/server/BattleshipServer.java`: constructor calls `start()`, and `Main` calls `start()` again, causing double-start; `start()` blocks in the main loop, so the constructor never returns cleanly.
- `src/main/java/doom/despair/server/BattleshipServer.java`: `activeThreads` and `playerBoards` are mutated across threads without synchronization; `HashMap` and a plain `int` are not thread-safe and can race.
- `src/main/java/doom/despair/server/BattleshipServer.java`: server socket is never closed; loop can exit without releasing the port.
- `src/main/java/doom/despair/server/ClientHandler.java`: `line.split(";;")` assumes two parts; missing payload leads to `ArrayIndexOutOfBoundsException` not handled, and the socket is not explicitly closed after handling.
- `src/main/java/doom/despair/core/events/ValidEvents.java`: `registerEvents()` is never called anywhere, so `SERVER_EVENTS`/`CLIENT_EVENTS` remain empty and no events deserialize in `ClientHandler`.
- `src/main/java/doom/despair/core/ShipFactory.java`: method has no `return` statement on any path and the `switch` falls through; this should not compile as written.
- `src/main/java/doom/despair/core/events/ShipCreateEvent.java`: references `EventContext`, which does not exist in the codebase; this will not compile.
- `src/main/java/doom/despair/server/Board.java`: `placeShip` is empty, so ships are never placed on the board.
- `src/main/java/doom/despair/Ship.java`: `uuid` is never assigned, `health` is never decremented, and `sunk` only updates when `tick()` is called, so ship state never changes in practice.
- `src/main/java/doom/despair/core/PlayerManagedObject.java`: `player` is never set (no constructor/setter), so `getPlayer()` always returns `null` unless reflection mutates it.

