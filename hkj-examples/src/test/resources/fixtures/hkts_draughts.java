// Fixture for hkj-book/src/hkts/draughts.md
//
// The draughts example's domain types are package-private, so the fixture declares them with the
// shapes the example gives them - including `@GenerateFocus`, so the page's snippets navigate
// through the same generated focuses the example does. This page quotes whole classes, so the ones
// a snippet calls but does not show are declared here; a snippet that shows one shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPaths;

enum Player {
  RED,
  BLACK
}

enum PieceType {
  MAN,
  KING
}

@GenerateFocus
record Piece(Player owner, PieceType type) {}

@GenerateFocus
record Square(int row, int col) {}

@GenerateFocus
record GameError(String description, boolean isQuit) {

  GameError(String description) {
    this(description, false);
  }
}

@GenerateFocus
record MoveCommand(Square from, Square to) {}

enum MoveOutcome {
  SUCCESS,
  INVALID_MOVE,
  CAPTURE_MADE,
  GAME_WON
}

@GenerateFocus
record MoveResult(MoveOutcome outcome, String message) {}

@GenerateFocus
record GameState(
    Map<Square, Piece> board, Player currentPlayer, String message, boolean isGameOver) {

  static GameState initial() {
    return new GameState(Map.of(), Player.RED, "Game started. RED's turn.", false);
  }

  GameState withMessage(String newMessage) {
    return new GameState(board, currentPlayer, newMessage, isGameOver);
  }

  GameState togglePlayer() {
    Player next = currentPlayer == Player.RED ? Player.BLACK : Player.RED;
    return new GameState(board, next, next + "'s turn.", isGameOver);
  }
}

/** The classes the page quotes one at a time; each snippet that shows one shadows this copy. */
class InputHandler {

  static IOPath<Either<GameError, MoveCommand>> readMoveCommand() {
    return Path.ioPure(Either.left(new GameError("no input", false)));
  }
}

class BoardDisplay {

  static IOPath<Unit> displayBoard(GameState gameState) {
    return Path.ioRunnable(() -> {});
  }
}

class GameLogic {

  static WithStatePath<GameState, MoveResult> applyMove(MoveCommand command) {
    return Fixture.sample();
  }
}

class Draughts {

  static IOPath<GameState> processTurn(GameState currentState) {
    return Path.ioPure(currentState);
  }

  static IOPath<Unit> gameLoop(GameState gameState) {
    return Path.ioRunnable(() -> {});
  }
}

class Fixture {

  static final Piece piece = new Piece(Player.RED, PieceType.MAN);

  static final MoveCommand command = new MoveCommand(new Square(2, 1), new Square(3, 2));

  static final GameState gameState = sample();


  static final Scanner scanner = new Scanner(System.in);

  static final GameState state = sample();

  static final Square square = new Square(2, 1);

  static final Map<Square, Piece> newBoard = Map.of();

  // The gate compiles snippets; it never runs them. `sample()` stands in where building a value
  // would say nothing about the code the page is showing.
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static IOPath<Unit> displayBoard(GameState gameState) {
    return Path.ioRunnable(() -> {});
  }

  static IOPath<Either<GameError, MoveCommand>> readMoveCommand() {
    return Path.ioPure(Either.left(new GameError("no input", false)));
  }

  static IOPath<GameState> handleTurnResult(
      Either<GameError, MoveCommand> result, GameState gameState) {
    return Path.ioPure(gameState);
  }

  static EitherPath<String, Piece> getPieceAt(Square square, GameState gameState) {
    return Path.left("No piece at " + square);
  }

  static EitherPath<String, Piece> validateOwnership(Piece piece, GameState gameState) {
    return Path.right(piece);
  }

  static EitherPath<String, Unit> validateDestinationEmpty(Square to, GameState gameState) {
    return Path.right(Unit.INSTANCE);
  }

  static EitherPath<String, StateTuple<GameState, MoveResult>> validateAndApply(
      GameState gameState, MoveCommand command, Piece piece, Square from, Square to) {
    return Path.left("not applied");
  }

  // Called by a snippet but declared in another, so these stay visible.
  static EitherPath<String, Piece> validateSimpleMove(Piece piece, int rowDiff) {
    return Path.right(piece);
  }

  static EitherPath<String, StateTuple<GameState, MoveResult>> validateJumpMove(
      GameState state, MoveCommand command, Piece piece, Square from, int rowDiff, int colDiff) {
    return Path.left("no jump");
  }

  static GameState checkAndKingPiece(GameState state, Square square) {
    return state;
  }

  static StateTuple<GameState, MoveResult> performMove(
      GameState state, MoveCommand command, Piece piece) {
    return new StateTuple<>(new MoveResult(MoveOutcome.SUCCESS, "Move successful."), state);
  }

  static IOPath<GameState> processTurnStep(GameState currentState) {
    return Draughts.processTurn(currentState);
  }

  static StateTuple<GameState, MoveResult> invalidMove(String error, GameState gameState) {
    return new StateTuple<>(new MoveResult(MoveOutcome.INVALID_MOVE, error), gameState);
  }
}
