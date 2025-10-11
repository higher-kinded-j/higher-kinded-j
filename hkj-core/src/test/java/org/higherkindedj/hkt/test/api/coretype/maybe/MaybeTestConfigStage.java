// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import java.util.function.Function;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <T> The value type
 * @param <S> The mapped type
 */
public final class MaybeTestConfigStage<T, S> {
  private final Class<?> contextClass;
  private final Maybe<T> justInstance;
  private final Maybe<T> nothingInstance;
  private final Function<T, S> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeGetters = true;
  private boolean includeOrElse = true;
  private boolean includeMap = true;
  private boolean includeFlatMap = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  MaybeTestConfigStage(
      Class<?> contextClass,
      Maybe<T> justInstance,
      Maybe<T> nothingInstance,
      Function<T, S> mapper) {
    this.contextClass = contextClass;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  public MaybeTestConfigStage<T, S> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipGetters() {
    this.includeGetters = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipOrElse() {
    this.includeOrElse = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipMap() {
    this.includeMap = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipFlatMap() {
    this.includeFlatMap = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public MaybeTestConfigStage<T, S> skipEdgeCases() {
    this.includeEdgeCases = false;
    return this;
  }

  /**
   * Enters validation configuration mode.
   *
   * <p>Progressive disclosure: Shows validation context configuration options.
   *
   * @return Validation stage for configuring error message contexts
   */
  public MaybeValidationStage<T, S> configureValidation() {
    return new MaybeValidationStage<>(this);
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public MaybeTestConfigStage<T, S> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyGetters() {
    disableAll();
    this.includeGetters = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyOrElse() {
    disableAll();
    this.includeOrElse = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyMap() {
    disableAll();
    this.includeMap = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyFlatMap() {
    disableAll();
    this.includeFlatMap = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public MaybeTestConfigStage<T, S> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  private void disableAll() {
    includeFactoryMethods = false;
    includeGetters = false;
    includeOrElse = false;
    includeMap = false;
    includeFlatMap = false;
    includeValidations = false;
    includeEdgeCases = false;
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
    MaybeTestExecutor<T, S> executor = buildExecutor();
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

  private MaybeTestExecutor<T, S> buildExecutor() {
    return new MaybeTestExecutor<>(
        contextClass,
        justInstance,
        nothingInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeOrElse,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        null);
  }

  MaybeTestExecutor<T, S> buildExecutorWithValidation(MaybeValidationStage<T, S> validationStage) {
    return new MaybeTestExecutor<>(
        contextClass,
        justInstance,
        nothingInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeOrElse,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
