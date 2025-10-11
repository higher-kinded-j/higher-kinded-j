package org.higherkindedj.hkt.test.api.kind;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;

/**
 * Enhanced KindHelper test stage with specialized factory methods for better type inference.
 *
 * <h2>Key Improvements:</h2>
 *
 * <ul>
 *   <li>Specialized factory methods per type (Either, Maybe, etc.)
 *   <li>Automatic helper adaptation - no manual work needed
 *   <li>Type-safe fluent API with proper inference
 *   <li>Consistent with TypeClassTest entry point
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Simple Test:</h3>
 *
 * <pre>{@code
 * TypeClassTest.kindHelper()
 *
 * .forEither(Either.right("test"))
 *
 *
 * .test();
 *
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * TypeClassTest.kindHelper()
 *
 * .forMaybe(Maybe.just(42))
 *
 *
 * .skipValidations()
 *
 *
 * .test();
 *
 * }</pre>
 *
 * <h3>Custom KindHelper:</h3>
 *
 * <pre>{@code
 * TypeClassTest.kindHelper()
 *
 * .forType(MyType.class, myInstance)
 *
 *
 * .withHelper(MY_HELPER::widen, MY_HELPER::narrow)
 *
 *
 * .test();
 *
 * }</pre>
 */
public final class KindHelperTestStage {

  private KindHelperTestStage() {}

  /**
   * Creates a new KindHelper test builder.
   *
   * @return A builder for configuring KindHelper tests
   */
  public static KindHelperBuilder builder() {
    return new KindHelperBuilder();
  }

  // ============================================================================
  // Builder with specialized factory methods
  // ============================================================================
  public static final class KindHelperBuilder {
    private KindHelperBuilder() {}

    /**
     * Test Either KindHelper with automatic helper detection.
     *
     * <p>Automatically uses EitherKindHelper.EITHER for widen/narrow operations.
     *
     * @param instance The Either instance to test
     * @param <L> The Left type
     * @param <R> The Right type
     * @return Configuration stage for Either testing
     */
    public <L, R> EitherKindHelperConfig<L, R> forEither(
        org.higherkindedj.hkt.either.Either<L, R> instance) {
      return new EitherKindHelperConfig<>(instance);
    }

    /**
     * Test Maybe KindHelper with automatic helper detection.
     *
     * <p>Automatically uses MaybeKindHelper.MAYBE for widen/narrow operations.
     *
     * @param instance The Maybe instance to test
     * @param <A> The value type
     * @return Configuration stage for Maybe testing
     */
    public <A> MaybeKindHelperConfig<A> forMaybe(org.higherkindedj.hkt.maybe.Maybe<A> instance) {
      return new MaybeKindHelperConfig<>(instance);
    }

    /**
     * Test IO KindHelper with automatic helper detection.
     *
     * <p>Automatically uses IOKindHelper.IO_OP for widen/narrow operations.
     *
     * @param instance The IO instance to test
     * @param <A> The value type
     * @return Configuration stage for IO testing
     */
    public <A> IOKindHelperConfig<A> forIO(org.higherkindedj.hkt.io.IO<A> instance) {
      return new IOKindHelperConfig<>(instance);
    }

    /**
     * Test custom KindHelper with explicit configuration.
     *
     * <p>Use this for testing custom type classes not built into the framework.
     *
     * @param targetClass The target type class
     * @param instance The instance to test
     * @param <T> The target type
     * @return Configuration stage for custom KindHelper
     */
    public <T> CustomKindHelperConfig<T> forType(Class<T> targetClass, T instance) {
      return new CustomKindHelperConfig<>(targetClass, instance);
    }
  }

  // ============================================================================
  // Either-Specific Configuration (with automatic helper)
  // ============================================================================
  public static final class EitherKindHelperConfig<L, R>
      extends BaseKindHelperConfig<Either<L, R>, EitherKind.Witness<L>, R> {
    private static final EitherKindHelper EITHER = EitherKindHelper.EITHER;

    private EitherKindHelperConfig(Either<L, R> instance) {
      super(
          instance, getEitherClass(), either -> EITHER.widen(either), kind -> EITHER.narrow(kind));
    }

    @SuppressWarnings("unchecked")
    private static <L, R> Class<Either<L, R>> getEitherClass() {
      return (Class<Either<L, R>>) (Class<?>) Either.class;
    }

    @Override
    protected EitherKindHelperConfig<L, R> self() {
      return this;
    }
  }

  // ============================================================================
  // Maybe-Specific Configuration (with automatic helper)
  // ============================================================================
  public static final class MaybeKindHelperConfig<A>
      extends BaseKindHelperConfig<Maybe<A>, MaybeKind.Witness, A> {
    private static final MaybeKindHelper MAYBE = MaybeKindHelper.MAYBE;

    private MaybeKindHelperConfig(Maybe<A> instance) {
      super(instance, getMaybeClass(), maybe -> MAYBE.widen(maybe), kind -> MAYBE.narrow(kind));
    }

    @SuppressWarnings("unchecked")
    private static <A> Class<org.higherkindedj.hkt.maybe.Maybe<A>> getMaybeClass() {
      return (Class<org.higherkindedj.hkt.maybe.Maybe<A>>)
          (Class<?>) org.higherkindedj.hkt.maybe.Maybe.class;
    }

    @Override
    protected MaybeKindHelperConfig<A> self() {
      return this;
    }
  }

