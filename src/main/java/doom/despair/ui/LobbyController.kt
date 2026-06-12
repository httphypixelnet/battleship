package doom.despair.ui

import doom.despair.client.LocalGameDiscovery
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import javafx.util.Callback
import javafx.scene.control.Button
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton

class LobbyController {
    @FXML lateinit var titleImage: ImageView
    @FXML lateinit var gamesTable: TableView<LocalGameDiscovery.LocalGame>
    @FXML lateinit var hostColumn: TableColumn<LocalGameDiscovery.LocalGame, String>
    @FXML lateinit var idColumn: TableColumn<LocalGameDiscovery.LocalGame, String>
    @FXML lateinit var joinColumn: TableColumn<LocalGameDiscovery.LocalGame, String>
    @FXML lateinit var settingsButton: Button
    @FXML lateinit var joinByIdButton: Button
    @FXML lateinit var gameIdField: TextField
    @FXML lateinit var createGameButton: Button
    @FXML lateinit var settingsIcon: ImageView

    private var app: BattleshipApp? = null
    private var localGameDiscovery: LocalGameDiscovery? = null

    fun setApp(app: BattleshipApp) {
        this.app = app
    }

    fun setLocalGameDiscovery(discovery: LocalGameDiscovery?) {
        localGameDiscovery = discovery
    }

    @FXML
    fun initialize() {
        settingsIcon.image = ImageLoader.loadImage("Ship_cog")
        titleImage.image = ImageLoader.loadImage("Title", 64.0, 16.0)
        hostColumn.cellValueFactory = Callback { SimpleStringProperty(it.value.name) }
        idColumn.cellValueFactory = Callback { SimpleStringProperty(it.value.gameId ?: "") }
        joinColumn.cellValueFactory = Callback { SimpleStringProperty("Join") }

        gamesTable.setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY && event.clickCount >= 2) {
                val selected = gamesTable.selectionModel.selectedItem ?: return@setOnMouseClicked
                app?.joinLocalGame(selected)
            }
        }

        createGameButton.setOnAction { app?.onCreateGame() }
        joinByIdButton.setOnAction {
            val id = gameIdField.text.ifBlank { return@setOnAction }
            app?.onJoinById(id)
        }
        settingsButton.setOnAction { app?.onSettings() }

        refreshGames()
    }

    fun refreshGames() {
        val discovery = localGameDiscovery
        if (discovery == null) {
            gamesTable.items.clear()
            return
        }
        Thread {
            val games = try {
                discovery.discover()
            } catch (_: Exception) {
                emptyList()
            }
            Platform.runLater {
                gamesTable.items.setAll(games)
            }
        }.start()
    }
}
