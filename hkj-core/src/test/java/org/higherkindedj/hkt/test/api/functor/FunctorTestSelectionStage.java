// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.functor;

/**
 * Stage 5: Fine-grained test selection.
 *
 * <p>Progressive disclosure: Shows test selection options when fine-grained control is needed.
 *
 * @param <F> The Functor witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class FunctorTestSelectionStage<F, A, B> {
  private final FunctorTestConfigStage<F, A, B> config;
    private final FunctorLawsStage<F, A, B> lawsStage;
    private final FunctorValidationStage<F, A, B> validationStage;

    FunctorTestSelectionStage(
            FunctorTestConfigStage<F, A, B> config,
            FunctorLawsStage<F, A, B> lawsStage,
            FunctorValidationStage<F, A, B> validationStage) {
        this.config = config;
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
  public FunctorTestSelectionStage<F, A, B> skipOperations() {
    config.includeOperations = false;
    return this;
  }

  /**
   * Skips validation tests.
   *
   * @return This stage for further selection or execution
   */
  public FunctorTestSelectionStage<F, A, B> skipValidations() {
    config.includeValidations = false;
    return this;
  }

  /**
   * Skips exception propagation tests.
   *
   * @return This stage for further selection or execution
   */
  public FunctorTestSelectionStage<F, A, B> skipExceptions() {
    config.includeExceptions = false;
    return this;
  }

  /**
   * Skips law tests.
   *
   * @return This stage for further selection or execution
   */
  public FunctorTestSelectionStage<F, A, B> skipLaws() {
    config.includeLaws = false;
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
  public FunctorTestSelectionStage<F, A, B> onlyOperations() {
    config.includeOperations = true;
    config.includeValidations = false;
    config.includeExceptions = false;
    config.includeLaws = false;
    return this;
  }

  /**
   * Runs only validation tests (disables all others).
   *
   * @return This stage for further selection or execution
   */
  public FunctorTestSelectionStage<F, A, B> onlyValidations() {
    config.includeOperations = false;
    config.includeValidations = true;
    config.includeExceptions = false;
    config.includeLaws = false;
    return this;
  }

  /**
   * Runs only exception propagation tests (disables all others).
   *
   * @return This stage for further selection or execution
   */
  public FunctorTestSelectionStage<F, A, B> onlyExceptions() {
    config.includeOperations = false;
    config.includeValidations = false;
    config.includeExceptions = true;
    config.includeLaws = false;
    return this;
  }

  /**
   * Runs only law tests (disables all others).
   *
   * @return This stage for further selection or execution
   */
  public FunctorTestSelectionStage<F, A, B> onlyLaws() {
    config.includeOperations = false;
    config.includeValidations = false;
    config.includeExceptions = false;
    config.includeLaws = true;
    return this;
  }

  // =============================================================================
  // Return to Config or Execute
  // =============================================================================

  /**
   * Returns to configuration stage for additional setup or execution.
   *
   * @return The configuration stage
   */
  public FunctorTestConfigStage<F, A, B> and() {
    return config;
  }

  /** Executes the selected tests. */
  public void test() {
    config.build().executeSelected();
  }
}
