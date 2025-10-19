// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.eithert;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either_t.EitherT;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <F> The outer monad witness type
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The mapped type
 */
public final class EitherTTestConfigStage<F, L, R, S> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final EitherT<F, L, R> leftInstance;
  private final EitherT<F, L, R> rightInstance;
  private final Function<R, S> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeValueAccessor = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  EitherTTestConfigStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      EitherT<F, L, R> leftInstance,
      EitherT<F, L, R> rightInstance,
      Function<R, S> mapper) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  public EitherTTestConfigStage<F, L, R, S> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public EitherTTestConfigStage<F, L, R, S> skipValueAccessor() {
    this.includeValueAccessor = false;
    return this;
  }

  public EitherTTestConfigStage<F, L, R, S> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public EitherTTestConfigStage<F, L, R, S> skipEdgeCases() {
    this.includeEdgeCases = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public EitherTTestConfigStage<F, L, R, S> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public EitherTTestConfigStage<F, L, R, S> onlyValueAccessor() {
    disableAll();
    this.includeValueAccessor = true;
    return this;
  }

  public EitherTTestConfigStage<F, L, R, S> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public EitherTTestConfigStage<F, L, R, S> onlyEdgeCases() {
    disableAll();
    this.includeEdgeCases = true;
    return this;
  }

  private void disableAll() {
    includeFactoryMethods = false;
    includeValueAccessor = false;
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
  public EitherTValidationStage<F, L, R, S> configureValidation() {
    return new EitherTValidationStage<>(this);
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
    EitherTTestExecutor<F, L, R, S> executor = buildExecutor();
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

  private EitherTTestExecutor<F, L, R, S> buildExecutor() {
    return new EitherTTestExecutor<>(
        contextClass,
        outerMonad,
        leftInstance,
        rightInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases);
  }

  EitherTTestExecutor<F, L, R, S> buildExecutorWithValidation(
      EitherTValidationStage<F, L, R, S> validationStage) {
    return new EitherTTestExecutor<>(
        contextClass,
        outerMonad,
        leftInstance,
        rightInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
