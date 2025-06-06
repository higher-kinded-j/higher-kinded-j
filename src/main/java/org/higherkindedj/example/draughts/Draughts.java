// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateKindHelper;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.unit.Unit;

public class Draughts {

  private static final IOMonad ioMonad = new IOMonad();

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

    return ioMonad.flatMap(
        ignored -> // After display...
        ioMonad.flatMap(
                eitherResult -> // After read...
                eitherResult.fold(
                        error -> { // Left case: Input error
                          // Create an IO action that prints the error and returns the unchanged
                          // state
                          return IOKindHelper.IO_OP.delay(
                              () -> {
                                System.out.println("Error: " + error.description());
                                return currentGameState;
                              });
                        },
                        moveCommand -> { // Right case: Valid input format
                          // Run the stateful game logic
                          Kind<StateKind.Witness<GameState>, MoveResult> stateComputation =
                              GameLogic.applyMove(moveCommand);
                          StateTuple<GameState, MoveResult> resultTuple =
                              StateKindHelper.STATE.runState(stateComputation, currentGameState);

                          // The new state is in the tuple result
                          GameState nextState = resultTuple.state();
                          return ioMonad.of(nextState); // Lift the new state back into IO
                        }),
                readAction),
        displayAction);
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
