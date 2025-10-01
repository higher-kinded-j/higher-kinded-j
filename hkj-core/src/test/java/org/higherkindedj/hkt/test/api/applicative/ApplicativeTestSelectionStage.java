// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.applicative;

/**
 * Stage 6: Fine-grained test selection for Applicative.
 *
 * @param <F> The Applicative witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class ApplicativeTestSelectionStage<F, A, B> {
    private final ApplicativeOperationsStage<F, A, B> operationsStage;
    private final ApplicativeLawsStage<F, A, B> lawsStage;
    private final ApplicativeValidationStage<F, A, B> validationStage;

    private boolean includeOperations = true;
    private boolean includeValidations = true;
    private boolean includeExceptions = true;
    private boolean includeLaws = true;

    ApplicativeTestSelectionStage(
            ApplicativeOperationsStage<F, A, B> operationsStage,
            ApplicativeLawsStage<F, A, B> lawsStage,
            ApplicativeValidationStage<F, A, B> validationStage) {
        this.operationsStage = operationsStage;
        this.lawsStage = lawsStage;
        this.validationStage = validationStage;
    }

  public ApplicativeTestSelectionStage<F, A, B> skipOperations() {
    includeOperations = false;
    return this;
  }

  public ApplicativeTestSelectionStage<F, A, B> skipValidations() {
    includeValidations = false;
    return this;
  }

  public ApplicativeTestSelectionStage<F, A, B> skipExceptions() {
    includeExceptions = false;
    return this;
  }

  public ApplicativeTestSelectionStage<F, A, B> skipLaws() {
    includeLaws = false;
    return this;
  }

  public ApplicativeTestSelectionStage<F, A, B> onlyOperations() {
    includeOperations = true;
    includeValidations = false;
    includeExceptions = false;
    includeLaws = false;
    return this;
  }

  public ApplicativeTestSelectionStage<F, A, B> onlyValidations() {
    includeOperations = false;
    includeValidations = true;
    includeExceptions = false;
    includeLaws = false;
    return this;
  }

  public ApplicativeTestSelectionStage<F, A, B> onlyExceptions() {
    includeOperations = false;
    includeValidations = false;
    includeExceptions = true;
    includeLaws = false;
    return this;
  }

  public ApplicativeTestSelectionStage<F, A, B> onlyLaws() {
    includeOperations = false;
    includeValidations = false;
    includeExceptions = false;
    includeLaws = true;
    return this;
  }

  public ApplicativeOperationsStage<F, A, B> and() {
    return operationsStage;
  }

  public void test() {
    ApplicativeTestExecutor<F, A, B> executor = operationsStage.build(lawsStage);
    executor.setTestSelection(
        includeOperations, includeValidations, includeExceptions, includeLaws);
    executor.executeSelected();
  }
}
