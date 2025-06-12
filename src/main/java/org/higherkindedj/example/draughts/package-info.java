/**
 * A simple command-line draughts (checkers) game demonstrating the use of the IO and State monads
 * to manage side effects and game state.
 *
 * <p>This example showcases how to structure a complete application using functional concepts:
 *
 * <ul>
 *   <li><b>IO Monad:</b> For handling all interactions with the console (reading input, displaying
 *       the board).
 *   <li><b>State Monad:</b> For managing the game's state (board layout, current player) in a
 *       purely functional way.
 *   <li><b>Either:</b> For handling parsing errors from user input.
 * </ul>
 *
 * The main game loop in {@link org.higherkindedj.example.draughts.Draughts} composes these effects
 * to create the interactive gameplay experience.
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.example.draughts;
