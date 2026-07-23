package games.mancala

import games.core.*

@JvmInline
value class PitIndex(val value: Int) {
    init {
        require(value >= 0)
    }
}

enum class Side { SOUTH, NORTH }

data class Sow(val pit: PitIndex) : PlayerIntent

sealed interface Cup {
    data class Pit(val side: Side, val index: PitIndex) : Cup
    data class Store(val side: Side) : Cup
}

sealed interface MancalaEvent : GameEvent {
    data class StonesSown(val from: Cup.Pit, val placements: List<Cup>) : MancalaEvent
    data class StonesCaptured(val landingPit: Cup.Pit, val oppositePit: Cup.Pit, val stones: Int) : MancalaEvent
    data class RemainingStonesCollected(val side: Side, val stones: Int) : MancalaEvent
    data class TurnAdvanced(val nextPlayer: PlayerId, val nextSide: Side, val extraTurn: Boolean) : MancalaEvent
    data class GameWon(val winner: PlayerId, val winningSide: Side) : MancalaEvent
    data object GameDrawn : MancalaEvent
}

data class KalahConfiguration(
    val pitsPerSide: Int = 6,
    val stonesPerPit: Int = 4,
    override val name: String = "Kalah",
) : GameConfiguration {
    init {
        require(pitsPerSide > 0)
        require(stonesPerPit > 0)
    }
}

data class MancalaPlayers(val south: PlayerId, val north: PlayerId) : PlayerRegistry {
    override val players = setOf(south, north)

    fun playerOn(side: Side) = if (side == Side.SOUTH) south else north
}

data class MancalaBoard(
    val pits: Map<Side, List<Int>>,
    val stores: Map<Side, Int>,
) {
    init {
        require(Side.entries.all { it in pits && it in stores })
        require(pits.values.flatten().all { it >= 0 })
        require(stores.values.all { it >= 0 })
        require(pits.values.map(List<Int>::size).distinct().size == 1)
    }

    val pitsPerSide: Int get() = pits.getValue(Side.SOUTH).size

    fun stonesAt(pit: Cup.Pit) = pits.getValue(pit.side)[pit.index.value]
    fun sideIsEmpty(side: Side) = pits.getValue(side).all { it == 0 }
    fun stonesOnSide(side: Side) = pits.getValue(side).sum()

    fun empty(pit: Cup.Pit) = updatePit(pit, 0)
    fun addToPit(pit: Cup.Pit, stones: Int) = updatePit(pit, stonesAt(pit) + stones)
    fun addToStore(side: Side, stones: Int) = copy(stores = stores + (side to stores.getValue(side) + stones))

    fun collectSide(side: Side): MancalaBoard {
        val stones = stonesOnSide(side)
        return copy(pits = pits + (side to List(pitsPerSide) { 0 })).addToStore(side, stones)
    }

    private fun updatePit(pit: Cup.Pit, stones: Int): MancalaBoard {
        require(pit.index.value in 0 until pitsPerSide)
        val updated = pits.getValue(pit.side).toMutableList().also { it[pit.index.value] = stones }
        return copy(pits = pits + (pit.side to updated))
    }

    companion object {
        fun initial(configuration: KalahConfiguration) = MancalaBoard(
            pits = Side.entries.associateWith { List(configuration.pitsPerSide) { configuration.stonesPerPit } },
            stores = Side.entries.associateWith { 0 },
        )
    }
}

sealed interface MancalaStatus {
    val turn: TurnContext?

    data class AwaitingSow(override val turn: TurnContext, val activeSide: Side) : MancalaStatus

    data class ResolvingSow(val player: PlayerId, val side: Side, val from: Cup.Pit) : MancalaStatus {
        override val turn = null
    }

    data class Won(val winner: PlayerId, val winningSide: Side) : MancalaStatus {
        override val turn = null
    }

    data object Draw : MancalaStatus {
        override val turn = null
    }
}

data class MancalaState(
    override val board: MancalaBoard,
    val registry: MancalaPlayers,
    val status: MancalaStatus,
    override val turnNumber: TurnNumber = TurnNumber(0),
    override val history: EventHistory<MancalaEvent> = EventHistory(),
) : BoardGameState<MancalaEvent, MancalaBoard>,
    HistoryWritableState<MancalaEvent> {
    override val turn: TurnContext? get() = status.turn

    init {
        when (val status = status) {
            is MancalaStatus.AwaitingSow -> {
                val activePlayer = registry.playerOn(status.activeSide)
                require(status.turn.owner == activePlayer) { "The turn owner must control the active side." }
                require(status.turn.decisionActors == setOf(activePlayer)) { "Only the active side's player may act." }
            }

            is MancalaStatus.ResolvingSow -> {
                require(status.player == registry.playerOn(status.side)) {
                    "The player must control the side being resolved."
                }
                require(status.from.side == status.side) { "The sow must originate on the side being resolved." }
            }

            is MancalaStatus.Won -> require(status.winner == registry.playerOn(status.winningSide)) {
                "The winner must control the winning side."
            }

            MancalaStatus.Draw -> Unit
        }
    }

    override fun withHistory(history: EventHistory<MancalaEvent>) = copy(history = history)
}

