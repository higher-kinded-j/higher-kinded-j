// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.applicative;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;

/**
 * Stage 5: Law testing configuration for Applicative.
 *
 * @param <F> The Applicative witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class ApplicativeLawsStage<F, A, B> {
  private final ApplicativeOperationsStage<F, A, B> operationsStage;
  private final A testValue;
  private final Function<A, B> testFunction;
  private final BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

  ApplicativeLawsStage(
      ApplicativeOperationsStage<F, A, B> operationsStage,
      A testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    this.operationsStage = operationsStage;
    this.testValue = testValue;
    this.testFunction = testFunction;
    this.equalityChecker = equalityChecker;
  }

    /**
     * Configures law testing parameters.
     *
     * @param testValue A test value for law testing
     * @param testFunction Test function for law verification
     * @param equalityChecker Equality checker for comparing Kind instances
     * @return Laws stage with further options
     */
    public ApplicativeLawsStage<F, A, B> withLawsTesting(
            A testValue,
            Function<A, B> testFunction,
            BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

        return new ApplicativeLawsStage<>(operationsStage, testValue, testFunction, equalityChecker); }
    /**
     * Enters test selection mode.
     *
     * @return Stage for selecting which tests to run
     */
    public ApplicativeTestSelectionStage<F, A, B> selectTests() {
        return new ApplicativeTestSelectionStage<>(operationsStage, this, null);
    }


    /**
     * Enters validation configuration mode.
     *
     * <p>Progressive disclosure: Shows validation context configuration options.
     *
     * @return Validation stage for configuring error message contexts
     */
    public ApplicativeValidationStage<F, A, B> configureValidation() {
        return new ApplicativeValidationStage<>(operationsStage, this);
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

  Function<A, B> getTestFunction() {
    return testFunction;
  }

  BiPredicate<Kind<F, ?>, Kind<F, ?>> getEqualityChecker() {
    return equalityChecker;
  }
}
