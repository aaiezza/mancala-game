package games.mancala

import games.core.GameEngine
import games.core.GameOutcome
import games.core.PlayerId

object MancalaTerminalPlaytest {
    @JvmStatic
    fun main(args: Array<String>) {
        val human = PlayerId("human")
        val computer = PlayerId("computer")
        val (game, initial) = Mancala.newGame(human, computer)
        val engine = GameEngine(game)
        val artificialPlayer = KalahArtificialPlayer(computer)
        var state = initial

        println("Mancala (Kalah): you are SOUTH and move first.")
        println("Choose pits 1-6 from left to right. Type q to quit.\n")

        while (game.outcome(state) == GameOutcome.InProgress) {
            printBoard(state.board)
            state = if (state.currentPlayer == human) {
                val intent = readHumanIntent(game, state) ?: return
                engine.play(state, human, intent)
            } else {
                val intent = artificialPlayer.chooseIntent(game, state)
                println("Computer chooses pit ${intent.pit.value + 1}.\n")
                engine.play(state, computer, intent)
            }
        }

        printBoard(state.board)
        when (game.outcome(state)) {
            GameOutcome.Draw -> println("The game is a draw.")
            GameOutcome.PlayerWon(human) -> println("You win!")
            GameOutcome.PlayerWon(computer) -> println("The computer wins.")
            else -> error("Unexpected outcome")
        }
    }

    private fun readHumanIntent(game: Mancala, state: MancalaState): Sow? {
        val legal = game.legalIntents(state).map { it.intent }.toSet()
        while (true) {
            print("Your move (${legal.joinToString { (it.pit.value + 1).toString() }}): ")
            val input = readlnOrNull()?.trim()?.lowercase() ?: return null
            if (input == "q" || input == "quit") return null
            val pit = input.toIntOrNull()?.minus(1)?.takeIf { it >= 0 }?.let(::PitIndex)
            val intent = pit?.let(::Sow)
            if (intent in legal) return intent
            println("Please choose a non-empty pit shown in the list.")
        }
    }

    private fun printBoard(board: MancalaBoard) {
        val north = board.pits.getValue(Side.NORTH).reversed().joinToString(" | ") { "%2d".format(it) }
        val south = board.pits.getValue(Side.SOUTH).joinToString(" | ") { "%2d".format(it) }
        println("                 NORTH")
        println("          6    5    4    3    2    1")
        println("      +----+----+----+----+----+----+")
        println("      | $north |")
        println(
            "  %2d  +----+----+----+----+----+----+  %2d".format(
                board.stores.getValue(Side.NORTH),
                board.stores.getValue(Side.SOUTH),
            ),
        )
        println("      | $south |")
        println("      +----+----+----+----+----+----+")
        println("          1    2    3    4    5    6")
        println("                 SOUTH\n")
    }
}
