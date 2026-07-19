package games.mancala

import games.core.GameEngine
import games.core.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MancalaRulesTest {
    private val south = PlayerId("south")
    private val north = PlayerId("north")

    @Test
    fun `classic game starts with six legal pits and forty eight stones`() {
        val (game, state) = Mancala.newGame(south, north)

        assertEquals(6, game.legalIntents(state).size)
        assertEquals(48, totalStones(state.board))
        assertEquals(KalahConfiguration(), game.configuration)
    }

    @Test
    fun `empty pits are not legal intents`() {
        val board = board(southPits = listOf(0, 1, 0, 0, 0, 0), northPits = listOf(1, 0, 0, 0, 0, 0))
        val (game, state) = Mancala.customGame(south, north, board)

        assertEquals(setOf(Sow(PitIndex(1))), game.legalIntents(state).map { it.intent }.toSet())
    }

    @Test
    fun `sowing skips the opponent store and preserves every stone`() {
        val board = board(
            southPits = listOf(14, 0, 0, 0, 0, 1),
            northPits = listOf(1, 0, 0, 0, 0, 0),
            northStore = 7,
        )
        val (game, state) = Mancala.customGame(south, north, board)
        val result = GameEngine(game).play(state, south, Sow(PitIndex(0)))

        assertEquals(7, result.board.stores.getValue(Side.NORTH))
        assertEquals(totalStones(board), totalStones(result.board))
    }

    @Test
    fun `sowing continues into opponent pits adjacent to own store`() {
        val (game, initial) = Mancala.newGame(south, north)
        val engine = GameEngine(game)

        val afterExtraTurn = engine.play(initial, south, Sow(PitIndex(2)))
        val result = engine.play(afterExtraTurn, south, Sow(PitIndex(3)))

        assertEquals(listOf(4, 4, 0, 0, 6, 6), result.board.pits.getValue(Side.SOUTH))
        assertEquals(listOf(5, 5, 4, 4, 4, 4), result.board.pits.getValue(Side.NORTH))
        assertEquals(2, result.board.stores.getValue(Side.SOUTH))
        assertEquals(0, result.board.stores.getValue(Side.NORTH))
        assertEquals(48, totalStones(result.board))
    }

    @Test
    fun `landing in own store grants another turn`() {
        val (game, initial) = Mancala.newGame(south, north)
        val result = GameEngine(game).play(initial, south, Sow(PitIndex(2)))

        assertEquals(south, result.currentPlayer)
        assertTrue((result.history.events.last().event as MancalaEvent.TurnAdvanced).extraTurn)
    }

    @Test
    fun `landing in empty own pit captures the opposite stones`() {
        val board = board(
            southPits = listOf(1, 0, 0, 0, 1, 0),
            northPits = listOf(3, 1, 0, 0, 0, 0),
        )
        val (game, state) = Mancala.customGame(south, north, board)
        val result = GameEngine(game).play(state, south, Sow(PitIndex(4)))

        assertEquals(4, result.board.stores.getValue(Side.SOUTH))
        assertEquals(0, result.board.stonesAt(Cup.Pit(Side.SOUTH, PitIndex(5))))
        assertEquals(0, result.board.stonesAt(Cup.Pit(Side.NORTH, PitIndex(0))))
        assertFalse(result.history.events.none { it.event is MancalaEvent.StonesCaptured })
    }

    private fun board(
        southPits: List<Int>,
        northPits: List<Int>,
        southStore: Int = 0,
        northStore: Int = 0,
    ) = MancalaBoard(
        pits = mapOf(Side.SOUTH to southPits, Side.NORTH to northPits),
        stores = mapOf(Side.SOUTH to southStore, Side.NORTH to northStore),
    )

    private fun totalStones(board: MancalaBoard) = board.pits.values.flatten().sum() + board.stores.values.sum()
}
