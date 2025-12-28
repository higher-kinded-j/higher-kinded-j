// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.applicative;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 4: Optional configuration and test execution.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows optional law testing and
 * execution options.
 *
 * @param <F> The Applicative witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class ApplicativeOperationsStage<F extends WitnessArity<TypeArity.Unary>, A, B> {
  private final Class<?> contextClass;
  private final Applicative<F> applicative;
  private final Kind<F, A> validKind;
  private final Kind<F, A> validKind2;
  private final Function<A, B> mapper;
  private final Kind<F, Function<A, B>> functionKind;
  private final BiFunction<A, A, B> combiningFunction;

  ApplicativeOperationsStage(
      Class<?> contextClass,
      Applicative<F> applicative,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> mapper,
      Kind<F, Function<A, B>> functionKind,
      BiFunction<A, A, B> combiningFunction) {

    this.contextClass = contextClass;
    this.applicative = applicative;
    this.validKind = validKind;
    this.validKind2 = validKind2;
    this.mapper = mapper;
    this.functionKind = functionKind;
    this.combiningFunction = combiningFunction;
  }

  /**
   * Configures law testing parameters.
   *
   * @param testValue A test value for law testing
   * @param testFunction Test function for law verification
   * @param equalityChecker Equality checker for comparing Kind instances
   * @return Laws stage with further options
   */
  public ApplicativeLawsStage<F, A, B> withLawsTesting(
      A testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    return new ApplicativeLawsStage<>(this, testValue, testFunction, equalityChecker);
  }

  /**
   * Enters validation configuration mode.
   *
   * <p>Progressive disclosure: Shows validation context configuration options.
   *
   * @return Validation stage for configuring error message contexts
   */
  public ApplicativeValidationStage<F, A, B> configureValidation() {
    return new ApplicativeValidationStage<>(this, null);
  }

  /**
   * Enters test selection mode for fine-grained control.
   *
   * @return Stage for selecting which tests to run
   */
  public ApplicativeTestSelectionStage<F, A, B> selectTests() {
    return new ApplicativeTestSelectionStage<>(this, null, null);
  }

  /** Executes operation and validation tests only (no laws). */
  public void testOperationsAndValidations() {
    build(null).executeOperationsAndValidations();
  }

  /** Executes only operation tests. */
  public void testOperations() {
    build(null).executeOperations();
  }

  /** Executes only validation tests. */
  public void testValidations() {
    build(null).executeValidations();
  }

  /** Executes only exception propagation tests. */
  public void testExceptions() {
    build(null).executeExceptions();
  }

  /**
   * Attempts to execute all tests including laws.
   *
   * @throws IllegalStateException if laws are not configured
   */
  public void testAll() {
    throw new IllegalStateException(
        "Cannot test laws without law configuration. "
            + "Use .withLawsTesting() to configure laws, or use .testOperationsAndValidations()");
  }

  ApplicativeTestExecutor<F, A, B> build(ApplicativeLawsStage<F, A, B> lawsStage) {
    return build(lawsStage, null);
  }

  ApplicativeTestExecutor<F, A, B> build(
      ApplicativeLawsStage<F, A, B> lawsStage,
      ApplicativeValidationStage<F, A, B> validationStage) {
    return new ApplicativeTestExecutor<>(
        contextClass,
        applicative,
        validKind,
        validKind2,
        mapper,
        functionKind,
        combiningFunction,
        lawsStage,
        validationStage); // Add null for validationStage
  }

  // Package-private getters
  Class<?> getContextClass() {
    return contextClass;
  }

  Applicative<F> getApplicative() {
    return applicative;
  }

  Kind<F, A> getValidKind() {
    return validKind;
  }

  Kind<F, A> getValidKind2() {
    return validKind2;
  }

  Function<A, B> getMapper() {
    return mapper;
  }

  Kind<F, Function<A, B>> getFunctionKind() {
    return functionKind;
  }

  BiFunction<A, A, B> getCombiningFunction() {
    return combiningFunction;
  }
}
