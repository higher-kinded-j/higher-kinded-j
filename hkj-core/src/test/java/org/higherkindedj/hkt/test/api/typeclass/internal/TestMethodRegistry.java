// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.internal;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.test.patterns.TypeClassTestPattern;

/**
 * Internal registry of test methods.
 *
 * <p>This class delegates to the existing {@link TypeClassTestPattern} methods. It is
 * package-private and not exposed to users, who should use the fluent API instead.
 *
 * <p><strong>Note:</strong> Direct use of these methods is deprecated. Use {@link
 * org.higherkindedj.hkt.test.api.TypeClassTest} instead.
 */
public final class TestMethodRegistry {

  private TestMethodRegistry() {
    throw new AssertionError("TestMethodRegistry is a utility class");
  }

  // =============================================================================
  // Functor Tests
  // =============================================================================

  public static <F, A, B> void testFunctorOperations(
      Functor<F> functor, Kind<F, A> validKind, Function<A, B> mapper) {
    TypeClassTestPattern.testFunctorOperations(functor, validKind, mapper);
  }

  public static <F, A, B> void testFunctorValidations(
      Functor<F> functor, Class<?> contextClass, Kind<F, A> validKind, Function<A, B> mapper) {
    TypeClassTestPattern.testFunctorValidations(functor, contextClass, validKind, mapper);
  }

  public static <F, A> void testFunctorExceptionPropagation(
      Functor<F> functor, Kind<F, A> validKind) {
    TypeClassTestPattern.testFunctorExceptionPropagation(functor, validKind);
  }

