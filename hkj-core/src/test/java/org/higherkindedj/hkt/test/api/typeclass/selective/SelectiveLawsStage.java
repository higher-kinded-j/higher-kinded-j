// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 6: Law testing configuration for Selective.
 *
 * <p>Progressive disclosure: Shows law testing options after core operations configured.
 *
 * @param <F> The Selective witness type
 * @param <A> The input type
 * @param <B> The output type
 * @param <C> The result type
 */
public final class SelectiveLawsStage<F extends WitnessArity<TypeArity.Unary>, A, B, C> {
  private final SelectiveHandlerStage<F, A, B, C> handlerStage;
  private final B testValue;
  private final Function<A, B> testFunction;
  private final BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

  SelectiveLawsStage(
      SelectiveHandlerStage<F, A, B, C> handlerStage,
      B testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    this.handlerStage = handlerStage;
    this.testValue = testValue;
    this.testFunction = testFunction;
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
  public SelectiveValidationStage<F, A, B, C> configureValidation() {
    return new SelectiveValidationStage<>(handlerStage, this);
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
  public SelectiveTestSelectionStage<F, A, B, C> selectTests() {
    return new SelectiveTestSelectionStage<>(handlerStage, this, null);
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
   *   <li>Law tests (identity, distributivity)
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

  B getTestValue() {
    return testValue;
  }

  Function<A, B> getTestFunction() {
    return testFunction;
  }

  BiPredicate<Kind<F, ?>, Kind<F, ?>> getEqualityChecker() {
    return equalityChecker;
  }
}
