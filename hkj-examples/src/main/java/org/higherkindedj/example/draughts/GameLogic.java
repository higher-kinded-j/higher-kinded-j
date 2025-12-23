// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPaths;

/**
 * Complete game logic for draughts with multi-jump rules.
 *
 * <p>This class extends the patterns from {@link GameLogicSimple} to include the mandatory
 * multi-jump rule: when a capture leads to another possible capture with the same piece, that jump
 * must also be taken.
 *
 * <h2>Functional Programming Patterns</h2>
 *
 * <p>This implementation uses railway-oriented programming where validations chain on the success
 * track and errors automatically short-circuit to the error track:
 *
 * <ul>
 *   <li>{@code EitherPath.via()} - Chain dependent validations
 *   <li>{@code EitherPath.fold()} - Terminal operation converting to result
 *   <li>{@code MaybePath} with {@code .filter().map()} - Optional value pipelines
 *   <li>{@code Stream} - Declarative iteration for jump detection
 * </ul>
 *
 * <h2>Multi-Jump Rule</h2>
 *
 * <p>After a capture, if the same piece can make another jump, the turn does not end. The player
 * must complete all available jumps before the turn passes to the opponent.
 */
public class GameLogic {

  /** Jump direction offsets for checking available jumps. */
  private static final int[] JUMP_OFFSETS = {-2, 2};

  /**
   * Applies a move command to the current game state.
   *
   * <p>Uses railway-oriented programming to chain validations. Each step either continues on the
   * success track or short-circuits to the error track.
   *
   * @param command the move command to apply
   * @return a WithStatePath describing the state transformation and result
   */
  public static WithStatePath<GameState, MoveResult> applyMove(MoveCommand command) {
    Square from = MoveCommandFocus.from().get(command);
    Square to = MoveCommandFocus.to().get(command);

    return Path.state(
        State.of(
            (GameState state) ->
                // Railway: get piece → validate ownership → validate destination → apply move
                getPieceAt(from, state)
                    .via(piece -> validateOwnership(piece, state))
                    .via(piece -> validateDestinationEmpty(to, state).map(unit -> piece))
                    .via(piece -> validateAndApply(state, command, piece, from, to))
                    .fold(error -> invalidMove(error, state), result -> result)));
  }

  // ===== Validation Pipeline Steps =====

  /** Gets the piece at a square, returning an error if none exists. */
  private static EitherPath<String, Piece> getPieceAt(Square square, GameState state) {
    return AffinePath.of(FocusPaths.<Square, Piece>mapAt(square))
        .toEitherPath(state.board(), "No piece at " + square);
  }

  /** Validates that the piece belongs to the current player. */
  private static EitherPath<String, Piece> validateOwnership(Piece piece, GameState state) {
    Player currentPlayer = GameStateFocus.currentPlayer().get(state);
    Player pieceOwner = PieceFocus.owner().get(piece);

    return pieceOwner == currentPlayer ? Path.right(piece) : Path.left("Not your piece.");
  }

  /** Validates that the destination square is empty. */
  private static EitherPath<String, Unit> validateDestinationEmpty(Square to, GameState state) {
    boolean isEmpty =
        AffinePath.of(FocusPaths.<Square, Piece>mapAt(to)).getOptional(state.board()).isEmpty();

    return isEmpty
        ? Path.right(Unit.INSTANCE)
        : Path.left("Destination square " + to + " is occupied.");
  }

  /** Validates the move type and applies it if valid. */
  private static EitherPath<String, StateTuple<GameState, MoveResult>> validateAndApply(
      GameState state, MoveCommand command, Piece piece, Square from, Square to) {

    int rowDiff = SquareFocus.row().get(to) - SquareFocus.row().get(from);
    int colDiff = SquareFocus.col().get(to) - SquareFocus.col().get(from);

    if (Math.abs(rowDiff) == 1 && Math.abs(colDiff) == 1) {
      return validateSimpleMove(piece, rowDiff).map(p -> performMove(state, command, p));
    } else if (Math.abs(rowDiff) == 2 && Math.abs(colDiff) == 2) {
      return validateJumpMove(state, command, piece, from, rowDiff, colDiff);
    } else {
      return Path.left("Move must be diagonal by 1 or 2 squares.");
    }
  }

  /** Validates a simple diagonal move (men can only move forward). */
  private static EitherPath<String, Piece> validateSimpleMove(Piece piece, int rowDiff) {
    PieceType type = PieceFocus.type().get(piece);
    Player owner = PieceFocus.owner().get(piece);

    if (type == PieceType.MAN) {
      boolean isBackward =
          (owner == Player.RED && rowDiff > 0) || (owner == Player.BLACK && rowDiff < 0);
      if (isBackward) {
        return Path.left("Men can only move forward.");
      }
    }

    return Path.right(piece);
  }

