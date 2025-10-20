// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.maybe_t.MaybeT;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and
 * execution.
 *
 * @param <F> The outer monad witness type
 * @param <A> The type of the value potentially held by the inner Maybe
 * @param <B> The mapped type
 */
public final class MaybeTTestConfigStage<F, A, B> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final MaybeT<F, A> justInstance;
  private final MaybeT<F, A> nothingInstance;
  private final Function<A, B> mapper;

  // Test selection flags
  private boolean includeFactoryMethods = true;
  private boolean includeValueAccessor = true;
  private boolean includeValidations = true;
  private boolean includeEdgeCases = true;

  MaybeTTestConfigStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      MaybeT<F, A> justInstance,
      MaybeT<F, A> nothingInstance,
      Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
    this.mapper = mapper;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  public MaybeTTestConfigStage<F, A, B> skipFactoryMethods() {
    this.includeFactoryMethods = false;
    return this;
  }

  public MaybeTTestConfigStage<F, A, B> skipValueAccessor() {
    this.includeValueAccessor = false;
    return this;
  }

  public MaybeTTestConfigStage<F, A, B> skipValidations() {
    this.includeValidations = false;
    return this;
  }

  public MaybeTTestConfigStage<F, A, B> skipEdgeCases() {
    this.includeEdgeCases = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  public MaybeTTestConfigStage<F, A, B> onlyFactoryMethods() {
    disableAll();
    this.includeFactoryMethods = true;
    return this;
  }

  public MaybeTTestConfigStage<F, A, B> onlyValueAccessor() {
    disableAll();
    this.includeValueAccessor = true;
    return this;
  }

  public MaybeTTestConfigStage<F, A, B> onlyValidations() {
    disableAll();
    this.includeValidations = true;
    return this;
  }

  public MaybeTTestConfigStage<F, A, B> onlyEdgeCases() {
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
  public MaybeTValidationStage<F, A, B> configureValidation() {
    return new MaybeTValidationStage<>(this);
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
    MaybeTTestExecutor<F, A, B> executor = buildExecutor();
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

  private MaybeTTestExecutor<F, A, B> buildExecutor() {
    return new MaybeTTestExecutor<>(
        contextClass,
        outerMonad,
        justInstance,
        nothingInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases);
  }

  MaybeTTestExecutor<F, A, B> buildExecutorWithValidation(
      MaybeTValidationStage<F, A, B> validationStage) {
    return new MaybeTTestExecutor<>(
        contextClass,
        outerMonad,
        justInstance,
        nothingInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
