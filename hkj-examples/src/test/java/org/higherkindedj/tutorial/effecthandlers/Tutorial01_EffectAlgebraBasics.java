// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 01: Effect Algebra Basics
 *
 * <p>Effect algebras are sealed interfaces that describe operations a program can perform. They
 * represent programs as data — the operations are described but not executed.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>Effect algebras are sealed interfaces with record operations
 *   <li>{@code @EffectAlgebra} generates HKT boilerplate automatically
 *   <li>{@code Free.liftF} lifts operations into the Free monad
 *   <li>{@code foldMap} interprets programs using a natural transformation
 *   <li>{@code ProgramAnalyser} inspects programs without executing them
 * </ul>
 *
 * <p>This tutorial uses the Payment Processing example from hkj-examples.
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial01_EffectAlgebraBasics {

  /** Helper method for incomplete exercises. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Part 1: Understanding Free Monad Programs
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Pure values
   *
   * <p>{@code Free.pure(value)} creates a program that immediately produces a value without
   * performing any effects.
   *
   * <p>Task: Create a Free program that produces the string "hello"
   */
  @Test
  void exercise1_pureProgram() {
    // TODO: Create a Free program that produces "hello"
    Free<IdKind.Witness, String> program = answerRequired();

    Id<String> result =
        IdKindHelper.ID.narrow(
            program.foldMap(
                new Natural<IdKind.Witness, IdKind.Witness>() {
                  @Override
                  public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
                    return fa;
                  }
                },
                IdMonad.instance()));

    assertThat(result.value()).isEqualTo("hello");
  }

  /**
   * Exercise 2: Chaining with flatMap
   *
   * <p>{@code flatMap} sequences two Free programs. The second program can depend on the result of
   * the first.
   *
   * <p>Task: Chain two pure values using flatMap
   */
  @Test
  void exercise2_flatMapChaining() {
    Free<IdKind.Witness, Integer> first = Free.pure(10);

    // TODO: Use flatMap to create a program that adds 5 to the first value
    // Hint: first.flatMap(n -> Free.pure(n + 5))
    Free<IdKind.Witness, Integer> chained = answerRequired();

    Id<Integer> result =
        IdKindHelper.ID.narrow(
            chained.foldMap(
                new Natural<IdKind.Witness, IdKind.Witness>() {
                  @Override
                  public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
                    return fa;
                  }
                },
                IdMonad.instance()));

    assertThat(result.value()).isEqualTo(15);
  }

  /**
   * Exercise 3: Mapping values
   *
   * <p>{@code map} transforms the result of a Free program without adding new effects.
   *
   * <p>Task: Transform a Free program's result using map
   */
  @Test
  void exercise3_mappingValues() {
    Free<IdKind.Witness, String> greeting = Free.pure("hello");

    // TODO: Use map to convert the string to uppercase
    // Hint: greeting.map(String::toUpperCase)
    Free<IdKind.Witness, String> upperGreeting = answerRequired();

    Id<String> result =
        IdKindHelper.ID.narrow(
            upperGreeting.foldMap(
                new Natural<IdKind.Witness, IdKind.Witness>() {
                  @Override
                  public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
                    return fa;
                  }
                },
                IdMonad.instance()));

    assertThat(result.value()).isEqualTo("HELLO");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Part 2: Program Analysis
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: Analysing programs
   *
   * <p>{@code ProgramAnalyser.analyse} traverses a Free program tree without executing it. It
   * counts instructions, recovery points, and parallel scopes.
   *
   * <p>Task: Analyse a pure program and verify the counts
   */
  @Test
  void exercise4_programAnalysis() {
    Free<IdKind.Witness, String> program = Free.pure("hello");

    // TODO: Use ProgramAnalyser.analyse to get the analysis
    ProgramAnalysis analysis = answerRequired();

    // A pure program has no instructions
    assertThat(analysis.suspendCount()).isEqualTo(0);
    assertThat(analysis.recoveryPoints()).isEqualTo(0);
    assertThat(analysis.hasOpaqueRegions()).isFalse();
  }

  /**
   * Exercise 5: Error recovery analysis
   *
   * <p>{@code handleError} adds recovery points that ProgramAnalyser can detect.
   *
   * <p>Task: Add error recovery to a program and verify the analysis counts it
   */
  @Test
  void exercise5_errorRecoveryAnalysis() {
    Free<IdKind.Witness, String> risky = Free.pure("data");

    // TODO: Add error recovery using handleError
    // Hint: risky.handleError(Throwable.class, e -> Free.pure("recovered"))
    Free<IdKind.Witness, String> safe = answerRequired();

    ProgramAnalysis analysis = ProgramAnalyser.analyse(safe);

    assertThat(analysis.recoveryPoints()).isEqualTo(1);
  }
}
