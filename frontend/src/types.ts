export interface Packet {
  type: string;
  payload: string | null;
}

export function serializePacket(p: Packet): string {
  return JSON.stringify(p);
}

export function deserializePacket(raw: string): Packet {
  return JSON.parse(raw) as Packet;
}

export type ShipType = "DESTROYER" | "AIRCRAFT_CARRIER" | "SUBMARINE";

export const SHIP_LENGTHS: Record<ShipType, number> = {
  DESTROYER: 3,
  AIRCRAFT_CARRIER: 4,
  SUBMARINE: 2,
};

export const ALL_SHIPS: ShipType[] = [
  "AIRCRAFT_CARRIER",
  "DESTROYER",
  "SUBMARINE",
];

export type CellState = "UNKNOWN" | "SHIP" | "HIT" | "MISS";

export interface CellView {
  x: number;
  y: number;
  state: CellState;
  shipType?: ShipType;
  segment?: number;
}

export interface CreateGameRequest {
  playerName: string;
}

export interface JoinGameRequest {
  playerName: string;
}

export interface PlaceShipRequest {
  playerId: string;
  type: ShipType;
  x: number;
  y: number;
  horizontal: boolean;
}

export interface FireShotRequest {
  playerId: string;
  x: number;
  y: number;
}

export interface GetStateRequest {
  playerId: string;
}

export interface CreateGameResponse {
  ok: boolean;
  message?: string;
  playerId?: string;
}

export interface JoinGameResponse {
  ok: boolean;
  message?: string;
  playerId?: string;
}

export interface ActionResponse {
  ok: boolean;
  message?: string;
}

export interface FireShotResponse {
  ok: boolean;
  message?: string;
  hit: boolean;
  sunk: boolean;
  won: boolean;
}

export interface GameStateDto {
  playerId: string;
  playerName: string;
  opponentName: string | null;
  waitingForOpponent: boolean;
  currentTurnPlayerId: string | null;
  winnerPlayerId: string | null;
  playerShipsPlaced: boolean;
  opponentShipsPlaced: boolean;
  playerShipsRemainingToPlace: ShipType[];
  playerBoard: CellView[];
  opponentBoard: CellView[];
}

export interface GameStateResponse {
  ok: boolean;
  message?: string;
  state?: GameStateDto;
}

export interface LobbyGame {
  gameId: string;
  hostName: string;
  address: string;
}
