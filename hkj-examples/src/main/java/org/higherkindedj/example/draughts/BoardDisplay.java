// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPaths;

/**
 * Handles rendering the game board to the console.
 *
 * <p>This class uses {@link IOPath} to encapsulate side effects (console output) as values. The
 * {@code displayBoard} method returns an {@code IOPath<Unit>} which describes the printing action
 * but does not execute it until the IOPath is run.
 *
 * <p>The rendering logic uses a functional approach with streams and pure helper functions:
 *
 * <ul>
 *   <li>{@code renderBoard()} - Builds the complete board string using streams
 *   <li>{@code renderRow()} - Renders a single row using IntStream
 *   <li>{@code renderSquare()} - Uses MaybePath to handle optional pieces
 *   <li>{@code pieceToChar()} - Pure function mapping pieces to display characters
 * </ul>
 *
 * <h2>Board Legend</h2>
 *
 * <ul>
 *   <li>{@code r} - RED man
 *   <li>{@code R} - RED king
 *   <li>{@code b} - BLACK man
 *   <li>{@code B} - BLACK king
 *   <li>{@code .} - empty square
 * </ul>
 */
public class BoardDisplay {

  private static final String BOARD_HEADER = "  a b c d e f g h";
  private static final String BOARD_BORDER = " +-----------------+";

  /**
   * Creates an IOPath that, when executed, displays the current game state to the console.
   *
   * <p>The actual printing is deferred until {@code unsafeRun()} is called on the returned IOPath.
   *
   * @param gameState the current state of the game to display
   * @return an IOPath describing the display action
   */
  public static IOPath<Unit> displayBoard(GameState gameState) {
    return Path.ioRunnable(() -> System.out.println(renderGameState(gameState)));
  }

  /**
   * Renders the complete game state as a string.
   *
   * <p>Composes the board display from header, rows, footer, and status message.
   */
  private static String renderGameState(GameState state) {
    return String.join(
        "\n",
        "",
        BOARD_HEADER,
        BOARD_BORDER,
        renderBoard(state),
        BOARD_BORDER,
        BOARD_HEADER,
        "",
        state.message(),
        renderCurrentPlayer(state));
  }

  /**
   * Renders all board rows using streams.
   *
   * <p>Iterates from row 7 down to 0 (displaying row 8 at top, row 1 at bottom).
   */
  private static String renderBoard(GameState state) {
    return IntStream.iterate(7, row -> row >= 0, row -> row - 1)
        .mapToObj(row -> renderRow(state, row))
        .collect(Collectors.joining("\n"));
  }

  /**
   * Renders a single row of the board.
   *
   * <p>Format: "N| x x x x x x x x |N" where N is the row number (1-8).
   */
  private static String renderRow(GameState state, int row) {
    String squares =
        IntStream.range(0, 8)
            .mapToObj(col -> renderSquare(state, row, col))
            .collect(Collectors.joining(" "));

    int displayRow = row + 1;
    return displayRow + "| " + squares + " |" + displayRow;
  }

  /**
   * Renders a single square using MaybePath for optional piece handling.
   *
   * <p>Uses the Focus-Effect bridge to safely access the piece at the given position, returning "."
   * if no piece exists.
   */
  private static String renderSquare(GameState state, int row, int col) {
    return AffinePath.of(FocusPaths.<Square, Piece>mapAt(new Square(row, col)))
        .toMaybePath(state.board())
        .map(BoardDisplay::pieceToChar)
        .getOrElse(".");
  }

  /**
   * Converts a piece to its display character.
   *
   * <p>This is a pure function with no side effects:
   *
   * <ul>
   *   <li>RED pieces: 'r' (man) or 'R' (king)
   *   <li>BLACK pieces: 'b' (man) or 'B' (king)
   * </ul>
   */
  private static String pieceToChar(Piece piece) {
    char base = PieceFocus.owner().get(piece) == Player.RED ? 'r' : 'b';
    char display =
        PieceFocus.type().get(piece) == PieceType.KING ? Character.toUpperCase(base) : base;
    return String.valueOf(display);
  }

  /**
   * Renders the current player status line.
   *
   * <p>Returns empty string if game is over, otherwise shows whose turn it is.
   */
  private static String renderCurrentPlayer(GameState state) {
    return state.isGameOver() ? "" : "Current Player: " + GameStateFocus.currentPlayer().get(state);
  }
}
