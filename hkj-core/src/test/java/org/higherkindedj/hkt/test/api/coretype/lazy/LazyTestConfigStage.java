// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.lazy;

import java.util.function.Function;
import org.higherkindedj.hkt.lazy.Lazy;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class LazyTestConfigStage<A, B> {
  private final Class<?> contextClass;
  private final Lazy<A> deferredInstance;
  private final Lazy<A> nowInstance;
  private final Function<A, B> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeForce = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;
  private boolean includeMemoisation = true;
  private boolean includeConcurrency = true;

  LazyTestConfigStage(
      Class<?> contextClass, Lazy<A> deferredInstance, Lazy<A> nowInstance, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.deferredInstance = deferredInstance;
    this.nowInstance = nowInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  public LazyTestConfigStage<A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public LazyTestConfigStage<A, B> skipForce() {
    this.includeForce = false;
    return this;
  }

  public LazyTestConfigStage<A, B> skipMap() {
    this.includeMap = false;
    return this;
  }

  public LazyTestConfigStage<A, B> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  public LazyTestConfigStage<A, B> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public LazyTestConfigStage<A, B> skipEdgeCases() {
    this.includeEdgeCases = false;
    return this;
  }

  public LazyTestConfigStage<A, B> skipMemoisation() {
    this.includeMemoisation = false;
    return this;
  }

  public LazyTestConfigStage<A, B> skipConcurrency() {
    this.includeConcurrency = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public LazyTestConfigStage<A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public LazyTestConfigStage<A, B> onlyForce() {
    disableAll();
    this.includeForce = true;
    return this;
  }

  public LazyTestConfigStage<A, B> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public LazyTestConfigStage<A, B> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  public LazyTestConfigStage<A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public LazyTestConfigStage<A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  public LazyTestConfigStage<A, B> onlyMemoisation() {
    disableAll();
    this.includeMemoisation = true;
    return this;
  }

  public LazyTestConfigStage<A, B> onlyConcurrency() {
    disableAll();
    this.includeConcurrency = true;
    return this;
  }

  private void disableAll() {
    includeFactoryMethods = false;
    includeForce = false;
    includeMap = false;
    includeFlatMap = false;
    includeValidations = false;
    includeEdgeCases = false;
    includeMemoisation = false;
    includeConcurrency = false;
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
  public LazyValidationStage<A, B> configureValidation() {
    return new LazyValidationStage<>(this);
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
    LazyTestExecutor<A, B> executor = buildExecutor();
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

  private LazyTestExecutor<A, B> buildExecutor() {
    return new LazyTestExecutor<>(
        contextClass,
        deferredInstance,
        nowInstance,
        mapper,
        includeFactoryMethods,
        includeForce,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        includeMemoisation,
        includeConcurrency);
  }

  LazyTestExecutor<A, B> buildExecutorWithValidation(LazyValidationStage<A, B> validationStage) {
    return new LazyTestExecutor<>(
        contextClass,
        deferredInstance,
        nowInstance,
        mapper,
        includeFactoryMethods,
        includeForce,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        includeMemoisation,
        includeConcurrency,
        validationStage);
  }
}
