# Mancala

An immutable, event-driven implementation of Kalah, the game commonly sold as Mancala. The default configuration uses six pits per player and four stones per pit.

Implemented rules include sowing, skipping the opponent's store, extra turns, opposite-pit captures, end-game sweeping, wins, and draws.

```bash
mvn spotless:apply
mvn verify
```

## Terminal playtest

Play against the deterministic minimax artificial player from a terminal:

```bash
mvn -Pterminal test-compile exec:java
```

You play the south side and move first. Enter a pit number from `1` through `6`, or enter `q` to quit.
