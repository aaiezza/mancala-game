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
        val side = state.requireCurrentSide()
        require(state.registry.playerOn(side) == playerId) { "It is not the artificial player's turn" }
        return game.legalIntents(state)
            .map { it.intent }
            .sortedBy { it.pit.value }
            .maxWithOrNull(compareBy<Sow> { score(game, play(game, state, it), side, searchDepth - 1) }.thenByDescending { it.pit.value })
            ?: error("The artificial player has no legal intent")
    }

    private fun score(game: Mancala, state: MancalaState, side: Side, depth: Int): Int {
        when (game.outcome(state)) {
            GameOutcome.InProgress -> Unit

            GameOutcome.Draw -> return 0

            is GameOutcome.PlayerWon -> {
                val storeDifference = storeDifference(state, side)
                return if (storeDifference > 0) WIN_SCORE + depth else -WIN_SCORE - depth
            }

            else -> error("Unsupported Kalah outcome: ${game.outcome(state)}")
        }

        if (depth == 0) return heuristic(state, side)
        val childScores = game.legalIntents(state).map { legal ->
            score(game, play(game, state, legal.intent), side, depth - 1)
        }
        return if (state.currentSide == side) childScores.max() else childScores.min()
    }

    private fun heuristic(state: MancalaState, side: Side): Int {
        val opponentSide = side.opponent()
        val storeAdvantage = storeDifference(state, side)
        val pitAdvantage = state.board.stonesOnSide(side) - state.board.stonesOnSide(opponentSide)
        return storeAdvantage * STORE_WEIGHT + pitAdvantage
    }

    private fun storeDifference(state: MancalaState, side: Side) = state.board.stores.getValue(side) - state.board.stores.getValue(side.opponent())

    private fun play(game: Mancala, state: MancalaState, intent: Sow): MancalaState {
        val side = state.requireCurrentSide()
        return GameEngine(game).play(state, state.registry.playerOn(side), intent)
    }

    private companion object {
        const val WIN_SCORE = 100_000
        const val STORE_WEIGHT = 20
    }
}
