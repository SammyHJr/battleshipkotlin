package com.example.battleshipkotlin

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.materialIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


@Composable
fun BattleShip(){
    val navController = rememberNavController()
    val model = GameModel()
    model.initGame()
    Log.i("BattleShipInfo", "In battleship()")
    NavHost(navController = navController, startDestination = "Player"){
        composable("player") {NewPlayerScreen(navController, model)}
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

                                    existingPlayer.reference.update("isOnline", true)
                                        .addOnSuccessListener {
                                            sharedPreference.edit().putString("playerId", existingPlayerId).apply()
                                            model.localPlayerId.value = existingPlayerId

                                            // ✅ Show "Welcome Back" message
                                            Toast.makeText(context, "Welcome back, $playerName!", Toast.LENGTH_LONG).show()

                                            navController.navigate("Lobby")
                                        }
                                        .addOnFailureListener { error ->
                                            Log.e("BattleShipError", "Error updating player status: ${error.message}")
                                        }
                                } else {
                                    // ✅ Player does not exist, create new
                                    val newPlayerData = hashMapOf(
                                        "name" to playerName,
                                        "isOnline" to true
                                    )

                                    model.db.collection("players")
                                        .add(newPlayerData)
                                        .addOnSuccessListener { documentRef ->
                                            val newPlayerId = documentRef.id

                                            sharedPreference.edit().putString("playerId", newPlayerId).apply()
                                            model.localPlayerId.value = newPlayerId

                                            // ✅ Show "New Player Created" message
                                            Toast.makeText(context, "New player created: $playerName!", Toast.LENGTH_LONG).show()

                                            navController.navigate("Lobby")
                                        }
                                        .addOnFailureListener { error ->
                                            Log.e("BattleShipError", "Error creating player: ${error.message}")
                                        }
                                }
                            }
                            .addOnFailureListener { error ->
                                Log.e("BattleShipError", "Error checking for existing player: ${error.message}")
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

    // 👇 LISTEN FOR CHALLENGES
    LaunchedEffect(Unit) {
        model.db.collection("games")
            .whereEqualTo("playerId2", model.localPlayerId.value)
            .whereEqualTo("gameState", "invite")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documents?.firstOrNull()?.let { doc ->
                    val player1Id = doc.getString("playerId1")
                    val gameId = doc.id

                    if (player1Id != null) {
                        model.db.collection("players").document(player1Id).get()
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
        topBar = { TopAppBar(title = { Text("BattleShip - $playerName") }) }
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
                                        .whereEqualTo("name", player.name) // Find the player by name
                                        .get()
                                        .addOnSuccessListener { querySnapshot ->
                                            val player2Id = querySnapshot.documents.firstOrNull()?.id // Get the document ID
                                            if (player2Id != null) {
                                                model.db.collection("games").add(Game(
                                                    gameState = "invite",
                                                    playerId1 = model.localPlayerId.value!!, // The challenging player
                                                    playerId2 = player2Id // The challenged player's ID
                                                )).addOnSuccessListener { documentRef ->
                                                    navController.navigate("game/${documentRef.id}") // Navigate to game screen

                                                }.addOnFailureListener {
                                                    Log.e("BattleShipError", "Error creating challenge")
                                                }
                                            } else {
                                                Log.e("BattleShipError", "Player2 ID not found")
                                            }
                                        }
                                        .addOnFailureListener { exception ->
                                            Log.e("BattleShipError", "Error fetching Player2 ID: ${exception.message}")
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
    var opponentGameBoard by remember { mutableStateOf(MutableList(100) { 'W' }) } // Opponent's board
    var firstClickIndex by remember { mutableStateOf(-1) }  // Track start of ship placement
    var placingShip by remember { mutableStateOf(true) }

    val playerName = players[model.localPlayerId.value]?.name ?: "Unknown"

    // Ordered ship queue for placement
    val shipQueue = remember {
        mutableStateListOf(
            "Carrier" to 4,
            "Battleship" to 3,
            "Cruiser" to 2,
            "Submarine1" to 2,
            "Submarine2" to 2,
            "Destroyer1" to 1,
            "Destroyer2" to 1
        )
    }



    // Function to place a ship
    fun placeShip(playerGameBoard: MutableList<Char>, firstClickIndex: Int, index: Int, shipSize: Int): Boolean {
        val rowSize = 10 // 10x10 grid

        val startRow = firstClickIndex / rowSize
        val startCol = firstClickIndex % rowSize
        val endRow = index / rowSize
        val endCol = index % rowSize

        // Check if ship is placed in a straight line (horizontal or vertical)
        if (startRow == endRow) { // Horizontal placement
            val minCol = minOf(startCol, endCol)
            val maxCol = maxOf(startCol, endCol)

            if ((maxCol - minCol + 1) != shipSize) return false // Ensure correct ship size

            // Ensure no overlapping ships
            for (col in minCol..maxCol) {
                if (playerGameBoard[startRow * rowSize + col] == 'S') return false
            }

            // Place the ship
            for (col in minCol..maxCol) {
                playerGameBoard[startRow * rowSize + col] = 'S'
            }
            return true

        } else if (startCol == endCol) { // Vertical placement
            val minRow = minOf(startRow, endRow)
            val maxRow = maxOf(startRow, endRow)

            if ((maxRow - minRow + 1) != shipSize) return false // Ensure correct ship size

            // Ensure no overlapping ships
            for (row in minRow..maxRow) {
                if (playerGameBoard[row * rowSize + startCol] == 'S') return false
            }

            // Place the ship
            for (row in minRow..maxRow) {
                playerGameBoard[row * rowSize + startCol] = 'S'
            }
            return true
        }

        return false // Invalid placement (diagonal or wrong size)
    }


    // Handles ship placement logic
    fun placeShips(index: Int) {
        if (shipQueue.isEmpty()) {
            placingShip = false // All ships placed, proceed to battle phase
            return
        }

        val (currentShip, size) = shipQueue.first()

        if (firstClickIndex == -1) {
            // First click: set the starting position
            firstClickIndex = index
        } else {
            // Second click: attempt to place the ship
            val success = placeShip(playerGameBoard, firstClickIndex, index, size)

            if (success) {
                Log.i("BattleShipInfo", "$currentShip placed from $firstClickIndex to $index")
                shipQueue.removeAt(0) // Move to the next ship
                firstClickIndex = -1 // Reset for next ship placement
            } else {
                Log.e("BattleShipError", "Invalid placement for $currentShip. Try again.")
                firstClickIndex = -1 // Reset selection for retry
            }
        }
    }


    if (gameId != null && games.containsKey(gameId)) {
        val game = games[gameId]!!

        Scaffold(topBar = { TopAppBar(title = { Text("BattleShip - $playerName") }) }) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Player 1: ${players[game.playerId1]?.name ?: "Unknown"}", style = MaterialTheme.typography.bodyLarge)
                Text("Player 2: ${players[game.playerId2]?.name ?: "Unknown"}", style = MaterialTheme.typography.bodyLarge)
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
                            (game.gameState == "player 1 turn" && game.playerId1 == model.localPlayerId.value) ||
                                    (game.gameState == "player 2 turn" && game.playerId2 == model.localPlayerId.value)
                        val turnText = if (myTurn) "Your Turn" else "Waiting for Opponent"

                        Text(turnText, style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(20.dp))

                        if (placingShip) {
                            val (currentShip, size) = shipQueue.firstOrNull() ?: "All ships placed" to 0
                            Text("Placing: $currentShip - Size: $size", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Your Board", style = MaterialTheme.typography.headlineMedium)
                            GameBoardGrid(gameBoard = playerGameBoard, isOpponentBoard = false, onCellClick = { index ->
                                placeShips(index)
                            })

                            Spacer(modifier = Modifier.height(20.dp))
                        } else {
                            Text("Your Board", style = MaterialTheme.typography.headlineMedium)
                            GameBoardGrid(gameBoard = playerGameBoard, isOpponentBoard = false) {}

                            Spacer(modifier = Modifier.height(20.dp))

                            Text("Opponent's Board", style = MaterialTheme.typography.headlineMedium)
                            GameBoardGrid(gameBoard = opponentGameBoard, isOpponentBoard = true) {}
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
                            .size(32.dp)  // Slightly larger for better touch interaction
                            .padding(1.dp)
                            .aspectRatio(1f)

                            .background(
                                when (gameBoard[index]) {
                                    'W' -> Color.LightGray   // Water
                                    'S' -> if (isOpponentBoard) Color.LightGray else Color.Black  // Hide ships from opponent
                                    'H' -> Color.Red         // Hit
                                    else -> Color.DarkGray
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


fun updatePlayerStatus(playerId: String, status: String) {
    val db = FirebaseFirestore.getInstance()
    db.collection("players").document(playerId)
        .update("status", status)
        .addOnSuccessListener {
            Log.i("BattleShipInfo", "Player $playerId status updated to $status")
        }
        .addOnFailureListener { error ->
            Log.e("BattleShipError", "Failed to update status: ${error.message}")
        }
}