package doom.despair

import javafx.application.Application
import doom.despair.ui.BattleshipApp

fun main(args: Array<String>) {
    System.setProperty("http.proxyHost", "localhost")
    System.setProperty("http.proxyPort", "5559")
    System.setProperty("https.proxyHost", "localhost")
    System.setProperty("https.proxyPort", "5559")
    System.setProperty("debug.relay", "true")
    Application.launch(BattleshipApp::class.java, *args)
}