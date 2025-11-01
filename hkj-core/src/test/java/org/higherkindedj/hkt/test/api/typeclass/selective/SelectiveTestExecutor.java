// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.test.api.typeclass.internal.TestMethodRegistry;

/**
 * Internal executor for Selective tests.
 *
 * <p>This class is package-private and not exposed to users. It coordinates test execution by
 * delegating to {@link TestMethodRegistry}.
 *
 * @param <F> The Selective witness type
 * @param <A> The input type
 * @param <B> The output type
 * @param <C> The result type
 */
final class SelectiveTestExecutor<F, A, B, C> {
  private final Class<?> contextClass;
  private final Selective<F> selective;
  private final Kind<F, A> validKind;
  private final Kind<F, Choice<A, B>> validChoiceKind;
  private final Kind<F, Function<A, B>> validFunctionKind;
  private final Kind<F, Function<A, C>> validLeftHandler;
  private final Kind<F, Function<B, C>> validRightHandler;
  private final Kind<F, Boolean> validCondition;
  private final Kind<F, A> validEffect;
  private final Kind<F, A> validThenBranch;
  private final Kind<F, A> validElseBranch;

  // Optional law testing
  private final SelectiveLawsStage<F, A, B, C> lawsStage;

  // Optional validation configuration
  private final SelectiveValidationStage<F, A, B, C> validationStage;

  // Test selection flags
  private boolean includeOperations = true;
  private boolean includeValidations = true;
  private boolean includeExceptions = true;
  private boolean includeLaws = true;

  SelectiveTestExecutor(
      Class<?> contextClass,
      Selective<F> selective,
      Kind<F, A> validKind,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind,
      Kind<F, Function<A, C>> validLeftHandler,
      Kind<F, Function<B, C>> validRightHandler,
      Kind<F, Boolean> validCondition,
      Kind<F, A> validEffect,
      Kind<F, A> validThenBranch,
      Kind<F, A> validElseBranch,
      SelectiveLawsStage<F, A, B, C> lawsStage,
      SelectiveValidationStage<F, A, B, C> validationStage) {

    this.contextClass = contextClass;
    this.selective = selective;
    this.validKind = validKind;
    this.validChoiceKind = validChoiceKind;
    this.validFunctionKind = validFunctionKind;
    this.validLeftHandler = validLeftHandler;
    this.validRightHandler = validRightHandler;
    this.validCondition = validCondition;
    this.validEffect = validEffect;
    this.validThenBranch = validThenBranch;
    this.validElseBranch = validElseBranch;
    this.lawsStage = lawsStage;
    this.validationStage = validationStage;
  }

  void setTestSelection(boolean operations, boolean validations, boolean exceptions, boolean laws) {
    this.includeOperations = operations;
    this.includeValidations = validations;
    this.includeExceptions = exceptions;
    this.includeLaws = laws;
  }

  void executeAll() {
    if (includeOperations) executeOperations();
    if (includeValidations) executeValidations();
    if (includeExceptions) executeExceptions();
    if (includeLaws && lawsStage != null) executeLaws();
  }

  void executeOperationsAndValidations() {
    if (includeOperations) executeOperations();
    if (includeValidations) executeValidations();
  }

  void executeOperationsAndLaws() {
    if (includeOperations) executeOperations();
    if (includeLaws) executeLaws();
  }

  void executeOperations() {
    TestMethodRegistry.testSelectiveOperations(
        selective,
        validChoiceKind,
        validFunctionKind,
        validLeftHandler,
        validRightHandler,
        validCondition,
        validEffect,
        validThenBranch,
        validElseBranch);
  }

  void executeValidations() {
    if (validationStage != null) {
      // Custom validation configuration - would need FlexibleValidationConfig support
      // For now, use standard validation
      TestMethodRegistry.testSelectiveValidations(
          selective,
          contextClass,
          validChoiceKind,
          validFunctionKind,
          validLeftHandler,
          validRightHandler,
          validCondition,
          validEffect,
          validThenBranch,
          validElseBranch);
    } else {
      // Use standard validation
      TestMethodRegistry.testSelectiveValidations(
          selective,
          contextClass,
          validChoiceKind,
          validFunctionKind,
          validLeftHandler,
          validRightHandler,
          validCondition,
          validEffect,
          validThenBranch,
          validElseBranch);
    }
  }

  void executeExceptions() {
    TestMethodRegistry.testSelectiveExceptionPropagation(
        selective, validChoiceKind, validFunctionKind);
  }

  void executeLaws() {
    if (lawsStage == null) {
      throw new IllegalStateException(
          "Cannot execute laws without law configuration. "
              + "Use .withLawsTesting() to configure laws.");
    }

    TestMethodRegistry.testSelectiveLaws(
        selective,
        validChoiceKind,
        lawsStage.getTestValue(),
        lawsStage.getTestFunction(),
        lawsStage.getEqualityChecker());
  }

  void executeSelected() {
    executeAll();
  }
}
