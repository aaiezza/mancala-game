package games.mancala

import games.core.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KalahArtificialPlayerTest {
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
