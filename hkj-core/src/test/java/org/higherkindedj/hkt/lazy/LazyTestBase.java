// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

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
 *   <li>{@link #DEFAULT_STRING_VALUE} - A default String value ("TestValue")
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
 *
 * <h2>String-based Fixtures</h2>
 *
 * <p>Since several Lazy tests use String as a type parameter, this base class provides String-based
 * fixtures:
 *
 * <ul>
 *   <li>{@link #stringLazyKind(String)} - Creates a Lazy Kind with a String value
 *   <li>{@link #stringToIntMapper()} - Maps String to Integer (String::length)
 *   <li>{@link #stringToIntFlatMapper()} - FlatMaps String to Kind&lt;Lazy, Integer&gt;
 *   <li>{@link #stringCombiningFunction()} - Combines two Strings into an Integer
 * </ul>
 */
abstract class LazyTestBase extends TypeClassTestBase<LazyKind.Witness, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default value for Lazy instances in tests. */
  protected static final Integer DEFAULT_LAZY_VALUE = 42;

  /** Alternative value for Lazy instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_LAZY_VALUE = 24;

  /** Default String value for String-based tests. */
  protected static final String DEFAULT_STRING_VALUE = "TestValue";

  // ============================================================================
  // String-based Fixtures
  // ============================================================================

  /** String-based Lazy Kind for tests that work with String values. */
  protected Kind<LazyKind.Witness, String> stringLazyKind(String value) {
    return LAZY.widen(Lazy.now(value));
  }

  /** Mapper from String to Integer (String::length). */
  protected Function<String, Integer> stringToIntMapper() {
    return String::length;
  }

  /** FlatMapper from String to Kind&lt;Lazy, Integer&gt;. */
  protected Function<String, Kind<LazyKind.Witness, Integer>> stringToIntFlatMapper() {
    return s -> LAZY.widen(Lazy.now(s.length()));
  }

  /** Function Kind for String to Integer mapping. */
  protected Kind<LazyKind.Witness, Function<String, Integer>> stringToIntFunctionKind() {
    return LAZY.widen(Lazy.now(String::length));
  }

  /** Combining function for two Strings producing Integer. */
  protected BiFunction<String, String, Integer> stringCombiningFunction() {
    return (s1, s2) -> s1.length() + s2.length();
  }

  /** Second mapper from Integer to Integer for composition testing. */
  protected Function<Integer, Integer> intToIntMapper() {
    return i -> i * 2;
  }

  /** Second mapper from Integer to String for Functor composition testing. */
  protected Function<Integer, String> intToStringMapper() {
    return i -> "Value:" + i;
  }

  /** Test function from String to Kind&lt;Lazy, Integer&gt;. */
  protected Function<String, Kind<LazyKind.Witness, Integer>> stringTestFunction() {
    return s -> LAZY.widen(Lazy.now(s.length()));
  }

  /** Chain function from Integer to Kind&lt;Lazy, Integer&gt;. */
  protected Function<Integer, Kind<LazyKind.Witness, Integer>> intChainFunction() {
    return i -> LAZY.widen(Lazy.now(i * 2));
  }

  /** Shared counter for tracking evaluation in tests. */
  protected static final AtomicInteger COUNTER = new AtomicInteger(0);

  /**
   * Creates a ThrowableSupplier that increments the shared counter and returns a success value.
   * Includes a small delay to test memoisation behavior.
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
   * Creates a custom ThrowableSupplier with a specific counter and value.
   *
   * @param counter The counter to increment
   * @param value The value to return
   * @return A ThrowableSupplier that increments the counter and returns the value
   */
  protected static <T> ThrowableSupplier<T> countingSupplier(AtomicInteger counter, T value) {
    return () -> {
      counter.incrementAndGet();
      return value;
    };
  }

  /**
   * Creates a custom ThrowableSupplier that throws an exception.
   *
   * @param counter The counter to increment
   * @param exception The exception to throw
   * @return A ThrowableSupplier that increments the counter and throws the exception
   */
  protected static <T> ThrowableSupplier<T> failingSupplier(
      AtomicInteger counter, Throwable exception) {
    return () -> {
      counter.incrementAndGet();
      throw exception;
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
  protected <A> A forceLazy(Kind<LazyKind.Witness, A> kind) throws Throwable {
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
    return (k1, k2) -> {
      try {
        Object v1 = LAZY.force(k1);
        Object v2 = LAZY.force(k2);
        if (v1 == null && v2 == null) return true;
        if (v1 == null || v2 == null) return false;
        return v1.equals(v2);
      } catch (Throwable t) {
        return false;
      }
    };
  }
}
