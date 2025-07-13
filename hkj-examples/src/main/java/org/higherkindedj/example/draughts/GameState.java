// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;

// Enum for the two players
enum Player {
  RED,
  BLACK
}

// Enum for the type of piece
enum PieceType {
  MAN,
  KING
}

// A piece on the board, owned by a player with a certain type
record Piece(Player owner, PieceType type) {}

// A square on the 8x8 board, identified by row and column
record Square(int row, int col) {
  @Override
  public @NonNull String toString() {
    return "" + (char) ('a' + col) + (row + 1);
  }
}

// Represents an error during move parsing or validation
record GameError(String description) {}

// The command to make a move from one square to another
record MoveCommand(Square from, Square to) {}

// The outcome of a move attempt
enum MoveOutcome {
  SUCCESS,
  INVALID_MOVE,
  CAPTURE_MADE,
  GAME_WON
}

record MoveResult(MoveOutcome outcome, String message) {}

// The complete, immutable state of the game at any point in time
public record GameState(
    Map<Square, Piece> board, Player currentPlayer, String message, boolean isGameOver) {

  public static GameState initial() {
    Map<Square, Piece> startingBoard = new HashMap<>();
    // Place BLACK pieces
    for (int r = 0; r < 3; r++) {
      for (int c = (r % 2 != 0) ? 0 : 1; c < 8; c += 2) {
        startingBoard.put(new Square(r, c), new Piece(Player.BLACK, PieceType.MAN));
      }
    }
    // Place RED pieces
    for (int r = 5; r < 8; r++) {
      for (int c = (r % 2 != 0) ? 0 : 1; c < 8; c += 2) {
        startingBoard.put(new Square(r, c), new Piece(Player.RED, PieceType.MAN));
      }
    }
    return new GameState(
        Collections.unmodifiableMap(startingBoard), Player.RED, "Game started. RED's turn.", false);
  }

  GameState withBoard(Map<Square, Piece> newBoard) {
    return new GameState(
        Collections.unmodifiableMap(newBoard), this.currentPlayer, this.message, this.isGameOver);
  }

  GameState withCurrentPlayer(Player nextPlayer) {
    return new GameState(this.board, nextPlayer, this.message, this.isGameOver);
  }

  GameState withMessage(String newMessage) {
    return new GameState(this.board, this.currentPlayer, newMessage, this.isGameOver);
  }

  GameState withGameOver() {
    return new GameState(this.board, this.currentPlayer, this.message, true);
  }

  GameState togglePlayer() {
    Player next = (this.currentPlayer == Player.RED) ? Player.BLACK : Player.RED;
    return withCurrentPlayer(next).withMessage(next + "'s turn.");
  }
}
