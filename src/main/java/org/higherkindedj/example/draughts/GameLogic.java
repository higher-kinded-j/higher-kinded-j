// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import java.util.HashMap;
import java.util.Map;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateKindHelper;
import org.higherkindedj.hkt.state.StateTuple;

public class GameLogic {

  static Kind<StateKind.Witness<GameState>, MoveResult> applyMove(MoveCommand command) {
    return StateKindHelper.STATE.widen(
        State.of(
            currentState -> {
              Square from = command.from();
              Square to = command.to();
              Piece piece = currentState.board().get(from);
              String invalidMsg;

              // Validation checks
              if (piece == null) {
                invalidMsg = "No piece at " + from;
                return new StateTuple<>(
                    new MoveResult(MoveOutcome.INVALID_MOVE, invalidMsg),
                    currentState.withMessage(invalidMsg));
              }
              if (piece.owner() != currentState.currentPlayer()) {
                invalidMsg = "Not your piece.";
                return new StateTuple<>(
                    new MoveResult(MoveOutcome.INVALID_MOVE, invalidMsg),
                    currentState.withMessage(invalidMsg));
              }
              if (currentState.board().containsKey(to)) {
                invalidMsg = "Destination square " + to + " is occupied.";
                return new StateTuple<>(
                    new MoveResult(MoveOutcome.INVALID_MOVE, invalidMsg),
                    currentState.withMessage(invalidMsg));
              }

              int rowDiff = to.row() - from.row();
              int colDiff = to.col() - from.col();

              if (Math.abs(rowDiff) == 1 && Math.abs(colDiff) == 1) { // Simple move
                if (piece.type() == PieceType.MAN) {
                  if ((piece.owner() == Player.RED && rowDiff > 0)
                      || (piece.owner() == Player.BLACK && rowDiff < 0)) {
                    invalidMsg = "Men can only move forward.";
                    return new StateTuple<>(
                        new MoveResult(MoveOutcome.INVALID_MOVE, invalidMsg),
                        currentState.withMessage(invalidMsg));
                  }
                }
                return performMove(currentState, command, piece);

              } else if (Math.abs(rowDiff) == 2 && Math.abs(colDiff) == 2) { // Jump move
                if (piece.type() == PieceType.MAN) {
                  if ((piece.owner() == Player.RED && rowDiff > 0)
                      || (piece.owner() == Player.BLACK && rowDiff < 0)) {
                    invalidMsg = "Men can only jump forward.";
                    return new StateTuple<>(
                        new MoveResult(MoveOutcome.INVALID_MOVE, invalidMsg),
                        currentState.withMessage(invalidMsg));
                  }
                }

                Square jumpedSquare =
                    new Square(from.row() + rowDiff / 2, from.col() + colDiff / 2);
                Piece jumpedPiece = currentState.board().get(jumpedSquare);

                if (jumpedPiece == null || jumpedPiece.owner() == currentState.currentPlayer()) {
                  invalidMsg = "Invalid jump. Must jump over an opponent's piece.";
                  return new StateTuple<>(
                      new MoveResult(MoveOutcome.INVALID_MOVE, invalidMsg),
                      currentState.withMessage(invalidMsg));
                }
                return performJump(currentState, command, piece, jumpedSquare);
              } else {
                invalidMsg = "Move must be diagonal by 1 or 2 squares.";
                return new StateTuple<>(
                    new MoveResult(MoveOutcome.INVALID_MOVE, invalidMsg),
                    currentState.withMessage(invalidMsg));
              }
            }));
  }

  private static StateTuple<GameState, MoveResult> performMove(
      GameState state, MoveCommand command, Piece piece) {
    Map<Square, Piece> newBoard = new HashMap<>(state.board());
    newBoard.remove(command.from());
    newBoard.put(command.to(), piece);
    GameState movedState = state.withBoard(newBoard);
    GameState finalState = checkAndKingPiece(movedState, command.to());
    return new StateTuple<>(
        new MoveResult(MoveOutcome.SUCCESS, "Move successful."), finalState.togglePlayer());
  }

