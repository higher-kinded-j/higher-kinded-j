// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Comprehensive type class assertions aligned with standardised validation framework.
 *
 * <p>This class provides operation-specific assertions for all major type classes, using production
 * validators to ensure test expectations match actual implementation behaviour.
 *
 * <h2>Supported Type Classes:</h2>
 *
 * <ul>
 *   <li>{@link Functor} - map operations
 *   <li>{@link Applicative} - of, ap, map2, map3, map4, map5 operations
 *   <li>{@link Monad} - flatMap operations (extends Applicative)
 *   <li>{@link MonadError} - raiseError, handleErrorWith, recover operations
 *   <li>{@link Foldable} - foldMap operations
 *   <li>{@link Traverse} - traverse, sequenceA operations (extends Functor and Foldable)
 * </ul>
 *
 * <h2>Key Benefits:</h2>
 *
 * <ul>
 *   <li>Type class specific method names for better readability
 *   <li>Context-aware error messages using class-based contexts
 *   <li>Comprehensive validation coverage for all type class operations
 *   <li>Production-aligned validation using standardised framework
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Functor Validation:</h3>
 *
 * <pre>{@code
 * // Test functor operations
 * TypeClassAssertions.assertFunctorMapFunctionNull(
 *     () -> functor.map(null, validKind), MyFunctor.class);
 * TypeClassAssertions.assertFunctorMapKindNull(
 *     () -> functor.map(validMapper, null), MyFunctor.class);
 * }</pre>
 *
 * <h3>Monad Validation:</h3>
 *
 * <pre>{@code
 * // Test all monad operations
 * TypeClassAssertions.assertAllMonadOperations(
 *     monad, MyMonad.class, validKind, validMapper, validFlatMapper, validFunctionKind);
 * }</pre>
 *
 * <h3>Traverse Validation:</h3>
 *
 * <pre>{@code
 * // Test traverse operations
 * TypeClassAssertions.assertTraverseApplicativeNull(
 *     () -> traverse.traverse(null, validFunction, validKind), MyTraverse.class);
 * TypeClassAssertions.assertTraverseFunctionNull(
 *     () -> traverse.traverse(validApplicative, null, validKind), MyTraverse.class);
 * }</pre>
 *
 * @see FunctionAssertions
 * @see KindAssertions
 */
public final class TypeClassAssertions {

