// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;

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
public final class SelectiveOperationsStage<F, A, B, C> {
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
   * <p>Progressive disclosure: Next steps are optional law configuration, validation configuration,
   * test selection, or immediate execution.
   *
   * @param validLeftHandler Handler for Left branch
   * @param validRightHandler Handler for Right branch
   * @param validCondition Boolean condition for whenS/ifS
   * @param validEffect Effect for whenS
   * @param validThenBranch Then branch for ifS
   * @param validElseBranch Else branch for ifS
   * @return Handler stage with execution options
   */
  public SelectiveHandlerStage<F, A, B, C> withOperations(
      Kind<F, Function<A, C>> validLeftHandler,
      Kind<F, Function<B, C>> validRightHandler,
      Kind<F, Boolean> validCondition,
      Kind<F, A> validEffect,
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
        validEffect,
        validThenBranch,
        validElseBranch);
  }
}
