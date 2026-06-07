import type { LobbyGame } from "./types";

const LOBBY_HOST = import.meta.env.VITE_LOBBY_HOST ?? "example.com:25567";
const LOBBY_SCHEME = LOBBY_HOST.startsWith("localhost") || LOBBY_HOST.startsWith("127.0.0.1")
  ? "http"
  : "https";
const LOBBY_WS_SCHEME = LOBBY_SCHEME === "http" ? "ws" : "wss";

export const lobbyHttpBase = `${LOBBY_SCHEME}://${LOBBY_HOST}`;
export const lobbyWsBase = `${LOBBY_WS_SCHEME}://${LOBBY_HOST}`;

export async function fetchGames(): Promise<LobbyGame[]> {
  const res = await fetch(`${lobbyHttpBase}/games`);
  if (!res.ok) throw new Error(`Lobby returned ${res.status}`);
  return res.json() as Promise<LobbyGame[]>;
}

export function openRelaySocket(gameId: string, timeoutMs = 5000): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const url = `${lobbyWsBase}/relay?role=client&gameId=${encodeURIComponent(gameId)}`;
    const ws = new WebSocket(url);

    const timer = setTimeout(() => {
      ws.close();
      reject(new Error("Relay connection timed out"));
    }, timeoutMs);

    ws.addEventListener("open", () => {
      clearTimeout(timer);
      resolve(ws);
    });

    ws.addEventListener("error", (_ev) => {
      clearTimeout(timer);
      reject(new Error("Relay WebSocket error"));
    });
  });
}
