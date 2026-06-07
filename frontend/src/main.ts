import { app } from "./app";

(window as unknown as Record<string, unknown>)["__app"] = app;

app.refreshLobby();

console.log(
  "[Battleship] App initialised. Access via window.__app in DevTools.\n" +
  "UI layer not yet implemented — logic is fully wired."
);
