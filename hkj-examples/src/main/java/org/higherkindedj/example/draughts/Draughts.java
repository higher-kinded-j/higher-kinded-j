// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.ForPath;

/**
 * Main game orchestration for the Draughts (Checkers) game.
 *
 * <p>This class demonstrates functional composition of the Effects Path API:
 *
 * <ul>
 *   <li>{@link IOPath} - Encapsulates side effects (display, input)
 *   <li>{@link org.higherkindedj.hkt.effect.WithStatePath} - Pure game state management
 *   <li>{@link org.higherkindedj.hkt.effect.EitherPath} - Type-safe error handling
 *   <li>{@link ForPath} - For-comprehension style composition
 * </ul>
 *
 * <h2>Functional Architecture</h2>
 *
 * <p>The game follows a purely functional architecture:
 *
 * <ul>
 *   <li>All state changes are pure (GameLogic returns WithStatePath)
 *   <li>Side effects are deferred until execution (IOPath)
 *   <li>Error cases are explicit in the type system (Either)
 *   <li>The game loop is a recursive IOPath computation
 *   <li>Error handling uses pure predicates and extracted handlers
 * </ul>
 *
 * <p>Side effects are only executed in {@code main()}, at the application boundary.
 */
public class Draughts {

  /**
   * The main game loop as a recursive IOPath computation.
   *
   * <p>Creates an IOPath representing the entire game. When executed, it displays the board, reads
   * input, applies game logic, and recurses until the game ends.
   *
   * @param gameState the current state of the game
   * @return an IOPath describing the game loop computation
   */
  private static IOPath<Unit> gameLoop(GameState gameState) {
    return gameState.isGameOver()
        ? BoardDisplay.displayBoard(gameState)
        : processTurn(gameState).via(Draughts::gameLoop);
  }

  /**
   * Processes a single turn of the game.
   *
   * <p>Uses {@link ForPath} to compose the turn workflow:
   *
   * <ol>
   *   <li>Display the current board state
   *   <li>Read the player's move command
   *   <li>Handle errors or apply the move
   * </ol>
   *
   * @param currentState the current state before this turn
   * @return an IOPath producing the new game state after this turn
   */
  private static IOPath<GameState> processTurn(GameState currentState) {
    return ForPath.from(BoardDisplay.displayBoard(currentState))
        .from(ignored -> InputHandler.readMoveCommand())
        .yield((ignored, result) -> result)
        .via(result -> handleTurnResult(result, currentState));
  }

  /**
   * Handles the result of reading input using Either.fold().
   *
   * <p>Delegates to extracted handler functions for cleaner separation of concerns.
   */
  private static IOPath<GameState> handleTurnResult(
      Either<GameError, MoveCommand> result, GameState state) {
    return result.fold(error -> handleError(error, state), command -> applyMove(command, state));
  }

  // ===== Error Handling =====

  /**
   * Handles an error from input parsing.
   *
   * <p>Distinguishes between quit commands and regular errors using a pure predicate.
   */
  private static IOPath<GameState> handleError(GameError error, GameState state) {
    return isQuitCommand(error) ? handleQuit(state) : displayErrorAndContinue(error, state);
  }

  /** Pure predicate: checks if the error represents a quit command using Focus DSL. */
  private static boolean isQuitCommand(GameError error) {
    return GameErrorFocus.isQuit().get(error);
  }

  /** Handles the quit command by setting game over and displaying farewell. */
  private static IOPath<GameState> handleQuit(GameState state) {
    return Path.io(
        () -> {
          System.out.println("Goodbye!");
          return GameStateFocus.isGameOver().set(true, state);
        });
  }

  /** Displays an error message and returns the unchanged state. */
  private static IOPath<GameState> displayErrorAndContinue(GameError error, GameState state) {
    return Path.io(
        () -> {
          System.out.println("Error: " + GameErrorFocus.description().get(error));
          return state;
        });
  }

  // ===== Move Application =====

  /**
   * Applies a valid move command to the game state.
   *
   * <p>Runs the stateful game logic computation and wraps the result in IOPath.
   */
  private static IOPath<GameState> applyMove(MoveCommand command, GameState state) {
    return Path.ioPure(GameLogic.applyMove(command).run(state).state());
  }

  // ===== Entry Point =====

  /**
   * Application entry point.
   *
   * <p>This is the only place where side effects are executed. The entire game is constructed as a
   * pure IOPath value, then executed at this boundary.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    IOPath<Unit> game =
        Path.ioPure(GameState.initial())
            .via(Draughts::gameLoop)
            .then(() -> Path.ioRunnable(() -> System.out.println("Thank you for playing!")));

    game.unsafeRun();
  }
}
