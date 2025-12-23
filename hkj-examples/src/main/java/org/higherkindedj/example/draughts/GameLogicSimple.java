// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import java.util.HashMap;
import java.util.Map;
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
 * Simple game logic for draughts without multi-jump rules.
 *
 * <p>This class demonstrates functional game logic using railway-oriented programming. All
 * validations are chained using {@link EitherPath}, where errors automatically short-circuit the
 * computation. This eliminates nested if-statements and makes the validation pipeline explicit.
 *
 * <h2>Railway-Oriented Programming Pattern</h2>
 *
 * <p>Each validation step returns an {@code EitherPath<String, T>}:
 *
 * <ul>
 *   <li>Left (error track) - Validation failed, error propagates automatically
 *   <li>Right (success track) - Validation passed, continue to next step
 * </ul>
 *
 * <p>The {@code .via()} method chains operations on the success track, while errors bypass all
 * subsequent steps and flow directly to the final {@code .fold()}.
 *
 * <h2>Key Patterns Demonstrated</h2>
 *
 * <ul>
 *   <li>{@code WithStatePath} - Pure state transformations
 *   <li>{@code EitherPath.via()} - Railway-oriented validation chaining
 *   <li>{@code EitherPath.fold()} - Terminal operation converting to result type
 *   <li>{@code FocusPaths.mapAt()} - Map navigation via Focus DSL
 * </ul>
 */
public class GameLogicSimple {

  /**
   * Applies a move command to the current game state.
   *
   * <p>This method builds a validation pipeline using railway-oriented programming. Each validation
   * step is chained with {@code .via()}, and the final result is produced by {@code .fold()}.
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
                // Start the railway: get piece at source square
                getPieceAt(from, state)
                    // Validate ownership (stays on success track or switches to error track)
                    .via(piece -> validateOwnership(piece, state))
                    // Validate destination is empty
                    .via(piece -> validateDestinationEmpty(to, state).map(unit -> piece))
                    // Validate and apply the move
                    .via(piece -> validateAndApply(state, command, piece, from, to))
                    // Terminal: convert EitherPath to StateTuple
                    .fold(error -> invalidMove(error, state), result -> result)));
  }

  // ===== Validation Pipeline Steps =====

  /**
   * Gets the piece at a square, returning an error if no piece exists.
   *
   * <p>This is the first step in the validation railway.
   */
  private static EitherPath<String, Piece> getPieceAt(Square square, GameState state) {
    return AffinePath.of(FocusPaths.<Square, Piece>mapAt(square))
        .toEitherPath(state.board(), "No piece at " + square);
  }

  /**
   * Validates that the piece belongs to the current player.
   *
   * <p>Returns the piece on success, or an error message on failure.
   */
  private static EitherPath<String, Piece> validateOwnership(Piece piece, GameState state) {
    Player currentPlayer = GameStateFocus.currentPlayer().get(state);
    Player pieceOwner = PieceFocus.owner().get(piece);

    return pieceOwner == currentPlayer ? Path.right(piece) : Path.left("Not your piece.");
  }

  /**
   * Validates that the destination square is empty.
   *
   * <p>Returns Unit on success (we don't need a value, just validation).
   */
  private static EitherPath<String, Unit> validateDestinationEmpty(Square to, GameState state) {
    boolean isEmpty =
        AffinePath.of(FocusPaths.<Square, Piece>mapAt(to)).getOptional(state.board()).isEmpty();

    return isEmpty
        ? Path.right(Unit.INSTANCE)
        : Path.left("Destination square " + to + " is occupied.");
  }

  /**
   * Validates the move type and applies it if valid.
   *
   * <p>Determines whether this is a simple move or a jump based on distance, validates direction
   * rules, and applies the appropriate move logic.
   */
  private static EitherPath<String, StateTuple<GameState, MoveResult>> validateAndApply(
      GameState state, MoveCommand command, Piece piece, Square from, Square to) {

    int rowDiff = SquareFocus.row().get(to) - SquareFocus.row().get(from);
    int colDiff = SquareFocus.col().get(to) - SquareFocus.col().get(from);

    // Determine move type and validate
    if (Math.abs(rowDiff) == 1 && Math.abs(colDiff) == 1) {
      return validateSimpleMove(piece, rowDiff)
          .map(validPiece -> performMove(state, command, validPiece));
    } else if (Math.abs(rowDiff) == 2 && Math.abs(colDiff) == 2) {
      return validateJumpMove(state, command, piece, from, rowDiff, colDiff);
    } else {
      return Path.left("Move must be diagonal by 1 or 2 squares.");
    }
  }

