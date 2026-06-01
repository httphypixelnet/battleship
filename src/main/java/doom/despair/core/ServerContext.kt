package doom.despair.core

import doom.despair.server.BattleshipServer

@JvmRecord
data class ServerContext(@JvmField val server: BattleshipServer)
