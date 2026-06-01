package doom.despair

import doom.despair.server.BattleshipServer

fun main() {
    val server = BattleshipServer(autoStart = false)
    server.start()
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    while (server.isRunning) {
        Thread.sleep(500)
    }
}
