// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 8: Fine-grained test selection for Selective.
 *
 * <p>Progressive disclosure: Shows test selection options when fine-grained control is needed.
 *
 * @param <F> The Selective witness type
 * @param <A> The input type
 * @param <B> The output type
 * @param <C> The result type
 */
public final class SelectiveTestSelectionStage<F extends WitnessArity<TypeArity.Unary>, A, B, C> {
  private final SelectiveHandlerStage<F, A, B, C> handlerStage;
  private final SelectiveLawsStage<F, A, B, C> lawsStage;
  private final SelectiveValidationStage<F, A, B, C> validationStage;

  private boolean includeOperations = true;
  private boolean includeValidations = true;
  private boolean includeExceptions = true;
  private boolean includeLaws = true;

  SelectiveTestSelectionStage(
      SelectiveHandlerStage<F, A, B, C> handlerStage,
      SelectiveLawsStage<F, A, B, C> lawsStage,
      SelectiveValidationStage<F, A, B, C> validationStage) {
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
  public SelectiveTestSelectionStage<F, A, B, C> skipOperations() {
    includeOperations = false;
    return this;
  }

  /**
   * Skips validation tests.
   *
   * @return This stage for further selection or execution
   */
  public SelectiveTestSelectionStage<F, A, B, C> skipValidations() {
    includeValidations = false;
    return this;
  }

  /**
   * Skips exception propagation tests.
   *
   * @return This stage for further selection or execution
   */
  public SelectiveTestSelectionStage<F, A, B, C> skipExceptions() {
    includeExceptions = false;
    return this;
  }

  /**
   * Skips law tests.
   *
   * @return This stage for further selection or execution
   */
  public SelectiveTestSelectionStage<F, A, B, C> skipLaws() {
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
  public SelectiveTestSelectionStage<F, A, B, C> onlyOperations() {
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
  public SelectiveTestSelectionStage<F, A, B, C> onlyValidations() {
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
  public SelectiveTestSelectionStage<F, A, B, C> onlyExceptions() {
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
  public SelectiveTestSelectionStage<F, A, B, C> onlyLaws() {
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
  public SelectiveHandlerStage<F, A, B, C> and() {
    return handlerStage;
  }

  /** Executes the selected tests. */
  public void test() {
    SelectiveTestExecutor<F, A, B, C> executor = handlerStage.build(lawsStage, validationStage);

    executor.setTestSelection(
        includeOperations, includeValidations, includeExceptions, includeLaws);

    executor.executeSelected();
  }
}