class Mancala(override val configuration: KalahConfiguration) : ConfigurableGameDefinition<MancalaState, Sow, MancalaEvent, KalahConfiguration> {

    override fun legalIntents(state: MancalaState): Set<LegalIntent<Sow>> {
        if (outcome(state) != GameOutcome.InProgress) return emptySet()
        val side = state.requireCurrentSide()
        return state.board.pits.getValue(side)
            .mapIndexedNotNull { index, stones -> if (stones > 0) LegalIntent(Sow(PitIndex(index))) else null }
            .toSet()
    }

    override fun resolve(state: MancalaState, actor: PlayerId, intent: Sow): Resolution<MancalaEvent> {
        val side = state.requireCurrentSide()
        val from = Cup.Pit(side, intent.pit)
        val sowing = sow(state.board, from)
        val steps = mutableListOf<ResolutionStep<MancalaEvent>>(
            ResolutionStep.PlayerDriven(listOf(MancalaEvent.StonesSown(from, sowing.placements))),
        )
        var resolvedBoard = sowing.board

        val lastPit = sowing.lastCup as? Cup.Pit
        if (lastPit?.side == side && sowing.lastPitWasEmpty && resolvedBoard.stonesAt(opposite(lastPit)) > 0) {
            val opposite = opposite(lastPit)
            val captured = resolvedBoard.stonesAt(opposite) + 1
            steps += ResolutionStep.RuleDriven(
                CAPTURE_RULE,
                listOf(MancalaEvent.StonesCaptured(lastPit, opposite, captured)),
            )
            resolvedBoard = resolvedBoard.empty(lastPit).empty(opposite).addToStore(side, captured)
        }

        val gameEnded = Side.entries.any(resolvedBoard::sideIsEmpty)
        if (gameEnded) {
            val collectionEvents = mutableListOf<MancalaEvent>()
            Side.entries.filterNot(resolvedBoard::sideIsEmpty).forEach { remainingSide ->
                val remaining = resolvedBoard.stonesOnSide(remainingSide)
                collectionEvents += MancalaEvent.RemainingStonesCollected(remainingSide, remaining)
                resolvedBoard = resolvedBoard.collectSide(remainingSide)
            }
            if (collectionEvents.isNotEmpty()) {
                steps += ResolutionStep.RuleDriven(END_GAME_COLLECTION_RULE, collectionEvents)
            }
            steps += ResolutionStep.RuleDriven(
                GAME_END_RULE,
                listOf(completionEvent(state.registry, resolvedBoard)),
            )
        } else {
            val extraTurn = sowing.lastCup == Cup.Store(side)
            val nextSide = if (extraTurn) side else side.opponent()
            steps += ResolutionStep.RuleDriven(
                TURN_ADVANCEMENT_RULE,
                listOf(
                    MancalaEvent.TurnAdvanced(
                        nextPlayer = state.registry.playerOn(nextSide),
                        nextSide = nextSide,
                        extraTurn = extraTurn,
                    ),
                ),
            )
        }
        return Resolution(steps)
    }

    override fun reduce(state: MancalaState, event: MancalaEvent): MancalaState = when (event) {
        is MancalaEvent.StonesSown -> state.copy(
            board = applySowing(state.board, event),
            status = MancalaStatus.ResolvingSow(
                player = checkNotNull(state.turnOwner),
                side = event.from.side,
                from = event.from,
            ),
        )

        is MancalaEvent.StonesCaptured -> state.copy(
            board = state.board.empty(event.landingPit).empty(event.oppositePit)
                .addToStore(event.landingPit.side, event.stones),
        )

        is MancalaEvent.RemainingStonesCollected -> state.copy(board = state.board.collectSide(event.side))

        is MancalaEvent.TurnAdvanced -> state.copy(
            status = MancalaStatus.AwaitingSow(TurnContext(event.nextPlayer), event.nextSide),
            turnNumber = TurnNumber(state.turnNumber.value + 1),
        )

        is MancalaEvent.GameWon -> state.copy(
            status = MancalaStatus.Won(event.winner, event.winningSide),
            turnNumber = TurnNumber(state.turnNumber.value + 1),
        )

        MancalaEvent.GameDrawn -> state.copy(
            status = MancalaStatus.Draw,
            turnNumber = TurnNumber(state.turnNumber.value + 1),
        )
    }

    override fun outcome(state: MancalaState): GameOutcome = when (val status = state.status) {
        is MancalaStatus.AwaitingSow -> GameOutcome.InProgress
        is MancalaStatus.ResolvingSow -> GameOutcome.InProgress
        is MancalaStatus.Won -> GameOutcome.PlayerWon(status.winner)
        MancalaStatus.Draw -> GameOutcome.Draw
    }

    private fun completionEvent(registry: MancalaPlayers, board: MancalaBoard): MancalaEvent {
        val southScore = board.stores.getValue(Side.SOUTH)
        val northScore = board.stores.getValue(Side.NORTH)
        return when {
            southScore > northScore -> MancalaEvent.GameWon(registry.south, Side.SOUTH)
            northScore > southScore -> MancalaEvent.GameWon(registry.north, Side.NORTH)
            else -> MancalaEvent.GameDrawn
        }
    }