  /**
   * Validates a simple one-square diagonal move.
   *
   * <p>Men can only move forward; kings can move in any diagonal direction.
   */
  private static EitherPath<String, Piece> validateSimpleMove(Piece piece, int rowDiff) {
    PieceType type = PieceFocus.type().get(piece);
    Player owner = PieceFocus.owner().get(piece);

    // Men can only move forward
    if (type == PieceType.MAN) {
      boolean isBackward =
          (owner == Player.RED && rowDiff > 0) || (owner == Player.BLACK && rowDiff < 0);
      if (isBackward) {
        return Path.left("Men can only move forward.");
      }
    }

    return Path.right(piece);
  }

  /**
   * Validates and performs a jump move (capturing an opponent's piece).
   *
   * <p>Uses railway-oriented programming to chain: get jumped piece → validate it's an opponent →
   * perform the jump.
   */
  private static EitherPath<String, StateTuple<GameState, MoveResult>> validateJumpMove(
      GameState state, MoveCommand command, Piece piece, Square from, int rowDiff, int colDiff) {

    Square jumpedSquare = new Square(from.row() + rowDiff / 2, from.col() + colDiff / 2);
    Player currentPlayer = GameStateFocus.currentPlayer().get(state);

    // Railway: get jumped piece → validate it's opponent → perform jump
    return getPieceAt(jumpedSquare, state)
        .mapError(err -> "Invalid jump. Must jump over an opponent's piece.")
        .via(
            jumpedPiece -> {
              Player jumpedOwner = PieceFocus.owner().get(jumpedPiece);
              return jumpedOwner != currentPlayer
                  ? Path.right(jumpedPiece)
                  : Path.<String, Piece>left("Cannot jump over your own piece.");
            })
        .map(jumpedPiece -> performJump(state, command, piece, jumpedSquare));
  }

  // ===== Result Helpers =====

  /** Creates an invalid move result with the given error message. */
  private static StateTuple<GameState, MoveResult> invalidMove(String message, GameState state) {
    return new StateTuple<>(
        new MoveResult(MoveOutcome.INVALID_MOVE, message),
        GameStateFocus.message().set(message, state));
  }

  // ===== Move Execution =====

  /** Performs a simple move and updates the game state. */
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

  /** Performs a jump move, removing the captured piece. */
  private static StateTuple<GameState, MoveResult> performJump(
      GameState state, MoveCommand command, Piece piece, Square jumpedSquare) {

    Map<Square, Piece> newBoard = new HashMap<>(state.board());
    newBoard.remove(command.from());
    newBoard.remove(jumpedSquare);
    newBoard.put(command.to(), piece);

    GameState jumpedState = GameStateFocus.board().set(newBoard, state);
    GameState finalState = checkAndKingPiece(jumpedState, command.to());

    // Check for win condition
    return checkWinCondition(finalState)
        .map(
            winner ->
                new StateTuple<>(
                    new MoveResult(MoveOutcome.GAME_WON, winner + " wins!"),
                    GameStateFocus.isGameOver()
                        .set(
                            true,
                            GameStateFocus.message()
                                .set(winner + " has captured all pieces!", finalState))))
        .getOrElse(
            new StateTuple<>(
                new MoveResult(MoveOutcome.CAPTURE_MADE, "Capture successful."),
                finalState.togglePlayer()));
  }

  /**
   * Checks if a player has won by capturing all opponent pieces.
   *
   * @return Maybe containing the winner's name, or Nothing if game continues
   */
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

  /** Checks if a piece should be kinged and updates the state accordingly. */
  private static GameState checkAndKingPiece(GameState state, Square to) {
    return AffinePath.of(FocusPaths.<Square, Piece>mapAt(to))
        .toMaybePath(state.board())
        .filter(piece -> PieceFocus.type().get(piece) == PieceType.MAN)
        .filter(piece -> shouldBeKinged(piece, to))
        .map(piece -> kingPiece(state, to, piece))
        .getOrElse(state);
  }

  /** Determines if a piece should be kinged based on its position. */
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
