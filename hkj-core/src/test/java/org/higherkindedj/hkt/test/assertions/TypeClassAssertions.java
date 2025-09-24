// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Comprehensive type class assertions aligned with standardized validation framework.
 *
 * <p>This class provides operation-specific assertions for all major type classes, using production
 * validators to ensure test expectations match actual implementation behavior.
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
 *   <li>Production-aligned validation using standardized framework
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
   * @param contextClass The Functor implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFunctorMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return FunctionAssertions.assertMapperNull(executable, contextClass, Operation.MAP);
  }

  /**
   * Asserts Functor map Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Functor implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFunctorMapKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertKindNull(executable, contextClass, Operation.MAP);
  }

  /**
   * Asserts all Functor operations for null parameters.
   *
   * @param functor The Functor instance to test
   * @param contextClass The Functor implementation class
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param <F> The Functor witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F, A, B> void assertAllFunctorOperations(
      Functor<F> functor, Class<?> contextClass, Kind<F, A> validKind, Function<A, B> validMapper) {

    assertFunctorMapFunctionNull(() -> functor.map(null, validKind), contextClass);
    assertFunctorMapKindNull(() -> functor.map(validMapper, null), contextClass);
  }

  // =============================================================================
  // Applicative Assertions
  // =============================================================================

  /**
   * Asserts Applicative ap function Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Applicative implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeApFunctionKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertApFunctionKindNull(executable, contextClass);
  }

  /**
   * Asserts Applicative ap argument Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Applicative implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeApArgumentKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertApArgumentKindNull(executable, contextClass);
  }

  /**
   * Asserts Applicative map2 first Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Applicative implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeMap2FirstKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertMap2FirstKindNull(executable, contextClass);
  }

  /**
   * Asserts Applicative map2 second Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Applicative implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeMap2SecondKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertMap2SecondKindNull(executable, contextClass);
  }

  /**
   * Asserts Applicative map2 function validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Applicative implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeMap2FunctionNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return FunctionAssertions.assertFunctionNull(
        executable, "combining function", contextClass, MAP_2);
  }

  /**
   * Asserts all Applicative operations for null parameters.
   *
   * @param applicative The Applicative instance to test
   * @param contextClass The Applicative implementation class
   * @param validKind A valid Kind for testing
   * @param validKind2 A second valid Kind for map2 testing
   * @param validMapper A valid mapping function
   * @param validFunctionKind A valid function Kind for ap testing
   * @param validCombiningFunction A valid combining function for map2 testing
   * @param <F> The Applicative witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F, A, B> void assertAllApplicativeOperations(
      Applicative<F> applicative,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> validMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      BiFunction<A, A, B> validCombiningFunction) {

    // Functor operations (inherited)
    assertAllFunctorOperations(applicative, contextClass, validKind, validMapper);

    // Ap operations
    assertApplicativeApFunctionKindNull(() -> applicative.ap(null, validKind), contextClass);
    assertApplicativeApArgumentKindNull(
        () -> applicative.ap(validFunctionKind, null), contextClass);

    // Map2 operations - cast null to BiFunction to resolve ambiguity
    assertApplicativeMap2FirstKindNull(
        () -> applicative.map2(null, validKind2, validCombiningFunction), contextClass);
    assertApplicativeMap2SecondKindNull(
        () -> applicative.map2(validKind, null, validCombiningFunction), contextClass);
    assertApplicativeMap2FunctionNull(
        () -> applicative.map2(validKind, validKind2, (BiFunction<A, A, B>) null), contextClass);
  }

  // =============================================================================
  // Monad Assertions
  // =============================================================================

  /**
   * Asserts Monad flatMap function validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Monad implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonadFlatMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return FunctionAssertions.assertFlatMapperNull(executable, contextClass, FLAT_MAP);
  }

  /**
   * Asserts Monad flatMap Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Monad implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonadFlatMapKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertKindNull(executable, contextClass, FLAT_MAP);
  }

  /**
   * Asserts all Monad operations for null parameters.
   *
   * @param monad The Monad instance to test
   * @param contextClass The Monad implementation class
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validFlatMapper A valid flatMap function
   * @param validFunctionKind A valid function Kind for ap testing
   * @param <F> The Monad witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F, A, B> void assertAllMonadOperations(
      Monad<F> monad,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind) {

    // Applicative operations (inherited) - using BiFunction for map2
    BiFunction<A, A, B> validCombiningFunction = (a1, a2) -> validMapper.apply(a1);
    assertAllApplicativeOperations(
        monad,
        contextClass,
        validKind,
        validKind,
        validMapper,
        validFunctionKind,
        validCombiningFunction);

    // FlatMap operations
    assertMonadFlatMapFunctionNull(() -> monad.flatMap(null, validKind), contextClass);
    assertMonadFlatMapKindNull(() -> monad.flatMap(validFlatMapper, null), contextClass);
  }

  // =============================================================================
  // MonadError Assertions
  // =============================================================================

  /**
   * Asserts MonadError handleErrorWith Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The MonadError implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable>
      assertMonadErrorHandleErrorWithKindNull(
          ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertKindNull(executable, contextClass, HANDLE_ERROR_WITH, "source");
  }

  /**
   * Asserts MonadError handleErrorWith handler validation.
   *
   * @param executable The code that should throw
   * @param contextClass The MonadError implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable>
      assertMonadErrorHandleErrorWithHandlerNull(
          ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return FunctionAssertions.assertHandlerNull(executable, contextClass, HANDLE_ERROR_WITH);
  }

  /**
   * Asserts MonadError recover Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The MonadError implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonadErrorRecoverKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertKindNull(executable, contextClass, RECOVER, "source");
  }

  /**
   * Asserts MonadError recoverWith Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The MonadError implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonadErrorRecoverWithKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertKindNull(executable, contextClass, RECOVER_WITH, "source");
  }

  /**
   * Asserts MonadError recoverWith fallback validation.
   *
   * @param executable The code that should throw
   * @param contextClass The MonadError implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable>
      assertMonadErrorRecoverWithFallbackNull(
          ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertKindNull(executable, contextClass, RECOVER_WITH, "fallback");
  }

  /**
   * Asserts all MonadError operations for null parameters.
   *
   * @param monadError The MonadError instance to test
   * @param contextClass The MonadError implementation class
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
  public static <F, E, A, B> void assertAllMonadErrorOperations(
      MonadError<F, E> monadError,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      Function<E, Kind<F, A>> validHandler,
      Kind<F, A> validFallback) {

    // Monad operations (inherited)
    assertAllMonadOperations(
        monadError, contextClass, validKind, validMapper, validFlatMapper, validFunctionKind);

    // MonadError operations
    assertMonadErrorHandleErrorWithKindNull(
        () -> monadError.handleErrorWith(null, validHandler), contextClass);
    assertMonadErrorHandleErrorWithHandlerNull(
        () -> monadError.handleErrorWith(validKind, null), contextClass);
    assertMonadErrorRecoverWithKindNull(
        () -> monadError.recoverWith(null, validFallback), contextClass);
    assertMonadErrorRecoverWithFallbackNull(
        () -> monadError.recoverWith(validKind, null), contextClass);
  }

  // =============================================================================
  // Foldable Assertions
  // =============================================================================

  /**
   * Asserts Foldable foldMap monoid validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Foldable implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldableFoldMapMonoidNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return FunctionAssertions.assertMonoidNull(executable, contextClass, FOLD_MAP);
  }

  /**
   * Asserts Foldable foldMap function validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Foldable implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldableFoldMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return FunctionAssertions.assertMapperNull(executable, contextClass, FOLD_MAP);
  }

  /**
   * Asserts Foldable foldMap Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Foldable implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldableFoldMapKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertFoldMapKindNull(executable, contextClass);
  }

  /**
   * Asserts all Foldable operations for null parameters.
   *
   * @param foldable The Foldable instance to test
   * @param contextClass The Foldable implementation class
   * @param validKind A valid Kind for testing
   * @param validMonoid A valid Monoid instance
   * @param validFoldMapFunction A valid foldMap function
   * @param <F> The Foldable witness type
   * @param <A> The element type
   * @param <M> The Monoid type
   */
  public static <F, A, M> void assertAllFoldableOperations(
      Foldable<F> foldable,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    assertFoldableFoldMapMonoidNull(
        () -> foldable.foldMap(null, validFoldMapFunction, validKind), contextClass);
    assertFoldableFoldMapFunctionNull(
        () -> foldable.foldMap(validMonoid, null, validKind), contextClass);
    assertFoldableFoldMapKindNull(
        () -> foldable.foldMap(validMonoid, validFoldMapFunction, null), contextClass);
  }

  // =============================================================================
  // Traverse Assertions
  // =============================================================================

  /**
   * Asserts Traverse traverse applicative validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Traverse implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseApplicativeNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return FunctionAssertions.assertApplicativeNull(executable, contextClass, TRAVERSE);
  }

  /**
   * Asserts Traverse traverse function validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Traverse implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseFunctionNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return FunctionAssertions.assertMapperNull(executable, contextClass, TRAVERSE);
  }

  /**
   * Asserts Traverse traverse Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Traverse implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertTraverseKindNull(executable, contextClass);
  }

  /**
   * Asserts Traverse sequenceA applicative validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Traverse implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSequenceAApplicativeNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return FunctionAssertions.assertApplicativeNull(executable, contextClass, SEQUENCE_A);
  }

  /**
   * Asserts Traverse sequenceA Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The Traverse implementation class
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertSequenceAKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return KindAssertions.assertKindNull(executable, contextClass, SEQUENCE_A);
  }

  /**
   * Asserts all Traverse operations for null parameters.
   *
   * @param traverse The Traverse instance to test
   * @param contextClass The Traverse implementation class
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
  public static <F, G, A, B, M> void assertAllTraverseOperations(
      Traverse<F> traverse,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Applicative<G> validApplicative,
      Function<A, Kind<G, B>> validTraverseFunction,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    // Functor operations (inherited)
    assertAllFunctorOperations(traverse, contextClass, validKind, validMapper);

    // Foldable operations (inherited)
    assertAllFoldableOperations(
        traverse, contextClass, validKind, validMonoid, validFoldMapFunction);

    // Traverse operations
    assertTraverseApplicativeNull(
        () -> traverse.traverse(null, validTraverseFunction, validKind), contextClass);
    assertTraverseFunctionNull(
        () -> traverse.traverse(validApplicative, null, validKind), contextClass);
    assertTraverseKindNull(
        () -> traverse.traverse(validApplicative, validTraverseFunction, null), contextClass);
  }
}
