// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;

/**
 * Stage 5: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows optional law testing,
 * validation configuration, and execution.
 *
 * @param <F> The Selective witness type
 * @param <A> The input type
 * @param <B> The output type
 * @param <C> The result type
 */
public final class SelectiveHandlerStage<F, A, B, C> {
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

  SelectiveHandlerStage(
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
      Kind<F, A> validElseBranch) {

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
  }

  // =============================================================================
  // Optional Law Configuration
  // =============================================================================

  /**
   * Configures law testing parameters.
   *
   * <p>Progressive disclosure: Next steps are validation configuration, test selection, or
   * execution.
   *
   * @param testValue A test value for law testing
   * @param testFunction Test function for law verification
   * @param equalityChecker Equality checker for comparing Kind instances
   * @return Laws stage with further configuration or execution options
   */
  public SelectiveLawsStage<F, A, B, C> withLawsTesting(
      B testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    return new SelectiveLawsStage<>(this, testValue, testFunction, equalityChecker);
  }

  // =============================================================================
  // Optional Validation Configuration
  // =============================================================================

  /**
   * Enters validation configuration mode.
   *
   * <p>Progressive disclosure: Shows validation context configuration options.
   *
   * @return Validation stage for configuring error message contexts
   */
  public SelectiveValidationStage<F, A, B, C> configureValidation() {
    return new SelectiveValidationStage<>(this, null);
  }

  // =============================================================================
  // Test Selection
  // =============================================================================

  /**
   * Enters test selection mode for fine-grained control.
   *
   * <p>Progressive disclosure: Shows test selection options.
   *
   * @return Stage for selecting which tests to run
   */
  public SelectiveTestSelectionStage<F, A, B, C> selectTests() {
    return new SelectiveTestSelectionStage<>(this, null, null);
  }

  // =============================================================================
  // Quick Execution (Without Laws)
  // =============================================================================

  /**
   * Executes operation and validation tests only (no laws).
   *
   * <p>Use this for quick smoke testing without full law verification.
   */
  public void testOperationsAndValidations() {
    build(null, null).executeOperationsAndValidations();
  }

  /** Executes only operation tests. */
  public void testOperations() {
    build(null, null).executeOperations();
  }

  /** Executes only validation tests. */
  public void testValidations() {
    build(null, null).executeValidations();
  }

  /** Executes only exception propagation tests. */
  public void testExceptions() {
    build(null, null).executeExceptions();
  }

  /**
   * Attempts to execute all tests including laws.
   *
   * <p><strong>Important:</strong> This will fail if laws are not configured. Use {@code
   * .withLawsTesting(...).testAll()} instead, or use {@code .testOperationsAndValidations()} to
   * skip laws.
   *
   * @throws IllegalStateException if law configuration is missing
   */
  public void testAll() {
    throw new IllegalStateException(
        "Cannot test laws without law configuration. "
            + "Use .withLawsTesting() to configure laws, or use .testOperationsAndValidations()");
  }

  SelectiveTestExecutor<F, A, B, C> build(
      SelectiveLawsStage<F, A, B, C> lawsStage,
      SelectiveValidationStage<F, A, B, C> validationStage) {

    return new SelectiveTestExecutor<>(
        contextClass,
        selective,
        validKind,
        validChoiceKind,
        validFunctionKind,
        validLeftHandler,
        validRightHandler,
        validCondition,
        validUnitEffect, // ✓ Pass Unit effect
        validThenBranch,
        validElseBranch,
        lawsStage,
        validationStage);
  }

  // Package-private getters for other stages
  Class<?> getContextClass() {
    return contextClass;
  }

  Selective<F> getSelective() {
    return selective;
  }

  Kind<F, A> getValidKind() {
    return validKind;
  }

  Kind<F, Choice<A, B>> getValidChoiceKind() {
    return validChoiceKind;
  }

  Kind<F, Function<A, B>> getValidFunctionKind() {
    return validFunctionKind;
  }

  Kind<F, Function<A, C>> getValidLeftHandler() {
    return validLeftHandler;
  }

  Kind<F, Function<B, C>> getValidRightHandler() {
    return validRightHandler;
  }

  Kind<F, Boolean> getValidCondition() {
    return validCondition;
  }

  Kind<F, Unit> getValidUnitEffect() { // ✓ Changed return type and method name
    return validUnitEffect;
  }

  Kind<F, A> getValidThenBranch() {
    return validThenBranch;
  }

  Kind<F, A> getValidElseBranch() {
    return validElseBranch;
  }
}
