# Draughts (Checkers) Game

An interactive command-line game demonstrating pure functional game development.

---

## Overview

The Draughts game is a complete, playable implementation of checkers that demonstrates how functional programming patterns make game development cleaner and more maintainable. Every aspect of the game (input handling, validation, state updates, and rendering) is expressed through composable functional abstractions.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DRAUGHTS GAME ARCHITECTURE                       │
│                                                                     │
│                        ┌──────────────┐                             │
│                        │  Focus DSL   │                             │
│                        │  Navigation  │                             │
│                        └──────┬───────┘                             │
│                               │                                     │
│   User Input          Game Logic              Display               │
│   ─────────           ──────────              ───────               │
│                               │                                     │
│   IOPath              WithStatePath           IOPath                │
│   ┌──────────┐        ┌──────┴──────┐        ┌──────────┐           │
│   │ Read     │───────►│ Validate    │───────►│ Render   │           │
│   │ Parse    │ railway│ Navigate    │ stream │ Board    │           │
│   │ Validate │───────►│ Update      │───────►│          │           │
│   └──────────┘        └─────────────┘        └──────────┘           │
│        │                    │                     │                 │
│        ▼                    ▼                     ▼                 │
│   EitherPath          WithStatePath           IOPath                │
│   <Error,             <GameState,             <Unit>                │
│   MoveCommand>        MoveResult>                                   │
│                                                                     │
│                    Composed via ForPath                             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Key Patterns Demonstrated

### Pure State Management

Game state is immutable and managed through `WithStatePath`:

<!-- verify -->
```java
@GenerateFocus
record GameState(
    Map<Square, Piece> board,
    Player currentPlayer,
    String message,
    boolean isGameOver
) {}

// A move is a pure state transition: the validations either produce the next state and its
// result, or fold back to the state they started from carrying the reason.
static WithStatePath<GameState, MoveResult> applyMove(MoveCommand command) {
    Square from = MoveCommandFocus.from().get(command);
    Square to = MoveCommandFocus.to().get(command);

    return Path.state(
        State.of(
            (GameState state) ->
                getPieceAt(from, state)
                    .via(piece -> validateOwnership(piece, state))
                    .via(piece -> validateDestinationEmpty(to, state).map(unit -> piece))
                    .via(piece -> validateAndApply(state, command, piece, from, to))
                    .fold(error -> invalidMove(error, state), result -> result)));
}
```

### Railway-Oriented Validation

Move validation uses `EitherPath` for clear error handling:

<!-- verify -->
```java
static EitherPath<String, StateTuple<GameState, MoveResult>> validateMove(
        MoveCommand command, GameState state) {
    Square from = MoveCommandFocus.from().get(command);
    Square to = MoveCommandFocus.to().get(command);

    return getPieceAt(from, state)                                        // Piece at source?
        .via(piece -> validateOwnership(piece, state))                    // Player's piece?
        .via(piece -> validateDestinationEmpty(to, state).map(u -> piece))  // Dest free?
        .via(piece -> validateAndApply(state, command, piece, from, to)); // Slide or jump?
}
```

### Side Effect Encapsulation

All I/O is captured in `IOPath`:

<!-- verify -->
```java
// Console I/O is deferred and composable
IOPath<String> readLine = Path.io(() -> scanner.nextLine());
IOPath<Unit> printBoard = displayBoard(state);

// A turn is the board, then the move that follows it. Reading returns an Either, so a bad
// command is a value the next step handles rather than an exception.
IOPath<GameState> processTurn(GameState currentState) {
    return ForPath.from(displayBoard(currentState))
        .from(shown -> readMoveCommand())
        .yield((shown, result) -> result)
        .via(result -> handleTurnResult(result, currentState));
}

// The loop recurses rather than looping, and stays stack-safe because
// nothing runs until the whole description is executed
IOPath<Unit> gameLoop(GameState currentState) {
    return currentState.isGameOver()
        ? displayBoard(currentState)
        : processTurn(currentState).via(this::gameLoop);
}
```

### Focus DSL for Game State

Type-safe navigation through nested game structures:

