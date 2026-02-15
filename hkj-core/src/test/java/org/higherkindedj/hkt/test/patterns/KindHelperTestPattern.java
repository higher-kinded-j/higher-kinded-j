// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;

/**
 * Test pattern support for KindHelper implementations.
 *
 * <p>Provides package-private helper methods used by {@link KindHelperTestConfig}.
 *
 * <h2>Usage:</h2>
 *
 * <p>Use {@link KindHelperTestConfig} for type-safe testing:
 *
 * <pre>{@code
 * KindHelperTestConfig.forEither(Either.right("test"), EITHER)
 *     .test();
 *
 * KindHelperTestConfig.forMaybe(Maybe.just("test"), MAYBE)
 *     .skipValidations()
 *     .test();
 * }</pre>
 *
 * @see KindHelperTestConfig
 */
public final class KindHelperTestPattern {

  private KindHelperTestPattern() {
    throw new AssertionError("KindHelperTestPattern is a utility class");
  }

  /** Tests round-trip widen/narrow preserves identity. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void testRoundTripWithHelper(
      T validInstance, KindHelper<T, F, A> helper) {
    Kind<F, A> widened = helper.widen(validInstance);
    T narrowed = helper.narrow(widened);

    assertThat(narrowed)
        .as("Round-trip widen/narrow should preserve identity")
        .isSameAs(validInstance);
  }

  /** Tests null parameter validations using production validators. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void testValidationsWithHelper(
      Class<T> targetType, KindHelper<T, F, A> helper) {
    ValidationTestBuilder.create()
        .assertWidenNull(() -> helper.widen(null), targetType)
        .assertNarrowNull(() -> helper.narrow(null), targetType)
        .execute();
  }

  /** Tests invalid Kind type validation with proper error handling. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void testInvalidTypeWithHelper(
      Class<T> targetType, KindHelper<T, F, A> helper) {
    Kind<F, A> invalidKind = createDummyKind("invalid_" + targetType.getSimpleName());

    assertThatThrownBy(() -> helper.narrow(invalidKind))
        .isInstanceOf(KindUnwrapException.class)
        .satisfies(
            throwable -> {
              String message = throwable.getMessage();
              assertThat(message)
                  .as("Error message should indicate invalid Kind type")
                  .satisfiesAnyOf(
                      msg ->
                          assertThat(msg)
                              .contains(
                                  "Kind instance cannot be narrowed to "
                                      + targetType.getSimpleName()));
            });
  }

  /** Tests multiple round-trips preserve idempotency. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void testIdempotencyWithHelper(
      T validInstance, KindHelper<T, F, A> helper) {
    T current = validInstance;
    for (int i = 0; i < 3; i++) {
      Kind<F, A> widened = helper.widen(current);
      current = helper.narrow(widened);
    }

    assertThat(current).as("Multiple round-trips should preserve identity").isSameAs(validInstance);
  }

  /** Tests edge cases and boundary conditions. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void testEdgeCasesWithHelper(
      T validInstance, Class<T> targetType, KindHelper<T, F, A> helper) {

    Kind<F, A> widened = helper.widen(validInstance);
    assertThat(widened).as("widen should always return non-null Kind").isNotNull();

    T narrowed = helper.narrow(widened);
    assertThat(narrowed).as("narrow should return non-null for valid Kind").isNotNull();
    assertThat(narrowed)
        .as("narrowed result should be instance of target type")
        .isInstanceOf(targetType);
  }

  /** Validates that KindHelper implementation follows standardised patterns. */
  public static void validateImplementationStandards(Class<?> targetType, Class<?> helperClass) {
    assertThat(helperClass).as("KindHelper implementation class should be non-null").isNotNull();
    assertThat(targetType).as("Target type class should be non-null").isNotNull();

    String helperName = helperClass.getSimpleName();
    String targetName = targetType.getSimpleName();

    assertThat(helperName)
        .as("KindHelper should follow naming convention")
        .satisfiesAnyOf(
            name -> assertThat(name).contains(targetName),
            name -> assertThat(name).contains("KindHelper"),
            name -> assertThat(name).contains("Helper"));

    boolean hasWiden =
        Arrays.stream(helperClass.getMethods()).anyMatch(m -> m.getName().equals("widen"));
    boolean hasNarrow =
        Arrays.stream(helperClass.getMethods()).anyMatch(m -> m.getName().equals("narrow"));

    assertThat(hasWiden && hasNarrow)
        .as("KindHelper should have widen and narrow methods")
        .isTrue();
  }

  /** Tests performance characteristics of widen/narrow operations. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void testPerformance(
      T validInstance, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    int iterations = 10000;

    // Warm up
    for (int i = 0; i < 1000; i++) {
      Kind<F, A> widened = widenFunc.apply(validInstance);
      narrowFunc.apply(widened);
    }

    long widenStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      widenFunc.apply(validInstance);
    }
    long widenTime = System.nanoTime() - widenStart;

    Kind<F, A> widened = widenFunc.apply(validInstance);
    long narrowStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      narrowFunc.apply(widened);
    }
    long narrowTime = System.nanoTime() - narrowStart;

    double widenAvgNanos = (double) widenTime / iterations;
    double narrowAvgNanos = (double) narrowTime / iterations;

    assertThat(widenAvgNanos)
        .as("widen operation should be fast (< 2000ns average)")
        .isLessThan(2000.0);

    assertThat(narrowAvgNanos)
        .as("narrow operation should be fast (< 2000ns average)")
        .isLessThan(2000.0);
  }

  /** Tests concurrent access to ensure thread safety. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> void testConcurrentAccess(
      T validInstance, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    CompletableFuture<Void>[] futures = new CompletableFuture[10];

    for (int i = 0; i < futures.length; i++) {
      futures[i] =
          CompletableFuture.runAsync(
              () -> {
                Kind<F, A> widened = widenFunc.apply(validInstance);
                T narrowed = narrowFunc.apply(widened);
                assertThat(narrowed).isSameAs(validInstance);
              });
    }

    CompletableFuture<Void> allTasks = CompletableFuture.allOf(futures);

    assertThat(allTasks)
        .as("All concurrent operations should complete successfully")
        .succeedsWithin(Duration.ofSeconds(5));
  }

  // =============================================================================
  // KindHelper Interface and Adapter
  // =============================================================================

  /** Interface for KindHelper implementations to provide better type safety. */
  public interface KindHelper<T, F extends WitnessArity<TypeArity.Unary>, A> {
    Kind<F, A> widen(T instance);

    T narrow(Kind<F, A> kind);
  }

  /** Adapter for existing enum-based KindHelpers. */
  public static <T, F extends WitnessArity<TypeArity.Unary>, A> KindHelper<T, F, A> adapt(
      Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {
    return new KindHelper<T, F, A>() {
      @Override
      public Kind<F, A> widen(T instance) {
        return widenFunc.apply(instance);
      }

      @Override
      public T narrow(Kind<F, A> kind) {
        return narrowFunc.apply(kind);
      }
    };
  }

  static <F extends WitnessArity<TypeArity.Unary>, A> Kind<F, A> createDummyKind(
      String identifier) {
    return new Kind<F, A>() {
      @Override
      public String toString() {
        return "DummyKind{" + identifier + "}";
      }
    };
  }
}
