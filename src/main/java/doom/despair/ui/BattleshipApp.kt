package doom.despair.ui

import doom.despair.Player
import doom.despair.client.Client
import doom.despair.client.LocalGameDiscovery
import doom.despair.client.RemoteServer
import doom.despair.core.CellState
import doom.despair.core.CellView
import doom.despair.core.GameStateDto
import doom.despair.server.EmbeddedServerManager
import doom.despair.ships.ShipType
import javafx.application.Application
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.slf4j.Logger
import java.util.logging.Level
import kotlin.concurrent.atomics.AtomicBoolean

class BattleshipApp : Application() {
    private data class SessionContext(
        val client: Client,
        val remote: RemoteServer,
        val playerId: String,
        val playerName: String,
        var resultRecorded: Boolean = false
    )

    private lateinit var stage: Stage
    private var session: SessionContext? = null
    private val boardSize = 10
    private var localGameDiscovery: LocalGameDiscovery? = null
    private val profileStore = UserProfileStore()
    private var userProfile = UserProfile()

    override fun start(primaryStage: Stage) {
        stage = primaryStage
        stage.title = parameters.unnamed.getOrElse(0) { "Battleship" }

        userProfile = profileStore.load()
        try {
            localGameDiscovery = LocalGameDiscovery()
        } catch (e: Exception) {
            localGameDiscovery = null
        }
        showHomeScene()
        stage.show()
    }

    override fun stop() {
        session?.remote?.setStateListener(null)
        session?.client?.disconnect()
        localGameDiscovery?.close()
        localGameDiscovery = null
        EmbeddedServerManager.stopIfRunning()
        Platform.exit()
    }

    private fun showHomeScene() {
        val nameField = TextField(userProfile.name)
        val addressField = TextField(userProfile.lastServerAddress)
        val addressingHelp = Label("One game per server. Both players connect to the same server address.")

        val createButton = Button("Create Game")
        val joinButton = Button("Join Game")
        val refreshLocalGamesButton = Button("Refresh Local Games")
        val joinSelectedLocalButton = Button("Join Selected Local Game")
        val localGamesList = ListView<LocalGameDiscovery.LocalGame>()
        localGamesList.prefHeight = 120.0
        localGamesList.maxHeight = 120.0
        val profileLabel = Label()
        profileLabel.style = "-fx-font-weight: bold;"
        val statusLabel = Label("Ready")

        fun updateProfileLabel() {
            userProfile = profileStore.load()
            profileLabel.text = "Profile: ${userProfile.name} | Wins: ${userProfile.wins} | Losses: ${userProfile.losses}"
        }
        updateProfileLabel()

        fun refreshLocalGames() {
            val discovery = localGameDiscovery
            if (discovery == null) {
                statusLabel.text = "Local game discovery unavailable."
                localGamesList.items.clear()
                return
            }
            Thread {
                val games = try {
                    discovery.discover()
                } catch (_: Exception) {
                    emptyList()
                }
                Platform.runLater {
                    localGamesList.items.setAll(games)
                    if (games.isEmpty()) {
                        statusLabel.text = "No local open games found."
                    }
                }
            }.start()
        }

        createButton.setOnAction {
            val playerName = nameField.text.ifBlank { "Player" }
            val serverAddress = "127.0.0.1:25567"
            try {
                userProfile = userProfile.copy(name = playerName, lastServerAddress = serverAddress)
                profileStore.save(userProfile)
                if (isLocalAddress(serverAddress)) {
                    EmbeddedServerManager.startIfNeeded()
                }
                val client = Client(Player(playerName))
                val remote = client.connect(serverAddress)
                val created = remote.createGame(playerName)
                val playerId = created.playerId ?: throw IllegalStateException("Server did not return player id")
                session = SessionContext(client, remote, playerId, playerName)
                statusLabel.text = "Created game on $serverAddress"
                showPlacementScene()
            } catch (e: Exception) {
                showError("Create Game failed", e.message ?: "Unknown error")
            }
        }

        joinButton.setOnAction {
            val playerName = nameField.text.ifBlank { "Player" }
            val serverAddress = addressField.text.ifBlank { "127.0.0.1:25567" }
            try {
                userProfile = userProfile.copy(name = playerName, lastServerAddress = serverAddress)
                profileStore.save(userProfile)
                val client = Client(Player(playerName))
                val remote = client.connect(serverAddress)
                val joined = remote.joinGame(playerName)
                val playerId = joined.playerId ?: throw IllegalStateException("Server did not return player id")
                session = SessionContext(client, remote, playerId, playerName)
                statusLabel.text = "Joined game on $serverAddress"
                showPlacementScene()
            } catch (e: Exception) {
                showError("Join Game failed", e.message ?: "Unknown error")
            }
        }

        joinSelectedLocalButton.setOnAction {
            val selected = localGamesList.selectionModel.selectedItem
            if (selected == null) {
                showError("Join Game failed", "Select a local game first.")
                return@setOnAction
            }
            addressField.text = selected.address
            joinButton.fire()
        }
        refreshLocalGamesButton.setOnAction {
            refreshLocalGames()
        }

        val root = VBox(
            10.0,
            Label("Name"),
            nameField,
            profileLabel,
            Label("Server address"),
            addressField,
            addressingHelp,
            Label("Local games"),
            localGamesList,
            refreshLocalGamesButton,
            joinSelectedLocalButton,
            createButton,
            joinButton,
            statusLabel
        )
        root.padding = Insets(16.0)
        stage.scene = Scene(root, 520.0, 520.0)
        refreshLocalGames()
    }

