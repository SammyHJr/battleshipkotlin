package com.example.battleshipkotlin

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.*
import com.google.firebase.firestore.toObject



data class Player(
    val name: String = "",
    val isOnline: Boolean = false,
    val id: String = ""
) {
    constructor() : this("", false)  // No-argument constructor required by Firestore
}


data class Game(
    var gameBoard1: List<Int> = List(100) {0},
    var gameBoard2: List<Int> = List(100) {0},
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
            val player1ShipsLeft = game.gameBoard1.count { it == 1 } // Count ships on Player 1's board
            val player2ShipsLeft = game.gameBoard2.count { it == 1 } // Count ships on Player 2's board

            return when {
                player1ShipsLeft == 0 -> 2  // Player 2 wins
                player2ShipsLeft == 0 -> 1  // Player 1 wins
                else -> null  // No winner yet
            }
        }



        fun checkGameState(gameId: String?, cell: Int) {
            if (gameId == null) return

            val game: Game? = gameMap.value[gameId]
            if (game != null) {

                val isPlayer1 = game.playerId1 == localPlayerId.value
                val isPlayer2 = game.playerId2 == localPlayerId.value
                val myTurn = (game.gameState == "player1_turn" && isPlayer1) ||
                        (game.gameState == "player2_turn" && isPlayer2)

                if (!myTurn) return

                // Determine the board to update (Player 1 attacks Player 2's board and vice versa)
                val opponentBoard = if (isPlayer1) game.gameBoard2.toMutableList() else game.gameBoard1.toMutableList()

                // Check what is currently in the cell
                when (opponentBoard[cell]) {
                    1 -> opponentBoard[cell] = 'H'.code // Ship hit (H)
                    0 -> opponentBoard[cell] = 'M'.code // Missed shot (M)
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
                val updatedFields = mutableMapOf<String, Any>(
                    "gameState" to turn
                )

                if (isPlayer1) {
                    updatedFields["gameBoard2"] = opponentBoard
                } else {
                    updatedFields["gameBoard1"] = opponentBoard
                }

                db.collection("games").document(gameId)
                    .update(updatedFields)
                    .addOnSuccessListener {
                        Log.d("BattleShipInfo", "Game state updated successfully.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("BattleShipError", "Failed to update game state: ", e)
                    }
            }
        }


    }

    fun setPlayerReady(gameId: String, playerId: String) {
        val gameRef = Firebase.firestore.collection("games").document(gameId)

        Firebase.firestore.runTransaction { transaction ->
            val gameSnapshot = transaction.get(gameRef)
            val readyPlayers = gameSnapshot.getLong("readyPlayers") ?: 0
            val newReadyCount = readyPlayers + 1

            transaction.update(gameRef, "readyPlayers", newReadyCount)

            if (newReadyCount >= 2) {
                transaction.update(gameRef, "gameState", "player 1 turn")
            }
        }.addOnSuccessListener {
            Log.d("BattleShipInfo", "Player $playerId is ready. Game updated.")
        }.addOnFailureListener { e ->
            Log.e("BattleShipError", "Failed to update readiness: ", e)
        }
    }


}