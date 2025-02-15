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

        fun checkWinner(game: Game): Int? {
            val list = game.gameBoard
            val player1ShipsLeft = list.count { it == 1 } // Remaining ships for Player 1
            val player2ShipsLeft = list.count { it == 2 } // Remaining ships for Player 2 (if using a single board)

            return when {
                player1ShipsLeft == 0 -> 2  // Player 2 wins
                player2ShipsLeft == 0 -> 1  // Player 1 wins
                else -> null  // No winner yet
            }
        }


        fun checkGameState(gameId: String?, cell: Int) {
            if (gameId != null) {
                val game: Game? = gameMap.value[gameId]
                if (game != null) {

                    val myTurn = (game.gameState == "player1_turn" && game.playerId1 == localPlayerId.value) ||
                            (game.gameState == "player2_turn" && game.playerId2 == localPlayerId.value)
                    if (!myTurn) return

                    val list: MutableList<Int> = game.gameBoard.toMutableList()

                    // Check what is currently in the cell
                    when (list[cell]) {
                        1 -> list[cell] = 'H'.code // Ship hit (H)
                        0 -> list[cell] = 'M'.code // Missed shot (M)
                        'H'.code, 'M'.code -> return // Ignore already hit/missed cells
                    }

                    // Determine next turn
                    var turn = if (game.gameState == "player1_turn") "player2_turn" else "player1_turn"

                    // Check if there is a winner
                    val winner = checkWinner(game)
                    if (winner == 1) {
                        turn = "player1_won"
                    } else if (winner == 2) {
                        turn = "player2_won"
                    }

                    // Update Firebase with the new board state and turn
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