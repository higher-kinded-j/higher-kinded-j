// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api;

import org.higherkindedj.hkt.test.api.applicative.ApplicativeTestStage;
import org.higherkindedj.hkt.test.api.type.either.EitherCoreTestStage;
import org.higherkindedj.hkt.test.api.foldable.FoldableTestStage;
import org.higherkindedj.hkt.test.api.functor.FunctorTestStage;
import org.higherkindedj.hkt.test.api.kind.KindHelperTestStage;
import org.higherkindedj.hkt.test.api.type.io.IOCoreTestStage;
import org.higherkindedj.hkt.test.api.type.maybe.MaybeCoreTestStage;
import org.higherkindedj.hkt.test.api.monad.MonadTestStage;
import org.higherkindedj.hkt.test.api.monaderror.MonadErrorTestStage;
import org.higherkindedj.hkt.test.api.traverse.TraverseTestStage;

/**
 * Entry point for hierarchical, progressive disclosure type class testing.
 *
 * <p>This API provides a fluent, stage-based approach to testing both type class implementations
 * and core types where each step reveals only contextually relevant options through IDE autocomplete.
 *
 * <h2>Key Features:</h2>
 *
 * <ul>
 *   <li>Progressive disclosure - each stage shows only relevant next steps
 *   <li>Type-safe configuration - impossible to skip required parameters
 *   <li>Hierarchical structure - mirrors type class hierarchy
 *   <li>Clear error messages - helpful guidance when configuration is incomplete
 *   <li>Core type testing - test Either, Maybe, and other core types
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
 * <h3>Core Type Test (Either):</h3>
 *
 * <pre>{@code
 * TypeClassTest.either(Either.class)
 *     .withLeft(Either.left("error"))
 *     .withRight(Either.right(42))
 *     .withMappers(INT_TO_STRING)
 *     .testAll();
 * }</pre>
 *
 * <h3>Core Type Test (Maybe):</h3>
 *
 * <pre>{@code
 * TypeClassTest.maybe(Maybe.class)
 *     .withJust(Maybe.just(42))
 *     .withNothing(Maybe.nothing())
 *     .withMapper(INT_TO_STRING)
 *     .testAll();
 * }</pre>
 */
public final class TypeClassTest {

    private TypeClassTest() {
        throw new AssertionError("TypeClassTest is a utility class and should not be instantiated");
    }

    // =============================================================================
    // Type Class Entry Points
    // =============================================================================

    /**
     * Begins configuration for testing a Functor implementation.
     *
     * <p>Progressive disclosure: Next step is {@code .instance(functor)}
     *
     * @param contextClass The implementation class for error messages (e.g., EitherFunctor.class)
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
     * Begins configuration for testing a KindHelper implementation.
     *
     * <p>Progressive disclosure: Next step is to specify the type using specialized methods.
     *
     * <h3>Usage Examples:</h3>
     * <h4>Either KindHelper:</h4>
     * <pre>{@code
     * TypeClassTest.kindHelper()
     *     .forEither(Either.right("test"))
     *     .test();
     * }</pre>
     *
     * <h4>Maybe KindHelper:</h4>
     * <pre>{@code
     * TypeClassTest.kindHelper()
     *     .forMaybe(Maybe.just(42))
     *     .skipValidations()
     *     .test();
     * }</pre>
     *
     * @return Builder for KindHelper testing
     */
    public static KindHelperTestStage.KindHelperBuilder kindHelper() {
        return KindHelperTestStage.builder();
    }

    // =============================================================================
    // Core Type Entry Points
    // =============================================================================

    /**
     * Begins configuration for testing a core Either implementation.
     *
     * <p>Tests Either-specific operations like fold, ifLeft, ifRight, getLeft, getRight,
     * as well as factory methods and basic operations.
     *
     * <p>Progressive disclosure: Next step is {@code .withLeft(...)} or {@code .withRight(...)}
     *
     * <h3>Usage Example:</h3>
     * <pre>{@code
     * TypeClassTest.either(Either.class)
     *     .withLeft(Either.left("error"))
     *     .withRight(Either.right(42))
     *     .withMappers(INT_TO_STRING)
     *     .testAll();
     * }</pre>
     *
     * @param contextClass The implementation class for error messages (e.g., Either.class)
     * @param <L> The Left type
     * @param <R> The Right type
     * @return Stage for providing test instances
     */
    public static <L, R> EitherCoreTestStage<L, R> either(Class<?> contextClass) {
        return new EitherCoreTestStage<>(contextClass);
    }

    /**
     * Begins configuration for testing a core Maybe implementation.
     *
     * <p>Tests Maybe-specific operations like get, orElse, orElseGet, isJust, isNothing,
     * as well as factory methods and basic operations.
     *
     * <p>Progressive disclosure: Next step is {@code .withJust(...)} or {@code .withNothing(...)}
     *
     * <h3>Usage Example:</h3>
     * <pre>{@code
     * TypeClassTest.maybe(Maybe.class)
     *     .withJust(Maybe.just(42))
     *     .withNothing(Maybe.nothing())
     *     .withMapper(INT_TO_STRING)
     *     .testAll();
     * }</pre>
     *
     * @param contextClass The implementation class for error messages (e.g., Maybe.class)
     * @param <T> The value type
     * @return Stage for providing test instances
     */
    public static <T> MaybeCoreTestStage<T> maybe(Class<?> contextClass) {
        return new MaybeCoreTestStage<>(contextClass);
    }


    public static <T> IOCoreTestStage<T> io(Class<?> contextClass) {
        return new IOCoreTestStage<>(contextClass);
    }

}