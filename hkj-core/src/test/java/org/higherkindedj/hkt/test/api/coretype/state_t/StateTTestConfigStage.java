// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.state_t.StateT;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <S> The state type
 * @param <F> The outer monad witness type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class StateTTestConfigStage<S, F, A, B> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final StateT<S, F, A> firstInstance;
  private final StateT<S, F, A> secondInstance;
  private final Function<A, B> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeRunnerMethods = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  StateTTestConfigStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      StateT<S, F, A> firstInstance,
      StateT<S, F, A> secondInstance,
      Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.firstInstance = firstInstance;
    this.secondInstance = secondInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  public StateTTestConfigStage<S, F, A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public StateTTestConfigStage<S, F, A, B> skipRunnerMethods() {
    this.includeRunnerMethods = false;
    return this;
  }

  public StateTTestConfigStage<S, F, A, B> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public StateTTestConfigStage<S, F, A, B> skipEdgeCases() {
    this.includeEdgeCases = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public StateTTestConfigStage<S, F, A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public StateTTestConfigStage<S, F, A, B> onlyRunnerMethods() {
    disableAll();
    this.includeRunnerMethods = true;
    return this;
  }

  public StateTTestConfigStage<S, F, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public StateTTestConfigStage<S, F, A, B> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  private void disableAll() {
    includeFactoryMethods = false;
    includeRunnerMethods = false;
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
  public StateTValidationStage<S, F, A, B> configureValidation() {
    return new StateTValidationStage<>(this);
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
    StateTTestExecutor<S, F, A, B> executor = buildExecutor();
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

  private StateTTestExecutor<S, F, A, B> buildExecutor() {
    return new StateTTestExecutor<>(
        contextClass,
        outerMonad,
        firstInstance,
        secondInstance,
        mapper,
        includeFactoryMethods,
        includeRunnerMethods,
        includeValidations,
        includeEdgeCases);
  }

  StateTTestExecutor<S, F, A, B> buildExecutorWithValidation(
      StateTValidationStage<S, F, A, B> validationStage) {
    return new StateTTestExecutor<>(
        contextClass,
        outerMonad,
        firstInstance,
        secondInstance,
        mapper,
        includeFactoryMethods,
        includeRunnerMethods,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
