// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.functor;

import java.util.function.BiPredicate;
import org.higherkindedj.hkt.Kind;

/**
 * Stage 5: Law testing configuration for Functor.
 *
 * <p>Progressive disclosure: Shows law testing options after operations configured.
 *
 * @param <F> The Functor witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class FunctorLawsStage<F, A, B> {
  private final FunctorTestConfigStage<F, A, B> configStage;
  final BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

  FunctorLawsStage(
      FunctorTestConfigStage<F, A, B> configStage,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {
    this.configStage = configStage;
    this.equalityChecker = equalityChecker;
  }

  /**
   * Enters validation configuration mode.
   *
   * <p>Progressive disclosure: Shows validation context configuration options.
   *
   * @return Validation stage for configuring error message contexts
   */
  public FunctorValidationStage<F, A, B> configureValidation() {
    return new FunctorValidationStage<>(configStage, this);
  }

  /**
   * Enters test selection mode for fine-grained control.
   *
   * <p>Progressive disclosure: Shows test selection options.
   *
   * @return Stage for selecting which tests to run
   */
  public FunctorTestSelectionStage<F, A, B> selectTests() {
    return new FunctorTestSelectionStage<>(configStage, this, null);
  }

  /** Executes all tests including laws. */
  public void testAll() {
    buildExecutor().executeAll();
  }

  /** Executes operation and law tests (skips validations). */
  public void testOperationsAndLaws() {
    buildExecutor().executeOperationsAndLaws();
  }

  /** Executes only law tests. */
  public void testLaws() {
    buildExecutor().executeLaws();
  }

  private FunctorTestExecutor<F, A, B> buildExecutor() {
    return new FunctorTestExecutor<>(
        configStage.contextClass,
        configStage.functor,
        configStage.validKind,
        configStage.mapper,
        configStage.secondMapper,
        equalityChecker,
        configStage.includeOperations,
        configStage.includeValidations,
        configStage.includeExceptions,
        configStage.includeLaws,
        null);
  }
}
