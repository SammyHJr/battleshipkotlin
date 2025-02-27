package com.example.battleshipkotlin

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.asStateFlow


@Composable
fun BattleShip() {
    val navController = rememberNavController()
    val model = GameModel()
    model.initGame()
    Log.i("BattleShipInfo", "In battleship()")
    NavHost(navController = navController, startDestination = "Player") {
        composable("player") { NewPlayerScreen(navController, model) }
        composable("lobby") { LobbyScreen(navController, model) }
        composable("game/{gameId}") { navBackStackEntry ->
            val gameId = navBackStackEntry.arguments?.getString("gameId")
            GameScreen(navController, model, gameId)
        }
    }
}

@Composable
fun NewPlayerScreen(navController: NavController, model: GameModel) {

    val sharedPreference =
        LocalContext.current.getSharedPreferences("BattleShipPrefrences", Context.MODE_PRIVATE)
    Log.i("BattleShipInfo", "New playerScreen")

    // ✅ Create HashMap with default values
    val playerData = hashMapOf(
        "name" to "",
        "isOnline" to false
    )

    LaunchedEffect(Unit) {
        sharedPreference.edit().remove("playerId").apply() // ✅ Force name entry on every launch
        model.localPlayerId.value = null  // Reset local player ID
    }
    Log.i("BattleShipInfo", "playerID = null")

    LaunchedEffect(Unit) {
        model.localPlayerId.value = sharedPreference.getString("playerId", null)
        if (model.localPlayerId.value != null) {
            updatePlayerStatus(model.localPlayerId.value!!, "online") // Set status to online
            navController.navigate("lobby")
        }
    }

    if (model.localPlayerId.value == null) {
        var playerName by remember { mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to BattleShip", fontSize = 36.sp, fontWeight = FontWeight.Bold)

            Spacer(
                modifier = Modifier
                    .height(75.dp)
            )

            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text("Enter Your Name...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(75.dp)
            )

            Spacer(
                modifier = Modifier
                    .height(50.dp)
            )

            val context = LocalContext.current
            Button(
                onClick = {
                    if (playerName.isNotBlank()) {
                        model.db.collection("players")
                            .whereEqualTo("name", playerName)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                if (!querySnapshot.isEmpty) {
                                    // ✅ Player exists
                                    val existingPlayer = querySnapshot.documents[0]
                                    val existingPlayerId = existingPlayer.id

                                    existingPlayer.reference.update("isOnline", true) // changees the status of the player from being offline to online as soon as the players name is entered
                                        .addOnSuccessListener {
                                            sharedPreference.edit()
                                                .putString("playerId", existingPlayerId).apply()
                                            model.localPlayerId.value = existingPlayerId

                                            // ✅ Show "Welcome Back" message
                                            Toast.makeText(
                                                context,
                                                "Welcome back, $playerName!",
                                                Toast.LENGTH_LONG
                                            ).show()

                                            navController.navigate("Lobby")
                                        }
                                        .addOnFailureListener { error ->
                                            Log.e(
                                                "BattleShipError",
                                                "Error updating player status: ${error.message}"
                                            )
                                        }
                                } else {
                                    // ✅ Player does not exist, create new
                                    val newPlayerData = hashMapOf(
                                        "name" to playerName,
                                        "isOnline" to true
                                    )

                                    model.db.collection("players")      // in the players Collection it will add new players
                                        .add(newPlayerData)
                                        .addOnSuccessListener { documentRef ->
                                            val newPlayerId = documentRef.id        // gives the player a ID

                                            sharedPreference.edit()
                                                .putString("playerId", newPlayerId).apply()
                                            model.localPlayerId.value = newPlayerId     // local player will get the value of the documentId

                                            // ✅ Show "New Player Created" message
                                            Toast.makeText(
                                                context,
                                                "New player created: $playerName!",
                                                Toast.LENGTH_LONG
                                            ).show()

                                            navController.navigate("Lobby")
                                        }
                                        .addOnFailureListener { error ->
                                            Log.e(
                                                "BattleShipError",
                                                "Error creating player: ${error.message}"
                                            )
                                        }
                                }
                            }
                            .addOnFailureListener { error ->
                                Log.e(
                                    "BattleShipError",
                                    "Error checking for existing player: ${error.message}"
                                )
                            }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(75.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text("Login", fontSize = 20.sp, color = Color.White)
            }
        }
    } else {
        Text("Loading...")
    }

}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(navController: NavController, model: GameModel) {
    val player by model.playerMap.asStateFlow().collectAsStateWithLifecycle()
    val games by model.gameMap.asStateFlow().collectAsStateWithLifecycle()
    val onlinePlayers = remember { mutableStateListOf<Player>() }

    Log.i("BattleShipInfo", "LobbyScreen")

    var showDialog by remember { mutableStateOf(false) }
    var challengerName by remember { mutableStateOf("") }
    var challengeGameId by remember { mutableStateOf("") }

    // LISTEN FOR CHALLENGES
    LaunchedEffect(Unit) {
        model.db.collection("games")            // store challanges in the games collection so we are taking players from the players collection and adding them to the games collection
            .whereEqualTo("playerId2", model.localPlayerId.value)
            .whereEqualTo("gameState", "invite")            //changes the game state to invite
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documents?.firstOrNull()?.let { doc ->
                    val player1Id = doc.getString("playerId1")
                    val gameId = doc.id

                    if (player1Id != null) {
                        model.db.collection("players").document(player1Id).get() // if the player1 is null theen it should get tfrom the players collection
                            .addOnSuccessListener { playerDoc ->
                                challengerName = playerDoc.getString("name") ?: "Unknown Player"
                                challengeGameId = gameId
                                showDialog = true
                            }
                    }
                }
            }
    }

    // 👇 SHOW CHALLENGE DIALOG IF TRIGGERED
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Game Challenge!") },
            text = { Text("$challengerName has challenged you to a game!") },
            confirmButton = {
                Button(onClick = {
                    model.db.collection("games").document(challengeGameId)
                        .update("gameState", "player1 turn")
                        .addOnSuccessListener {
                            navController.navigate("game/${challengeGameId}")
                        }
                    showDialog = false
                }) {
                    Text("Accept")
                }
            },
            dismissButton = {
                Button(onClick = {
                    model.db.collection("games").document(challengeGameId)
                        .delete()
                    navController.navigate("lobby")
                    showDialog = false
                }) {
                    Text("Decline")

                }
            }
        )
    }

    var playerName = "Unknown?"
    player[model.localPlayerId.value]?.let {
        playerName = it.name
    }

    // Fetch online players from Firestore
    LaunchedEffect(Unit) {
        model.db.collection("players") // Firestore collection
            .whereEqualTo("isOnline", true) // Filter for only online players
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("BattleShipError", "Error fetching online players: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val updatedPlayers = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Player::class.java) // Only retrieve name and isOnline
                    }
                    onlinePlayers.clear()
                    onlinePlayers.addAll(updatedPlayers)
                    Log.i("BattleShipInfo", "Fetched ${updatedPlayers.size} online players")
                }
            }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("BattleShip - $playerName") }) }            //present the top app bar and the localplayers name should show nex to the title
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(onlinePlayers) { player ->
                // Only show players who are not the local player
                if (player.id != model.localPlayerId.value) {
                    ListItem(
                        headlineContent = { Text("Player Name: ${player.name}") },
                        supportingContent = { Text("Status: Online") },
                        trailingContent = {
                            // Check if the player is already in a game with the local player
                            var hasGame = false
                            games.forEach { (gameId, game) ->
                                if (game.playerId1 == model.localPlayerId.value && game.playerId2 == player.id) {
                                    // Player is already part of an active game with the local player
                                    Text("Already in a game")
                                    hasGame = true
                                }
                            }

                            // If no active game exists, show the "Challenge" button
                            if (!hasGame) {
                                Button(onClick = {
                                    model.db.collection("players")
                                        .whereEqualTo(
                                            "name",
                                            player.name
                                        ) // Find the player by name
                                        .get()
                                        .addOnSuccessListener { querySnapshot ->
                                            val player2Id =
                                                querySnapshot.documents.firstOrNull()?.id // Get the document ID
                                            if (player2Id != null) {
                                                model.db.collection("games").add(
                                                    Game(
                                                        gameState = "invite",
                                                        playerId1 = model.localPlayerId.value!!, // The challenging player
                                                        playerId2 = player2Id // The challenged player's ID
                                                    )
                                                ).addOnSuccessListener { documentRef ->
                                                    navController.navigate("game/${documentRef.id}") // Navigate to game screen

                                                }.addOnFailureListener {
                                                    Log.e(
                                                        "BattleShipError",
                                                        "Error creating challenge"
                                                    )
                                                }
                                            } else {
                                                Log.e("BattleShipError", "Player2 ID not found")
                                            }
                                        }
                                        .addOnFailureListener { exception ->
                                            Log.e(
                                                "BattleShipError",
                                                "Error fetching Player2 ID: ${exception.message}"
                                            )
                                        }
                                }) {
                                    Text("Challenge")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(navController: NavController, model: GameModel, gameId: String?) {
    val players by model.playerMap.asStateFlow().collectAsStateWithLifecycle()
    val games by model.gameMap.asStateFlow().collectAsStateWithLifecycle()

    var playerGameBoard by remember { mutableStateOf(MutableList(100) { 'W' }) }  // Player's board
    var firstClickIndex by remember { mutableStateOf(-1) }  // Track start of ship placement
    var placingShip by remember { mutableStateOf(true) }
    var readyToBattle by remember { mutableStateOf(false) } // State for the "Ready" button

    val playerName = players[model.localPlayerId.value]?.name ?: "Unknown"

    // Ordered ship queue for placement, ships with size
    val shipQueue = remember {
        mutableStateListOf(
            "Carrier" to 4,
            "Battleship" to 3,
            "Submarine1" to 2,
            "Submarine2" to 2,
            "Destroyer1" to 1,
            "Destroyer2" to 1
        )
    }

    fun takeShot(index: Int) {
        Log.d("BattleShipDebug", "takeShot called at index $index") // Debug log

        if (!readyToBattle || gameId == null || model.localPlayerId.value == null) {
            Log.e("BattleShipError", "Not ready to battle, gameId: $gameId")
            return
        }

        val game = games[gameId] ?: run {
            Log.e("BattleShipError", "Game not found in games map!")
            return
        }

        val currentPlayerId = model.localPlayerId.value!!
        val isPlayer1 = game.playerId1 == currentPlayerId
        val isPlayerTurn = (game.gameState == "player1_turn" && isPlayer1) ||
                (game.gameState == "player2_turn" && !isPlayer1)

        Log.d(
            "BattleShipDebug",
            "Game state: ${game.gameState}, isPlayer1: $isPlayer1, isPlayerTurn: $isPlayerTurn"
        )

        if (!isPlayerTurn) {
            Log.e("BattleShipError", "Not your turn!")
            return
        }

        val gameRef = Firebase.firestore.collection("games").document(gameId) // everything will be logged in the games collection

        Firebase.firestore.runTransaction { transaction ->                              // transactions take care of multiple read and write at the same time.
            Log.d("BattleShipDebug", "Starting transaction for index $index")

            val gameSnapshot = transaction.get(gameRef)

            // Determine which board to target
            val targetBoardField = if (isPlayer1) "gameBoard2" else "gameBoard1"
            val targetBoard =
                (gameSnapshot.get(targetBoardField) as? List<Int>)?.toMutableList() ?: MutableList(
                    100
                ) { 0 }

            Log.d("BattleShipDebug", "Target board before shot: $targetBoard")

            // Check if already hit or missed
            if (targetBoard[index] == 'H'.code || targetBoard[index] == 'M'.code) {
                Log.w("BattleShipWarning", "Already shot here!")
                return@runTransaction
            }

            // Determine hit or miss
            targetBoard[index] = if (targetBoard[index] == 1) {
                Log.d("BattleShipDebug", "Hit at index $index")
                'H'.code
            } else {
                Log.d("BattleShipDebug", "Miss at index $index")
                'M'.code
            }

            // Check if all ships are sunk
            val allShipsSunk = targetBoard.none { it == 1 } // when all ships are sunken then game over
            val nextGameState = when {
                allShipsSunk -> if (isPlayer1) "Player 1 sank all BATTLESHIPS" else "Player 2 sank all BATTLESHIPS"
                else -> if (isPlayer1) "player2_turn" else "player1_turn"
            }

            // Update Firestore
            transaction.update(gameRef, targetBoardField, targetBoard)
            transaction.update(gameRef, "gameState", nextGameState)

            Log.d("BattleShipDebug", "Updated Firestore: new game state = $nextGameState")
        }.addOnSuccessListener {
            Log.d("BattleShipDebug", "Firestore transaction successful")
        }.addOnFailureListener { e ->
            Log.e("BattleShipError", "Failed to take shot: ", e)
        }
    }

    // Function to check if there's at least one 'W' between ships
    fun isAdjacentToAnotherShip(
        playerGameBoard: MutableList<Char>,
        rowSize: Int,
        startRow: Int,
        startCol: Int,
        endRow: Int,
        endCol: Int
    ): Boolean {
        val directions = arrayOf(
            -1 to 0, 1 to 0, 0 to -1, 0 to 1,  // Vertical and Horizontal
            -1 to -1, -1 to 1, 1 to -1, 1 to 1 // Diagonal directions
        )

        for (d in directions) {
            var row = startRow + d.first
            var col = startCol + d.second

            if (row in 0 until 10 && col in 0 until 10 && playerGameBoard[row * rowSize + col] == 'S') {
                return true // Ship is adjacent
            }

            row = endRow + d.first
            col = endCol + d.second

            if (row in 0 until 10 && col in 0 until 10 && playerGameBoard[row * rowSize + col] == 'S') {
                return true // Ship is adjacent
            }
        }
        return false // No adjacent ships found
    }

    // Handles Placing ships on the board. Checking for valid placements
    fun placeShip(
        playerGameBoard: MutableList<Char>,
        firstClickIndex: Int,                   // looks at where the player first pressed on the grid
        index: Int,                             // looks at the last index
        shipSize: Int                           // maps out according ot index
    ): Boolean {
        val rowSize = 10 // 10x10 grid

        val startRow = firstClickIndex / rowSize
        val startCol = firstClickIndex % rowSize
        val endRow = index / rowSize
        val endCol = index % rowSize

        if (startRow == endRow) { // Horizontal placement
            val minCol = minOf(startCol, endCol)
            val maxCol = maxOf(startCol, endCol)

            if ((maxCol - minCol + 1) != shipSize) return false

            for (col in minCol..maxCol) {
                if (playerGameBoard[startRow * rowSize + col] == 'S') return false
            }

            if (isAdjacentToAnotherShip(            // checks if its adjacent to another ship
                    playerGameBoard,
                    rowSize,
                    startRow,
                    minCol,
                    startRow,
                    maxCol
                )
            ) {
                return false
            }

            for (col in minCol..maxCol) {
                playerGameBoard[startRow * rowSize + col] = 'S'
            }
            return true
        } else if (startCol == endCol) { // Vertical placement
            val minRow = minOf(startRow, endRow)
            val maxRow = maxOf(startRow, endRow)

            if ((maxRow - minRow + 1) != shipSize) return false

            for (row in minRow..maxRow) {
                if (playerGameBoard[row * rowSize + startCol] == 'S') return false
            }

            if (isAdjacentToAnotherShip(
                    playerGameBoard,
                    rowSize,
                    minRow,
                    startCol,
                    maxRow,
                    startCol
                )
            ) {
                return false
            }

            for (row in minRow..maxRow) {
                playerGameBoard[row * rowSize + startCol] = 'S'
            }
            return true
        }
        return false
    }

    // Handles ship placement logic
    fun placeShips(index: Int) {
        if (shipQueue.isEmpty()) {
            placingShip = false
            readyToBattle = true // Show the "Ready" button

            if (gameId != null && model.localPlayerId.value != null) {
                saveShipCoordinatesToDatabase(gameId, model.localPlayerId.value!!, playerGameBoard) // sends information to DB

                // Mark the player as "ready"
                model.setPlayerReady(gameId, model.localPlayerId.value!!)
            }
            return // once all the ships are placed then it should exit the code
        }

        val (currentShip, size) = shipQueue.first()

        if (firstClickIndex == -1) {
            firstClickIndex = index
        } else {
            val success = placeShip(playerGameBoard, firstClickIndex, index, size)

            if (success) {
                Log.i("BattleShipInfo", "$currentShip placed from $firstClickIndex to $index")
                shipQueue.removeAt(0)
                firstClickIndex = -1
            } else {
                Log.e("BattleShipError", "Invalid placement for $currentShip. Try again.")
                firstClickIndex = -1
            }
        }
    }

    if (gameId != null && games.containsKey(gameId)) { // ensure valid game id
        val game = games[gameId]!!                      // ensure that game exist in game collection

        LaunchedEffect(game.gameState) {                // fetches game object when the gamestate chances then rerun
            model.refreshGameState(gameId!!)
        }


        Scaffold(topBar = { TopAppBar(title = { Text("BattleShip - $playerName") }) }) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Player 1: ${players[game.playerId1]?.name ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Player 2: ${players[game.playerId2]?.name ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text("State: ${game.gameState}", style = MaterialTheme.typography.bodyLarge)
                Text("Game ID: $gameId", style = MaterialTheme.typography.bodyLarge)

                Spacer(modifier = Modifier.height(20.dp))

                when (game.gameState) {
                    "Player 1 sank all BATTLESHIPS", "Player 2 sank all BATTLESHIPS" -> {
                        Text("Game Over!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            if (game.gameState == "Player 1 sank all BATTLESHIPS") "Player 1 WON"
                            else "Player 2 WON",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Button(onClick = { navController.navigate("lobby") }) {
                            Text("Back to Lobby")
                        }
                    }

                    else -> {
                        val myTurn =
                            (game.gameState == "player1_turn" && game.playerId1 == model.localPlayerId.value) ||
                                    (game.gameState == "player2_turn" && game.playerId2 == model.localPlayerId.value) // either player 1 turn or player 2 turn

                        var turnText by remember { mutableStateOf("Waiting for Opponent") }

                        // 🔹 Ensure turnText updates when gameState changes
                        LaunchedEffect(game.gameState) {
                            turnText = if (myTurn) "Your Turn" else "Waiting for Opponent" // switch depening if it is your turn or not
                            Log.d(
                                "BattleShipDebug",
                                "Turn updated: $turnText (gameState = ${game.gameState})"
                            )
                        }

                        Text(turnText, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(20.dp))

                        if (placingShip) {              // place ships
                            val (currentShip, size) = shipQueue.firstOrNull()
                                ?: "All ships placed" to 0
                            Text(
                                "Placing: $currentShip - Size: $size",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Your Board", style = MaterialTheme.typography.headlineMedium)
                            GameBoardGrid(
                                gameBoard = playerGameBoard,
                                isOpponentBoard = false,
                                onCellClick = { index -> placeShips(index) })

                            Spacer(modifier = Modifier.height(20.dp))

                        } else {
                            Text("Your Board", style = MaterialTheme.typography.headlineMedium)
                            GameBoardGrid(gameBoard = playerGameBoard, isOpponentBoard = false) {}

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                "Opponent's Board",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            GameBoardGrid(
                                gameBoard = if (model.localPlayerId.value == game.playerId1)
                                    game.gameBoard2.map { it.toChar() } // Convert Int to Char CONVERTS THE H to 72 becasue of Mutable list issue
                                else
                                    game.gameBoard1.map { it.toChar() }, // Convert Int to Char
                                isOpponentBoard = true
                            ) { index ->
                                if ((game.gameState == "player1_turn" && model.localPlayerId.value == game.playerId1) ||
                                    (game.gameState == "player2_turn" && model.localPlayerId.value == game.playerId2)
                                ) {
                                    takeShot(index)  // ✅ Only allow shooting if it's the player's turn
                                } else {
                                    Log.e("BattleShipError", "Not your turn!")
                                }
                            }
                        }
                    }

                }
            }
        }
    } else {
        Log.e("BattleShipError", "Error: Game not found! Game ID: $gameId")
        navController.navigate("lobby")
    }
}


@Composable
fun GameBoardGrid(
    gameBoard: List<Char>,
    isOpponentBoard: Boolean = false,
    onCellClick: (Int) -> Unit
) {
    Column {
        for (row in 0 until 10) {
            Row {
                for (col in 0 until 10) {
                    val index = row * 10 + col
                    Box(
                        modifier = Modifier
                            .size(30.dp)  // Slightly larger for better touch interaction
                            .padding(1.dp)
                            .aspectRatio(1f)

                            .background(
                                when (gameBoard[index]) {
                                    'W' -> Color.LightGray   // Water
                                    'S' -> if (isOpponentBoard) Color.LightGray else Color.Black  // Hide ships from opponent
                                    'H' -> Color.Red         // Hit
                                    'M' -> Color.Cyan
                                    else -> Color.Gray
                                }
                            )
                            .clickable {
                                onCellClick(index)  // Allow clicks on both boards
                            }
                    )
                }
            }
        }
    }
}


fun updatePlayerStatus(playerId: String, status: String) {      //pass String "isOnline"
    val db = FirebaseFirestore.getInstance()
    db.collection("players").document(playerId)     // in playerId updates the status to online
        .update("status", status)
        .addOnSuccessListener {
            Log.i("BattleShipInfo", "Player $playerId status updated to $status")
        }
        .addOnFailureListener { error ->
            Log.e("BattleShipError", "Failed to update status: ${error.message}")
        }
}

// Function to store ship coordinates in the Firebase database
fun saveShipCoordinatesToDatabase(
    gameId: String,
    localPlayerId: String,
    playerGameBoard: MutableList<Char>
) {
    val shipCoordinates = mutableListOf<Int>()

    // ✅ Collect the correct indices where ships are placed
    for (i in playerGameBoard.indices) {
        if (playerGameBoard[i] == 'S') {
            shipCoordinates.add(i) // Directly store 1D index
        }
    }

    // ✅ Get Firebase reference
    val db = FirebaseFirestore.getInstance()
    val gameRef = db.collection("games").document(gameId) // access the gameId and mutates the fields in that gameId

    // ✅ Fetch the game document
    gameRef.get()
        .addOnSuccessListener { document ->
            if (!document.exists()) {
                Log.e("BattleShipError", "Game document not found in Firestore.")
                return@addOnSuccessListener
            }

            val game = document.toObject(Game::class.java)
            if (game == null) {
                Log.e("BattleShipError", "Game object is null.")
                return@addOnSuccessListener
            }

            val updatedFields = mutableMapOf<String, Any>()

            when (localPlayerId) {
                game.playerId1 -> {
                    val updatedGameBoard1 = game.gameBoard1.toMutableList()
                    shipCoordinates.forEach { index ->
                        updatedGameBoard1[index] = 1 // ✅ Corrected indexing        // ship will be shown as 1 in the 1D  gameboard
                    }
                    updatedFields["gameBoard1"] = updatedGameBoard1                         // updatesGameBoard1 which is assigned to player 1
                    Log.i("BattleShipInfo", "Updating Player 1's game board.")
                }

                game.playerId2 -> {
                    val updatedGameBoard2 = game.gameBoard2.toMutableList()
                    shipCoordinates.forEach { index ->
                        updatedGameBoard2[index] = 1 // ✅ Corrected indexing
                    }
                    updatedFields["gameBoard2"] = updatedGameBoard2                 // updates gameBoard2 which is assign to the second player
                    Log.i("BattleShipInfo", "Updating Player 2's game board.")
                }

                else -> {
                    Log.e(
                        "BattleShipError",
                        "Local player ID does not match any player in the game."
                    )
                    return@addOnSuccessListener
                }
            }

            // ✅ Push the updates to Firestore
            gameRef.update(updatedFields)
                .addOnSuccessListener {
                    Log.i("BattleShipInfo", "Game board updated successfully.")
                }
                .addOnFailureListener { e ->
                    Log.e("BattleShipError", "Failed to update game board: $e")
                }
        }
        .addOnFailureListener { e ->
            Log.e("BattleShipError", "Failed to fetch game document: $e")
        }
}


private operator fun MutableList<Int>.set(index: Pair<Int, Int>, value: Int) {
    val (row, col) = index
    val rowSize = 10 // Assuming 10x10 board
    val linearIndex = row * rowSize + col
    this[linearIndex] = value
}

