// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.bifunctor;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;

/**
 * Configuration stage with optional settings and test execution methods.
 *
 * @param <F> The Bifunctor witness type
 * @param <A> The first input type parameter
 * @param <B> The second input type parameter
 * @param <C> The first output type parameter
 * @param <D> The second output type parameter
 */
public final class BifunctorTestConfigStage<F, A, B, C, D> {

  final Class<?> contextClass;
  final Bifunctor<F> bifunctor;
  final Kind2<F, A, B> validKind;
  final Function<A, C> firstMapper;
  final Function<B, D> secondMapper;

  // Optional configurations
  Function<C, String> compositionFirstMapper;
  Function<D, String> compositionSecondMapper;
  BiPredicate<Kind2<F, ?, ?>, Kind2<F, ?, ?>> equalityChecker;

  // Test inclusion flags
  boolean includeOperations = true;
  boolean includeValidations = true;
  boolean includeExceptions = true;
  boolean includeLaws = true;

  BifunctorTestConfigStage(
      Class<?> contextClass,
      Bifunctor<F> bifunctor,
      Kind2<F, A, B> validKind,
      Function<A, C> firstMapper,
      Function<B, D> secondMapper) {
    this.contextClass = contextClass;
    this.bifunctor = bifunctor;
    this.validKind = validKind;
    this.firstMapper = firstMapper;
    this.secondMapper = secondMapper;
  }

  /**
   * Provides an additional mapper for composition law testing.
   *
   * @param compositionFirstMapper Second mapper for first parameter composition
   * @return This configuration stage for chaining
   */
  public BifunctorTestConfigStage<F, A, B, C, D> withCompositionFirstMapper(
      Function<C, String> compositionFirstMapper) {
    this.compositionFirstMapper = compositionFirstMapper;
    return this;
  }

  /**
   * Provides an additional mapper for composition law testing.
   *
   * @param compositionSecondMapper Second mapper for second parameter composition
   * @return This configuration stage for chaining
   */
  public BifunctorTestConfigStage<F, A, B, C, D> withCompositionSecondMapper(
      Function<D, String> compositionSecondMapper) {
    this.compositionSecondMapper = compositionSecondMapper;
    return this;
  }

  /**
   * Provides a custom equality checker for law testing.
   *
   * @param equalityChecker BiPredicate to compare Kind2 instances
   * @return This configuration stage for chaining
   */
  public BifunctorTestConfigStage<F, A, B, C, D> withEqualityChecker(
      BiPredicate<Kind2<F, ?, ?>, Kind2<F, ?, ?>> equalityChecker) {
    this.equalityChecker = equalityChecker;
    return this;
  }

  /**
   * Begins fine-grained test selection.
   *
   * @return Selection stage for choosing specific tests
   */
  public BifunctorTestSelectionStage<F, A, B, C, D> selectTests() {
    return new BifunctorTestSelectionStage<>(this);
  }

  /**
   * Executes all configured tests.
   */
  public void testAll() {
    build().executeAll();
  }

  /**
   * Executes only operation tests.
   */
  public void testOperations() {
    build().executeOperations();
  }

  /**
   * Executes only validation tests.
   */
  public void testValidations() {
    build().executeValidations();
  }

  /**
   * Executes only exception propagation tests.
   */
  public void testExceptions() {
    build().executeExceptions();
  }

  /**
   * Executes only law tests.
   *
   * <p>Requires equality checker to be set.
   */
  public void testLaws() {
    if (equalityChecker == null) {
      throw new IllegalStateException(
          "Law tests require an equality checker. Use withEqualityChecker() first.");
    }
    build().executeLaws();
  }

  BifunctorTestExecutor<F, A, B, C, D> build() {
    return new BifunctorTestExecutor<>(this);
  }
}
