package games.mancala

import games.core.GameEngine
import games.core.PlayerId
import games.core.RuleId
import games.core.TransitionCause
import games.core.TurnContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `one player can play both sides`() {
        val player = PlayerId("solo")
        val (game, initial) = Mancala.newSelfPlayGame(player)
        val engine = GameEngine(game)

        val afterSouthMove = engine.play(initial, player, Sow(PitIndex(0)))
        val afterNorthMove = engine.play(afterSouthMove, player, Sow(PitIndex(0)))

        assertEquals(Side.NORTH, afterSouthMove.activeSide)
        assertEquals(player, afterSouthMove.currentPlayer)
        assertEquals(Side.SOUTH, afterNorthMove.activeSide)
        assertEquals(player, afterNorthMove.currentPlayer)
    }

    @Test
    fun `current player is derived from the player on the current side`() {
        val board = board(
            southPits = listOf(1, 0, 0, 0, 0, 0),
            northPits = listOf(1, 0, 0, 0, 0, 0),
        )

        val (_, state) = Mancala.customGame(south, north, board, currentSide = Side.NORTH)

        val status = assertInstanceOf(MancalaStatus.AwaitingSow::class.java, state.status)
        assertEquals(north, state.currentPlayer)
        assertEquals(TurnContext(north), status.turn)
        assertEquals(Side.NORTH, status.activeSide)
    }

    @Test
    fun `custom games require explicit self play construction`() {
        val board = board(
            southPits = listOf(1, 0, 0, 0, 0, 0),
            northPits = listOf(1, 0, 0, 0, 0, 0),
        )
        val player = PlayerId("solo")

        assertThrows(IllegalArgumentException::class.java) {
            Mancala.customGame(player, player, board)
        }

        val (_, state) = Mancala.customSelfPlayGame(player, board, currentSide = Side.NORTH)
        assertEquals(Side.NORTH, state.activeSide)
        assertEquals(player, state.currentPlayer)
    }

    @Test
    fun `status rejects a turn owner who does not control the active side`() {
        val board = board(
            southPits = listOf(1, 0, 0, 0, 0, 0),
            northPits = listOf(1, 0, 0, 0, 0, 0),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MancalaState(
                board = board,
                registry = MancalaPlayers(south, north),
                status = MancalaStatus.AwaitingSow(TurnContext(north), Side.SOUTH),
            )
        }
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
        val progression = GameEngine(game).playWithTrace(state, south, Sow(PitIndex(4)))
        val result = progression.resultingState

        assertEquals(4, result.board.stores.getValue(Side.SOUTH))
        assertEquals(0, result.board.stonesAt(Cup.Pit(Side.SOUTH, PitIndex(5))))
        assertEquals(0, result.board.stonesAt(Cup.Pit(Side.NORTH, PitIndex(0))))
        assertFalse(result.history.events.none { it.event is MancalaEvent.StonesCaptured })
        assertEquals(
            listOf(
                TransitionCause.PlayerDriven(south, Sow(PitIndex(4))),
                TransitionCause.RuleDriven(RuleId("mancala.capture")),
                TransitionCause.RuleDriven(RuleId("mancala.advance-turn")),
            ),
            progression.steps.map { it.cause },
        )
        assertEquals(0, progression.steps.first().resultingState.board.stores.getValue(Side.SOUTH))
        assertEquals(1, progression.steps.first().resultingState.board.stonesAt(Cup.Pit(Side.SOUTH, PitIndex(5))))
        assertTrue(progression.steps.first().resultingState.status is MancalaStatus.ResolvingSow)
        assertTrue(progression.steps.first().resultingState.decisionActors.isEmpty())
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
