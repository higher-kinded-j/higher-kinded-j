// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 03: Error Recovery in Free Programs
 *
 * <p>Free programs can include error recovery strategies using {@code handleError}. The recovery is
 * part of the program description: interpreters that support error handling will use it; those that
 * do not will silently ignore it.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@code handleError(Class, Function)}: typed recovery for specific error classes
 *   <li>{@code handleError} is structural: it wraps a sub-program with a recovery strategy
 *   <li>Recovery is a hint: the interpreter decides whether errors can occur
 *   <li>{@code ProgramAnalyser} can count recovery points without executing the program
 * </ul>
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial03_ErrorRecovery {

  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  private static final Natural<IdKind.Witness, IdKind.Witness> IDENTITY =
      new Natural<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return fa;
        }
      };

  /**
   * Exercise 1: Basic error recovery
   *
   * <p>{@code handleError} wraps a program with a recovery function. If interpretation encounters
   * an error of the specified type, the recovery function is called instead.
   *
   * <p>Task: Add error recovery that returns "recovered" on any Throwable
   */
  @Test
  void exercise1_basicRecovery() {
    Free<IdKind.Witness, String> program = Free.pure("success");

    // TODO: Add error recovery using handleError(Throwable.class, ...)
    Free<IdKind.Witness, String> safe = answerRequired();

    Id<String> result = IdKindHelper.ID.narrow(safe.foldMap(IDENTITY, IdMonad.instance()));

    // With Id monad (no errors), the original value passes through
    assertThat(result.value()).isEqualTo("success");
  }

  /**
   * Exercise 2: Nested recovery
   *
   * <p>Multiple handleError calls can be nested. Inner recovery takes precedence.
   *
   * <p>Task: Add two levels of error recovery
   */
  @Test
  void exercise2_nestedRecovery() {
    Free<IdKind.Witness, String> program = Free.pure("data");

    // TODO: Add inner recovery, then outer recovery
    // Hint: program.handleError(...).handleError(...)
    Free<IdKind.Witness, String> doubleRecovery = answerRequired();

    // Verify both recovery points are visible to the analyser
    var analysis = ProgramAnalyser.analyse(doubleRecovery);
    assertThat(analysis.recoveryPoints()).isEqualTo(2);
  }

  /**
   * Exercise 3: Recovery with map
   *
   * <p>Recovery can be combined with map to transform the result after recovery.
   *
   * <p>Task: Create a program that maps the result to uppercase, with recovery
   */
  @Test
  void exercise3_recoveryWithMap() {
    Free<IdKind.Witness, String> program = Free.pure("hello");

    // TODO: Add recovery, then map to uppercase
    // Hint: program.handleError(Throwable.class, ...).map(String::toUpperCase)
    Free<IdKind.Witness, String> result = answerRequired();

    Id<String> interpreted = IdKindHelper.ID.narrow(result.foldMap(IDENTITY, IdMonad.instance()));

    assertThat(interpreted.value()).isEqualTo("HELLO");
  }
}
