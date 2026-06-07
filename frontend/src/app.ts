import { ServerConnection } from "./connection";
import { GameClient } from "./gameClient";
import { fetchGames, openRelaySocket } from "./lobby";
import type { GameStateDto, LobbyGame, ShipType } from "./types";

// ── App state ────────────────────────────────────────────────────────────────

export type AppPhase =
  | "HOME"
  | "PLACEMENT"
  | "BATTLE"
  | "GAME_OVER";

export interface AppState {
  phase: AppPhase;
  playerName: string;
  playerId: string | null;
  gameState: GameStateDto | null;
  lobbyGames: LobbyGame[];
  error: string | null;
  selectedShip: ShipType;
  shipOrientation: "horizontal" | "vertical";
}

export type StateListener = (state: AppState) => void;

// ── App controller ────────────────────────────────────────────────────────────

/**
 * Central application controller — owns all state transitions and exposes
 * simple async actions.  No rendering logic here; attach a StateListener to
 * drive the UI layer.
 */
export class App {
  private state: AppState = {
    phase: "HOME",
    playerName: "",
    playerId: null,
    gameState: null,
    lobbyGames: [],
    error: null,
    selectedShip: "AIRCRAFT_CARRIER",
    shipOrientation: "horizontal",
  };

  private listeners: StateListener[] = [];
  private client: GameClient | null = null;

  // ── Subscribe ───────────────────────────────────────────────────────────────

  subscribe(fn: StateListener): () => void {
    this.listeners.push(fn);
    fn(this.state); // emit immediately with current state
    return () => {
      this.listeners = this.listeners.filter((l) => l !== fn);
    };
  }

  private emit(): void {
    const snap = { ...this.state };
    for (const fn of this.listeners) fn(snap);
  }

  private patch(partial: Partial<AppState>): void {
    this.state = { ...this.state, ...partial };
    this.emit();
  }

  getState(): AppState {
    return { ...this.state };
  }

  // ── Home actions ─────────────────────────────────────────────────────────────

  setPlayerName(name: string): void {
    this.patch({ playerName: name });
  }

  async refreshLobby(): Promise<void> {
    this.patch({ error: null });
    try {
      const games = await fetchGames();
      this.patch({ lobbyGames: games });
    } catch (e) {
      this.patch({ error: (e as Error).message });
    }
  }

  /**
   * Create a new game.  Connects to the lobby relay for the newly registered
   * game once the server advertises it (the host connects via control socket).
   * For "create", the Kotlin server itself is the host; the browser always
   * uses the relay path.
   */
  async createGame(gameId: string): Promise<void> {
    this.patch({ error: null });
    try {
      const ws = await openRelaySocket(gameId);
      this.setupClient(ws);
      const resp = await this.client!.createGame(this.state.playerName || "Player");
      this.patch({ playerId: resp.playerId ?? null, phase: "PLACEMENT" });
      await this.syncState();
    } catch (e) {
      this.patch({ error: (e as Error).message });
    }
  }

  /**
   * Join an existing game from the lobby list via relay.
   */
  async joinGame(game: LobbyGame): Promise<void> {
    this.patch({ error: null });
    try {
      const ws = await openRelaySocket(game.gameId);
      this.setupClient(ws);
      const resp = await this.client!.joinGame(this.state.playerName || "Player");
      this.patch({ playerId: resp.playerId ?? null, phase: "PLACEMENT" });
      await this.syncState();
    } catch (e) {
      this.patch({ error: (e as Error).message });
    }
  }

  // ── Placement actions ────────────────────────────────────────────────────────

  selectShip(type: ShipType): void {
    this.patch({ selectedShip: type });
  }

  toggleOrientation(): void {
    this.patch({
      shipOrientation:
        this.state.shipOrientation === "horizontal" ? "vertical" : "horizontal",
    });
  }

  async placeShip(x: number, y: number): Promise<void> {
    if (!this.client || !this.state.playerId) return;
    try {
      await this.client.placeShip(
        this.state.playerId,
        this.state.selectedShip,
        x,
        y,
        this.state.shipOrientation === "horizontal"
      );
      await this.syncState();
    } catch (e) {
      this.patch({ error: (e as Error).message });
    }
  }

  // ── Battle actions ────────────────────────────────────────────────────────────

  async fireShot(x: number, y: number): Promise<void> {
    if (!this.client || !this.state.playerId) return;
    try {
      await this.client.fireShot(this.state.playerId, x, y);
      await this.syncState();
    } catch (e) {
      this.patch({ error: (e as Error).message });
    }
  }

  // ── Disconnect / reset ────────────────────────────────────────────────────────

  disconnect(): void {
    this.client?.close();
    this.client = null;
    this.patch({
      phase: "HOME",
      playerId: null,
      gameState: null,
      error: null,
    });
  }

  // ── Internal ──────────────────────────────────────────────────────────────────

  private setupClient(ws: WebSocket): void {
    this.client?.close();
    const conn = new ServerConnection(ws);
    this.client = new GameClient(conn);

    this.client.onStateUpdate((gameState) => {
      const phase = derivePhase(gameState, this.state.phase);
      this.patch({ gameState, phase });
    });

    conn.onClose(() => {
      if (this.state.phase !== "GAME_OVER") {
        this.patch({ error: "Connection to server lost." });
      }
    });
  }

  private async syncState(): Promise<void> {
    if (!this.client || !this.state.playerId) return;
    try {
      const gameState = await this.client.getState(this.state.playerId);
      const phase = derivePhase(gameState, this.state.phase);
      this.patch({ gameState, phase });
    } catch (e) {
      this.patch({ error: (e as Error).message });
    }
  }
}

// ── Phase derivation ──────────────────────────────────────────────────────────

function derivePhase(state: GameStateDto, current: AppPhase): AppPhase {
  if (state.winnerPlayerId != null) return "GAME_OVER";
  if (state.playerShipsPlaced && !state.waitingForOpponent && state.opponentShipsPlaced) {
    return "BATTLE";
  }
  if (current === "BATTLE") return "BATTLE"; // stay in battle until game over
  return "PLACEMENT";
}

// ── Singleton export ──────────────────────────────────────────────────────────

export const app = new App();
