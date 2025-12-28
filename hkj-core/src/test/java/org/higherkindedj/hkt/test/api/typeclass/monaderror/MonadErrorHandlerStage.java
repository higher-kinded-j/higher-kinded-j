// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monaderror;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 5: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows optional law testing,
 * validation configuration, and execution.
 *
 * @param <F> The MonadError witness type
 * @param <E> The error type
 * @param <A> The input type
 * @param <B> The mapped type
 */
public final class MonadErrorHandlerStage<F extends WitnessArity<TypeArity.Unary>, E, A, B> {
  private final Class<?> contextClass;
  private final MonadError<F, E> monadError;
  private final Kind<F, A> validKind;
  private final Function<A, B> mapper;
  private final Function<A, Kind<F, B>> flatMapper;
  private final Kind<F, Function<A, B>> functionKind;
  private final Function<E, Kind<F, A>> handler;
  private final Kind<F, A> fallback;

  MonadErrorHandlerStage(
      Class<?> contextClass,
      MonadError<F, E> monadError,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind,
      Function<E, Kind<F, A>> handler,
      Kind<F, A> fallback) {

    this.contextClass = contextClass;
    this.monadError = monadError;
    this.validKind = validKind;
    this.mapper = mapper;
    this.flatMapper = flatMapper;
    this.functionKind = functionKind;
    this.handler = handler;
    this.fallback = fallback;
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
   * @param chainFunction Chain function for associativity testing
   * @param equalityChecker Equality checker for comparing Kind instances
   * @return Laws stage with further configuration or execution options
   */
  public MonadErrorLawsStage<F, E, A, B> withLawsTesting(
      A testValue,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    return new MonadErrorLawsStage<>(this, testValue, testFunction, chainFunction, equalityChecker);
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
  public MonadErrorValidationStage<F, E, A, B> configureValidation() {
    return new MonadErrorValidationStage<>(this, null);
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
  public MonadErrorTestSelectionStage<F, E, A, B> selectTests() {
    return new MonadErrorTestSelectionStage<>(this, null, null);
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

  MonadErrorTestExecutor<F, E, A, B> build(
      MonadErrorLawsStage<F, E, A, B> lawsStage,
      MonadErrorValidationStage<F, E, A, B> validationStage) {

    return new MonadErrorTestExecutor<>(
        contextClass,
        monadError,
        validKind,
        mapper,
        flatMapper,
        functionKind,
        handler,
        fallback,
        lawsStage,
        validationStage);
  }

  // Package-private getters for other stages
  Class<?> getContextClass() {
    return contextClass;
  }

  MonadError<F, E> getMonadError() {
    return monadError;
  }

  Kind<F, A> getValidKind() {
    return validKind;
  }

  Function<A, B> getMapper() {
    return mapper;
  }

  Function<A, Kind<F, B>> getFlatMapper() {
    return flatMapper;
  }

  Kind<F, Function<A, B>> getFunctionKind() {
    return functionKind;
  }

  Function<E, Kind<F, A>> getHandler() {
    return handler;
  }

  Kind<F, A> getFallback() {
    return fallback;
  }
}