  /** Validates direction for a jump move (men can only jump forward). */
  private static EitherPath<String, Piece> validateJumpDirection(Piece piece, int rowDiff) {
    PieceType type = PieceFocus.type().get(piece);
    Player owner = PieceFocus.owner().get(piece);

    if (type == PieceType.MAN) {
      boolean isBackward =
          (owner == Player.RED && rowDiff > 0) || (owner == Player.BLACK && rowDiff < 0);
      if (isBackward) {
        return Path.left("Men can only jump forward.");
      }
    }

    return Path.right(piece);
  }

  /**
   * Validates and performs a jump move.
   *
   * <p>Railway: validate direction → get jumped piece → validate it's opponent → perform jump
   */
  private static EitherPath<String, StateTuple<GameState, MoveResult>> validateJumpMove(
      GameState state, MoveCommand command, Piece piece, Square from, int rowDiff, int colDiff) {

    Square jumpedSquare = new Square(from.row() + rowDiff / 2, from.col() + colDiff / 2);
    Player currentPlayer = GameStateFocus.currentPlayer().get(state);

    return validateJumpDirection(piece, rowDiff)
        .via(p -> getPieceAt(jumpedSquare, state))
        .mapError(err -> "Invalid jump. Must jump over an opponent's piece.")
        .via(
            jumpedPiece ->
                PieceFocus.owner().get(jumpedPiece) != currentPlayer
                    ? Path.right(jumpedPiece)
                    : Path.<String, Piece>left("Cannot jump over your own piece."))
        .map(jumpedPiece -> performJump(state, command, piece, jumpedSquare));
  }

  // ===== Result Helpers =====

  /** Creates an invalid move result. */
  private static StateTuple<GameState, MoveResult> invalidMove(String message, GameState state) {
    return new StateTuple<>(
        new MoveResult(MoveOutcome.INVALID_MOVE, message),
        GameStateFocus.message().set(message, state));
  }

  // ===== Move Execution =====

  /** Performs a simple move. */
  private static StateTuple<GameState, MoveResult> performMove(
      GameState state, MoveCommand command, Piece piece) {

    Map<Square, Piece> newBoard = new HashMap<>(state.board());
    newBoard.remove(command.from());
    newBoard.put(command.to(), piece);

    GameState movedState = GameStateFocus.board().set(newBoard, state);
    GameState finalState = checkAndKingPiece(movedState, command.to());

    return new StateTuple<>(
        new MoveResult(MoveOutcome.SUCCESS, "Move successful."), finalState.togglePlayer());
  }

  /**
   * Performs a jump move with multi-jump support.
   *
   * <p>After completing the jump, checks if another jump is available using a stream-based
   * approach.
   */
  private static StateTuple<GameState, MoveResult> performJump(
      GameState state, MoveCommand command, Piece piece, Square jumpedSquare) {

    Map<Square, Piece> newBoard = new HashMap<>(state.board());
    newBoard.remove(command.from());
    newBoard.remove(jumpedSquare);
    newBoard.put(command.to(), piece);

    GameState jumpedState = GameStateFocus.board().set(newBoard, state);
    GameState stateAfterKinging = checkAndKingPiece(jumpedState, command.to());

    // Check for win condition using MaybePath
    return checkWinCondition(stateAfterKinging)
        .map(winner -> createWinResult(winner, stateAfterKinging))
        .getOrElseGet(() -> checkMultiJumpOrEndTurn(stateAfterKinging, command.to()));
  }

  /** Creates a win result with the game over state. */
  private static StateTuple<GameState, MoveResult> createWinResult(String winner, GameState state) {
    GameState wonState =
        GameStateFocus.isGameOver()
            .set(true, GameStateFocus.message().set(winner + " has captured all pieces!", state));

    return new StateTuple<>(new MoveResult(MoveOutcome.GAME_WON, winner + " wins!"), wonState);
  }

  /** Checks for multi-jump or ends the turn. */
  private static StateTuple<GameState, MoveResult> checkMultiJumpOrEndTurn(
      GameState state, Square position) {

    return canPieceJump(state, position)
        ? new StateTuple<>(
            new MoveResult(
                MoveOutcome.CAPTURE_MADE,
                "Capture successful. You must jump again with the same piece."),
            GameStateFocus.message()
                .set("Capture successful. You must jump again with the same piece.", state))
        : new StateTuple<>(
            new MoveResult(MoveOutcome.CAPTURE_MADE, "Capture successful."), state.togglePlayer());
  }

  // ===== Jump Detection (Stream-based) =====