<!-- verify -->
```java
// Read a component through the generated focus
Player current = GameStateFocus.currentPlayer().get(state);

// Replace the board, leaving every other component alone
GameState moved = GameStateFocus.board().set(newBoard, state);

// End the game, and say why
GameState finished = GameStateFocus.message()
    .set("RED wins.", GameStateFocus.isGameOver().set(true, state));
```

### Stream-Based Iteration

The initial board is generated rather than written out:

<!-- verify -->
```java
// The starting board: every dark square in a player's rows carries one of their men
static Stream<Map.Entry<Square, Piece>> placePieces(Player owner, int startRow, int endRow) {
    Piece piece = new Piece(owner, PieceType.MAN);

    return IntStream.range(startRow, endRow)
        .boxed()
        .flatMap(row ->
            darkSquaresInRow(row).mapToObj(col -> Map.entry(new Square(row, col), piece)));
}

// Dark squares alternate, starting at column 0 in odd rows and column 1 in even ones
static IntStream darkSquaresInRow(int row) {
    int startCol = (row % 2 != 0) ? 0 : 1;
    return IntStream.iterate(startCol, col -> col < 8, col -> col + 2);
}
```

---

## Running the Game

```bash
./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.draughts.Draughts
```

### How to Play

1. The board displays with row numbers (1-8) and column letters (a-h)
2. Red pieces move first, shown as `r` (men) or `R` (kings)
3. Black pieces are shown as `b` (men) or `B` (kings)
4. Enter moves as `source-destination`, e.g., `a3-b4`
5. Jumps are mandatory when available
6. Type `quit` to exit

### Sample Game Session

```
  a b c d e f g h
8 . b . b . b . b
7 b . b . b . b .
6 . b . b . b . b
5 . . . . . . . .
4 . . . . . . . .
3 r . r . r . r .
2 . r . r . r . r
1 r . r . r . r .

Red's turn. Enter move (e.g., a3-b4): c3-d4

  a b c d e f g h
8 . b . b . b . b
7 b . b . b . b .
6 . b . b . b . b
5 . . . . . . . .
4 . . . r . . . .
3 r . . . r . r .
2 . r . r . r . r
1 r . r . r . r .

Black's turn. Enter move (e.g., a3-b4):
```

---

## Source Files

| File | Description |
|------|-------------|
| [Draughts.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/draughts/Draughts.java) | Main entry point |
| [GameState.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/draughts/GameState.java) | Immutable game state record |
| [GameLogic.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/draughts/GameLogic.java) | Pure game rules |
| [InputHandler.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/draughts/InputHandler.java) | Input parsing and validation |
| [BoardDisplay.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/draughts/BoardDisplay.java) | Console rendering |

---

## Project Structure

```
hkj-examples/src/main/java/org/higherkindedj/example/draughts/
├── Draughts.java          # Main entry point and game loop
├── GameState.java         # Immutable state with @GenerateFocus
├── GameLogic.java         # Pure game rules using WithStatePath
├── GameLogicSimple.java   # Simplified logic for learning
├── InputHandler.java      # EitherPath validation pipeline
├── BoardDisplay.java      # IOPath rendering
└── package-info.java
```

---

## Functional Concepts Applied

| Concept | How It's Used |
|---------|---------------|
| **Immutability** | All game state is immutable; moves create new state |
| **Pure Functions** | Game logic has no side effects |
| **Railway-Oriented Programming** | Validation uses EitherPath's success/failure tracks |
| **State Monad** | WithStatePath threads state through computations |
| **IO Monad** | Console I/O is deferred and composable |
| **Focus DSL** | Type-safe access to nested board structures |
| **ForPath Comprehension** | Game loop composed declaratively |

---

## Related Documentation

- [Building a Playable Draughts Game](../hkts/draughts.md) – Full tutorial walkthrough
- [WithStatePath](../effect/path_types.md) – State management documentation
- [EitherPath](../effect/path_either.md) – Error handling documentation
- [IOPath](../effect/path_io.md) – Side effect management
- [Focus DSL](../optics/focus_dsl.md) – Generated navigation

---

**Previous:** [Order Processing Workflow](examples_order.md)
**Next:** [Building the Game](../hkts/draughts.md)
