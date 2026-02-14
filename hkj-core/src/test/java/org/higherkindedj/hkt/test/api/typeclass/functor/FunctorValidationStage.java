// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.functor;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage for configuring validation contexts in Functor tests.
 *
 * <p>Allows specifying which implementation class should be used in validation error messages for
 * each operation, supporting inheritance hierarchies.
 *
 * @param <F> The Functor witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class FunctorValidationStage<F extends WitnessArity<TypeArity.Unary>, A, B> {
  private final FunctorTestConfigStage<F, A, B> configStage;
  private final FunctorLawsStage<F, A, B> lawsStage;

  // Validation context classes
  private Class<?> mapContext;

  FunctorValidationStage(
      FunctorTestConfigStage<F, A, B> configStage, FunctorLawsStage<F, A, B> lawsStage) {
    this.configStage = configStage;
    this.lawsStage = lawsStage;
  }

  /**
   * Uses inheritance-based validation (recommended).
   *
   * <p>Specifies which implementation class is used for map operation's validation messages.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .useInheritanceValidation(EitherFunctor.class)
   * }</pre>
   *
   * @param mapContext Class context for map operations
   * @return This stage for further configuration or execution
   */
  public FunctorValidationStage<F, A, B> useInheritanceValidation(Class<?> mapContext) {
    this.mapContext = mapContext;
    return this;
  }

  /**
   * Uses default validation (no class context).
   *
   * <p>Error messages will not include specific class names.
   *
   * @return This stage for further configuration or execution
   */
  public FunctorValidationStage<F, A, B> useDefaultValidation() {
    this.mapContext = null;
    return this;
  }

  /**
   * Enters test selection mode for fine-grained control.
   *
   * <p>Progressive disclosure: Shows test selection options.
   *
   * @return Stage for selecting which tests to run
   */
  public FunctorTestSelectionStage<F, A, B> selectTests() {
    return new FunctorTestSelectionStage<>(configStage, lawsStage, this);
  }

  /**
   * Executes all configured tests.
   *
   * <p>If laws were configured, includes law tests. Otherwise, runs operations and validations
   * only.
   */
  public void testAll() {
    FunctorTestExecutor<F, A, B> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only operation and validation tests. */
  public void testOperationsAndValidations() {
    FunctorTestExecutor<F, A, B> executor = buildExecutor();
    executor.executeOperationsAndValidations();
  }

  /** Executes only validation tests. */
  public void testValidations() {
    FunctorTestExecutor<F, A, B> executor = buildExecutor();
    executor.executeValidations();
  }

  // Package-private getters
  Class<?> getMapContext() {
    return mapContext;
  }

  private FunctorTestExecutor<F, A, B> buildExecutor() {
    return new FunctorTestExecutor<>(
        configStage.contextClass,
        configStage.functor,
        configStage.validKind,
        configStage.mapper,
        configStage.secondMapper,
        lawsStage != null ? lawsStage.equalityChecker : null,
        configStage.includeOperations,
        configStage.includeValidations,
        configStage.includeExceptions,
        configStage.includeLaws,
        this);
  }
}
