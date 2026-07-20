package games.mancala

import games.core.GameEngine
import games.core.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KalahArtificialPlayerTest {
    @Test
    fun `artificial player can choose for either side in self play`() {
        val player = PlayerId("computer")
        val (game, initial) = Mancala.newSelfPlayGame(player)
        val northTurn = GameEngine(game).play(initial, player, Sow(PitIndex(0)))

        val chosen = KalahArtificialPlayer(player, searchDepth = 1).chooseIntent(game, northTurn)

        assertEquals(Side.NORTH, northTurn.currentSide)
        assertTrue(chosen in game.legalIntents(northTurn).map { it.intent })
    }

    @Test
    fun `artificial player chooses an available capture`() {
        val south = PlayerId("computer")
        val north = PlayerId("human")
        val board = MancalaBoard(
            pits = mapOf(
                Side.SOUTH to listOf(1, 0, 0, 0, 1, 0),
                Side.NORTH to listOf(3, 1, 0, 0, 0, 0),
            ),
            stores = mapOf(Side.SOUTH to 0, Side.NORTH to 0),
        )
        val (game, state) = Mancala.customGame(south, north, board)

        val chosen = KalahArtificialPlayer(south, searchDepth = 3).chooseIntent(game, state)

        assertEquals(Sow(PitIndex(4)), chosen)
    }
}
