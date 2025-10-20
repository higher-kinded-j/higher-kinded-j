// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.trymonad;

import java.util.function.Function;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <T> The value type
 * @param <S> The mapped type
 */
public final class TryTestConfigStage<T, S> {
  private final Class<?> contextClass;
  private final Try<T> successInstance;
  private final Try<T> failureInstance;
  private final Function<T, S> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeGetters = true;
  private boolean includeFold = true;
  private boolean includeOrElse = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;
  private boolean includeRecover = true;
  private boolean includeToEither = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  TryTestConfigStage(
      Class<?> contextClass,
      Try<T> successInstance,
      Try<T> failureInstance,
      Function<T, S> mapper) {
    this.contextClass = contextClass;
    this.successInstance = successInstance;
    this.failureInstance = failureInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  public TryTestConfigStage<T, S> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipGetters() {
    this.includeGetters = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipFold() {
    this.includeFold = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipOrElse() {
    this.includeOrElse = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipMap() {
    this.includeMap = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipRecover() {
    this.includeRecover = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipToEither() {
    this.includeToEither = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public TryTestConfigStage<T, S> skipEdgeCases() {
    this.includeEdgeCases = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public TryTestConfigStage<T, S> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyGetters() {
    disableAll();
    this.includeGetters = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyFold() {
    disableAll();
    this.includeFold = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyOrElse() {
    disableAll();
    this.includeOrElse = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyRecover() {
    disableAll();
    this.includeRecover = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyToEither() {
    disableAll();
    this.includeToEither = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public TryTestConfigStage<T, S> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  private void disableAll() {
    includeFactoryMethods = false;
    includeGetters = false;
    includeFold = false;
    includeOrElse = false;
    includeMap = false;
    includeFlatMap = false;
    includeRecover = false;
    includeToEither = false;
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
  public TryValidationStage<T, S> configureValidation() {
    return new TryValidationStage<>(this);
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
    TryTestExecutor<T, S> executor = buildExecutor();
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

  private TryTestExecutor<T, S> buildExecutor() {
    return new TryTestExecutor<>(
        contextClass,
        successInstance,
        failureInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeOrElse,
        includeMap,
        includeFlatMap,
        includeRecover,
        includeToEither,
        includeValidations,
        includeEdgeCases);
  }

  TryTestExecutor<T, S> buildExecutorWithValidation(TryValidationStage<T, S> validationStage) {
    return new TryTestExecutor<>(
        contextClass,
        successInstance,
        failureInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeOrElse,
        includeMap,
        includeFlatMap,
        includeRecover,
        includeToEither,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