  private TypeClassAssertions() {
    throw new AssertionError(
        "TypeClassAssertions is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Functor Assertions
  // =============================================================================

  /**
   * Asserts Functor map function validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFunctorMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable, String functionName) {
    return FunctionAssertions.assertMapperNull(executable, functionName, Operation.MAP);
  }

  /**
   * Asserts all Functor operations for null parameters.
   *
   * @param functor The Functor instance to test
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param <F> The Functor witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> void assertAllFunctorOperations(
      Functor<F> functor, Kind<F, A> validKind, Function<A, B> validMapper) {

    assertFunctorMapFunctionNull(() -> functor.map(null, validKind), "f");
    KindAssertions.assertKindNull(() -> functor.map(validMapper, null), MAP);
  }

  // =============================================================================
  // Applicative Assertions
  // =============================================================================

  /**
   * Asserts Applicative ap function Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeApFunctionKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, AP, "function");
  }

  /**
   * Asserts Applicative ap argument Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeApArgumentKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, AP, "argument");
  }

  /**
   * Asserts Applicative map2 first Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeMap2FirstKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, MAP_2, "first");
  }

  /**
   * Asserts Applicative map2 second Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeMap2SecondKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, MAP_2, "second");
  }

  /**
   * Asserts Applicative map2 function validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeMap2FunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "combining function", MAP_2);
  }

  /**
   * Asserts all Applicative operations for null parameters.
   *
   * @param applicative The Applicative instance to test
   * @param validKind A valid Kind for testing
   * @param validKind2 A second valid Kind for map2 testing
   * @param validMapper A valid mapping function
   * @param validFunctionKind A valid function Kind for ap testing
   * @param validCombiningFunction A valid combining function for map2 testing
   * @param <F> The Applicative witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> void assertAllApplicativeOperations(
      Applicative<F> applicative,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> validMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      BiFunction<A, A, B> validCombiningFunction) {

    // Functor operations (inherited)
    assertAllFunctorOperations(applicative, validKind, validMapper);

    // Ap operations
    assertApplicativeApFunctionKindNull(() -> applicative.ap(null, validKind));
    assertApplicativeApArgumentKindNull(() -> applicative.ap(validFunctionKind, null));

    // Map2 operations - cast null to BiFunction to resolve ambiguity
    assertApplicativeMap2FirstKindNull(
        () -> applicative.map2(null, validKind2, validCombiningFunction));
    assertApplicativeMap2SecondKindNull(
        () -> applicative.map2(validKind, null, validCombiningFunction));
    assertApplicativeMap2FunctionNull(
        () -> applicative.map2(validKind, validKind2, (BiFunction<A, A, B>) null));
  }

  // =============================================================================
  // Monad Assertions
  // =============================================================================

  /**
   * Asserts Monad flatMap function validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonadFlatMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable, String functionName) {
    return FunctionAssertions.assertFlatMapperNull(executable, functionName, FLAT_MAP);
  }

  /**
   * Asserts all Monad operations for null parameters.
   *
   * @param monad The Monad instance to test
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validFlatMapper A valid flatMap function
   * @param validFunctionKind A valid function Kind for ap testing
   * @param <F> The Monad witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> void assertAllMonadOperations(
      Monad<F> monad,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind) {

    // Applicative operations (inherited) - using BiFunction for map2
    BiFunction<A, A, B> validCombiningFunction = (a1, a2) -> validMapper.apply(a1);
    assertAllApplicativeOperations(
        monad, validKind, validKind, validMapper, validFunctionKind, validCombiningFunction);

    // FlatMap operations
    assertMonadFlatMapFunctionNull(() -> monad.flatMap(null, validKind), "f");
    KindAssertions.assertKindNull(() -> monad.flatMap(validFlatMapper, null), FLAT_MAP);
  }

  // =============================================================================
  // MonadError Assertions
  // =============================================================================

  /**
   * Asserts MonadError handleErrorWith Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable>
      assertMonadErrorHandleErrorWithKindNull(ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, HANDLE_ERROR_WITH, "source");
  }

  /**
   * Asserts MonadError handleErrorWith handler validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable>
      assertMonadErrorHandleErrorWithHandlerNull(ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertHandlerNull(executable, HANDLE_ERROR_WITH);
  }

  /**
   * Asserts MonadError recoverWith Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonadErrorRecoverWithKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, RECOVER_WITH, "source");
  }

  /**
   * Asserts MonadError recoverWith fallback validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable>
      assertMonadErrorRecoverWithFallbackNull(ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, RECOVER_WITH, "fallback");
  }

  /**
   * Asserts all MonadError operations for null parameters.
   *
   * @param monadError The MonadError instance to test
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validFlatMapper A valid flatMap function
   * @param validFunctionKind A valid function Kind for ap testing
   * @param validHandler A valid error handler function
   * @param validFallback A valid fallback Kind
   * @param <F> The MonadError witness type
   * @param <E> The error type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F extends WitnessArity<TypeArity.Unary>, E, A, B>
      void assertAllMonadErrorOperations(
          MonadError<F, E> monadError,
          Kind<F, A> validKind,
          Function<A, B> validMapper,
          Function<A, Kind<F, B>> validFlatMapper,
          Kind<F, Function<A, B>> validFunctionKind,
          Function<E, Kind<F, A>> validHandler,
          Kind<F, A> validFallback) {

    // Monad operations (inherited)
    assertAllMonadOperations(
        monadError, validKind, validMapper, validFlatMapper, validFunctionKind);

    // MonadError operations
    assertMonadErrorHandleErrorWithKindNull(() -> monadError.handleErrorWith(null, validHandler));
    assertMonadErrorHandleErrorWithHandlerNull(() -> monadError.handleErrorWith(validKind, null));
    assertMonadErrorRecoverWithKindNull(() -> monadError.recoverWith(null, validFallback));
    assertMonadErrorRecoverWithFallbackNull(() -> monadError.recoverWith(validKind, null));
  }

  // =============================================================================
  // Foldable Assertions
  // =============================================================================

  /**
   * Asserts Foldable foldMap monoid validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldableFoldMapMonoidNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertMonoidNull(executable, "monoid", FOLD_MAP);
  }

  /**
   * Asserts Foldable foldMap function validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldableFoldMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable, String functionName) {
    return FunctionAssertions.assertMapperNull(executable, functionName, FOLD_MAP);
  }

  /**
   * Asserts Foldable foldMap Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldableFoldMapKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, FOLD_MAP);
  }

  /**
   * Asserts all Foldable operations for null parameters.
   *
   * @param foldable The Foldable instance to test
   * @param validKind A valid Kind for testing
   * @param validMonoid A valid Monoid instance
   * @param validFoldMapFunction A valid foldMap function
   * @param <F> The Foldable witness type
   * @param <A> The element type
   * @param <M> The Monoid type
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, M> void assertAllFoldableOperations(
      Foldable<F> foldable,
      Kind<F, A> validKind,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    assertFoldableFoldMapMonoidNull(() -> foldable.foldMap(null, validFoldMapFunction, validKind));
    assertFoldableFoldMapFunctionNull(() -> foldable.foldMap(validMonoid, null, validKind), "f");
    assertFoldableFoldMapKindNull(() -> foldable.foldMap(validMonoid, validFoldMapFunction, null));
  }

  // =============================================================================
  // Selective Assertions
  // =============================================================================

  /**
   * Asserts Selective select choice Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSelectiveSelectChoiceNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, SELECT, "choice");
  }

  /**
   * Asserts Selective select function Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSelectiveSelectFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, SELECT, "function");
  }

  /**
   * Asserts Selective branch choice Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSelectiveBranchChoiceNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, BRANCH, "choice");
  }

  /**
   * Asserts Selective branch left handler validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable>
      assertSelectiveBranchLeftHandlerNull(ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, BRANCH, "leftHandler");
  }

  /**
   * Asserts Selective branch right handler validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable>
      assertSelectiveBranchRightHandlerNull(ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, BRANCH, "rightHandler");
  }

  /**
   * Asserts Selective whenS condition validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSelectiveWhenSConditionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, WHEN_S, "condition");
  }

  /**
   * Asserts Selective whenS effect validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSelectiveWhenSEffectNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, WHEN_S, "effect");
  }

  /**
   * Asserts Selective ifS condition validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSelectiveIfSConditionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, IF_S, "condition");
  }

  /**
   * Asserts Selective ifS then branch validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSelectiveIfSThenNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, IF_S, "thenBranch");
  }

  /**
   * Asserts Selective ifS else branch validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSelectiveIfSElseNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, IF_S, "elseBranch");
  }

  /**
   * Asserts all Selective operations for null parameters.
   *
   * @param selective The Selective instance to test
   * @param validChoiceKind A valid Kind containing a Choice
   * @param validFunctionKind A valid Kind containing a function
   * @param validLeftHandler A valid Kind for left handler
   * @param validRightHandler A valid Kind for right handler
   * @param validCondition A valid Kind containing a boolean
   * @param validUnitEffect A valid Kind<F, Unit> for whenS effect testing
   * @param validThenBranch A valid Kind for then branch
   * @param validElseBranch A valid Kind for else branch
   * @param <F> The Selective witness type
   * @param <A> The input type
   * @param <B> The output type
   * @param <C> The result type
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B, C>
      void assertAllSelectiveOperations(
          Selective<F> selective,
          Kind<F, Choice<A, B>> validChoiceKind,
          Kind<F, Function<A, B>> validFunctionKind,
          Kind<F, Function<A, C>> validLeftHandler,
          Kind<F, Function<B, C>> validRightHandler,
          Kind<F, Boolean> validCondition,
          Kind<F, Unit> validUnitEffect, // ✓ Changed from Kind<F, A> validEffect
          Kind<F, A> validThenBranch,
          Kind<F, A> validElseBranch) {

    // Applicative operations (inherited) - create valid test data
    @SuppressWarnings("unchecked")
    Kind<F, A> validKind = (Kind<F, A>) validThenBranch;
    @SuppressWarnings("unchecked")
    Kind<F, A> validKind2 = (Kind<F, A>) validElseBranch;
    // Dummy mapper for validation testing - never actually called
    @SuppressWarnings("unchecked")
    Function<A, B> validMapper = a -> (B) null;
    BiFunction<A, A, B> validCombiningFunction = (a1, a2) -> validMapper.apply(a1);

    assertAllApplicativeOperations(
        selective, validKind, validKind2, validMapper, validFunctionKind, validCombiningFunction);

    // Select operations
    assertSelectiveSelectChoiceNull(() -> selective.select(null, validFunctionKind));
    assertSelectiveSelectFunctionNull(() -> selective.select(validChoiceKind, null));

    // Branch operations
    assertSelectiveBranchChoiceNull(
        () -> selective.branch(null, validLeftHandler, validRightHandler));
    assertSelectiveBranchLeftHandlerNull(
        () -> selective.branch(validChoiceKind, null, validRightHandler));
    assertSelectiveBranchRightHandlerNull(
        () -> selective.branch(validChoiceKind, validLeftHandler, null));

    // WhenS operations - now using validUnitEffect
    assertSelectiveWhenSConditionNull(() -> selective.whenS(null, validUnitEffect)); // ✓ Fixed
    assertSelectiveWhenSEffectNull(() -> selective.whenS(validCondition, null));

    // IfS operations
    assertSelectiveIfSConditionNull(() -> selective.ifS(null, validThenBranch, validElseBranch));
    assertSelectiveIfSThenNull(() -> selective.ifS(validCondition, null, validElseBranch));
    assertSelectiveIfSElseNull(() -> selective.ifS(validCondition, validThenBranch, null));
  }

  // =============================================================================
  // Traverse Assertions
  // =============================================================================

  /**
   * Asserts Traverse traverse applicative validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseApplicativeNull(
      ThrowableAssert.ThrowingCallable executable, String applicativeName) {
    return FunctionAssertions.assertApplicativeNull(executable, applicativeName, TRAVERSE);
  }

  /**
   * Asserts Traverse traverse function validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseFunctionNull(
      ThrowableAssert.ThrowingCallable executable, String applicativeName) {
    return FunctionAssertions.assertMapperNull(executable, applicativeName, TRAVERSE);
  }

  /**
   * Asserts Traverse traverse Kind validation.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, TRAVERSE);
  }

  /**
   * Asserts all Traverse operations for null parameters.
   *
   * @param traverse The Traverse instance to test
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validApplicative A valid Applicative instance
   * @param validTraverseFunction A valid traverse function
   * @param validMonoid A valid Monoid instance
   * @param validFoldMapFunction A valid foldMap function
   * @param <F> The Traverse witness type
   * @param <G> The Applicative witness type
   * @param <A> The source element type
   * @param <B> The target element type
   * @param <M> The Monoid type
   */
  public static <
          F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>, A, B, M>
      void assertAllTraverseOperations(
          Traverse<F> traverse,
          Kind<F, A> validKind,
          Function<A, B> validMapper,
          Applicative<G> validApplicative,
          Function<A, Kind<G, B>> validTraverseFunction,
          Monoid<M> validMonoid,
          Function<A, M> validFoldMapFunction) {

    // Functor operations (inherited)
    assertAllFunctorOperations(traverse, validKind, validMapper);

    // Foldable operations (inherited)
    assertAllFoldableOperations(traverse, validKind, validMonoid, validFoldMapFunction);

    // Traverse operations
    assertTraverseApplicativeNull(
        () -> traverse.traverse(null, validTraverseFunction, validKind), "applicative");
    assertTraverseFunctionNull(() -> traverse.traverse(validApplicative, null, validKind), "f");
    assertTraverseKindNull(() -> traverse.traverse(validApplicative, validTraverseFunction, null));
  }

  // =============================================================================
  // Bifunctor Assertions
  // =============================================================================

  /**
   * Asserts all Bifunctor operations throw appropriate exceptions for null parameters.
   *
   * @param bifunctor The Bifunctor instance to test
   * @param validKind A valid Kind2 instance
   * @param firstMapper A valid function for the first parameter
   * @param secondMapper A valid function for the second parameter
   * @param <F> The Bifunctor witness type
   * @param <A> The first input type
   * @param <B> The second input type
   * @param <C> The first output type
   * @param <D> The second output type
   */
  public static <F extends WitnessArity<TypeArity.Binary>, A, B, C, D>
      void assertAllBifunctorOperations(
          Bifunctor<F> bifunctor,
          Kind2<F, A, B> validKind,
          Function<A, C> firstMapper,
          Function<B, D> secondMapper) {

    // bimap validations
    assertBimapFirstMapperNull(() -> bifunctor.bimap(null, secondMapper, validKind), "f");
    assertBimapSecondMapperNull(() -> bifunctor.bimap(firstMapper, null, validKind), "g");
    KindAssertions.assertKindNull(
        ((Runnable) () -> bifunctor.bimap(firstMapper, secondMapper, null))::run, BIMAP);

    // first validations
    assertFirstMapperNull(() -> bifunctor.first(null, validKind), "f");
    KindAssertions.assertKindNull(
        ((Runnable) () -> bifunctor.first(firstMapper, null))::run, FIRST);

    // second validations
    assertSecondMapperNull(() -> bifunctor.second(null, validKind), "g");
    KindAssertions.assertKindNull(
        ((Runnable) () -> bifunctor.second(secondMapper, null))::run, SECOND);
  }

  private static void assertBimapFirstMapperNull(Runnable operation, String paramName) {
    FunctionAssertions.assertMapperNull(operation::run, paramName, BIMAP);
  }

  private static void assertBimapSecondMapperNull(Runnable operation, String paramName) {
    FunctionAssertions.assertMapperNull(operation::run, paramName, BIMAP);
  }

  private static void assertFirstMapperNull(Runnable operation, String paramName) {
    FunctionAssertions.assertMapperNull(operation::run, paramName, FIRST);
  }

  private static void assertSecondMapperNull(Runnable operation, String paramName) {
    FunctionAssertions.assertMapperNull(operation::run, paramName, SECOND);
  }
}