    private fun showPlacementScene() {
        val active = session ?: return
//        val orientation = CheckBox("Horizontal placement (uncheck for vertical)")
//        orientation.isSelected = true
        val info = Label("Select a ship, then hover/click the board to preview/place.")
        val stateLabel = Label()
        var movedToBattle = false
        lateinit var placementGrid: GridPane
        var latestState: GameStateDto? = null
        var selectedShip = ShipType.AIRCRAFT_CARRIER
        val shipButtons = linkedMapOf(
            ShipType.AIRCRAFT_CARRIER to Button("Carrier"),
            ShipType.DESTROYER to Button("Destroyer"),
            ShipType.SUBMARINE to Button("Submarine")
        )
        var orientation = true
        fun updateShipButtons(state: GameStateDto) {
            val remaining = state.playerShipsRemainingToPlace.toSet()
            if (selectedShip !in remaining && remaining.isNotEmpty()) {
                selectedShip = remaining.first()
            }
            for ((type, button) in shipButtons) {
                button.isDisable = type !in remaining
                button.style = if (type == selectedShip) {
                    "-fx-background-color: #f1c40f; -fx-text-fill: #000000;"
                } else {
                    ""
                }
            }
        }

        fun applyState(state: GameStateDto) {
            latestState = state
            stateLabel.text =
                if (state.playerShipsRemainingToPlace.isNotEmpty()) {
                    "Place remaining ships: ${state.playerShipsRemainingToPlace.joinToString(", ")}"
                } else if (state.waitingForOpponent) {
                    "All ships placed. Waiting for opponent to join..."
                } else {
                    "All ships placed. Opponent: ${state.opponentName}"
                }
            renderBoard(state.playerBoard, placementGrid)
            updateShipButtons(state)
            if (state.playerShipsPlaced && !movedToBattle) {
                movedToBattle = true
                showBattleScene()
            }
        }

        placementGrid = createBoardGrid(
            clickable = true,
            onClick = { x, y, ev ->
                if (ev.button == MouseButton.SECONDARY) {
                    orientation = !orientation
                    val state = latestState ?: return@createBoardGrid
                    renderBoard(state.playerBoard, placementGrid)
                    applyPlacementPreview(placementGrid, state, selectedShip, x, y, orientation)
                    return@createBoardGrid
                }
                try {
                    active.remote.placeShip(active.playerId, selectedShip, x, y, orientation)
                } catch (e: Exception) {
                    showError("Ship placement failed", e.message ?: "Unknown error")
                }
            },
            onHover = { x, y ->
                val state = latestState ?: return@createBoardGrid
                renderBoard(state.playerBoard, placementGrid)
                applyPlacementPreview(placementGrid, state, selectedShip, x, y, orientation)
            },
            onHoverExit = {
                val state = latestState ?: return@createBoardGrid
                renderBoard(state.playerBoard, placementGrid)
            }
        )

        for ((type, button) in shipButtons) {
            button.setOnAction {
                selectedShip = type
                latestState?.let { updateShipButtons(it) }
            }
        }
        val shipSelector = HBox(8.0, *shipButtons.values.toTypedArray())
        shipSelector.alignment = Pos.CENTER

        active.remote.setStateListener { state ->
            Platform.runLater {
                val current = session
                if (current == null || current.playerId != state.playerId) {
                    return@runLater
                }
                applyState(state)
            }
        }
        try {
            applyState(active.remote.getState(active.playerId))
        } catch (e: Exception) {
            showError("State sync failed", e.message ?: "Unknown error")
        }

        val root = VBox(
            10.0,
            info,
            shipSelector,
            placementGrid,
            stateLabel
        )
        root.padding = Insets(16.0)
        root.alignment = Pos.TOP_CENTER
        stage.scene = Scene(root, 520.0, 560.0)
    }

