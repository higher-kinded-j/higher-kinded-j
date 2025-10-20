// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import java.util.function.Function;
import org.higherkindedj.hkt.id.Id;

/**
 * Stage 3: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class IdTestConfigStage<A, B> {
  private final Class<?> contextClass;
  private final Id<A> instance;
  private final Function<A, B> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeGetters = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  IdTestConfigStage(Class<?> contextClass, Id<A> instance, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.instance = instance;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  public IdTestConfigStage<A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public IdTestConfigStage<A, B> skipGetters() {
    this.includeGetters = false;
    return this;
  }

  public IdTestConfigStage<A, B> skipMap() {
    this.includeMap = false;
    return this;
  }

  public IdTestConfigStage<A, B> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  public IdTestConfigStage<A, B> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public IdTestConfigStage<A, B> skipEdgeCases() {
    this.includeEdgeCases = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public IdTestConfigStage<A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public IdTestConfigStage<A, B> onlyGetters() {
    disableAll();
    this.includeGetters = true;
    return this;
  }

  public IdTestConfigStage<A, B> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public IdTestConfigStage<A, B> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  public IdTestConfigStage<A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public IdTestConfigStage<A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  private void disableAll() {
    includeFactoryMethods = false;
    includeGetters = false;
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
  public IdValidationStage<A, B> configureValidation() {
    return new IdValidationStage<>(this);
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
    IdTestExecutor<A, B> executor = buildExecutor();
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

  private IdTestExecutor<A, B> buildExecutor() {
    return new IdTestExecutor<>(
        contextClass,
        instance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases);
  }

  IdTestExecutor<A, B> buildExecutorWithValidation(IdValidationStage<A, B> validationStage) {
    return new IdTestExecutor<>(
        contextClass,
        instance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
