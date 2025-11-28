// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.example.optics.fluent.generated.PlayerLenses;
import org.higherkindedj.example.optics.fluent.model.Player;
import org.higherkindedj.hkt.free.Free;

/**
 * Demonstrates the Free Monad DSL for optics.
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Build optic programs as data structures
 *   <li>Compose complex workflows with conditional logic
 *   <li>Use different interpreters (direct, logging, validation)
 * </ul>
 */
public final class FreeDslExample {

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    try {
      Player player = new Player("Alice", 17, 85);

      System.out.println("=== Free Monad DSL Examples ===\n");

      // ========== Simple Program ==========
      System.out.println("--- Simple Program ---");

      // Build a program that increments age and score
      Free<OpticOpKind.Witness, Player> simpleProgram =
          OpticPrograms.modify(player, PlayerLenses.age(), age -> age + 1)
              .flatMap(p1 -> OpticPrograms.modify(p1, PlayerLenses.score(), score -> score + 10));

      // Execute with direct interpreter
      Player result1 = OpticInterpreters.direct().run(simpleProgram);
      System.out.println("Original: " + player);
      System.out.println("Updated:  " + result1);

      System.out.println();

      // ========== Conditional Program ==========
      System.out.println("--- Conditional Program ---");

      // Build a program with conditional logic
      Free<OpticOpKind.Witness, Player> conditionalProgram =
          OpticPrograms.get(player, PlayerLenses.age())
              .flatMap(
                  age -> {
                    if (age < 18) {
                      System.out.println("  Player is a minor, no changes");
                      return OpticPrograms.pure(player);
                    } else {
                      System.out.println("  Player is an adult, adding bonus");
                      return OpticPrograms.modify(
                          player, PlayerLenses.score(), score -> score + 20);
                    }
                  });

      Player result2 = OpticInterpreters.direct().run(conditionalProgram);
      System.out.println("Result: " + result2);

      System.out.println();

      // ========== Complex Workflow ==========
      System.out.println("--- Complex Workflow ---");

      // Build a multi-step program that:
      // 1. Checks age
      // 2. If >= 18, increments age and adds bonus
      // 3. If score > 100, gives extra reward
      Player adult = new Player("Bob", 25, 95);

      Free<OpticOpKind.Witness, Player> complexProgram =
          OpticPrograms.get(adult, PlayerLenses.age())
              .flatMap(
                  age -> {
                    if (age >= 18) {
                      return OpticPrograms.modify(adult, PlayerLenses.age(), a -> a + 1)
                          .flatMap(
                              p1 ->
                                  OpticPrograms.modify(
                                          p1, PlayerLenses.score(), score -> score + 15)
                                      .flatMap(
                                          p2 ->
                                              OpticPrograms.get(p2, PlayerLenses.score())
                                                  .flatMap(
                                                      score -> {
                                                        if (score > 100) {
                                                          return OpticPrograms.modify(
                                                              p2, PlayerLenses.score(), s -> s + 5);
                                                        } else {
                                                          return OpticPrograms.pure(p2);
                                                        }
                                                      })));
                    } else {
                      return OpticPrograms.pure(adult);
                    }
                  });

      Player result3 = OpticInterpreters.direct().run(complexProgram);
      System.out.println("Original: " + adult);
      System.out.println("Updated:  " + result3);

      System.out.println();

      // ========== Logging Interpreter ==========
      System.out.println("--- Logging Interpreter ---");

      // Execute with logging to see what happened
      LoggingOpticInterpreter logger = OpticInterpreters.logging();
      Player result4 = logger.run(complexProgram);

      System.out.println("Operations log:");
      logger.getLog().forEach(log -> System.out.println("  " + log));

      System.out.println();

      // ========== Validation Interpreter ==========
      System.out.println("--- Validation Interpreter ---");

      // Validate before executing
      // Note: Use a simple program without data-dependent conditionals
      // since the validator doesn't execute actual operations
      ValidationOpticInterpreter validator = OpticInterpreters.validating();
      ValidationOpticInterpreter.ValidationResult validation = validator.validate(simpleProgram);

      System.out.println("Validation result:");
      System.out.println("  Valid: " + validation.isValid());
      System.out.println("  Errors: " + validation.errors());
      System.out.println("  Warnings: " + validation.warnings());

      if (validation.isValid()) {
        System.out.println("  -> Safe to execute!");
      }

      System.out.println();

      // ========== Reusable Program Components ==========
      System.out.println("--- Reusable Components ---");

      // Define reusable program pieces
      Player youngPlayer = new Player("Charlie", 16, 75);

      Free<OpticOpKind.Witness, Player> agePlayer =
          OpticPrograms.modify(youngPlayer, PlayerLenses.age(), age -> age + 1);

      Free<OpticOpKind.Witness, Player> addBonus =
          agePlayer.flatMap(
              p -> OpticPrograms.modify(p, PlayerLenses.score(), score -> score + 10));

      // Compose them
      Free<OpticOpKind.Witness, Player> reusableProgram =
          addBonus.flatMap(
              p ->
                  OpticPrograms.get(p, PlayerLenses.age())
                      .flatMap(
                          age -> {
                            if (age >= 18) {
                              // Need to get the name first since p is a lambda parameter
                              return OpticPrograms.get(p, PlayerLenses.name())
                                  .flatMap(
                                      name ->
                                          OpticPrograms.set(
                                              p, PlayerLenses.name(), name + " (Adult)"));
                            } else {
                              return OpticPrograms.pure(p);
                            }
                          }));

      Player result5 = OpticInterpreters.direct().run(reusableProgram);
      System.out.println("Original: " + youngPlayer);
      System.out.println("Updated:  " + result5);

      // Log it too
      LoggingOpticInterpreter logger2 = OpticInterpreters.logging();
      logger2.run(reusableProgram);
      System.out.println("Operations:");
      logger2.getLog().forEach(log -> System.out.println("  " + log));
    } catch (Exception e) {
      System.err.println("ERROR in FreeDslExample:");
      e.printStackTrace();
      throw new RuntimeException("Example failed", e);
    }
  }

  // ========== Helper: Create a promotion program ==========

  /**
   * Creates a program that promotes a player if they meet criteria.
   *
   * @param player The player to promote
   * @return A program that may promote the player
   */
  private static Free<OpticOpKind.Witness, Player> promotePlayer(Player player) {
    Lens<Player, Integer> scoreLens = PlayerLenses.score();
    Lens<Player, Integer> ageLens = PlayerLenses.age();
    Lens<Player, String> nameLens = PlayerLenses.name();

    return OpticPrograms.get(player, scoreLens)
        .flatMap(
            score ->
                OpticPrograms.get(player, ageLens)
                    .flatMap(
                        age -> {
                          if (score > 100 && age >= 18) {
                            return OpticPrograms.set(player, nameLens, player.name() + " ⭐")
                                .flatMap(p -> OpticPrograms.modify(p, scoreLens, s -> s + 20));
                          } else {
                            return OpticPrograms.pure(player);
                          }
                        }));
  }
}
