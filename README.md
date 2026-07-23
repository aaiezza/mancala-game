# Mancala

An immutable, event-driven implementation of Kalah, the game commonly sold as Mancala. The default configuration uses six pits per player and four stones per pit.

Implemented rules include sowing, skipping the opponent's store, extra turns, opposite-pit captures, end-game sweeping, wins, and draws.

Mancala demonstrates semantic transition tracing. A sow intent returns one player-driven sowing step followed by rule-driven capture, end-game collection, and turn-advancement steps when applicable. `GameEngine.play` returns the fully resolved state; `playWithTrace` exposes those steps for animation and explanation.

The lifecycle is exhaustive:

- `AwaitingSow` identifies the turn owner and active side.
- `ResolvingSow` is a trace-only state with no decision actor while capture, collection, and completion rules run.
- `Won` records both the winning player and side, which remains unambiguous during self-play.
- `Draw` represents a completed tie.

Completion is expressed by `GameWon` or `GameDrawn`; turn advancement always contains a real next player and side. Ordinary construction requires distinct opponents, while `newSelfPlayGame` and `customSelfPlayGame` explicitly assign one player to both sides.

```bash
mvn spotless:apply
mvn verify
```

## Terminal transition-trace playtest

Watch the deterministic minimax artificial player play both sides while the terminal renders every player-driven and rule-driven transition:

```bash
mvn -Pterminal test-compile exec:java
```

Each move prints its sowing result, any capture or end-game collection, and the resulting turn advancement as separate semantic states.
