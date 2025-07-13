// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.state.StateKindHelper;
import org.higherkindedj.hkt.unit.Unit;

public class Draughts {

  private static final IOMonad ioMonad = IOMonad.INSTANCE;

  // The main game loop as a single, recursive IO computation
  private static Kind<IOKind.Witness, Unit> gameLoop(GameState gameState) {
    if (gameState.isGameOver()) {
      // Base case: game is over, just display the final board and message.
      return BoardDisplay.displayBoard(gameState);
    }

    // Recursive step: process one turn and then loop with the new state
    return ioMonad.flatMap(Draughts::gameLoop, processTurn(gameState));
  }

  // Processes a single turn of the game
  private static Kind<IOKind.Witness, GameState> processTurn(GameState currentGameState) {
    // The flow for a turn: Display -> Read -> Process -> Return New State
    Kind<IOKind.Witness, Unit> displayAction = BoardDisplay.displayBoard(currentGameState);
    Kind<IOKind.Witness, Either<GameError, MoveCommand>> readAction =
        InputHandler.readMoveCommand();

    // 1. Use 'For' to clearly sequence the display and read actions.
    var sequence =
        For.from(ioMonad, displayAction)
            .from(ignored -> readAction)
            .yield((ignored, eitherResult) -> eitherResult); // Yield the result of the read action

    // 2. The result of the 'For' is an IO<Either<...>>.
    //    Now, flatMap that single result to handle the branching.
    return ioMonad.flatMap(
        eitherResult ->
            eitherResult.fold(
                error -> { // Left case: Input error
                  // Print the error and return the unchanged state within an IO action.
                  return IOKindHelper.IO_OP.delay(
                      () -> {
                        System.out.println("Error: " + error.description());
                        return currentGameState;
                      });
                },
                moveCommand -> { // Right case: Valid input
                  // Run the stateful game logic.
                  var stateComputation = GameLogic.applyMove(moveCommand);
                  var resultTuple =
                      StateKindHelper.STATE.runState(stateComputation, currentGameState);
                  GameState nextState = resultTuple.state();
                  // Lift the new state back into IO.
                  return ioMonad.of(nextState);
                }),
        sequence);
  }

  public static void main(String[] args) {
    // Get the initial state
    GameState initialState = GameState.initial();
    // Create the full game IO program
    Kind<IOKind.Witness, Unit> fullGame = gameLoop(initialState);
    // Execute the program. This is the only place where side effects are actually run.
    IOKindHelper.IO_OP.unsafeRunSync(fullGame);
    System.out.println("Thank you for playing!");
  }
}
