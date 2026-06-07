import { ServerConnection } from "./connection";
import {
  type ActionResponse,
  type CreateGameResponse,
  type FireShotResponse,
  type GameStateDto,
  type GameStateResponse,
  type JoinGameResponse,
  type Packet,
  type ShipType,
} from "./types";

export class GameClient {
  private stateListener: ((state: GameStateDto) => void) | null = null;
  private readonly conn: ServerConnection;

  constructor(conn: ServerConnection) {
    this.conn = conn;
    conn.onStateUpdate((packet) => {
      const resp = parsePayload<GameStateResponse>(packet);
      if (resp.state) {
        this.stateListener?.(resp.state);
      }
    });
  }

  async createGame(playerName: string): Promise<CreateGameResponse> {
    const packet = await this.conn.request("CREATE_GAME", { playerName });
    const resp = parsePayload<CreateGameResponse>(packet);
    ensureOk(resp.ok, resp.message);
    return resp;
  }

  async joinGame(playerName: string): Promise<JoinGameResponse> {
    const packet = await this.conn.request("JOIN_GAME", { playerName });
    const resp = parsePayload<JoinGameResponse>(packet);
    ensureOk(resp.ok, resp.message);
    return resp;
  }
  async placeShip(
    playerId: string,
    type: ShipType,
    x: number,
    y: number,
    horizontal: boolean
  ): Promise<void> {
    const packet = await this.conn.request("PLACE_SHIP", {
      playerId,
      type,
      x,
      y,
      horizontal,
    });
    const resp = parsePayload<ActionResponse>(packet);
    ensureOk(resp.ok, resp.message);
  }

  async fireShot(playerId: string, x: number, y: number): Promise<FireShotResponse> {
    const packet = await this.conn.request("FIRE_SHOT", { playerId, x, y });
    const resp = parsePayload<FireShotResponse>(packet);
    ensureOk(resp.ok, resp.message);
    return resp;
  }


  async getState(playerId: string): Promise<GameStateDto> {
    const packet = await this.conn.request("GET_STATE", { playerId });
    const resp = parsePayload<GameStateResponse>(packet);
    ensureOk(resp.ok, resp.message);
    if (!resp.state) throw new Error("Server returned no game state");
    return resp.state;
  }

  async ping(): Promise<void> {
    const packet = await this.conn.request("PING");
    const resp = parsePayload<ActionResponse>(packet);
    ensureOk(resp.ok, resp.message);
  }

  onStateUpdate(listener: ((state: GameStateDto) => void) | null): void {
    this.stateListener = listener;
  }

  close(): void {
    this.stateListener = null;
    this.conn.close();
  }
}

function parsePayload<T>(packet: Packet): T {
  const raw = packet.payload ?? "{}";
  return JSON.parse(raw) as T;
}

function ensureOk(ok: boolean, message?: string): void {
  if (!ok) {
    throw new Error(message ?? "Server rejected request");
  }
}