  /** Check if a piece at a given square has any valid jumps. */
  private static boolean canPieceJump(GameState state, Square from) {
    Piece piece = state.board().get(from);
    if (piece == null) return false;

    int[] directions = {-2, 2};
    for (int rowOffset : directions) {
      for (int colOffset : directions) {
        if (piece.type() == PieceType.MAN) {
          if ((piece.owner() == Player.RED && rowOffset > 0)
              || (piece.owner() == Player.BLACK && rowOffset < 0)) {
            continue; // Invalid forward direction for man
          }
        }

        Square to = new Square(from.row() + rowOffset, from.col() + colOffset);
        if (to.row() < 0
            || to.row() > 7
            || to.col() < 0
            || to.col() > 7
            || state.board().containsKey(to)) {
          continue; // Off board or destination occupied
        }

        Square jumpedSquare = new Square(from.row() + rowOffset / 2, from.col() + colOffset / 2);
        Piece jumpedPiece = state.board().get(jumpedSquare);
        if (jumpedPiece != null && jumpedPiece.owner() != piece.owner()) {
          return true; // Found a valid jump
        }
      }
    }
    return false;
  }

  /** Now checks for subsequent jumps after a capture. */
  private static StateTuple<GameState, MoveResult> performJump(
      GameState state, MoveCommand command, Piece piece, Square jumpedSquare) {
    // Perform the jump and update board
    Map<Square, Piece> newBoard = new HashMap<>(state.board());
    newBoard.remove(command.from());
    newBoard.remove(jumpedSquare);
    newBoard.put(command.to(), piece);
    GameState jumpedState = state.withBoard(newBoard);

    // Check for kinging after the jump
    GameState stateAfterKinging = checkAndKingPiece(jumpedState, command.to());

    // Check for win condition after the capture
    boolean blackWins =
        !stateAfterKinging.board().values().stream().anyMatch(p -> p.owner() == Player.RED);
    boolean redWins =
        !stateAfterKinging.board().values().stream().anyMatch(p -> p.owner() == Player.BLACK);
    if (blackWins || redWins) {
      String winner = blackWins ? "BLACK" : "RED";
      return new StateTuple<>(
          new MoveResult(MoveOutcome.GAME_WON, winner + " wins!"),
          stateAfterKinging.withGameOver().withMessage(winner + " has captured all pieces!"));
    }

    // Check if the same piece can make another jump
    boolean anotherJumpPossible = canPieceJump(stateAfterKinging, command.to());

    if (anotherJumpPossible) {
      // If another jump exists, DO NOT toggle the player.
      // Update the message to prompt for the next jump.
      String msg = "Capture successful. You must jump again with the same piece.";
      return new StateTuple<>(
          new MoveResult(MoveOutcome.CAPTURE_MADE, msg), stateAfterKinging.withMessage(msg));
    } else {
      // No more jumps, so end the turn and toggle the player.
      return new StateTuple<>(
          new MoveResult(MoveOutcome.CAPTURE_MADE, "Capture successful."),
          stateAfterKinging.togglePlayer());
    }
  }

  private static GameState checkAndKingPiece(GameState state, Square to) {
    Piece piece = state.board().get(to);
    if (piece != null && piece.type() == PieceType.MAN) {
      // RED is kinged on row index 0. BLACK is kinged on row index 7.
      if ((piece.owner() == Player.RED && to.row() == 0)
          || (piece.owner() == Player.BLACK && to.row() == 7)) {
        Map<Square, Piece> newBoard = new HashMap<>(state.board());
        newBoard.put(to, new Piece(piece.owner(), PieceType.KING));
        return state
            .withBoard(newBoard)
            .withMessage(piece.owner() + "'s piece at " + to + " has been kinged!");
      }
    }
    return state;
  }
}