    private fun showBattleScene() {
        val active = session ?: return
        val stateLabel = Label()
        val playerGrid = createBoardGrid(clickable = false)
        val opponentGrid = createBoardGrid(clickable = true, onClick = { x, y, ev ->
            try {
                val result = active.remote.fireShot(active.playerId, x, y)
                stateLabel.text = buildString {
                    append(if (result.hit) "Hit" else "Miss")
                    if (result.sunk) append(" - sunk")
                    if (result.won) append(" - you won")
                }
            } catch (e: Exception) {
                showError("Fire failed", e.message ?: "Unknown error")
            }
        })
        fun applyState(state: GameStateDto) {
            updateBattleUi(stateLabel, playerGrid, opponentGrid, state)
            if (state.winnerPlayerId != null) {
                showGameOverScene(state)
            }
        }
        active.remote.setStateListener { state ->
            Platform.runLater {
                val current = session
                if (current == null || current.playerId != state.playerId) {
                    return@runLater
                }
                applyState(state)
            }
        }
        try {
            applyState(active.remote.getState(active.playerId))
        } catch (e: Exception) {
            showError("State sync failed", e.message ?: "Unknown error")
        }

        val boards = HBox(
            18.0,
            VBox(8.0, Label("Your board"), playerGrid),
            VBox(8.0, Label("Opponent board (click to fire)"), opponentGrid)
        )
        boards.alignment = Pos.TOP_CENTER

        val root = VBox(
            10.0,
            Label("Battle"),
            stateLabel,
            boards
        )
        root.padding = Insets(16.0)
        root.alignment = Pos.TOP_CENTER
        stage.scene = Scene(root, 900.0, 540.0)
    }

    private fun showGameOverScene(state: GameStateDto) {
        val active = session ?: return
        val didWin = state.winnerPlayerId == active.playerId
        if (!active.resultRecorded) {
            userProfile = if (didWin) {
                userProfile.copy(name = active.playerName, wins = userProfile.wins + 1)
            } else {
                userProfile.copy(name = active.playerName, losses = userProfile.losses + 1)
            }
            profileStore.save(userProfile)
            active.resultRecorded = true
        }
        val title = if (didWin) "You won!" else "You lost."
        val backButton = Button("Back to Home")
        backButton.setOnAction {
            active.remote.setStateListener(null)
            active.client.disconnect()
            session = null
            showHomeScene()
        }
        val root = VBox(12.0, Label(title), Label("Game is complete."), backButton)
        root.padding = Insets(20.0)
        stage.scene = Scene(root, 320.0, 180.0)
    }

    private fun updateBattleUi(
        stateLabel: Label,
        playerGrid: GridPane,
        opponentGrid: GridPane,
        state: GameStateDto
    ) {
        stateLabel.text = when {
            state.waitingForOpponent -> "Waiting for opponent..."
            !state.playerShipsPlaced || !state.opponentShipsPlaced -> "Waiting for both players to place ships..."
            state.winnerPlayerId != null -> "Game over"
            state.currentTurnPlayerId == state.playerId -> "Your turn"
            else -> "Opponent's turn"
        }
        renderBoard(state.playerBoard, playerGrid)
        renderBoard(state.opponentBoard, opponentGrid)
        val canShoot = state.currentTurnPlayerId == state.playerId && state.winnerPlayerId == null &&
            state.playerShipsPlaced && state.opponentShipsPlaced
        setGridEnabled(opponentGrid, canShoot)
    }

