// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.reader_t.ReaderT;

/**
 * Stage 3: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <F> The outer monad witness type
 * @param <R> The environment type
 * @param <A> The value type
 * @param <B> The mapped type
 */
public final class ReaderTTestConfigStage<F, R, A, B> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final ReaderT<F, R, A> readerTInstance;
  private final Function<A, B> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeRunnerMethods = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  ReaderTTestConfigStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      ReaderT<F, R, A> readerTInstance,
      Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.readerTInstance = readerTInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  public ReaderTTestConfigStage<F, R, A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public ReaderTTestConfigStage<F, R, A, B> skipRunnerMethods() {
    this.includeRunnerMethods = false;
    return this;
  }

  public ReaderTTestConfigStage<F, R, A, B> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public ReaderTTestConfigStage<F, R, A, B> skipEdgeCases() {
    this.includeEdgeCases = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public ReaderTTestConfigStage<F, R, A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public ReaderTTestConfigStage<F, R, A, B> onlyRunnerMethods() {
    disableAll();
    this.includeRunnerMethods = true;
    return this;
  }

  public ReaderTTestConfigStage<F, R, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public ReaderTTestConfigStage<F, R, A, B> onlyEdgeCases() {
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
  public ReaderTValidationStage<F, R, A, B> configureValidation() {
    return new ReaderTValidationStage<>(this);
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
    ReaderTTestExecutor<F, R, A, B> executor = buildExecutor();
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

  private ReaderTTestExecutor<F, R, A, B> buildExecutor() {
    return new ReaderTTestExecutor<>(
        contextClass,
        outerMonad,
        readerTInstance,
        mapper,
        includeFactoryMethods,
        includeRunnerMethods,
        includeValidations,
        includeEdgeCases);
  }

  ReaderTTestExecutor<F, R, A, B> buildExecutorWithValidation(
      ReaderTValidationStage<F, R, A, B> validationStage) {
    return new ReaderTTestExecutor<>(
        contextClass,
        outerMonad,
        readerTInstance,
        mapper,
        includeFactoryMethods,
        includeRunnerMethods,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
