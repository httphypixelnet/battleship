package doom.despair.core

import doom.despair.ships.ShipType

data class CreateGameRequest(val playerName: String)
data class JoinGameRequest(val playerName: String)
data class PlaceShipRequest(
    val playerId: String,
    val type: ShipType,
    val x: Int,
    val y: Int,
    val horizontal: Boolean
)
data class FireShotRequest(val playerId: String, val x: Int, val y: Int)
data class GetStateRequest(val playerId: String)

data class CreateGameResponse(
    val ok: Boolean,
    val message: String? = null,
    val playerId: String? = null
)

data class JoinGameResponse(
    val ok: Boolean,
    val message: String? = null,
    val playerId: String? = null
)

data class ActionResponse(
    val ok: Boolean,
    val message: String? = null
)

data class FireShotResponse(
    val ok: Boolean,
    val message: String? = null,
    val hit: Boolean = false,
    val sunk: Boolean = false,
    val won: Boolean = false
)

data class GameStateResponse(
    val ok: Boolean,
    val message: String? = null,
    val state: GameStateDto? = null
)

data class GameStateDto(
    val playerId: String,
    val playerName: String,
    val opponentName: String?,
    val waitingForOpponent: Boolean,
    val currentTurnPlayerId: String?,
    val winnerPlayerId: String?,
    val playerShipsPlaced: Boolean,
    val opponentShipsPlaced: Boolean,
    val playerShipsRemainingToPlace: List<ShipType>,
    val playerBoard: List<CellView>,
    val opponentBoard: List<CellView>
)

enum class CellState {
    UNKNOWN,
    SHIP,
    HIT,
    MISS
}

data class CellView(
    val x: Int,
    val y: Int,
    val state: CellState,
    val shipType: ShipType? = null,
    val segment: Int? = null,
    val horizontal: Boolean? = null
)