    private fun createBoardGrid(
        clickable: Boolean,
        onClick: ((Int, Int, MouseEvent) -> Unit)? = null,
        onHover: ((Int, Int) -> Unit)? = null,
        onHoverExit: (() -> Unit)? = null
    ): GridPane {
        val grid = GridPane()
        grid.hgap = 2.0
        grid.vgap = 2.0
        grid.alignment = Pos.CENTER
        for (y in 0 until boardSize) {
            for (x in 0 until boardSize) {
                val cell = Button()
                cell.prefWidth = 34.0
                cell.prefHeight = 34.0
                cell.minWidth = 34.0
                cell.minHeight = 34.0
                cell.maxWidth = 34.0
                cell.maxHeight = 34.0
                if (clickable && onClick != null) {
                    cell.setOnMouseClicked { onClick(x, y, it) }
                    cell.setOnMouseEntered { onHover?.invoke(x, y) }
                    cell.setOnMouseExited { onHoverExit?.invoke() }
                } else {
                    cell.isDisable = true
                }
                GridPane.setHalignment(cell, HPos.CENTER)
                grid.add(cell, x, y)
            }
        }
        return grid
    }

    private fun renderBoard(cells: List<CellView>, grid: GridPane) {
        val cellButtons = grid.children.filterIsInstance<Button>()
        for (button in cellButtons) {
            button.text = ""
            button.style = "-fx-background-color: #2f3b52;"
        }
        if (cells.isEmpty()) {
            return
        }
        for (cell in cells) {
            val target = grid.children
                .filterIsInstance<Button>()
                .firstOrNull { GridPane.getColumnIndex(it) == cell.x && GridPane.getRowIndex(it) == cell.y }
                ?: continue
            when (cell.state) {
                CellState.UNKNOWN -> {
                    target.style = "-fx-background-color: #2f3b52;"
                    target.text = ""
                }

                CellState.SHIP -> {
                    target.style = "-fx-background-color: #6c8ebf;"
                    target.text = "S"
                }

                CellState.HIT -> {
                    target.style = "-fx-background-color: #c0392b;"
                    target.text = "X"
                }

                CellState.MISS -> {
                    target.style = "-fx-background-color: #95a5a6;"
                    target.text = "o"
                }
            }
        }
    }

    private fun setGridEnabled(grid: GridPane, enabled: Boolean) {
        for (button in grid.children.filterIsInstance<Button>()) {
            button.isDisable = !enabled
        }
    }

    private fun applyPlacementPreview(
        grid: GridPane,
        state: GameStateDto,
        shipType: ShipType,
        startX: Int,
        startY: Int,
        horizontal: Boolean
    ) {
        val length = shipLength(shipType)
        val occupied = state.playerBoard
            .filter { it.state == CellState.SHIP }
            .map { it.x to it.y }
            .toSet()
        val cells = ArrayList<Pair<Int, Int>>(length)
        var valid = true
        for (i in 0 until length) {
            val x = if (horizontal) startX + i else startX
            val y = if (horizontal) startY else startY + i
            if (x !in 0 until boardSize || y !in 0 until boardSize || occupied.contains(x to y)) {
                valid = false
            }
            cells.add(x to y)
        }
        val style = if (valid) "-fx-background-color: #27ae60; -fx-text-fill: #ffffff;" else "-fx-background-color: #e74c3c; -fx-text-fill: #ffffff;"
        for ((x, y) in cells) {
            if (x !in 0 until boardSize || y !in 0 until boardSize) {
                continue
            }
            val target = getCellButton(grid, x, y) ?: continue
            target.style = style
            target.text = "P"
        }
    }

    private fun shipLength(type: ShipType): Int {
        return when (type) {
            ShipType.AIRCRAFT_CARRIER -> 4
            ShipType.DESTROYER -> 3
            ShipType.SUBMARINE -> 2
        }
    }

    private fun getCellButton(grid: GridPane, x: Int, y: Int): Button? {
        return grid.children
            .filterIsInstance<Button>()
            .firstOrNull { GridPane.getColumnIndex(it) == x && GridPane.getRowIndex(it) == y }
    }

    private fun isLocalAddress(address: String): Boolean {
        val host = address.substringBefore(':').trim().lowercase()
        return host == "127.0.0.1" || host == "localhost"
    }

    private fun showError(title: String, message: String) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = title
        alert.headerText = title
        alert.contentText = message
        alert.showAndWait()
    }
}

