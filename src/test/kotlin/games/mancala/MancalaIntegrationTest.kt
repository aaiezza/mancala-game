package games.mancala

import games.core.GameEngine
import games.core.GameOutcome
import games.core.PlayerId
import games.core.RuleId
import games.core.TransitionCause
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MancalaIntegrationTest {
    private val south = PlayerId("south")
    private val north = PlayerId("north")

    @Test
    fun `emptying one side sweeps remaining stones and ends the game`() {
        val board = MancalaBoard(
            pits = mapOf(
                Side.SOUTH to listOf(0, 0, 0, 0, 0, 1),
                Side.NORTH to listOf(2, 0, 0, 0, 0, 0),
            ),
            stores = mapOf(Side.SOUTH to 0, Side.NORTH to 0),
        )
        val (game, initial) = Mancala.customGame(south, north, board)

        val progression = GameEngine(game).playWithTrace(initial, south, Sow(PitIndex(5)))
        val result = progression.resultingState

        assertEquals(MancalaStatus.Won(north, Side.NORTH), result.status)
        assertTrue(result.decisionActors.isEmpty())
        assertTrue(result.board.pits.values.flatten().all { it == 0 })
        assertEquals(1, result.board.stores.getValue(Side.SOUTH))
        assertEquals(2, result.board.stores.getValue(Side.NORTH))
        assertEquals(GameOutcome.PlayerWon(north), game.outcome(result))
        assertEquals(3, result.history.events.size)
        assertEquals(
            TransitionCause.RuleDriven(RuleId("mancala.game-end")),
            progression.steps.last().cause,
        )
        assertTrue(progression.steps.last().events.single() is MancalaEvent.GameWon)
    }

    @Test
    fun `equal stores produce a draw`() {
        val board = MancalaBoard(
            pits = mapOf(
                Side.SOUTH to listOf(0, 0, 0, 0, 0, 1),
                Side.NORTH to listOf(1, 0, 0, 0, 0, 0),
            ),
            stores = mapOf(Side.SOUTH to 0, Side.NORTH to 0),
        )
        val (game, initial) = Mancala.customGame(south, north, board)

        val result = GameEngine(game).play(initial, south, Sow(PitIndex(5)))

        assertEquals(MancalaStatus.Draw, result.status)
        assertEquals(GameOutcome.Draw, game.outcome(result))
    }

    @Test
    fun `terminal UI renders sowing capture and turn advancement in order`() {
        val board = MancalaBoard(
            pits = mapOf(
                Side.SOUTH to listOf(1, 0, 0, 0, 1, 0),
                Side.NORTH to listOf(3, 1, 0, 0, 0, 0),
            ),
            stores = mapOf(Side.SOUTH to 0, Side.NORTH to 0),
        )
        val (game, initial) = Mancala.customGame(south, north, board)

        val progression = GameEngine(game).playWithTrace(initial, south, Sow(PitIndex(4)))
        val rendered = MancalaTerminalRenderer.renderProgression(progression)

        val sowing = rendered.indexOf("Player-driven: south submitted")
        val capture = rendered.indexOf("Rule-driven: mancala.capture")
        val turnAdvance = rendered.indexOf("Rule-driven: mancala.advance-turn")
        assertTrue(sowing >= 0)
        assertTrue(capture > sowing)
        assertTrue(turnAdvance > capture)
        assertTrue("Captured 4 stones" in rendered)
        assertTrue("Awaiting sow: turn owner north; active side NORTH" in rendered)
        assertTrue("Resolving sow: south on SOUTH; no player decision required" in rendered)
    }
}
