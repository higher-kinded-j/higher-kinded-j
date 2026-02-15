// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.jspecify.annotations.NonNull;

/** Enum for the two players. */
enum Player {
  RED,
  BLACK
}

/** Enum for the type of piece. */
enum PieceType {
  MAN,
  KING
}

/** A piece on the board, owned by a player with a certain type. */
@GenerateFocus
record Piece(Player owner, PieceType type) {}

/** A square on the 8x8 board, identified by row and column. */
@GenerateFocus
record Square(int row, int col) {
  @Override
  public @NonNull String toString() {
    return "" + (char) ('a' + col) + (row + 1);
  }
}

/**
 * Represents an error during move parsing or validation.
 *
 * @param description the error message
 * @param isQuit true if this error represents a quit command, false otherwise
 */
@GenerateFocus
record GameError(String description, boolean isQuit) {
  /** Convenience constructor for non-quit errors. */
  public GameError(String description) {
    this(description, false);
  }
}

/** The command to make a move from one square to another. */
@GenerateFocus
record MoveCommand(Square from, Square to) {}

/** The outcome of a move attempt. */
enum MoveOutcome {
  SUCCESS,
  INVALID_MOVE,
  CAPTURE_MADE,
  GAME_WON
}

/** The result of applying a move, containing the outcome and a message. */
@GenerateFocus
record MoveResult(MoveOutcome outcome, String message) {}

/**
 * The complete, immutable state of the game at any point in time.
 *
 * <p>This record uses the {@code @GenerateFocus} annotation to generate type-safe optics for
 * navigating and updating the game state using the Focus DSL.
 *
 * <h2>Functional Initialization</h2>
 *
 * <p>The initial board setup uses streams for a declarative, functional approach:
 *
 * <ul>
 *   <li>{@code placePieces()} - Generates piece placements for a player using flatMap
 *   <li>{@code darkSquaresInRow()} - Calculates playable squares using IntStream.iterate
 * </ul>
 */
@GenerateFocus
public record GameState(
    Map<Square, Piece> board, Player currentPlayer, String message, boolean isGameOver) {

  /** Rows where BLACK pieces start (0, 1, 2). */
  private static final int BLACK_START_ROW = 0;

  private static final int BLACK_END_ROW = 3;

  /** Rows where RED pieces start (5, 6, 7). */
  private static final int RED_START_ROW = 5;

  private static final int RED_END_ROW = 8;

  /**
   * Creates the initial game state with pieces in starting positions.
   *
   * <p>Uses stream-based board initialization for a functional, declarative approach.
   *
   * @return the initial game state
   */
  public static GameState initial() {
    Map<Square, Piece> board =
        Stream.concat(
                placePieces(Player.BLACK, BLACK_START_ROW, BLACK_END_ROW),
                placePieces(Player.RED, RED_START_ROW, RED_END_ROW))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    return new GameState(board, Player.RED, "Game started. RED's turn.", false);
  }

  /**
   * Generates piece placements for a player across the specified rows.
   *
   * <p>Uses flatMap to combine all squares across multiple rows into a single stream.
   *
   * @param owner the player who owns these pieces
   * @param startRow the first row (inclusive)
   * @param endRow the last row (exclusive)
   * @return a stream of (Square, Piece) entries
   */
  private static Stream<Map.Entry<Square, Piece>> placePieces(
      Player owner, int startRow, int endRow) {
    Piece piece = new Piece(owner, PieceType.MAN);

    return IntStream.range(startRow, endRow)
        .boxed()
        .flatMap(
            row -> darkSquaresInRow(row).mapToObj(col -> Map.entry(new Square(row, col), piece)));
  }

  /**
   * Returns the column indices of dark (playable) squares in a row.
   *
   * <p>On a checkerboard, playable squares alternate. In odd rows (1, 3, 5, 7), dark squares start
   * at column 0. In even rows (0, 2, 4, 6), dark squares start at column 1.
   *
   * @param row the row index (0-7)
   * @return IntStream of column indices for dark squares
   */
  private static IntStream darkSquaresInRow(int row) {
    int startCol = (row % 2 != 0) ? 0 : 1;
    return IntStream.iterate(startCol, col -> col < 8, col -> col + 2);
  }

  /**
   * Creates a new state with the specified player.
   *
   * @param nextPlayer the player to set
   * @return a new GameState with the updated player
   */
  GameState withCurrentPlayer(Player nextPlayer) {
    return new GameState(this.board, nextPlayer, this.message, this.isGameOver);
  }

  /**
   * Creates a new state with the specified message.
   *
   * @param newMessage the message to set
   * @return a new GameState with the updated message
   */
  GameState withMessage(String newMessage) {
    return new GameState(this.board, this.currentPlayer, newMessage, this.isGameOver);
  }

  /**
   * Toggles the current player and updates the turn message.
   *
   * @return a new GameState with the next player's turn
   */
  GameState togglePlayer() {
    Player next = (this.currentPlayer == Player.RED) ? Player.BLACK : Player.RED;
    return withCurrentPlayer(next).withMessage(next + "'s turn.");
  }
}
