package games.mancala

import games.core.GameEngine
import games.core.GameOutcome
import games.core.PlayerId
import games.core.Progression
import games.core.TransitionCause

object MancalaTerminalPlaytest {
    @JvmStatic
    fun main(args: Array<String>) {
        val computer = PlayerId("computer")
        val (game, initial) = Mancala.newSelfPlayGame(computer)
        val engine = GameEngine(game)
        val artificialPlayer = KalahArtificialPlayer(computer)
        var state = initial

        println("Mancala (Kalah): computer self-play transition trace.\n")
        println(MancalaTerminalRenderer.renderState("Initial authoritative state", state))

        while (game.outcome(state) == GameOutcome.InProgress) {
            val side = state.requireCurrentSide()
            val intent = artificialPlayer.chooseIntent(game, state)
            println("${side.name} chooses pit ${intent.pit.value + 1}.")

            val progression = engine.playWithTrace(state, computer, intent)
            println(MancalaTerminalRenderer.renderProgression(progression))
            state = progression.resultingState
        }

        val southScore = state.board.stores.getValue(Side.SOUTH)
        val northScore = state.board.stores.getValue(Side.NORTH)
        println(
            when {
                southScore > northScore -> "SOUTH wins $southScore to $northScore."
                northScore > southScore -> "NORTH wins $northScore to $southScore."
                else -> "The game is a draw at $southScore."
            },
        )
    }
}

internal object MancalaTerminalRenderer {
    fun renderProgression(progression: Progression<MancalaState, MancalaEvent>): String = buildString {
        progression.steps.forEachIndexed { index, step ->
            val cause = when (val cause = step.cause) {
                is TransitionCause.PlayerDriven -> "Player-driven: ${cause.actor.value} submitted ${cause.intent}"
                is TransitionCause.RuleDriven -> "Rule-driven: ${cause.rule.value}"
            }
            appendLine("  Step ${index + 1} - $cause")
            step.events.forEach { appendLine("    ${describe(it)}") }
            appendLine(renderState("Resulting state", step.resultingState).prependIndent("    "))
        }
    }.trimEnd()

    fun renderState(label: String, state: MancalaState): String = buildString {
        appendLine(label)
        appendLine(describeStatus(state.status))
        append(renderBoard(state.board))
    }

    private fun describeStatus(status: MancalaStatus): String = when (status) {
        is MancalaStatus.AwaitingSow ->
            "Awaiting sow: turn owner ${status.turn.owner.value}; active side ${status.activeSide}"

        is MancalaStatus.ResolvingSow ->
            "Resolving sow: ${status.player.value} on ${status.side}; no player decision required"

        is MancalaStatus.Won -> "Complete: ${status.winner.value} won on ${status.winningSide}"

        MancalaStatus.Draw -> "Complete: draw"
    }

    private fun describe(event: MancalaEvent): String = when (event) {
        is MancalaEvent.StonesSown ->
            "Sowed ${event.placements.size} stones from ${event.from.side} pit ${event.from.index.value + 1}: " +
                event.placements.joinToString(transform = ::describeCup)

        is MancalaEvent.StonesCaptured ->
            "Captured ${event.stones} stones from ${describeCup(event.landingPit)} and ${describeCup(event.oppositePit)}"

        is MancalaEvent.RemainingStonesCollected ->
            "Collected ${event.stones} remaining stones for ${event.side}"

        is MancalaEvent.TurnAdvanced -> when {
            event.extraTurn -> "Retained the turn on ${event.nextSide}"
            else -> "Advanced the turn to ${event.nextSide}"
        }

        is MancalaEvent.GameWon -> "${event.winner.value} won on ${event.winningSide}"

        MancalaEvent.GameDrawn -> "Completed the game in a draw"
    }

    private fun describeCup(cup: Cup): String = when (cup) {
        is Cup.Pit -> "${cup.side} pit ${cup.index.value + 1}"
        is Cup.Store -> "${cup.side} store"
    }

    private fun renderBoard(board: MancalaBoard): String {
        val north = board.pits.getValue(Side.NORTH).reversed().joinToString(" | ") { "%2d".format(it) }
        val south = board.pits.getValue(Side.SOUTH).joinToString(" | ") { "%2d".format(it) }
        return buildString {
            appendLine("                 NORTH")
            appendLine("          6    5    4    3    2    1")
            appendLine("      +----+----+----+----+----+----+")
            appendLine("      | $north |")
            appendLine(
                "  %2d  +----+----+----+----+----+----+  %2d".format(
                    board.stores.getValue(Side.NORTH),
                    board.stores.getValue(Side.SOUTH),
                ),
            )
            appendLine("      | $south |")
            appendLine("      +----+----+----+----+----+----+")
            appendLine("          1    2    3    4    5    6")
            append("                 SOUTH")
        }
    }
}
