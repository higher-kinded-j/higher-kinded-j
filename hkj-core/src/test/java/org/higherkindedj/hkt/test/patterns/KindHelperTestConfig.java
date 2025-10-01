// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;

/**
 * Configuration-based testing for KindHelper implementations.
 *
 * <p>Provides a fluent, type-safe API for testing widen/narrow operations with better type
 * inference than the generic builder approach.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Complete Testing:</h3>
 *
 * <pre>{@code
 * KindHelperTestConfig.forEither(Either.right("test"), EITHER)
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * KindHelperTestConfig.forMaybe(Maybe.just("test"), MAYBE)
 *     .skipValidations()
 *     .test();
 * }</pre>
 */
public final class KindHelperTestConfig {

  private KindHelperTestConfig() {
    throw new AssertionError("KindHelperTestConfig is a utility class");
  }

  // =============================================================================
  // Factory Methods
  // =============================================================================

  /** Creates a test configuration for Either KindHelper. */
  public static <L, R> EitherKindHelperTest<L, R> forEither(
      Either<L, R> validInstance, EitherKindHelper helper) {
    return new EitherKindHelperTest<>(validInstance, helper);
  }

  /** Creates a test configuration for Maybe KindHelper. */
  public static <A> MaybeKindHelperTest<A> forMaybe(
      Maybe<A> validInstance, MaybeKindHelper helper) {
    return new MaybeKindHelperTest<>(validInstance, helper);
  }

  // =============================================================================
  // Base Configuration Class
  // =============================================================================

  /** Base class for KindHelper test configurations. */
  public abstract static class BaseKindHelperTest<T, F, A> {
    protected final T validInstance;

    private boolean includeRoundTrip = true;
    private boolean includeValidations = true;
    private boolean includeInvalidType = true;
    private boolean includeIdempotency = true;
    private boolean includeEdgeCases = true;
    private boolean includePerformance = false;
    private boolean includeConcurrency = false;

    protected BaseKindHelperTest(T validInstance) {
      this.validInstance = validInstance;
    }

    @SuppressWarnings("unchecked")
    protected <SELF extends BaseKindHelperTest<T, F, A>> SELF self() {
      return (SELF) this;
    }

    public <SELF extends BaseKindHelperTest<T, F, A>> SELF skipRoundTrip() {
      this.includeRoundTrip = false;
      return self();
    }

    public <SELF extends BaseKindHelperTest<T, F, A>> SELF skipValidations() {
      this.includeValidations = false;
      return self();
    }

    public <SELF extends BaseKindHelperTest<T, F, A>> SELF skipInvalidType() {
      this.includeInvalidType = false;
      return self();
    }

    public <SELF extends BaseKindHelperTest<T, F, A>> SELF skipIdempotency() {
      this.includeIdempotency = false;
      return self();
    }

    public <SELF extends BaseKindHelperTest<T, F, A>> SELF skipEdgeCases() {
      this.includeEdgeCases = false;
      return self();
    }

    public <SELF extends BaseKindHelperTest<T, F, A>> SELF skipPerformance() {
      this.includePerformance = false;
      return self();
    }

    public <SELF extends BaseKindHelperTest<T, F, A>> SELF withPerformanceTests() {
      this.includePerformance = true;
      return self();
    }

    public <SELF extends BaseKindHelperTest<T, F, A>> SELF withConcurrencyTests() {
      this.includeConcurrency = true;
      return self();
    }

    public void test() {
      KindHelperTestPattern.KindHelper<T, F, A> helper = createHelper();
      Class<T> targetType = getTargetType();

      if (includeRoundTrip) {
        KindHelperTestPattern.testRoundTripWithHelper(validInstance, helper);
      }
      if (includeValidations) {
        KindHelperTestPattern.testValidationsWithHelper(targetType, helper);
      }
      if (includeInvalidType) {
        KindHelperTestPattern.testInvalidTypeWithHelper(targetType, helper);
      }
      if (includeIdempotency) {
        KindHelperTestPattern.testIdempotencyWithHelper(validInstance, helper);
      }
      if (includeEdgeCases) {
        KindHelperTestPattern.testEdgeCasesWithHelper(validInstance, targetType, helper);
      }
      if (includePerformance) {
        KindHelperTestPattern.testPerformance(validInstance, helper::widen, helper::narrow);
      }
      if (includeConcurrency) {
        KindHelperTestPattern.testConcurrentAccess(validInstance, helper::widen, helper::narrow);
      }
    }

    protected abstract KindHelperTestPattern.KindHelper<T, F, A> createHelper();

    protected abstract Class<T> getTargetType();
  }

  // =============================================================================
  // Either-Specific Configuration
  // =============================================================================

  public static final class EitherKindHelperTest<L, R>
      extends BaseKindHelperTest<Either<L, R>, EitherKind.Witness<L>, R> {

    private final EitherKindHelper helper;

    private EitherKindHelperTest(Either<L, R> validInstance, EitherKindHelper helper) {
      super(validInstance);
      this.helper = helper;
    }

    @Override
    protected KindHelperTestPattern.KindHelper<Either<L, R>, EitherKind.Witness<L>, R>
        createHelper() {
      return KindHelperTestPattern.adapt(
          (Either<L, R> e) -> helper.widen(e),
          (Kind<EitherKind.Witness<L>, R> k) -> helper.narrow(k));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<Either<L, R>> getTargetType() {
      return (Class<Either<L, R>>) (Class<?>) Either.class;
    }
  }

  // =============================================================================
  // Maybe-Specific Configuration
  // =============================================================================

  public static final class MaybeKindHelperTest<A>
      extends BaseKindHelperTest<Maybe<A>, MaybeKind.Witness, A> {

    private final MaybeKindHelper helper;

    private MaybeKindHelperTest(Maybe<A> validInstance, MaybeKindHelper helper) {
      super(validInstance);
      this.helper = helper;
    }

    @Override
    protected KindHelperTestPattern.KindHelper<Maybe<A>, MaybeKind.Witness, A> createHelper() {
      return KindHelperTestPattern.adapt(
          (Maybe<A> m) -> helper.widen(m), (Kind<MaybeKind.Witness, A> k) -> helper.narrow(k));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<Maybe<A>> getTargetType() {
      return (Class<Maybe<A>>) (Class<?>) Maybe.class;
    }
  }
}
