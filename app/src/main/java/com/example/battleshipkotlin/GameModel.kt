package com.example.battleshipkotlin

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.*
import com.google.firebase.firestore.toObject

data class Player (
    var name : String = ""
)

data class Game(
    var gameBoard: List<Int> = List(100) {0},
    var gameState: String = "invite",
    var playerId1: String = "",
    var playerId2: String = ""
)

const val rows = 10;
const val cols = 10;

class GameModel: ViewModel(){
    val db = Firebase.firestore
    var localPlayerId = mutableStateOf<String?>(null)
    val playerMap = MutableStateFlow<Map<String, Player>>(emptyMap())
    val gameMap = MutableStateFlow<Map<String, Game>>(emptyMap())

    fun initGame(){
        //listen for players
        db.collection("players")
            .addSnapshotListener{ value, error ->
                if(error != null){
                    return@addSnapshotListener
                }
                if(value != null){
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Player:: class.java)!!
                    }
                    playerMap.value = updatedMap
                }
            }

        //listen for games
        db.collection("games")
            .addSnapshotListener{ value, error ->
                if(error != null){
                    return@addSnapshotListener
                }
                if(value != null){
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Game::class.java)!!
                    }
                    gameMap.value = updatedMap
                }
            }

        fun checkWinner(game: Game): Boolean {
            for (cell in game.gameBoard) {
                if (cell == 1) {
                    return false // There is at least one unhit ship
                }
            }
            return true // No unhit ships left
        }

        fun checkGameState(gameId: String?, cell: Int) {
            if (gameId != null) {
                val game: Game? = gameMap.value[gameId]
                if (game != null) {

                    val myTurn = game.gameState == "player1_turn" && game.playerId1 == localPlayerId.value ||
                            game.gameState == "player2_turn" && game.playerId2 == localPlayerId.value
                    if (!myTurn) return

                    val list: MutableList<Int> = game.gameBoard.toMutableList()

                    // Mark the cell as "hit" (2) if it's a ship (1)
                    if (list[cell] == 1 || list[cell] == 2) {
                        list[cell] = 2  // If it's a ship, mark it as hit
                    } else {
                        // Invalid move (cell is water or already hit)
                        return
                    }

                    // Determine the next turn
                    var turn = if (game.gameState == "player1_turn") "player2_turn" else "player1_turn"

                    // Check if there's a winner by calling checkWinner
                    val winner = checkWinner(game)
                    if (winner == 1) {
                        turn = "player1_won"
                    } else if (winner == 2) {
                        turn = "player2_won"
                    } else if (list.none { it == 1 }) { // No ships left for either player
                        turn = "draw"
                    }

                    // Update the game state in Firebase
                    db.collection("games").document(gameId)
                        .update(
                            "gameBoard", list,
                            "gameState", turn
                        )
                }
            }
        }


    }
}