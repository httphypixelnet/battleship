# Relay Compatibility Plan: Cell Segment Data

## Goal
Ensure the new `CellView.shipType` and `CellView.segment` fields flow through the TypeScript relay path without breaking existing clients, and remain hidden for opponent boards.

## Data Contract Checks
- Kotlin DTO: `CellView` defaults `shipType` and `segment` to empty optionals.
- TS DTO: `CellView` exposes `shipType?: ShipType` and `segment?: number`.
- Serialization: Gson omits absent optionals, so payloads for non-ship cells should remain unchanged.

## Relay Flow Verification
1. Start server and lobby relay.
2. Create a game via relay and confirm `STATE_UPDATE` packets include `playerBoard` cells with `shipType` + `segment` for `CellState.SHIP`.
3. Confirm `opponentBoard` cells do not include `shipType` + `segment` (only `HIT`/`MISS` when revealed).
4. Join game as second client through relay and repeat steps 2-3 for the second player.

## Client Parsing Checks
- Ensure `deserializePacket` in `frontend/src/connection.ts` handles new fields (no changes expected).
- Confirm `GameClient.parsePayload` parses optional fields without exceptions.

## Regression Tests
- Place ships with both orientations and confirm segment numbering starts at placement origin and increments along the ship length.
- Fire shots: hit/miss cells should not require `shipType` + `segment` to render.

## Troubleshooting
- If relay strips fields, inspect relay server JSON handling to ensure it forwards payloads verbatim.
- If clients fail to parse, confirm `CellView` typing matches payload shape in `frontend/src/types.ts`.
