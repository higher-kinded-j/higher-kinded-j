// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 04: Combining Effects with EitherF
 *
 * <p>When a program uses multiple effect algebras, they are composed via {@code EitherF}. Each
 * algebra gets its own interpreter, and {@code Interpreters.combine} merges them into a single
 * interpreter for the composed type.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@code Interpreters.combine(interpA, interpB)}: merges two interpreters
 *   <li>The composed type is {@code EitherFKind.Witness<A, B>}
 *   <li>Each interpreter handles its own operations; combine dispatches via Left/Right
 * </ul>
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial04_CombiningEffects {

  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /** Identity interpreter that passes through Id values unchanged. */
  private static final Natural<IdKind.Witness, IdKind.Witness> ID_INTERP =
      new Natural<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return fa;
        }
      };

  /**
   * Exercise 1: Combining two interpreters
   *
   * <p>{@code Interpreters.combine} takes two interpreters and produces one that handles the
   * composed type.
   *
   * <p>Task: Combine two identity interpreters
   */
  @Test
  void exercise1_combineTwo() {
    // TODO: Use Interpreters.combine to merge two identity interpreters
    // Hint: Interpreters.combine(ID_INTERP, ID_INTERP)
    Natural<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>, IdKind.Witness> combined =
        answerRequired();

    assertThat(combined).isNotNull();
  }

  /**
   * Exercise 2: Combining three interpreters
   *
   * <p>For three effects, the composed type is {@code EitherF<A, EitherF<B, C>>}. {@code
   * Interpreters.combine} has an overload for three interpreters.
   *
   * <p>Task: Combine three identity interpreters
   */
  @Test
  void exercise2_combineThree() {
    // TODO: Use Interpreters.combine with three interpreters
    var combined = answerRequired();

    assertThat(combined).isNotNull();
  }

  /**
   * Exercise 3: Interpreting a composed program
   *
   * <p>A pure program works with any composed type because {@code Free.pure} does not embed any
   * effect instructions.
   *
   * <p>Task: Create a pure Free program and interpret it with the combined interpreter
   */
  @Test
  void exercise3_interpretComposed() {
    var combined = Interpreters.combine(ID_INTERP, ID_INTERP);

    // TODO: Create a pure Free program with the composed witness type and interpret it
    // Hint: Free.<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>, String>pure("combined")
    Free<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>, String> program = answerRequired();

    Id<String> result = IdKindHelper.ID.narrow(program.foldMap(combined, IdMonad.instance()));

    assertThat(result.value()).isEqualTo("combined");
  }
}
