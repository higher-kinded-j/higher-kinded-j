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

```java
@GenerateFocus
record GameState(
    Map<Square, Piece> board,
    Player currentPlayer,
    List<Move> moveHistory,
    boolean gameOver
) {}

// State transitions are pure functions
WithStatePath<GameState, MoveResult> applyMove(Move move) {
    return WithStatePath.modify(state -> {
        var newBoard = movePiece(state.board(), move);
        var captured = checkCaptures(newBoard, move);
        var promoted = checkPromotion(newBoard, move);
        return new GameState(
            promoted,
            state.currentPlayer().opponent(),
            state.moveHistory().append(move),
            isGameOver(promoted)
        );
    }).map(_ -> new MoveResult(move, captured));
}
```

### Railway-Oriented Validation

Move validation uses `EitherPath` for clear error handling:

```java
EitherPath<GameError, Move> validateMove(String input, GameState state) {
    return parseInput(input)                           // Parse "a3-b4"
        .via(cmd -> validateSquareOnBoard(cmd))        // Check bounds
        .via(cmd -> validatePieceExists(cmd, state))   // Piece at source?
        .via(cmd -> validatePieceOwnership(cmd, state)) // Player's piece?
        .via(cmd -> validateDestinationEmpty(cmd, state)) // Dest free?
        .via(cmd -> validateMoveDirection(cmd, state)) // Legal direction?
        .via(cmd -> validateJumpOrSlide(cmd, state));  // Jump/slide rules
}
```

### Side Effect Encapsulation

All I/O is captured in `IOPath`:

```java
// Console I/O is deferred and composable
IOPath<String> readLine = IOPath.delay(() -> scanner.nextLine());
IOPath<Unit> printBoard = IOPath.delay(() -> renderer.display(state));

// Game loop composes pure logic with I/O
IOPath<Unit> gameLoop = ForPath.forPath(printBoard)
    .bind(_ -> readLine)
    .bind(input -> validateAndApply(input))
    .bind(result -> announceResult(result))
    .repeatWhile(result -> !result.gameOver());
```

### Focus DSL for Game State

Type-safe navigation through nested game structures:

```java
// Access the piece at a specific square
var pieceAtSquare = GameStateFocus.board()
    .at(square)
    .getOptional(state);

// Modify all pieces of a player
var promoted = GameStateFocus.board()
    .values()
    .filter(piece -> piece.owner() == player)
    .filter(piece -> shouldPromote(piece))
    .modify(state, piece -> piece.withType(PieceType.KING));
```

### Stream-Based Iteration

Declarative patterns for board operations:

```java
// Find all valid moves for current player
List<Move> validMoves = IntStream.range(0, 8)
    .boxed()
    .flatMap(row -> IntStream.range(0, 8)
        .mapToObj(col -> new Square(row, col)))
    .filter(sq -> hasPiece(state, sq, state.currentPlayer()))
    .flatMap(from -> possibleDestinations(from).stream()
        .map(to -> new Move(from, to)))
    .filter(move -> isValidMove(move, state))
    .toList();
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
