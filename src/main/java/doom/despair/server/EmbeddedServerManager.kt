package doom.despair.server

object EmbeddedServerManager {
    private var server: BattleshipServer? = null

    @Synchronized
    fun startIfNeeded(): BattleshipServer {
        val runningServer = server
        if (runningServer != null && runningServer.isRunning) {
            return runningServer
        }
        val newServer = BattleshipServer(autoStart = false)
        newServer.start()
        server = newServer
        return newServer
    }

    @Synchronized
    fun stopIfRunning() {
        val runningServer = server ?: return
        runningServer.stop()
        server = null
    }
}
