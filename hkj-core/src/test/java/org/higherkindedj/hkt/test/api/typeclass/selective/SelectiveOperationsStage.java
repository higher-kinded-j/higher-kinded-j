// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 4: Configure additional operation parameters and optional law testing.
 *
 * <p>Progressive disclosure: All core parameters configured. Shows optional law testing, validation
 * configuration, and execution.
 *
 * @param <F> The Selective witness type
 * @param <A> The input type
 * @param <B> The output type
 * @param <C> The result type for branch operations
 */
public final class SelectiveOperationsStage<F extends WitnessArity<TypeArity.Unary>, A, B, C> {
  private final Class<?> contextClass;
  private final Selective<F> selective;
  private final Kind<F, A> validKind;
  private final Kind<F, Choice<A, B>> validChoiceKind;
  private final Kind<F, Function<A, B>> validFunctionKind;

  SelectiveOperationsStage(
      Class<?> contextClass,
      Selective<F> selective,
      Kind<F, A> validKind,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind) {

    this.contextClass = contextClass;
    this.selective = selective;
    this.validKind = validKind;
    this.validChoiceKind = validChoiceKind;
    this.validFunctionKind = validFunctionKind;
  }

  /**
   * Provides additional operation parameters for branch, whenS, and ifS.
   *
   * <p><b>Unit Usage:</b> The {@code validUnitEffect} parameter must be {@code Kind<F, Unit>} to
   * match the new {@code whenS} signature which uses Unit to represent operations that complete
   * with no interesting result.
   *
   * <p>Progressive disclosure: Next steps are optional law configuration, validation configuration,
   * test selection, or immediate execution.
   *
   * @param <C> The result type for branch operations
   * @param validLeftHandler Handler for Left branch
   * @param validRightHandler Handler for Right branch
   * @param validCondition Boolean condition for whenS/ifS
   * @param validUnitEffect Unit effect for whenS
   * @param validThenBranch Then branch for ifS
   * @param validElseBranch Else branch for ifS
   * @return Handler stage with execution options
   */
  public <C> SelectiveHandlerStage<F, A, B, C> withOperations(
      Kind<F, Function<A, C>> validLeftHandler,
      Kind<F, Function<B, C>> validRightHandler,
      Kind<F, Boolean> validCondition,
      Kind<F, Unit> validUnitEffect,
      Kind<F, A> validThenBranch,
      Kind<F, A> validElseBranch) {

    return new SelectiveHandlerStage<>(
        contextClass,
        selective,
        validKind,
        validChoiceKind,
        validFunctionKind,
        validLeftHandler,
        validRightHandler,
        validCondition,
        validUnitEffect,
        validThenBranch,
        validElseBranch);
  }
}
