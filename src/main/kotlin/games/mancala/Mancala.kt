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
    data class TurnAdvanced(val nextSide: Side?, val extraTurn: Boolean) : MancalaEvent
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

data class MancalaState(
    override val board: MancalaBoard,
    val registry: MancalaPlayers,
    val currentSide: Side?,
    override val turnNumber: TurnNumber = TurnNumber(0),
    override val history: EventHistory<MancalaEvent> = EventHistory(),
) : BoardGameState<MancalaEvent, MancalaBoard>,
    HistoryWritableState<MancalaEvent> {
    override val currentPlayer: PlayerId? get() = currentSide?.let(registry::playerOn)

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
        val events = mutableListOf<MancalaEvent>(MancalaEvent.StonesSown(from, sowing.placements))
        var resolvedBoard = sowing.board

        val lastPit = sowing.lastCup as? Cup.Pit
        if (lastPit?.side == side && sowing.lastPitWasEmpty && resolvedBoard.stonesAt(opposite(lastPit)) > 0) {
            val opposite = opposite(lastPit)
            val captured = resolvedBoard.stonesAt(opposite) + 1
            events += MancalaEvent.StonesCaptured(lastPit, opposite, captured)
            resolvedBoard = resolvedBoard.empty(lastPit).empty(opposite).addToStore(side, captured)
        }

        val gameEnded = Side.entries.any(resolvedBoard::sideIsEmpty)
        if (gameEnded) {
            Side.entries.filterNot(resolvedBoard::sideIsEmpty).forEach { remainingSide ->
                val remaining = resolvedBoard.stonesOnSide(remainingSide)
                events += MancalaEvent.RemainingStonesCollected(remainingSide, remaining)
                resolvedBoard = resolvedBoard.collectSide(remainingSide)
            }
            events += MancalaEvent.TurnAdvanced(nextSide = null, extraTurn = false)
        } else {
            val extraTurn = sowing.lastCup == Cup.Store(side)
            events += MancalaEvent.TurnAdvanced(
                nextSide = if (extraTurn) side else side.opponent(),
                extraTurn = extraTurn,
            )
        }
        return Resolution(events)
    }

    override fun reduce(state: MancalaState, event: MancalaEvent): MancalaState = when (event) {
        is MancalaEvent.StonesSown -> state.copy(board = applySowing(state.board, event))

        is MancalaEvent.StonesCaptured -> state.copy(
            board = state.board.empty(event.landingPit).empty(event.oppositePit)
                .addToStore(event.landingPit.side, event.stones),
        )

        is MancalaEvent.RemainingStonesCollected -> state.copy(board = state.board.collectSide(event.side))

        is MancalaEvent.TurnAdvanced -> state.copy(
            currentSide = event.nextSide,
            turnNumber = TurnNumber(state.turnNumber.value + 1),
        )
    }

    override fun outcome(state: MancalaState): GameOutcome {
        if (state.currentSide != null) return GameOutcome.InProgress
        val southScore = state.board.stores.getValue(Side.SOUTH)
        val northScore = state.board.stores.getValue(Side.NORTH)
        return when {
            southScore > northScore -> GameOutcome.PlayerWon(state.registry.south)
            northScore > southScore -> GameOutcome.PlayerWon(state.registry.north)
            else -> GameOutcome.Draw
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
        fun newGame(
            south: PlayerId,
            north: PlayerId,
            configuration: KalahConfiguration = KalahConfiguration(),
        ): Pair<Mancala, MancalaState> {
            require(south != north)
            val game = Mancala(configuration)
            return game to MancalaState(MancalaBoard.initial(configuration), MancalaPlayers(south, north), Side.SOUTH)
        }

        fun newSelfPlayGame(
            player: PlayerId,
            configuration: KalahConfiguration = KalahConfiguration(),
        ): Pair<Mancala, MancalaState> {
            val game = Mancala(configuration)
            return game to MancalaState(MancalaBoard.initial(configuration), MancalaPlayers(player, player), Side.SOUTH)
        }

        fun customGame(
            south: PlayerId,
            north: PlayerId,
            board: MancalaBoard,
            currentSide: Side = Side.SOUTH,
            configuration: KalahConfiguration = KalahConfiguration(pitsPerSide = board.pitsPerSide),
        ): Pair<Mancala, MancalaState> {
            require(board.pitsPerSide == configuration.pitsPerSide)
            val game = Mancala(configuration)
            return game to MancalaState(board, MancalaPlayers(south, north), currentSide)
        }
    }
}

internal fun Side.opponent() = if (this == Side.SOUTH) Side.NORTH else Side.SOUTH

internal fun MancalaState.requireCurrentSide() = checkNotNull(currentSide) { "The game has ended" }
