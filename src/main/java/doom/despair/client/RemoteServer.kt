package doom.despair.client

import com.google.gson.Gson
import doom.despair.core.ActionResponse
import doom.despair.core.CreateGameRequest
import doom.despair.core.CreateGameResponse
import doom.despair.core.FireShotRequest
import doom.despair.core.FireShotResponse
import doom.despair.core.GameStateDto
import doom.despair.core.GameStateResponse
import doom.despair.core.GetStateRequest
import doom.despair.core.JoinGameRequest
import doom.despair.core.JoinGameResponse
import doom.despair.core.PlaceShipRequest
import doom.despair.ships.ShipType

class RemoteServer(private val handler: ServerHandler) {
    private val gson = Gson()
    private var stateListener: ((GameStateDto) -> Unit)? = null

    init {
        handler.onStateUpdate { packet ->
            val payload = packet.payload ?: return@onStateUpdate
            val stateResponse = gson.fromJson(payload, GameStateResponse::class.java)
            val state = stateResponse.state ?: return@onStateUpdate
            stateListener?.invoke(state)
        }
    }

    fun createGame(playerName: String): CreateGameResponse {
        val response = parsePayload(
            handler.request("CREATE_GAME", CreateGameRequest(playerName)),
            CreateGameResponse::class.java
        )
        ensureOk(response.ok, response.message)
        return response
    }

    fun joinGame(playerName: String): JoinGameResponse {
        val response = parsePayload(
            handler.request("JOIN_GAME", JoinGameRequest(playerName)),
            JoinGameResponse::class.java
        )
        ensureOk(response.ok, response.message)
        return response
    }

    fun placeShip(playerId: String, type: ShipType, x: Int, y: Int, horizontal: Boolean) {
        val response = parsePayload(
            handler.request("PLACE_SHIP", PlaceShipRequest(playerId, type, x, y, horizontal)),
            ActionResponse::class.java
        )
        ensureOk(response.ok, response.message)
    }

    fun fireShot(playerId: String, x: Int, y: Int): FireShotResponse {
        val response = parsePayload(
            handler.request("FIRE_SHOT", FireShotRequest(playerId, x, y)),
            FireShotResponse::class.java
        )
        ensureOk(response.ok, response.message)
        return response
    }

    fun getState(playerId: String): GameStateDto {
        val response = parsePayload(
            handler.request("GET_STATE", GetStateRequest(playerId)),
            GameStateResponse::class.java
        )
        ensureOk(response.ok, response.message)
        return response.state ?: throw IllegalStateException("Missing game state")
    }

    fun ping() {
        val response = parsePayload(
            handler.request("PING"),
            ActionResponse::class.java
        )
        ensureOk(response.ok, response.message)
    }

    fun close() {
        stateListener = null
        handler.close()
    }

    fun setStateListener(listener: ((GameStateDto) -> Unit)?) {
        stateListener = listener
    }

    private fun <T> parsePayload(packet: doom.despair.core.Packet, type: Class<T>): T {
        val payload = packet.payload ?: "{}"
        return gson.fromJson(payload, type)
    }

    private fun ensureOk(ok: Boolean, message: String?) {
        if (!ok) {
            throw IllegalStateException(message ?: "Server rejected request")
        }
    }
}