  // =============================================================================
  // IO-Specific Configuration (with automatic helper)
  // =============================================================================
  public static final class IOKindHelperConfig<A>
      extends BaseKindHelperConfig<IO<A>, IOKind.Witness, A> {
    private static final IOKindHelper IO_OP = IOKindHelper.IO_OP;

    private IOKindHelperConfig(IO<A> instance) {
      super(instance, getIOClass(), io -> IO_OP.widen(io), kind -> IO_OP.narrow(kind));
    }

    @SuppressWarnings("unchecked")
    private static <A> Class<IO<A>> getIOClass() {
      return (Class<IO<A>>) (Class<?>) IO.class;
    }

    @Override
    protected IOKindHelperConfig<A> self() {
      return this;
    }
  }

  // ============================================================================
  // Custom KindHelper Configuration (for extensibility)
  // ============================================================================
  public static final class CustomKindHelperConfig<T> {
    private final Class<T> targetClass;
    private final T instance;

    private CustomKindHelperConfig(Class<T> targetClass, T instance) {
      this.targetClass = targetClass;
      this.instance = instance;
    }

    /**
     * Provides widen/narrow functions for the custom type.
     *
     * @param widenFunc Function to widen T to Kind
     * @param narrowFunc Function to narrow Kind to T
     * @param <F> The witness type
     * @param <A> The value type
     * @return Configuration stage for testing
     */
    public <F, A> BaseKindHelperConfig<T, F, A> withHelper(
        Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {
      return new BaseKindHelperConfig<>(instance, targetClass, widenFunc, narrowFunc);
    }
  }

  // ============================================================================
  // Base Configuration with fluent API
  // ============================================================================
  public static class BaseKindHelperConfig<T, F, A> {
    protected final T instance;
    protected final Class<T> targetClass;
    protected final Function<T, Kind<F, A>> widenFunc;
    protected final Function<Kind<F, A>, T> narrowFunc;
    // Test selection flags
    private boolean includeRoundTrip = true;
    private boolean includeValidations = true;
    private boolean includeInvalidType = true;
    private boolean includeIdempotency = true;
    private boolean includeEdgeCases = true;
    private boolean includePerformance = false;
    private boolean includeConcurrency = false;

    protected BaseKindHelperConfig(
        T instance,
        Class<T> targetClass,
        java.util.function.Function<T, Kind<F, A>> widenFunc,
        java.util.function.Function<Kind<F, A>, T> narrowFunc) {
      this.instance = instance;
      this.targetClass = targetClass;
      this.widenFunc = widenFunc;
      this.narrowFunc = narrowFunc;
    }

    @SuppressWarnings("unchecked")
    protected <SELF extends BaseKindHelperConfig<T, F, A>> SELF self() {
      return (SELF) this;
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipRoundTrip() {
      this.includeRoundTrip = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipValidations() {
      this.includeValidations = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipInvalidType() {
      this.includeInvalidType = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipIdempotency() {
      this.includeIdempotency = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipEdgeCases() {
      this.includeEdgeCases = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipPerformance() {
      this.includePerformance = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF withPerformanceTests() {
      this.includePerformance = true;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF withConcurrencyTests() {
      this.includeConcurrency = true;
      return self();
    }

    /** Executes all configured tests. */
    public void test() {
      KindHelperTestPattern.KindHelper<T, F, A> helper =
          KindHelperTestPattern.adapt(widenFunc, narrowFunc);

      if (includeRoundTrip) {
        KindHelperTestPattern.testRoundTripWithHelper(instance, helper);
      }
      if (includeValidations) {
        KindHelperTestPattern.testValidationsWithHelper(targetClass, helper);
      }
      if (includeInvalidType) {
        KindHelperTestPattern.testInvalidTypeWithHelper(targetClass, helper);
      }
      if (includeIdempotency) {
        KindHelperTestPattern.testIdempotencyWithHelper(instance, helper);
      }
      if (includeEdgeCases) {
        KindHelperTestPattern.testEdgeCasesWithHelper(instance, targetClass, helper);
      }
      if (includePerformance) {
        KindHelperTestPattern.testPerformance(instance, widenFunc, narrowFunc);
      }
      if (includeConcurrency) {
        KindHelperTestPattern.testConcurrentAccess(instance, widenFunc, narrowFunc);
      }
    }

    /** Executes all tests including implementation standards validation. */
    public void testWithValidation(Class<?> helperClass) {
      test();
      KindHelperTestPattern.validateImplementationStandards(targetClass, helperClass);
    }
  }
}
