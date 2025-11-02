// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import static org.higherkindedj.hkt.util.validation.Operation.BRANCH;
import static org.higherkindedj.hkt.util.validation.Operation.IF_S;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.test.api.typeclass.internal.TestMethodRegistry;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Selective tests.
 *
 * <p>This class is package-private and not exposed to users. It coordinates test execution by
 * delegating to {@link TestMethodRegistry}.
 *
 * <p><b>Unit Usage:</b> This executor now uses {@link Unit} for {@code whenS} testing, reflecting
 * the updated signature that represents operations completing with no interesting result.
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
  private final Kind<F, Unit> validUnitEffect; // ✓ Changed from Kind<F, A> validEffect
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

  /**
   * Constructs a SelectiveTestExecutor.
   *
   * @param contextClass The context class for error messages
   * @param selective The Selective instance to test
   * @param validKind A valid Kind for general testing
   * @param validChoiceKind A valid Kind containing a Choice
   * @param validFunctionKind A valid Kind containing a function
   * @param validLeftHandler A valid Kind for left handler
   * @param validRightHandler A valid Kind for right handler
   * @param validCondition A valid Kind containing a boolean
   * @param validUnitEffect A valid Kind<F, Unit> for whenS testing (NOT Kind<F, A>)
   * @param validThenBranch A valid Kind for then branch
   * @param validElseBranch A valid Kind for else branch
   * @param lawsStage Optional laws testing configuration
   * @param validationStage Optional validation configuration
   */
  SelectiveTestExecutor(
      Class<?> contextClass,
      Selective<F> selective,
      Kind<F, A> validKind,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind,
      Kind<F, Function<A, C>> validLeftHandler,
      Kind<F, Function<B, C>> validRightHandler,
      Kind<F, Boolean> validCondition,
      Kind<F, Unit> validUnitEffect, // ✓ Changed parameter type
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
    this.validUnitEffect = validUnitEffect; // ✓ Store Unit effect
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
        validUnitEffect, // ✓ Pass Unit effect
        validThenBranch,
        validElseBranch);
  }

  void executeValidations() {
    Class<?> mapCtx =
        (validationStage != null && validationStage.getMapContext() != null)
            ? validationStage.getMapContext()
            : contextClass;
    Class<?> apCtx =
        (validationStage != null && validationStage.getApContext() != null)
            ? validationStage.getApContext()
            : contextClass;
    Class<?> selectCtx =
        (validationStage != null && validationStage.getSelectContext() != null)
            ? validationStage.getSelectContext()
            : contextClass;
    Class<?> branchCtx =
        (validationStage != null && validationStage.getBranchContext() != null)
            ? validationStage.getBranchContext()
            : contextClass;
    Class<?> whenSCtx =
        (validationStage != null && validationStage.getWhenSContext() != null)
            ? validationStage.getWhenSContext()
            : contextClass;
    Class<?> ifSCtx =
        (validationStage != null && validationStage.getIfSContext() != null)
            ? validationStage.getIfSContext()
            : contextClass;

    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Create dummy Kinds and Functions for inherited operation validation
    // Note: We use validThenBranch instead of validUnitEffect for Functor/Applicative tests
    // because those operations work with Kind<F, A>, not Kind<F, Unit>
    Kind<F, A> dummyKind = validThenBranch;
    Function<A, B> dummyMapper = a -> null;
    Kind<F, Function<A, B>> dummyFuncKind = validFunctionKind;

    // Test Functor operations (map) with map context
    builder.assertMapperNull(() -> selective.map(null, dummyKind), "f", mapCtx, Operation.MAP);
    builder.assertKindNull(() -> selective.map(dummyMapper, null), mapCtx, Operation.MAP);

    // Test Applicative operations (ap) with ap context
    builder.assertKindNull(() -> selective.ap(null, dummyKind), apCtx, Operation.AP, "function");
    builder.assertKindNull(
        () -> selective.ap(dummyFuncKind, null), apCtx, Operation.AP, "argument");

    // Test Selective-specific operations with their contexts
    builder.assertKindNull(
        () -> selective.select(null, validFunctionKind), selectCtx, Operation.SELECT, "choice");
    builder.assertKindNull(
        () -> selective.select(validChoiceKind, null), selectCtx, Operation.SELECT, "function");

    builder.assertKindNull(
        () -> selective.branch(null, validLeftHandler, validRightHandler),
        branchCtx,
        BRANCH,
        "choice");
    builder.assertKindNull(
        () -> selective.branch(validChoiceKind, null, validRightHandler),
        branchCtx,
        BRANCH,
        "leftHandler");
    builder.assertKindNull(
        () -> selective.branch(validChoiceKind, validLeftHandler, null),
        branchCtx,
        BRANCH,
        "rightHandler");

    // WhenS validations - now using validUnitEffect (Kind<F, Unit>)
    builder.assertKindNull(
        () -> selective.whenS(null, validUnitEffect), whenSCtx, Operation.WHEN_S, "condition");
    builder.assertKindNull(
        () -> selective.whenS(validCondition, null), whenSCtx, Operation.WHEN_S, "effect");

    // IfS validations - these still use Kind<F, A>
    builder.assertKindNull(
        () -> selective.ifS(null, validThenBranch, validElseBranch), ifSCtx, IF_S, "condition");
    builder.assertKindNull(
        () -> selective.ifS(validCondition, null, validElseBranch), ifSCtx, IF_S, "thenBranch");
    builder.assertKindNull(
        () -> selective.ifS(validCondition, validThenBranch, null), ifSCtx, IF_S, "elseBranch");

    builder.execute();
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
