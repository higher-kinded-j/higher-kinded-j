// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import java.util.Scanner;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;

/**
 * Handles reading and parsing user input from the console.
 *
 * <p>This class demonstrates railway-oriented programming for input parsing. The parsing pipeline
 * uses {@link EitherPath} to chain validation steps, where errors automatically short-circuit to
 * the error track.
 *
 * <h2>Parsing Pipeline</h2>
 *
 * <pre>{@code
 * readLine → checkQuit → splitInput → parseSquares → MoveCommand
 *    ↓          ↓           ↓            ↓
 *  IOPath   EitherPath  EitherPath   EitherPath
 * }</pre>
 *
 * <p>Each step either continues on the success track or switches to the error track. The final
 * result captures both:
 *
 * <ul>
 *   <li>The side effect of reading from console (IOPath)
 *   <li>The possibility of parse errors (Either)
 * </ul>
 */
class InputHandler {

  private static final Scanner scanner = new Scanner(System.in);
  private static final String QUIT_COMMAND = "quit";
  private static final String INPUT_PROMPT = "Enter move (e.g., 'a3 b4') or 'quit': ";

  /**
   * Reads a move command from the console.
   *
   * <p>Wraps the side-effecting input operation in IOPath and delegates parsing to a pure function.
   *
   * @return an IOPath containing either a GameError (left) or a valid MoveCommand (right)
   */
  static IOPath<Either<GameError, MoveCommand>> readMoveCommand() {
    return Path.io(
        () -> {
          System.out.print(INPUT_PROMPT);
          return parseLine(scanner.nextLine().trim());
        });
  }

  /**
   * Parses an input line into a MoveCommand using railway-oriented programming.
   *
   * <p>The parsing pipeline chains validation steps with {@code .via()}, where any error
   * short-circuits the entire pipeline.
   *
   * @param line the trimmed input line
   * @return Either containing a GameError or valid MoveCommand
   */
  private static Either<GameError, MoveCommand> parseLine(String line) {
    return checkNotQuit(line)
        .via(InputHandler::splitIntoTwoParts)
        .via(InputHandler::parseSquarePair)
        .run();
  }

  // ===== Pipeline Steps =====

  /**
   * First pipeline step: checks if input is the quit command.
   *
   * <p>Returns the input on success track, or a quit error on error track.
   */
  private static EitherPath<GameError, String> checkNotQuit(String line) {
    return QUIT_COMMAND.equalsIgnoreCase(line)
        ? Path.left(new GameError("Player quit the game.", true))
        : Path.right(line);
  }

  /**
   * Second pipeline step: splits input into exactly two parts.
   *
   * <p>Validates that input has the "from to" format.
   */
  private static EitherPath<GameError, String[]> splitIntoTwoParts(String line) {
    String[] parts = line.split("\\s+");
    return parts.length == 2
        ? Path.right(parts)
        : Path.left(new GameError("Invalid input. Use 'from to' format (e.g., 'c3 d4')."));
  }

  /**
   * Third pipeline step: parses both squares and combines into MoveCommand.
   *
   * <p>Uses {@code zipWith} to combine two EitherPaths, failing if either fails.
   */
  private static EitherPath<GameError, MoveCommand> parseSquarePair(String[] parts) {
    return parseSquare(parts[0]).zipWith(parseSquare(parts[1]), MoveCommand::new);
  }

  // ===== Square Parsing =====

  /**
   * Parses a square notation string (e.g., "a3") into a Square.
   *
   * <p>Uses railway-oriented programming to chain format and bounds validation.
   *
   * @param input the square notation string
   * @return an EitherPath containing either a GameError or valid Square
   */
  private static EitherPath<GameError, Square> parseSquare(String input) {
    return validateFormat(input).via(InputHandler::validateBoundsAndCreate);
  }

  /** Validates that input has exactly 2 characters. */
  private static EitherPath<GameError, String> validateFormat(String input) {
    return (input != null && input.length() == 2)
        ? Path.right(input)
        : Path.left(new GameError("Invalid square format: " + input));
  }

  /** Validates bounds (a-h, 1-8) and creates the Square. */
  private static EitherPath<GameError, Square> validateBoundsAndCreate(String input) {
    char colChar = input.charAt(0);
    char rowChar = input.charAt(1);

    boolean validCol = colChar >= 'a' && colChar <= 'h';
    boolean validRow = rowChar >= '1' && rowChar <= '8';

    if (!validCol || !validRow) {
      return Path.left(new GameError("Square out of bounds (a1-h8): " + input));
    }

    int col = colChar - 'a';
    int row = rowChar - '1';
    return Path.right(new Square(row, col));
  }
}
