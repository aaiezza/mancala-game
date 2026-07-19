package games.mancala

import games.core.GameEngine
import games.core.GameOutcome
import games.core.PlayerId

class KalahArtificialPlayer(
    private val playerId: PlayerId,
    private val searchDepth: Int = 7,
) {
    init {
        require(searchDepth > 0)
    }

    fun chooseIntent(game: Mancala, state: MancalaState): Sow {
        require(state.currentPlayer == playerId) { "It is not the artificial player's turn" }
        return game.legalIntents(state)
            .map { it.intent }
            .sortedBy { it.pit.value }
            .maxWithOrNull(compareBy<Sow> { score(game, play(game, state, it), searchDepth - 1) }.thenByDescending { it.pit.value })
            ?: error("The artificial player has no legal intent")
    }

    private fun score(game: Mancala, state: MancalaState, depth: Int): Int {
        when (val outcome = game.outcome(state)) {
            GameOutcome.InProgress -> Unit
            GameOutcome.Draw -> return 0
            is GameOutcome.PlayerWon -> return if (outcome.playerId == playerId) WIN_SCORE + depth else -WIN_SCORE - depth
            else -> error("Unsupported Kalah outcome: $outcome")
        }

        if (depth == 0) return heuristic(state)
        val childScores = game.legalIntents(state).map { legal ->
            score(game, play(game, state, legal.intent), depth - 1)
        }
        return if (state.currentPlayer == playerId) childScores.max() else childScores.min()
    }

    private fun heuristic(state: MancalaState): Int {
        val ownSide = state.registry.sideOf(playerId)
        val opponentSide = if (ownSide == Side.SOUTH) Side.NORTH else Side.SOUTH
        val storeAdvantage = state.board.stores.getValue(ownSide) - state.board.stores.getValue(opponentSide)
        val pitAdvantage = state.board.stonesOnSide(ownSide) - state.board.stonesOnSide(opponentSide)
        return storeAdvantage * STORE_WEIGHT + pitAdvantage
    }

    private fun play(game: Mancala, state: MancalaState, intent: Sow) = GameEngine(game).play(state, state.currentPlayer!!, intent)

    private companion object {
        const val WIN_SCORE = 100_000
        const val STORE_WEIGHT = 20
    }
}
