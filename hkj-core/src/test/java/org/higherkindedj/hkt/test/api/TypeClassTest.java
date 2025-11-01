// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api;

import org.higherkindedj.hkt.test.api.typeclass.applicative.ApplicativeTestStage;
import org.higherkindedj.hkt.test.api.typeclass.foldable.FoldableTestStage;
import org.higherkindedj.hkt.test.api.typeclass.functor.FunctorTestStage;
import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage;
import org.higherkindedj.hkt.test.api.typeclass.monad.MonadTestStage;
import org.higherkindedj.hkt.test.api.typeclass.monaderror.MonadErrorTestStage;
import org.higherkindedj.hkt.test.api.typeclass.selective.SelectiveTestStage;
import org.higherkindedj.hkt.test.api.typeclass.traverse.TraverseTestStage;

/**
 * Entry point for type class implementation testing.
 *
 * <p>This API provides a fluent, stage-based approach to testing type class implementations where
 * each step reveals only contextually relevant options through IDE autocomplete.
 *
 * <h2>Key Features:</h2>
 *
 * <ul>
 *   <li>Progressive disclosure - each stage shows only relevant next steps
 *   <li>Type-safe configuration - impossible to skip required parameters
 *   <li>Hierarchical structure - mirrors type class hierarchy
 *   <li>Clear error messages - helpful guidance when configuration is incomplete
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Simple Functor Test:</h3>
 *
 * <pre>{@code
 * TypeClassTest.functor(MyFunctor.class)
 *     .instance(functor)
 *     .withKind(validKind)
 *     .withMapper(INT_TO_STRING)
 *     .testAll();
 * }</pre>
 *
 * <h3>Custom Type KindHelper Test:</h3>
 *
 * <pre>{@code
 * TypeClassTest.kindHelper()
 *     .forType(MyType.class, myInstance)
 *     .withHelper(MY_HELPER::widen, MY_HELPER::narrow)
 *     .test();
 * }</pre>
 *
 * <p><strong>Note:</strong> For testing built-in types (Either, Maybe, IO), use {@link
 * CoreTypeTest} instead.
 */
public final class TypeClassTest {

  private TypeClassTest() {
    throw new AssertionError("TypeClassTest is a utility class");
  }

  // =============================================================================
  // Type Class Entry Points
  // =============================================================================

  /**
   * Begins configuration for testing a Functor implementation.
   *
   * <p>Progressive disclosure: Next step is {@code .instance(functor)}
   *
   * @param contextClass The implementation class for error messages (e.g., MyFunctor.class)
   * @param <F> The Functor witness type
   * @return Stage for providing the Functor instance
   */
  public static <F> FunctorTestStage<F> functor(Class<?> contextClass) {
    return new FunctorTestStage<>(contextClass);
  }

  /**
   * Begins configuration for testing an Applicative implementation.
   *
   * <p>Progressive disclosure: Next step is {@code .instance(applicative)}
   *
   * @param contextClass The implementation class for error messages
   * @param <F> The Applicative witness type
   * @return Stage for providing the Applicative instance
   */
  public static <F> ApplicativeTestStage<F> applicative(Class<?> contextClass) {
    return new ApplicativeTestStage<>(contextClass);
  }

  /**
   * Begins configuration for testing a Monad implementation.
   *
   * <p>Progressive disclosure: Next step is {@code .instance(monad)}
   *
   * @param contextClass The implementation class for error messages
   * @param <F> The Monad witness type
   * @return Stage for providing the Monad instance
   */
  public static <F> MonadTestStage<F> monad(Class<?> contextClass) {
    return new MonadTestStage<>(contextClass);
  }

  /**
   * Begins configuration for testing a MonadError implementation.
   *
   * <p>Progressive disclosure: Next step is {@code .instance(monadError)}
   *
   * @param contextClass The implementation class for error messages
   * @param <F> The MonadError witness type
   * @param <E> The error type
   * @return Stage for providing the MonadError instance
   */
  public static <F, E> MonadErrorTestStage<F, E> monadError(Class<?> contextClass) {
    return new MonadErrorTestStage<>(contextClass);
  }

  /* Begins configuration for testing a Selective implementation.
   *
   * <p>Progressive disclosure: Next step is {@code .instance(selective)}
   *
   * @param contextClass The implementation class for error messages
   * @param <F> The Selective witness type
   * @return Stage for providing the Selective instance
   */
  public static <F> SelectiveTestStage<F> selective(Class<?> contextClass) {
    return new SelectiveTestStage<>(contextClass);
  }

  /**
   * Begins configuration for testing a Traverse implementation.
   *
   * <p>Progressive disclosure: Next step is {@code .instance(traverse)}
   *
   * @param contextClass The implementation class for error messages
   * @param <F> The Traverse witness type
   * @return Stage for providing the Traverse instance
   */
  public static <F> TraverseTestStage<F> traverse(Class<?> contextClass) {
    return new TraverseTestStage<>(contextClass);
  }

  /**
   * Begins configuration for testing a Foldable implementation.
   *
   * <p>Progressive disclosure: Next step is {@code .instance(foldable)}
   *
   * @param contextClass The implementation class for error messages
   * @param <F> The Foldable witness type
   * @return Stage for providing the Foldable instance
   */
  public static <F> FoldableTestStage<F> foldable(Class<?> contextClass) {
    return new FoldableTestStage<>(contextClass);
  }

  /**
   * Begins configuration for testing a custom KindHelper implementation.
   *
   * <p>Progressive disclosure: Next step is to specify the type using {@code .forType()}.
   *
   * <h2>Usage Example:</h2>
   *
   * <pre>{@code
   * TypeClassTest.kindHelper()
   *     .forType(MyType.class, myInstance)
   *     .withHelper(MY_HELPER::widen, MY_HELPER::narrow)
   *     .test();
   * }</pre>
   *
   * <p><strong>Note:</strong> For built-in types (Either, Maybe, IO), use {@link
   * CoreTypeTest#eitherKindHelper(org.higherkindedj.hkt.either.Either)}, {@link
   * CoreTypeTest#maybeKindHelper(org.higherkindedj.hkt.maybe.Maybe)}, or {@link
   * CoreTypeTest#ioKindHelper(org.higherkindedj.hkt.io.IO)} instead.
   *
   * @return Builder for KindHelper testing
   */
  public static KindHelperTestStage.KindHelperBuilder kindHelper() {
    return KindHelperTestStage.builder();
  }
}
