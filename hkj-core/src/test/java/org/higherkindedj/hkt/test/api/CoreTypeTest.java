package org.higherkindedj.hkt.test.api;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.test.api.coretype.either.EitherCoreTestStage;
import org.higherkindedj.hkt.test.api.coretype.either.EitherKindHelperTest;
import org.higherkindedj.hkt.test.api.coretype.io.IOCoreTestStage;
import org.higherkindedj.hkt.test.api.coretype.io.IOKindHelperTest;
import org.higherkindedj.hkt.test.api.coretype.lazy.LazyCoreTestStage;
import org.higherkindedj.hkt.test.api.coretype.lazy.LazyKindHelperTest;
import org.higherkindedj.hkt.test.api.coretype.maybe.MaybeCoreTestStage;
import org.higherkindedj.hkt.test.api.coretype.maybe.MaybeKindHelperTest;

/**
 * Entry point for core type implementation testing.
 *
 * <p>Use this for testing the built-in Either, Maybe, IO, and Lazy types and their operations.
 *
 * <h2>Core Type Testing Examples:</h2>
 *
 * <h3>Test Either Operations:</h3>
 * <pre>{@code
 * CoreTypeTest.either(Either.class)
 *     .withLeft(Either.left("error"))
 *     .withRight(Either.right(42))
 *     .withMappers(INT_TO_STRING)
 *     .testAll();
 * }</pre>
 *
 * <h3>Test Maybe Operations:</h3>
 * <pre>{@code
 * CoreTypeTest.maybe(Maybe.class)
 *     .withJust(Maybe.just(42))
 *     .withNothing(Maybe.nothing())
 *     .withMapper(INT_TO_STRING)
 *     .testAll();
 * }</pre>
 *
 * <h3>Test Lazy Operations:</h3>
 * <pre>{@code
 * CoreTypeTest.lazy(Lazy.class)
 *     .withDeferred(Lazy.defer(() -> 42))
 *     .withNow(Lazy.now(24))
 *     .withMappers(Object::toString)
 *     .testAll();
 * }</pre>
 *
 * <h2>KindHelper Testing Examples:</h2>
 *
 * <h3>Test Either KindHelper:</h3>
 * <pre>{@code
 * CoreTypeTest.eitherKindHelper(Either.right("test"))
 *     .test();
 * }</pre>
 *
 * <h3>Test Maybe KindHelper with Selective Tests:</h3>
 * <pre>{@code
 * CoreTypeTest.maybeKindHelper(Maybe.just(42))
 *     .skipValidations()
 *     .test();
 * }</pre>
 *
 * <h3>Test Lazy KindHelper:</h3>
 * <pre>{@code
 * CoreTypeTest.lazyKindHelper(Lazy.now("test"))
 *     .test();
 * }</pre>
 */
public final class CoreTypeTest {

    private CoreTypeTest() {
        throw new AssertionError("CoreTypeTest is a utility class");
    }

    // =============================================================================
    // Core Type Testing
    // =============================================================================

    /**
     * Begins configuration for testing an Either implementation.
     *
     * <p>Tests Either-specific operations like fold, ifLeft, ifRight,
     * getLeft, getRight, as well as factory methods and basic operations.
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
     * Begins configuration for testing a Maybe implementation.
     *
     * <p>Tests Maybe-specific operations like get, orElse, orElseGet, isJust, isNothing,
     * as well as factory methods and basic operations.
     *
     * @param contextClass The implementation class for error messages (e.g., Maybe.class)
     * @param <T> The value type
     * @return Stage for providing test instances
     */
    public static <T> MaybeCoreTestStage<T> maybe(Class<?> contextClass) {
        return new MaybeCoreTestStage<>(contextClass);
    }

    /**
     * Begins configuration for testing an IO implementation.
     *
     * <p>Tests IO-specific operations like delay, unsafeRunSync,
     * as well as lazy evaluation semantics.
     *
     * @param contextClass The implementation class for error messages (e.g., IO.class)
     * @param <T> The value type
     * @return Stage for providing test instances
     */
    public static <T> IOCoreTestStage<T> io(Class<?> contextClass) {
        return new IOCoreTestStage<>(contextClass);
    }

