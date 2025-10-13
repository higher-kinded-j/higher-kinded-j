// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import java.util.function.Function;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ValidatedTestConfigStage<E, A, B> {
  private final Class<?> contextClass;
  private final Validated<E, A> invalidInstance;
  private final Validated<E, A> validInstance;
  private final Function<A, B> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeGetters = true;
  private boolean includeFold = true;
  private boolean includeSideEffects = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  ValidatedTestConfigStage(
      Class<?> contextClass,
      Validated<E, A> invalidInstance,
      Validated<E, A> validInstance,
      Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.invalidInstance = invalidInstance;
    this.validInstance = validInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  public ValidatedTestConfigStage<E, A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipGetters() {
    this.includeGetters = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipFold() {
    this.includeFold = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipSideEffects() {
    this.includeSideEffects = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipMap() {
    this.includeMap = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> skipEdgeCases() {
    this.includeEdgeCases = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public ValidatedTestConfigStage<E, A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyGetters() {
    disableAll();
    this.includeGetters = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyFold() {
    disableAll();
    this.includeFold = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlySideEffects() {
    disableAll();
    this.includeSideEffects = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public ValidatedTestConfigStage<E, A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  private void disableAll() {
    includeFactoryMethods = false;
    includeGetters = false;
    includeFold = false;
    includeSideEffects = false;
    includeMap = false;
    includeFlatMap = false;
    includeValidations = false;
    includeEdgeCases = false;
  }

  // =============================================================================
  // Validation Configuration
  // =============================================================================

  /**
   * Enters validation configuration mode.
   *
   * <p>Progressive disclosure: Shows validation context configuration options.
   *
   * @return Validation stage for configuring error message contexts
   */
  public ValidatedValidationStage<E, A, B> configureValidation() {
    return new ValidatedValidationStage<>(this);
  }

  // =============================================================================
  // Execution Methods
  // =============================================================================

  /**
   * Executes all configured tests.
   *
   * <p>This is the most comprehensive test execution option.
   */
  public void testAll() {
    ValidatedTestExecutor<E, A, B> executor = buildExecutor();
    executor.executeAll();
  }

  /** Executes only core operation tests (no validations or edge cases). */
  public void testOperations() {
    includeValidations = false;
    includeEdgeCases = false;
    testAll();
  }

  /** Executes only validation tests. */
  public void testValidations() {
    onlyValidations();
    testAll();
  }

  /** Executes only edge case tests. */
  public void testEdgeCases() {
    onlyEdgeCases();
    testAll();
  }

  // =============================================================================
  // Internal Builder
  // =============================================================================

  private ValidatedTestExecutor<E, A, B> buildExecutor() {
    return new ValidatedTestExecutor<>(
        contextClass,
        invalidInstance,
        validInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeSideEffects,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases);
  }

  ValidatedTestExecutor<E, A, B> buildExecutorWithValidation(
      ValidatedValidationStage<E, A, B> validationStage) {
    return new ValidatedTestExecutor<>(
        contextClass,
        invalidInstance,
        validInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeSideEffects,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
