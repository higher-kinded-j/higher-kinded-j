// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monad;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;

/**
 * Stage 5: Law testing configuration for Monad.
 *
 * @param <F> The Monad witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class MonadLawsStage<F, A, B> {
  private final MonadOperationsStage<F, A, B> operationsStage;
  private final A testValue;
  private final Function<A, Kind<F, B>> testFunction;
  private final Function<B, Kind<F, B>> chainFunction;
  private final BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

  MonadLawsStage(
      MonadOperationsStage<F, A, B> operationsStage,
      A testValue,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    this.operationsStage = operationsStage;
    this.testValue = testValue;
    this.testFunction = testFunction;
    this.chainFunction = chainFunction;
    this.equalityChecker = equalityChecker;
  }

  public MonadValidationStage<F, A, B> configureValidation() {
    return new MonadValidationStage<>(operationsStage, this);
  }

  /**
   * Enters test selection mode.
   *
   * @return Stage for selecting which tests to run
   */
  public MonadTestSelectionStage<F, A, B> selectTests() {
    return new MonadTestSelectionStage<>(operationsStage, this, null);
  }

  /** Executes all tests including laws. */
  public void testAll() {
    operationsStage.build(this).executeAll();
  }

  /** Executes operation and law tests (skips validations). */
  public void testOperationsAndLaws() {
    operationsStage.build(this).executeOperationsAndLaws();
  }

  /** Executes only law tests. */
  public void testLaws() {
    operationsStage.build(this).executeLaws();
  }

  // Package-private getters
  A getTestValue() {
    return testValue;
  }

  Function<A, Kind<F, B>> getTestFunction() {
    return testFunction;
  }

  Function<B, Kind<F, B>> getChainFunction() {
    return chainFunction;
  }

  BiPredicate<Kind<F, ?>, Kind<F, ?>> getEqualityChecker() {
    return equalityChecker;
  }
}
