// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monaderror;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 6: Law testing configuration for MonadError.
 *
 * <p>Progressive disclosure: Shows law testing options after core operations configured.
 *
 * @param <F> The MonadError witness type
 * @param <E> The error type
 * @param <A> The input type
 * @param <B> The mapped type
 */
public final class MonadErrorLawsStage<F extends WitnessArity<TypeArity.Unary>, E, A, B> {
  private final MonadErrorHandlerStage<F, E, A, B> handlerStage;
  private final A testValue;
  private final Function<A, Kind<F, B>> testFunction;
  private final Function<B, Kind<F, B>> chainFunction;
  private final BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

  MonadErrorLawsStage(
      MonadErrorHandlerStage<F, E, A, B> handlerStage,
      A testValue,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    this.handlerStage = handlerStage;
    this.testValue = testValue;
    this.testFunction = testFunction;
    this.chainFunction = chainFunction;
    this.equalityChecker = equalityChecker;
  }

  // =============================================================================
  // Optional Validation Configuration
  // =============================================================================

  /**
   * Enters validation configuration mode.
   *
   * <p>Progressive disclosure: Shows validation context configuration options.
   *
   * @return Validation stage for configuring error message contexts
   */
  public MonadErrorValidationStage<F, E, A, B> configureValidation() {
    return new MonadErrorValidationStage<>(handlerStage, this);
  }

  // =============================================================================
  // Test Selection
  // =============================================================================

  /**
   * Enters test selection mode for fine-grained control.
   *
   * <p>Progressive disclosure: Shows test selection options.
   *
   * @return Stage for selecting which tests to run
   */
  public MonadErrorTestSelectionStage<F, E, A, B> selectTests() {
    return new MonadErrorTestSelectionStage<>(handlerStage, this, null);
  }

  // =============================================================================
  // Quick Execution (With Laws)
  // =============================================================================

  /**
   * Executes all tests including laws.
   *
   * <p>This is the comprehensive testing option that includes:
   *
   * <ul>
   *   <li>Operation tests
   *   <li>Validation tests
   *   <li>Exception propagation tests
   *   <li>Law tests (identity, associativity)
   * </ul>
   */
  public void testAll() {
    handlerStage.build(this, null).executeAll();
  }

  /** Executes operation and law tests (skips validations). */
  public void testOperationsAndLaws() {
    handlerStage.build(this, null).executeOperationsAndLaws();
  }

  /** Executes only law tests. */
  public void testLaws() {
    handlerStage.build(this, null).executeLaws();
  }

  // =============================================================================
  // Package-private getters for other stages
  // =============================================================================

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
