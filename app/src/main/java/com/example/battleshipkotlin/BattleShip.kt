package com.example.battleshipkotlin

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


@Composable
fun BattleShip(){
    val navController = rememberNavController()
    val model = GameModel()
    model.initGame()

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

    LaunchedEffect(Unit) {
        model.localPlayerId.value = sharedPreference.getString("playerId", null)
        if (model.localPlayerId.value != null) {
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
            Text("Welcome to BattleShip")

            Spacer(
                modifier = Modifier
                    .height(16.dp)
            )

            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text("Enter Your Name") },
                modifier = Modifier.fillMaxSize()
            )

            Spacer(
                modifier = Modifier
                    .height(16.dp)
            )

            Button(
                onClick = {
                    if (playerName.isNotBlank()) {
                        var newPlayer = Player(name = playerName)

                        model.db.collection("players")
                            .add(newPlayer)
                            .addOnSuccessListener { documentRef ->
                                val newPlayerId = documentRef.id

                                sharedPreference.edit().putString("playerId", newPlayerId).apply()

                                model.localPlayerId.value = newPlayerId
                                navController.navigate("Lobby")
                            }
                            .addOnFailureListener { error ->
                                Log.e("BattleShipError", "Error creating player: ${error.message}")
                            }
                    }
                },
                modifier = Modifier.fillMaxSize()
                    ) {
                Text("Create Player")
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

    LaunchedEffect(games) {
        games.forEach {( gameId, game) ->
            if((game.playerId1 == model.localPlayerId.value || game.playerId2 ==model.localPlayerId.value )
                && (game.gameState == "player 1 turn" || game.gameState == "player 2 turn")) {
                navController.navigate("game/${gameId}")
            }
        }
    }

    var playerName = "Unknown?"
    player[model.localPlayerId.value]?.let {
        playerName = it.name
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("BattleShip - $playerName")}) }
    ){
       innerPadding ->
        LazyColumn (modifier = Modifier
            .padding(innerPadding)) {
            items(player.entries.toList()) {
                (documentId, player) ->
                if(documentId != model.localPlayerId.value) {
                    ListItem(
                        headlineContent = {
                            Text("player Name: ${player.name}")
                        },
                        supportingContent = {
                            Text("Status...")
                        },
                        trailingContent = {
                            var hasGame = false
                            games.forEach { (gameId, game) ->
                                if(game.playerId1 == model.localPlayerId.value && game.gameState == "invite") {
                                    Text("Waiting for player2 to accept the challenge...")
                                    hasGame = true
                                } else if (game.playerId2 == model.localPlayerId.value && game.gameState == "invite") {
                                    Button( onClick = {
                                        model.db.collection("games").document(gameId).update("gameState", "player1 turn").addOnSuccessListener {
                                            navController.navigate("games/${gameId}")
                                        }
                                            .addOnFailureListener {
                                                Log.e(
                                                    "BattleShipError", "Error updating game: $gameId"
                                                )
                                            }
                                    } ) {
                                        Text("Accept invite")
                                    }
                                    hasGame = true
                                }
                            }
                            if(!hasGame) {
                                Button( onClick = {
                                    model.db.collection("games").add(Game(gameState = "invite",
                                        playerId1 = model.localPlayerId.value!!,
                                        playerId2 = documentId))
                                        .addOnSuccessListener { documentRef ->
                                            //TODO NAVIGATE
                                        }
                                }) {
                                    Text("Challange")
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

    var playerName = "Unknown??"
    players[model.localPlayerId.value]?.let {
        playerName = it.name
    }

    if (gameId != null && games.containsKey(gameId)) {
        val game = games[gameId]!!
        Scaffold(
            topBar = { TopAppBar(title = { Text("BattleShip - $playerName") }) }
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                when (game.gameState) {
                    "Player 1 sank all BATTLESHIPS", "Player 2 sank all BATTLESHIPS" -> {
                        Text("Game over!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(
                            modifier = Modifier
                                .padding(20.dp)
                        )

                        if (game.gameState == "Player 1 sank all BATTLESHIP") {
                            Text(
                                "Player 1 WON", style = MaterialTheme.typography.headlineMedium
                            )
                        } else {
                            Text(
                                "Player 2 WON", style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Button(onClick = {
                            navController.navigate("lobby")
                        }) {
                            Text("Back to the Lobby")
                        }
                    }

                    else -> {
                        val myTurn =
                            game.gameState == "player 1 turn" && game.playerId1 == model.localPlayerId.value ||
                                    game.gameState == "player 2 turn" && game.playerId2 == model.localPlayerId.value
                        val turn = if (myTurn) "Your Turn" else "Wait for other players turn"
                        Text(turn, style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.padding(20.dp))

                        Text("Player1: ${players[game.playerId1]!!.name}")
                        Text("Player2: ${players[game.playerId2]!!.name}")
                        Text("State: ${game.gameState}")
                        Text("GameId: ${gameId}")

                    }
                }
                Spacer(
                    modifier = Modifier
                        .padding(20.dp)
                )

                for (i in 0..<rows) {
                    Row {
                        for (j in 0..<cols)
                            Button(modifier = Modifier
                                .size(100.dp)
                                .padding(20.dp),
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                                onClick = {
                                    model.checkGameState(gameId, i * cols + j)
                                }
                            ) {
                                if (game.gameBoard[i * cols + j] == 1) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.outline_cross_24),
                                        tint = Color.Red,
                                        contentDescription = "X",
                                        modifier = Modifier.size(48.dp)
                                    )
                                } else if (game.gameBoard[i * cols + j] == 2) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.outline_circle_24),
                                        tint = Color.Blue,
                                        contentDescription = "O",
                                        modifier = Modifier.size(48.dp)
                                    )
                                } else {
                                    Text("")
                                }
                            }
                    }
                }
            }
        }
    } else {
        Log.e("BattleShipError", "Error Game not found: $gameId")
        navController.navigate("lobby")
    }
}<