  /**
   * Checks if a piece can make any valid jump.
   *
   * <p>Uses a stream-based approach instead of nested loops for cleaner functional style.
   */
  private static boolean canPieceJump(GameState state, Square from) {
    return AffinePath.of(FocusPaths.<Square, Piece>mapAt(from))
        .toMaybePath(state.board())
        .map(piece -> hasAnyValidJump(state, from, piece))
        .getOrElse(false);
  }

  /** Checks all directions for valid jumps using streams. */
  private static boolean hasAnyValidJump(GameState state, Square from, Piece piece) {
    Player owner = PieceFocus.owner().get(piece);
    PieceType type = PieceFocus.type().get(piece);

    return generateJumpDirections()
        .filter(dir -> isValidJumpDirection(type, owner, dir.rowOffset()))
        .anyMatch(dir -> isValidJump(state, from, dir, owner));
  }

  /** Generates all possible jump direction pairs. */
  private static Stream<JumpDirection> generateJumpDirections() {
    return IntStream.of(JUMP_OFFSETS)
        .boxed()
        .flatMap(row -> IntStream.of(JUMP_OFFSETS).mapToObj(col -> new JumpDirection(row, col)));
  }

  /** Checks if a jump direction is valid for the piece type. */
  private static boolean isValidJumpDirection(PieceType type, Player owner, int rowOffset) {
    if (type == PieceType.KING) {
      return true;
    }
    // Men can only jump forward
    return !((owner == Player.RED && rowOffset > 0) || (owner == Player.BLACK && rowOffset < 0));
  }

  /** Checks if a specific jump is valid (destination empty, opponent piece to jump). */
  private static boolean isValidJump(
      GameState state, Square from, JumpDirection dir, Player owner) {

    int toRow = from.row() + dir.rowOffset();
    int toCol = from.col() + dir.colOffset();

    // Check bounds
    if (toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) {
      return false;
    }

    Square to = new Square(toRow, toCol);
    Square jumpedSquare =
        new Square(from.row() + dir.rowOffset() / 2, from.col() + dir.colOffset() / 2);

    // Destination must be empty
    boolean destEmpty =
        AffinePath.of(FocusPaths.<Square, Piece>mapAt(to)).getOptional(state.board()).isEmpty();

    // Must have opponent piece to jump
    boolean hasOpponentPiece =
        AffinePath.of(FocusPaths.<Square, Piece>mapAt(jumpedSquare))
            .toMaybePath(state.board())
            .filter(p -> PieceFocus.owner().get(p) != owner)
            .run()
            .isJust();

    return destEmpty && hasOpponentPiece;
  }

  /** Record representing a jump direction (row and column offsets). */
  private record JumpDirection(int rowOffset, int colOffset) {}

  // ===== Win Condition =====

  /** Checks if a player has won by capturing all opponent pieces. */
  private static MaybePath<String> checkWinCondition(GameState state) {
    Map<Square, Piece> board = GameStateFocus.board().get(state);

    boolean noRedPieces =
        board.values().stream().noneMatch(p -> PieceFocus.owner().get(p) == Player.RED);
    boolean noBlackPieces =
        board.values().stream().noneMatch(p -> PieceFocus.owner().get(p) == Player.BLACK);

    if (noRedPieces) {
      return Path.just("BLACK");
    } else if (noBlackPieces) {
      return Path.just("RED");
    } else {
      return Path.nothing();
    }
  }

  // ===== Kinging Logic =====

  /** Checks and kings a piece if it reached the opposite end. */
  private static GameState checkAndKingPiece(GameState state, Square to) {
    return AffinePath.of(FocusPaths.<Square, Piece>mapAt(to))
        .toMaybePath(state.board())
        .filter(piece -> PieceFocus.type().get(piece) == PieceType.MAN)
        .filter(piece -> shouldBeKinged(piece, to))
        .map(piece -> kingPiece(state, to, piece))
        .getOrElse(state);
  }

  /** Determines if a piece should be kinged based on position. */
  private static boolean shouldBeKinged(Piece piece, Square to) {
    Player owner = PieceFocus.owner().get(piece);
    return (owner == Player.RED && to.row() == 0) || (owner == Player.BLACK && to.row() == 7);
  }

  /** Kings a piece at the given square. */
  private static GameState kingPiece(GameState state, Square to, Piece piece) {
    Player owner = PieceFocus.owner().get(piece);
    Map<Square, Piece> newBoard = new HashMap<>(state.board());
    newBoard.put(to, new Piece(owner, PieceType.KING));

    return GameStateFocus.message()
        .set(
            owner + "'s piece at " + to + " has been kinged!",
            GameStateFocus.board().set(newBoard, state));
  }
}
