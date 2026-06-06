// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.fixtures.TestFunctions;
import org.higherkindedj.hkt.test.fixtures.TypeClassTestBase;
import org.jspecify.annotations.Nullable;

/**
 * Base class for Lazy type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all Lazy
 * type class tests, eliminating duplication across Functor, Applicative, and Monad tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Lazy tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_LAZY_VALUE} - The primary test value (42)
 *   <li>{@link #ALTERNATIVE_LAZY_VALUE} - A secondary test value (24)
 * </ul>
 *
 * <h2>Lazy Semantics</h2>
 *
 * <p>Unlike Maybe which has Just/Nothing states, Lazy has three states:
 *
 * <ul>
 *   <li>Unevaluated - The computation has not been forced yet
 *   <li>Evaluated (success) - The computation succeeded and the value is cached
 *   <li>Evaluated (failed) - The computation failed and the exception is cached
 * </ul>
 *
 * <h2>Equality Checking</h2>
 *
 * <p>Since Lazy instances use reference equality, the equality checker forces both Lazy instances
 * and compares their results. This means equality checking will trigger evaluation.
 */
abstract class LazyTestBase extends TypeClassTestBase<LazyKind.Witness, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default value for Lazy instances in tests. */
  protected static final Integer DEFAULT_LAZY_VALUE = 42;

  /** Alternative value for Lazy instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_LAZY_VALUE = 24;

  /** Shared counter for tracking evaluation in tests. */
  protected static final AtomicInteger COUNTER = new AtomicInteger(0);

  /**
   * Creates a ThrowableSupplier that increments the shared counter and returns a success value.
   * Includes a small delay to test memoisation behaviour.
   */
  protected static ThrowableSupplier<String> successSupplier() {
    return () -> {
      COUNTER.incrementAndGet();
      Thread.sleep(5); // Small delay to test memoisation
      return "SuccessValue";
    };
  }

  /** Creates a ThrowableSupplier that increments the counter and returns null. */
  protected static ThrowableSupplier<String> nullSupplier() {
    return () -> {
      COUNTER.incrementAndGet();
      return null;
    };
  }

  /** Creates a ThrowableSupplier that increments the counter and throws a RuntimeException. */
  protected static ThrowableSupplier<String> runtimeFailSupplier() {
    return () -> {
      COUNTER.incrementAndGet();
      throw new IllegalStateException("Runtime Failure");
    };
  }

  /** Creates a ThrowableSupplier that increments the counter and throws a checked exception. */
  protected static ThrowableSupplier<String> checkedFailSupplier() {
    return () -> {
      COUNTER.incrementAndGet();
      throw new IOException("Checked Failure");
    };
  }

  /**
   * Creates a Lazy Kind with the specified value (already evaluated).
   *
   * @param value The value to wrap in a Lazy
   * @return A Lazy Kind containing the specified value
   */
  protected <A> Kind<LazyKind.Witness, A> nowKind(A value) {
    return LAZY.widen(Lazy.now(value));
  }

  /**
   * Creates a Lazy Kind that will defer the computation.
   *
   * @param supplier The supplier to defer
   * @return A Lazy Kind that will evaluate the supplier when forced
   */
  protected <A> Kind<LazyKind.Witness, A> deferKind(ThrowableSupplier<A> supplier) {
    return LAZY.widen(Lazy.defer(supplier));
  }

  /**
   * Converts a Kind to a Lazy instance.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * LAZY.narrow() calls.
   *
   * @param <A> The type of the value
   * @param kind The Kind to convert
   * @return The underlying Lazy instance
   */
  protected <A> Lazy<A> narrowToLazy(Kind<LazyKind.Witness, A> kind) {
    return LAZY.narrow(kind);
  }

  /**
   * Forces a Lazy Kind and returns the result.
   *
   * <p>This is a convenience method for tests that need to force evaluation.
   *
   * @param <A> The type of the value
   * @param kind The Kind to force
   * @return The computed value
   * @throws Throwable if the lazy computation fails
   */
  protected <A> @Nullable A forceLazy(Kind<LazyKind.Witness, A> kind) throws Throwable {
    return LAZY.narrow(kind).force();
  }

  @Override
  protected Kind<LazyKind.Witness, Integer> createValidKind() {
    return LAZY.widen(Lazy.now(DEFAULT_LAZY_VALUE));
  }

  @Override
  protected Kind<LazyKind.Witness, Integer> createValidKind2() {
    return nowKind(ALTERNATIVE_LAZY_VALUE);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<LazyKind.Witness, String>> createValidFlatMapper() {
    return i -> LAZY.widen(Lazy.now("flat:" + i));
  }

  @Override
  protected Kind<LazyKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return LAZY.widen(Lazy.now(TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_LAZY_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected Function<Integer, Kind<LazyKind.Witness, String>> createTestFunction() {
    return i -> LAZY.widen(Lazy.now("test:" + i));
  }

  @Override
  protected Function<String, Kind<LazyKind.Witness, String>> createChainFunction() {
    return s -> LAZY.widen(Lazy.now(s + "!"));
  }

  @Override
  protected BiPredicate<Kind<LazyKind.Witness, ?>, Kind<LazyKind.Witness, ?>>
      createEqualityChecker() {
    return LazyLawFixtures.EQ;
  }
}