    /**
     * Begins configuration for testing a Lazy implementation.
     *
     * <p>Tests Lazy-specific operations like defer, now, force, map, flatMap,
     * as well as memoisation and thread-safety semantics.
     *
     * @param contextClass The implementation class for error messages (e.g., Lazy.class)
     * @param <A> The value type
     * @return Stage for providing test instances
     */
    public static <A> LazyCoreTestStage<A> lazy(Class<?> contextClass) {
        return new LazyCoreTestStage<>(contextClass);
    }

    // =============================================================================
    // KindHelper Testing for Core Types
    // =============================================================================

    /**
     * Test Either KindHelper with automatic helper detection.
     *
     * <p>Automatically uses EitherKindHelper.EITHER for widen/narrow operations.
     *
     * <h2>Usage Example:</h2>
     * <pre>{@code
     * CoreTypeTest.eitherKindHelper(Either.right("test"))
     *     .test();
     *
     * // With selective testing
     * CoreTypeTest.eitherKindHelper(Either.right("test"))
     *     .skipValidations()
     *     .withPerformanceTests()
     *     .test();
     * }</pre>
     *
     * @param instance The Either instance to test
     * @param <L> The Left type
     * @param <R> The Right type
     * @return Configuration stage for Either KindHelper testing
     */
    public static <L, R> EitherKindHelperTest<L, R> eitherKindHelper(Either<L, R> instance) {
        return new EitherKindHelperTest<>(instance);
    }

    /**
     * Test Maybe KindHelper with automatic helper detection.
     *
     * <p>Automatically uses MaybeKindHelper.MAYBE for widen/narrow operations.
     *
     * <h2>Usage Example:</h2>
     * <pre>{@code
     * CoreTypeTest.maybeKindHelper(Maybe.just(42))
     *     .test();
     *
     * // With selective testing
     * CoreTypeTest.maybeKindHelper(Maybe.just(42))
     *     .skipValidations()
     *     .test();
     * }</pre>
     *
     * @param instance The Maybe instance to test
     * @param <A> The value type
     * @return Configuration stage for Maybe KindHelper testing
     */
    public static <A> MaybeKindHelperTest<A> maybeKindHelper(Maybe<A> instance) {
        return new MaybeKindHelperTest<>(instance);
    }

    /**
     * Test IO KindHelper with automatic helper detection.
     *
     * <p>Automatically uses IOKindHelper.IO_OP for widen/narrow operations.
     *
     * <h2>Usage Example:</h2>
     * <pre>{@code
     * CoreTypeTest.ioKindHelper(IO.delay(() -> "test"))
     *     .test();
     *
     * // With performance and concurrency testing
     * CoreTypeTest.ioKindHelper(IO.delay(() -> "test"))
     *     .withPerformanceTests()
     *     .withConcurrencyTests()
     *     .test();
     * }</pre>
     *
     * @param instance The IO instance to test
     * @param <A> The value type
     * @return Configuration stage for IO KindHelper testing
     */
    public static <A> IOKindHelperTest<A> ioKindHelper(IO<A> instance) {
        return new IOKindHelperTest<>(instance);
    }

    /**
     * Test Lazy KindHelper with automatic helper detection.
     *
     * <p>Automatically uses LazyKindHelper.LAZY for widen/narrow operations.
     *
     * <h2>Usage Example:</h2>
     * <pre>{@code
     * CoreTypeTest.lazyKindHelper(Lazy.now("test"))
     *     .test();
     *
     * // With performance and concurrency testing
     * CoreTypeTest.lazyKindHelper(Lazy.defer(() -> 42))
     *     .withPerformanceTests()
     *     .withConcurrencyTests()
     *     .test();
     * }</pre>
     *
     * @param instance The Lazy instance to test
     * @param <A> The value type
     * @return Configuration stage for Lazy KindHelper testing
     */
    public static <A> LazyKindHelperTest<A> lazyKindHelper(Lazy<A> instance) {
        return new LazyKindHelperTest<>(instance);
    }
}