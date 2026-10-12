// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import org.higherkindedj.example.optics.fluent.generated.FootballerLenses;
import org.higherkindedj.example.optics.fluent.model.Footballer;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;

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

  public static void main(String[] args) {
    try {
      Footballer player = new Footballer("Alice", 17, 85);

      System.out.println("=== Free Monad DSL Examples ===\n");

      // ========== Simple Program ==========
      System.out.println("--- Simple Program ---");

      // Build a program that increments age and score
      Free<OpticOpKind.Witness, Footballer> simpleProgram =
          OpticPrograms.modify(player, FootballerLenses.age(), age -> age + 1)
              .flatMap(
                  p1 -> OpticPrograms.modify(p1, FootballerLenses.score(), score -> score + 10));

      // Execute with direct interpreter
      Footballer result1 = OpticInterpreters.direct().run(simpleProgram);
      System.out.println("Original: " + player);
      System.out.println("Updated:  " + result1);

      System.out.println();

      // ========== Conditional Program ==========
      System.out.println("--- Conditional Program ---");

      // Build a program with conditional logic
      Free<OpticOpKind.Witness, Footballer> conditionalProgram =
          OpticPrograms.get(player, FootballerLenses.age())
              .flatMap(
                  age -> {
                    if (age < 18) {
                      System.out.println("  Player is a minor, no changes");
                      return OpticPrograms.pure(player);
                    } else {
                      System.out.println("  Player is an adult, adding bonus");
                      return OpticPrograms.modify(
                          player, FootballerLenses.score(), score -> score + 20);
                    }
                  });

      Footballer result2 = OpticInterpreters.direct().run(conditionalProgram);
      System.out.println("Result: " + result2);

      System.out.println();

      // ========== Complex Workflow ==========
      System.out.println("--- Complex Workflow ---");

      // Build a multi-step program that:
      // 1. Checks age
      // 2. If >= 18, increments age and adds bonus
      // 3. If score > 100, gives extra reward
      Footballer adult = new Footballer("Bob", 25, 95);

      Free<OpticOpKind.Witness, Footballer> complexProgram =
          OpticPrograms.get(adult, FootballerLenses.age())
              .flatMap(
                  age -> {
                    if (age >= 18) {
                      return OpticPrograms.modify(adult, FootballerLenses.age(), a -> a + 1)
                          .flatMap(
                              p1 ->
                                  OpticPrograms.modify(
                                          p1, FootballerLenses.score(), score -> score + 15)
                                      .flatMap(
                                          p2 ->
                                              OpticPrograms.get(p2, FootballerLenses.score())
                                                  .flatMap(
                                                      score -> {
                                                        if (score > 100) {
                                                          return OpticPrograms.modify(
                                                              p2,
                                                              FootballerLenses.score(),
                                                              s -> s + 5);
                                                        } else {
                                                          return OpticPrograms.pure(p2);
                                                        }
                                                      })));
                    } else {
                      return OpticPrograms.pure(adult);
                    }
                  });

      Footballer result3 = OpticInterpreters.direct().run(complexProgram);
      System.out.println("Original: " + adult);
      System.out.println("Updated:  " + result3);

      System.out.println();

      // ========== Logging Interpreter ==========
      System.out.println("--- Logging Interpreter ---");

      // Execute with logging to see what happened
      LoggingOpticInterpreter logger = OpticInterpreters.logging();
      Footballer result4 = logger.run(complexProgram);

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
      Footballer youngPlayer = new Footballer("Charlie", 16, 75);

      Free<OpticOpKind.Witness, Footballer> agePlayer =
          OpticPrograms.modify(youngPlayer, FootballerLenses.age(), age -> age + 1);

      Free<OpticOpKind.Witness, Footballer> addBonus =
          agePlayer.flatMap(
              p -> OpticPrograms.modify(p, FootballerLenses.score(), score -> score + 10));

      // Compose them
      Free<OpticOpKind.Witness, Footballer> reusableProgram =
          addBonus.flatMap(
              p ->
                  OpticPrograms.get(p, FootballerLenses.age())
                      .flatMap(
                          age -> {
                            if (age >= 18) {
                              // Need to get the name first since p is a lambda parameter
                              return OpticPrograms.get(p, FootballerLenses.name())
                                  .flatMap(
                                      name ->
                                          OpticPrograms.set(
                                              p, FootballerLenses.name(), name + " (Adult)"));
                            } else {
                              return OpticPrograms.pure(p);
                            }
                          }));

      Footballer result5 = OpticInterpreters.direct().run(reusableProgram);
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
  private static Free<OpticOpKind.Witness, Footballer> promotePlayer(Footballer player) {
    Lens<Footballer, Integer> scoreLens = FootballerLenses.score();
    Lens<Footballer, Integer> ageLens = FootballerLenses.age();
    Lens<Footballer, String> nameLens = FootballerLenses.name();

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
