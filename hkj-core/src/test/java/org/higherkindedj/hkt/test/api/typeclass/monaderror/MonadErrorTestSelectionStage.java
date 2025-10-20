// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monaderror;

/**
 * Stage 8: Fine-grained test selection for MonadError.
 *
 * <p>Progressive disclosure: Shows test selection options when fine-grained control is needed.
 *
 * @param <F> The MonadError witness type
 * @param <E> The error type
 * @param <A> The input type
 * @param <B> The mapped type
 */
public final class MonadErrorTestSelectionStage<F, E, A, B> {
  private final MonadErrorHandlerStage<F, E, A, B> handlerStage;
  private final MonadErrorLawsStage<F, E, A, B> lawsStage;
  private final MonadErrorValidationStage<F, E, A, B> validationStage;

  private boolean includeOperations = true;
  private boolean includeValidations = true;
  private boolean includeExceptions = true;
  private boolean includeLaws = true;

  MonadErrorTestSelectionStage(
      MonadErrorHandlerStage<F, E, A, B> handlerStage,
      MonadErrorLawsStage<F, E, A, B> lawsStage,
      MonadErrorValidationStage<F, E, A, B> validationStage) {
    this.handlerStage = handlerStage;
    this.lawsStage = lawsStage;
    this.validationStage = validationStage;
  }

  // =============================================================================
  // Negative Selection (Skip Specific Tests)
  // =============================================================================

  /**
   * Skips operation tests.
   *
   * @return This stage for further selection or execution
   */
  public MonadErrorTestSelectionStage<F, E, A, B> skipOperations() {
    includeOperations = false;
    return this;
  }

  /**
   * Skips validation tests.
   *
   * @return This stage for further selection or execution
   */
  public MonadErrorTestSelectionStage<F, E, A, B> skipValidations() {
    includeValidations = false;
    return this;
  }

  /**
   * Skips exception propagation tests.
   *
   * @return This stage for further selection or execution
   */
  public MonadErrorTestSelectionStage<F, E, A, B> skipExceptions() {
    includeExceptions = false;
    return this;
  }

  /**
   * Skips law tests.
   *
   * @return This stage for further selection or execution
   */
  public MonadErrorTestSelectionStage<F, E, A, B> skipLaws() {
    includeLaws = false;
    return this;
  }

  // =============================================================================
  // Positive Selection (Run Only Specific Tests)
  // =============================================================================

  /**
   * Runs only operation tests (disables all others).
   *
   * @return This stage for further selection or execution
   */
  public MonadErrorTestSelectionStage<F, E, A, B> onlyOperations() {
    includeOperations = true;
    includeValidations = false;
    includeExceptions = false;
    includeLaws = false;
    return this;
  }

  /**
   * Runs only validation tests (disables all others).
   *
   * @return This stage for further selection or execution
   */
  public MonadErrorTestSelectionStage<F, E, A, B> onlyValidations() {
    includeOperations = false;
    includeValidations = true;
    includeExceptions = false;
    includeLaws = false;
    return this;
  }

  /**
   * Runs only exception propagation tests (disables all others).
   *
   * @return This stage for further selection or execution
   */
  public MonadErrorTestSelectionStage<F, E, A, B> onlyExceptions() {
    includeOperations = false;
    includeValidations = false;
    includeExceptions = true;
    includeLaws = false;
    return this;
  }

  /**
   * Runs only law tests (disables all others).
   *
   * <p>Note: Laws must be configured via {@code .withLawsTesting(...)} before using this.
   *
   * @return This stage for further selection or execution
   */
  public MonadErrorTestSelectionStage<F, E, A, B> onlyLaws() {
    includeOperations = false;
    includeValidations = false;
    includeExceptions = false;
    includeLaws = true;
    return this;
  }

  // =============================================================================
  // Return to Config or Execute
  // =============================================================================

  /**
   * Returns to handler stage for additional configuration.
   *
   * @return The handler stage
   */
  public MonadErrorHandlerStage<F, E, A, B> and() {
    return handlerStage;
  }

  /** Executes the selected tests. */
  public void test() {
    MonadErrorTestExecutor<F, E, A, B> executor = handlerStage.build(lawsStage, validationStage);

    executor.setTestSelection(
        includeOperations, includeValidations, includeExceptions, includeLaws);

    executor.executeSelected();
  }
}
