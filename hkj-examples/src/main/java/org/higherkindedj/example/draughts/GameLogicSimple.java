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

public class GameLogicSimple {

  static Kind<StateKind.Witness<GameState>, MoveResult> applyMove(MoveCommand command) {
    return StateKindHelper.STATE.widen(
        State.of(
            currentState -> {
              // Unpack command for easier access
              Square from = command.from();
              Square to = command.to();
              Piece piece = currentState.board().get(from);
              String invalidMsg; // To hold error messages

              // Validate the move based on currentState and command
              //    - Is it the current player's piece?
              //    - Is the move diagonal?
              //    - Is the destination square empty or an opponent's piece for a jump?

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

              // Simple move or jump?
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

  private static StateTuple<GameState, MoveResult> performJump(
      GameState state, MoveCommand command, Piece piece, Square jumpedSquare) {
    Map<Square, Piece> newBoard = new HashMap<>(state.board());
    newBoard.remove(command.from());
    newBoard.remove(jumpedSquare);
    newBoard.put(command.to(), piece);

    GameState jumpedState = state.withBoard(newBoard);
    GameState finalState = checkAndKingPiece(jumpedState, command.to());

    // Check for win condition
    boolean blackWins =
        finalState.board().values().stream().noneMatch(p -> p.owner() == Player.RED);
    boolean redWins =
        finalState.board().values().stream().noneMatch(p -> p.owner() == Player.BLACK);

    if (blackWins || redWins) {
      String winner = blackWins ? "BLACK" : "RED";
      return new StateTuple<>(
          new MoveResult(MoveOutcome.GAME_WON, winner + " wins!"),
          finalState.withGameOver().withMessage(winner + " has captured all pieces!"));
    }

    return new StateTuple<>(
        new MoveResult(MoveOutcome.CAPTURE_MADE, "Capture successful."), finalState.togglePlayer());
  }

  private static GameState checkAndKingPiece(GameState state, Square to) {
    Piece piece = state.board().get(to);
    if (piece != null && piece.type() == PieceType.MAN) {
      // A RED piece is kinged on row index 0 (the "1st" row).
      // A BLACK piece is kinged on row index 7 (the "8th" row).
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