  public static <F, A, B, C> void testFunctorLaws(
      Functor<F> functor,
      Kind<F, A> validKind,
      Function<A, B> f,
      Function<B, C> g,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {
    TypeClassTestPattern.testFunctorLaws(functor, validKind, f, g, equalityChecker);
  }

  // =============================================================================
  // Applicative Tests
  // =============================================================================

  public static <F, A, B> void testApplicativeOperations(
      Applicative<F> applicative,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> mapper,
      Kind<F, Function<A, B>> functionKind,
      BiFunction<A, A, B> combiningFunction) {
    TypeClassTestPattern.testApplicativeOperations(
        applicative, validKind, validKind2, mapper, functionKind, combiningFunction);
  }

  public static <F, A, B> void testApplicativeValidations(
      Applicative<F> applicative,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> mapper,
      Kind<F, Function<A, B>> functionKind,
      BiFunction<A, A, B> combiningFunction) {
    TypeClassTestPattern.testApplicativeValidations(
        applicative, contextClass, validKind, validKind2, mapper, functionKind, combiningFunction);
  }

  public static <F, A> void testApplicativeExceptionPropagation(
      Applicative<F> applicative, Kind<F, A> validKind) {
    TypeClassTestPattern.testApplicativeExceptionPropagation(applicative, validKind);
  }

  public static <F, A, B> void testApplicativeLaws(
      Applicative<F> applicative,
      Kind<F, A> validKind,
      A testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {
    TypeClassTestPattern.testApplicativeLaws(
        applicative, validKind, testValue, testFunction, equalityChecker);
  }

  // =============================================================================
  // Monad Tests
  // =============================================================================

  public static <F, A, B> void testMonadOperations(
      Monad<F> monad,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind) {
    TypeClassTestPattern.testMonadOperations(monad, validKind, mapper, flatMapper, functionKind);
  }

  public static <F, A, B> void testMonadValidations(
      Monad<F> monad,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind) {
    TypeClassTestPattern.testMonadValidations(
        monad, contextClass, validKind, mapper, flatMapper, functionKind);
  }

  public static <F, A> void testMonadExceptionPropagation(Monad<F> monad, Kind<F, A> validKind) {
    TypeClassTestPattern.testMonadExceptionPropagation(monad, validKind);
  }

  public static <F, A, B> void testMonadLaws(
      Monad<F> monad,
      Kind<F, A> validKind,
      A testValue,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {
    TypeClassTestPattern.testMonadLaws(
        monad, validKind, testValue, testFunction, chainFunction, equalityChecker);
  }

  // =============================================================================
  // MonadError Tests
  // =============================================================================

  public static <F, E, A, B> void testMonadErrorOperations(
      MonadError<F, E> monadError,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind,
      Function<E, Kind<F, A>> handler,
      Kind<F, A> fallback) {
    TypeClassTestPattern.testMonadErrorOperations(
        monadError, validKind, mapper, flatMapper, functionKind, handler, fallback);
  }

  public static <F, E, A, B> void testMonadErrorValidations(
      MonadError<F, E> monadError,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind,
      Function<E, Kind<F, A>> handler,
      Kind<F, A> fallback) {
    TypeClassTestPattern.testMonadErrorValidations(
        monadError, contextClass, validKind, mapper, flatMapper, functionKind, handler, fallback);
  }

  public static <F, E, A> void testMonadErrorExceptionPropagation(
      MonadError<F, E> monadError, Kind<F, A> validKind) {
    TypeClassTestPattern.testMonadErrorExceptionPropagation(monadError, validKind);
  }

  // =============================================================================
  // Selective Tests
  // =============================================================================

  /**
   * Tests Selective operations.
   *
   * <p><b>Note:</b> The {@code validUnitEffect} parameter must be of type {@code Kind<F, Unit>} to
   * match the new {@code whenS} signature which uses {@link Unit} to represent operations that
   * complete with no interesting result.
   *
   * @param selective The Selective instance to test
   * @param validChoiceKind A valid Kind containing a Choice
   * @param validFunctionKind A valid Kind containing a function
   * @param validLeftHandler A valid Kind for left handler
   * @param validRightHandler A valid Kind for right handler
   * @param validCondition A valid Kind containing a boolean
   * @param validUnitEffect A valid Kind<F, Unit> for whenS testing
   * @param validThenBranch A valid Kind for then branch
   * @param validElseBranch A valid Kind for else branch
   * @param <F> The Selective witness type
   * @param <A> The input type
   * @param <B> The output type
   * @param <C> The result type
   */
  public static <F, A, B, C> void testSelectiveOperations(
      Selective<F> selective,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind,
      Kind<F, Function<A, C>> validLeftHandler,
      Kind<F, Function<B, C>> validRightHandler,
      Kind<F, Boolean> validCondition,
      Kind<F, Unit> validUnitEffect, // ✓ Changed from Kind<F, A> validEffect
      Kind<F, A> validThenBranch,
      Kind<F, A> validElseBranch) {
    TypeClassTestPattern.testSelectiveOperations(
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

  /**
   * Tests Selective validations.
   *
   * <p><b>Note:</b> The {@code validUnitEffect} parameter must be of type {@code Kind<F, Unit>} to
   * match the new {@code whenS} signature which uses {@link Unit} to represent operations that
   * complete with no interesting result.
   *
   * @param selective The Selective instance to test
   * @param contextClass The context class for validation errors
   * @param validChoiceKind A valid Kind containing a Choice
   * @param validFunctionKind A valid Kind containing a function
   * @param validLeftHandler A valid Kind for left handler
   * @param validRightHandler A valid Kind for right handler
   * @param validCondition A valid Kind containing a boolean
   * @param validUnitEffect A valid Kind<F, Unit> for whenS testing
   * @param validThenBranch A valid Kind for then branch
   * @param validElseBranch A valid Kind for else branch
   * @param <F> The Selective witness type
   * @param <A> The input type
   * @param <B> The output type
   * @param <C> The result type
   */
  public static <F, A, B, C> void testSelectiveValidations(
      Selective<F> selective,
      Class<?> contextClass,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind,
      Kind<F, Function<A, C>> validLeftHandler,
      Kind<F, Function<B, C>> validRightHandler,
      Kind<F, Boolean> validCondition,
      Kind<F, Unit> validUnitEffect, // ✓ Changed from Kind<F, A> validEffect
      Kind<F, A> validThenBranch,
      Kind<F, A> validElseBranch) {
    TypeClassTestPattern.testSelectiveValidations(
        selective,
        contextClass,
        validChoiceKind,
        validFunctionKind,
        validLeftHandler,
        validRightHandler,
        validCondition,
        validUnitEffect, // ✓ Pass Unit effect
        validThenBranch,
        validElseBranch);
  }

  public static <F, A, B> void testSelectiveExceptionPropagation(
      Selective<F> selective,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind) {
    TypeClassTestPattern.testSelectiveExceptionPropagation(
        selective, validChoiceKind, validFunctionKind);
  }

  public static <F, A, B> void testSelectiveLaws(
      Selective<F> selective,
      Kind<F, Choice<A, B>> choiceKind,
      B testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {
    TypeClassTestPattern.testSelectiveLaws(
        selective, choiceKind, testValue, testFunction, equalityChecker);
  }

  // =============================================================================
  // Foldable Tests
  // =============================================================================

  public static <F, A, M> void testFoldableOperations(
      Foldable<F> foldable,
      Kind<F, A> validKind,
      Monoid<M> monoid,
      Function<A, M> foldMapFunction) {
    TypeClassTestPattern.testFoldableOperations(foldable, validKind, monoid, foldMapFunction);
  }

  public static <F, A, M> void testFoldableValidations(
      Foldable<F> foldable,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Monoid<M> monoid,
      Function<A, M> foldMapFunction) {
    TypeClassTestPattern.testFoldableValidations(
        foldable, contextClass, validKind, monoid, foldMapFunction);
  }

  public static <F, A, M> void testFoldableExceptionPropagation(
      Foldable<F> foldable, Kind<F, A> validKind, Monoid<M> monoid) {
    TypeClassTestPattern.testFoldableExceptionPropagation(foldable, validKind, monoid);
  }

  // =============================================================================
  // Traverse Tests
  // =============================================================================

  public static <F, G, A, B, M> void testTraverseOperations(
      Traverse<F> traverse,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Applicative<G> applicative,
      Function<A, Kind<G, B>> traverseFunction,
      Monoid<M> monoid,
      Function<A, M> foldMapFunction) {
    TypeClassTestPattern.testTraverseOperations(
        traverse, validKind, mapper, applicative, traverseFunction, monoid, foldMapFunction);
  }

  public static <F, G, A, B, M> void testTraverseValidations(
      Traverse<F> traverse,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Applicative<G> applicative,
      Function<A, Kind<G, B>> traverseFunction,
      Monoid<M> monoid,
      Function<A, M> foldMapFunction) {
    TypeClassTestPattern.testTraverseValidations(
        traverse,
        contextClass,
        validKind,
        mapper,
        applicative,
        traverseFunction,
        monoid,
        foldMapFunction);
  }

  public static <F, G, A, M> void testTraverseExceptionPropagation(
      Traverse<F> traverse, Kind<F, A> validKind, Applicative<G> applicative, Monoid<M> monoid) {
    TypeClassTestPattern.testTraverseExceptionPropagation(traverse, validKind, applicative, monoid);
  }

  public static <F, G, A, B> void testTraverseLaws(
      Traverse<F> traverse,
      Applicative<G> applicative,
      Kind<F, A> validKind,
      Function<A, Kind<G, B>> testFunction,
      BiPredicate<Kind<G, ?>, Kind<G, ?>> equalityChecker) {
    TypeClassTestPattern.testTraverseLaws(
        traverse, applicative, validKind, testFunction, equalityChecker);
  }
}