    private data class SowingResult(
        val board: MancalaBoard,
        val placements: List<Cup>,
        val lastCup: Cup,
        val lastPitWasEmpty: Boolean,
    )

    private fun sow(board: MancalaBoard, from: Cup.Pit): SowingResult {
        val stones = board.stonesAt(from)
        require(stones > 0)
        val ring = sowingRing(from.side, board.pitsPerSide)
        var nextBoard = board.empty(from)
        val placements = mutableListOf<Cup>()
        var position = ring.indexOf(from)
        var lastPitWasEmpty = false
        repeat(stones) {
            position = (position + 1) % ring.size
            val cup = ring[position]
            lastPitWasEmpty = cup is Cup.Pit && nextBoard.stonesAt(cup) == 0
            nextBoard = when (cup) {
                is Cup.Pit -> nextBoard.addToPit(cup, 1)
                is Cup.Store -> nextBoard.addToStore(cup.side, 1)
            }
            placements += cup
        }
        return SowingResult(nextBoard, placements, placements.last(), lastPitWasEmpty)
    }

    private fun applySowing(board: MancalaBoard, event: MancalaEvent.StonesSown): MancalaBoard {
        var nextBoard = board.empty(event.from)
        event.placements.forEach { cup ->
            nextBoard = when (cup) {
                is Cup.Pit -> nextBoard.addToPit(cup, 1)
                is Cup.Store -> nextBoard.addToStore(cup.side, 1)
            }
        }
        return nextBoard
    }

    private fun sowingRing(side: Side, pitsPerSide: Int): List<Cup> {
        val opponent = if (side == Side.SOUTH) Side.NORTH else Side.SOUTH
        return (0 until pitsPerSide).map { Cup.Pit(side, PitIndex(it)) } +
            Cup.Store(side) +
            (0 until pitsPerSide).map { Cup.Pit(opponent, PitIndex(it)) }
    }

    private fun opposite(pit: Cup.Pit) = Cup.Pit(
        side = pit.side.opponent(),
        index = PitIndex(configuration.pitsPerSide - 1 - pit.index.value),
    )

    companion object {
        private val CAPTURE_RULE = RuleId("mancala.capture")
        private val END_GAME_COLLECTION_RULE = RuleId("mancala.collect-remaining-stones")
        private val TURN_ADVANCEMENT_RULE = RuleId("mancala.advance-turn")
        private val GAME_END_RULE = RuleId("mancala.game-end")

        fun newGame(
            south: PlayerId,
            north: PlayerId,
            configuration: KalahConfiguration = KalahConfiguration(),
        ): Pair<Mancala, MancalaState> {
            require(south != north)
            val game = Mancala(configuration)
            return game to initialState(configuration, MancalaPlayers(south, north))
        }

        fun newSelfPlayGame(
            player: PlayerId,
            configuration: KalahConfiguration = KalahConfiguration(),
        ): Pair<Mancala, MancalaState> {
            val game = Mancala(configuration)
            return game to initialState(configuration, MancalaPlayers(player, player))
        }

        fun customGame(
            south: PlayerId,
            north: PlayerId,
            board: MancalaBoard,
            currentSide: Side = Side.SOUTH,
            configuration: KalahConfiguration = KalahConfiguration(pitsPerSide = board.pitsPerSide),
        ): Pair<Mancala, MancalaState> {
            require(board.pitsPerSide == configuration.pitsPerSide)
            require(south != north) { "Opposing sides require distinct players; use customSelfPlayGame for one player." }
            val game = Mancala(configuration)
            val registry = MancalaPlayers(south, north)
            return game to MancalaState(
                board,
                registry,
                MancalaStatus.AwaitingSow(TurnContext(registry.playerOn(currentSide)), currentSide),
            )
        }

        fun customSelfPlayGame(
            player: PlayerId,
            board: MancalaBoard,
            currentSide: Side = Side.SOUTH,
            configuration: KalahConfiguration = KalahConfiguration(pitsPerSide = board.pitsPerSide),
        ): Pair<Mancala, MancalaState> {
            require(board.pitsPerSide == configuration.pitsPerSide)
            val game = Mancala(configuration)
            val registry = MancalaPlayers(player, player)
            return game to MancalaState(
                board,
                registry,
                MancalaStatus.AwaitingSow(TurnContext(player), currentSide),
            )
        }

        private fun initialState(configuration: KalahConfiguration, registry: MancalaPlayers): MancalaState = MancalaState(
            MancalaBoard.initial(configuration),
            registry,
            MancalaStatus.AwaitingSow(TurnContext(registry.south), Side.SOUTH),
        )
    }
}

internal fun Side.opponent() = if (this == Side.SOUTH) Side.NORTH else Side.SOUTH

val MancalaState.activeSide: Side?
    get() = (status as? MancalaStatus.AwaitingSow)?.activeSide

internal fun MancalaState.requireCurrentSide() = checkNotNull(activeSide) { "The game is not awaiting a sow." }
