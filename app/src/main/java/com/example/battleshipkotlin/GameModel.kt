package com.example.battleshipkotlin

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow


data class Player(
    val name: String = "",
    val isOnline: Boolean = false,
    val id: String = ""
) {
    constructor() : this("", false)  // No-argument constructor required by Firestore
}


data class Game(
    var gameBoard1: MutableList<Int> = MutableList(100) { 0 }, // ✅ Mutable
    var gameBoard2: MutableList<Int> = MutableList(100) { 0 }, // ✅ Mutable
    var gameState: String = "invite",
    var playerId1: String = "",
    var playerId2: String = ""
)

const val rows = 10;
const val cols = 10;

class GameModel : ViewModel() {
    val db = Firebase.firestore
    var localPlayerId = mutableStateOf<String?>(null)
    val playerMap = MutableStateFlow<Map<String, Player>>(emptyMap())
    val gameMap = MutableStateFlow<Map<String, Game>>(emptyMap())

    fun initGame() {
        //listen for players
        db.collection("players")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (value != null) {
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Player::class.java)!!
                    }
                    playerMap.value = updatedMap
                }
            }

        //listen for games
        db.collection("games")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (value != null) {
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Game::class.java)!!
                    }
                    gameMap.value = updatedMap
                }
            }

        fun checkWinner(game: Game): Int? {
            val player1ShipsLeft =
                game.gameBoard1.count { it == 1 } // Count ships on Player 1's board
            val player2ShipsLeft =
                game.gameBoard2.count { it == 1 } // Count ships on Player 2's board

            return when {
                player1ShipsLeft == 0 -> 2  // Player 2 wins
                player2ShipsLeft == 0 -> 1  // Player 1 wins
                else -> null  // No winner yet
            }
        }


    }

    fun setPlayerReady(gameId: String, playerId: String) {
        val gameRef = Firebase.firestore.collection("games").document(gameId)

        Firebase.firestore.runTransaction { transaction ->
            val gameSnapshot = transaction.get(gameRef)
            val readyPlayers =
                gameSnapshot.get("readyPlayers") as? MutableList<String> ?: mutableListOf()

            Log.d("BattleShipDebug", "Ready Players Before Update: $readyPlayers")

            if (playerId in readyPlayers) return@runTransaction // Already ready

            readyPlayers.add(playerId)
            transaction.update(gameRef, "readyPlayers", readyPlayers)

            val playerId1 = gameSnapshot.getString("playerId1") ?: ""
            val playerId2 = gameSnapshot.getString("playerId2") ?: ""

            // ✅ Ensure game starts when both players are ready
            if (readyPlayers.containsAll(listOf(playerId1, playerId2))) {
                transaction.update(gameRef, "gameState", "player1_turn")
                Log.d("BattleShipDebug", "Both players ready! Game starting with player1_turn.")
            }
        }.addOnSuccessListener {
            Log.d("BattleShipInfo", "Player $playerId is ready. Game updated.")
        }.addOnFailureListener { e ->
            Log.e("BattleShipError", "Failed to update readiness: ", e)
        }
    }

    fun refreshGameState(gameId: String) {
        val gameRef = Firebase.firestore.collection("games").document(gameId)

        gameRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val updatedGame = document.toObject(Game::class.java)
                    updatedGame?.let {
                        val updatedGameMap = gameMap.value.toMutableMap()
                        updatedGameMap[gameId] = it
                        gameMap.value = updatedGameMap

                        Log.d("BattleShipDebug", "Game state refreshed: ${it.gameState}")
                    }
                }
            }
        .addOnFailureListener { e ->
            Log.e("BattleShipError", "Failed to refresh game state: ", e)
        }
    }

}