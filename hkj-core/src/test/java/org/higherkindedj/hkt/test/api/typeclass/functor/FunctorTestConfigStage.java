// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.functor;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;

/**
 * Stage 4: Configure optional parameters and execute tests.
 *
 * <p>Progressive disclosure: Shows optional configuration and execution options. All required
 * parameters have been provided.
 *
 * @param <F> The Functor witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class FunctorTestConfigStage<F, A, B> {
  final Class<?> contextClass;
  final Functor<F> functor;
  final Kind<F, A> validKind;
  final Function<A, B> mapper;

  // Optional configurations
  Function<B, String> secondMapper;
  BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

  // Package-private for access by selection stage
  boolean includeOperations = true;
  boolean includeValidations = true;
  boolean includeExceptions = true;
  boolean includeLaws = true;

  FunctorTestConfigStage(
      Class<?> contextClass, Functor<F> functor, Kind<F, A> validKind, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.functor = functor;
    this.validKind = validKind;
    this.mapper = mapper;
  }

  // =============================================================================
  // Optional Configuration
  // =============================================================================

  /**
   * Configures secondary mapper for composition law testing.
   *
   * <p>If not provided, defaults to {@code Object::toString}.
   *
   * @param secondMapper The second mapper function (B -> String)
   * @return This stage for further configuration or execution
   */
  public FunctorTestConfigStage<F, A, B> withSecondMapper(Function<B, String> secondMapper) {
    this.secondMapper = secondMapper;
    return this;
  }

  /**
   * Configures custom equality checker for law testing.
   *
   * <p>If not provided, defaults to reference equality.
   *
   * @param checker The equality checker for comparing Kind instances
   * @return This stage for further configuration or execution
   */
  public FunctorTestConfigStage<F, A, B> withEqualityChecker(
      BiPredicate<Kind<F, ?>, Kind<F, ?>> checker) {
    this.equalityChecker = checker;
    return this;
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
  public FunctorTestSelectionStage<F, A, B> selectTests() {
    return new FunctorTestSelectionStage<>(this, null, null);
  }

  // =============================================================================
  // Quick Execution
  // =============================================================================

  /**
   * Executes all configured tests: operations, validations, exceptions, and laws.
   *
   * <p>This is the most comprehensive test execution option.
   */
  public void testAll() {
    build().executeAll();
  }

  /** Executes only operation tests (basic functionality). */
  public void testOperations() {
    build().executeOperations();
  }

  /** Executes only validation tests (null parameter checking). */
  public void testValidations() {
    build().executeValidations();
  }

  /** Executes only exception propagation tests. */
  public void testExceptions() {
    build().executeExceptions();
  }

  /**
   * Executes only law tests (algebraic properties).
   *
   * <p>Note: Requires equality checker for proper comparison.
   */
  public void testLaws() {
    build().executeLaws();
  }

  // =============================================================================
  // Internal Builder
  // =============================================================================

  FunctorTestExecutor<F, A, B> build() {
    return new FunctorTestExecutor<>(
        contextClass,
        functor,
        validKind,
        mapper,
        secondMapper,
        equalityChecker,
        includeOperations,
        includeValidations,
        includeExceptions,
        includeLaws,
        null); // Add null for validationStage
  }
}
