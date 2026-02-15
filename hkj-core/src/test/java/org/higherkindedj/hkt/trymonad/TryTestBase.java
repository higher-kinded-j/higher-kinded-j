// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;

/**
 * Base class for Try type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all Try
 * type class tests, eliminating duplication across Functor, Applicative, Monad, MonadError,
 * Foldable, and Traverse tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Try tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_SUCCESS_VALUE} - The primary test value ("test value")
 *   <li>{@link #ALTERNATIVE_SUCCESS_VALUE} - A secondary test value ("second value")
 *   <li>{@link #DEFAULT_TEST_EXCEPTION} - The primary test exception
 * </ul>
 *
 * <h2>String-based Fixtures</h2>
 *
 * <p>Since {@link TryTest} uses String as its primary type, this base class provides String-based
 * fixtures alongside the Integer-based fixtures from the parent class:
 *
 * <ul>
 *   <li>{@link #stringSuccessKind(String)} - Creates a Success Kind with a String value
 *   <li>{@link #stringFailureKind(Throwable)} - Creates a Failure Kind with a Throwable
 *   <li>{@link #stringToIntMapper()} - Maps String to Integer (String::length)
 *   <li>{@link #stringToIntFlatMapper()} - FlatMaps String to Kind&lt;Try, Integer&gt;
 *   <li>{@link #stringCombiningFunction()} - Combines two Strings into an Integer
 * </ul>
 *
 * <h2>Foldable-specific Fixtures</h2>
 *
 * <p>Provides default implementations for Foldable tests that use String as the element type:
 *
 * <ul>
 *   <li>{@link #createFoldableValidKind()} - Creates a String-based validKind for Foldable tests
 *   <li>{@link #createFoldableValidKind2()} - Creates a second String-based validKind
 *   <li>{@link #createFoldableValidMapper()} - Creates a String to Integer mapper
 * </ul>
 */
abstract class TryTestBase extends TypeClassTestBase<TryKind.Witness, String, Integer> {

  /** Default value for Success instances in tests. */
  protected static final String DEFAULT_SUCCESS_VALUE = "test value";

  /** Alternative value for Success instances when testing with multiple values. */
  protected static final String ALTERNATIVE_SUCCESS_VALUE = "second value";

  /** Default exception for Failure instances in tests. */
  protected static final RuntimeException DEFAULT_TEST_EXCEPTION =
      new RuntimeException("Test exception");

  /** String-based Success Kind for tests that work with String values. */
  protected Kind<TryKind.Witness, String> stringSuccessKind(String value) {
    return TRY.widen(Try.success(value));
  }

  /** String-based Failure Kind for tests that work with String values. */
  protected Kind<TryKind.Witness, String> stringFailureKind(Throwable throwable) {
    return TRY.widen(Try.failure(throwable));
  }

  /** Mapper from String to Integer (String::length). */
  protected Function<String, Integer> stringToIntMapper() {
    return String::length;
  }

  /** FlatMapper from String to Kind&lt;Try, Integer&gt;. */
  protected Function<String, Kind<TryKind.Witness, Integer>> stringToIntFlatMapper() {
    return s -> TRY.widen(Try.success(s.length()));
  }

  /** Function Kind for String to Integer mapping. */
  protected Kind<TryKind.Witness, Function<String, Integer>> stringToIntFunctionKind() {
    return TRY.widen(Try.success(String::length));
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

  /** Test function from String to Kind&lt;Try, Integer&gt;. */
  protected Function<String, Kind<TryKind.Witness, Integer>> stringTestFunction() {
    return s -> TRY.widen(Try.success(s.length()));
  }

  /** Chain function from Integer to Kind&lt;Try, Integer&gt;. */
  protected Function<Integer, Kind<TryKind.Witness, Integer>> intChainFunction() {
    return i -> TRY.widen(Try.success(i * 2));
  }

  /**
   * Creates a validKind for Foldable tests that use String as the element type. Can be overridden
   * by subclasses that need different behaviour.
   */
  protected Kind<TryKind.Witness, String> createFoldableValidKind() {
    return TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
  }

  /**
   * Creates a second validKind for Foldable tests that use String as the element type. Can be
   * overridden by subclasses that need different behaviour.
   */
  protected Kind<TryKind.Witness, String> createFoldableValidKind2() {
    return TRY.widen(Try.success(ALTERNATIVE_SUCCESS_VALUE));
  }

  /**
   * Creates a mapper for Foldable tests that maps String to Integer. Can be overridden by
   * subclasses that need different behaviour.
   */
  protected Function<String, Integer> createFoldableValidMapper() {
    return String::length;
  }

  /**
   * Creates a Success Kind with the specified value.
   *
   * @param value The value to wrap in a Success
   * @return A Success Kind containing the specified value
   */
  protected <A> Kind<TryKind.Witness, A> successKind(A value) {
    return TRY.widen(Try.success(value));
  }

  /**
   * Creates a Failure Kind with the specified exception.
   *
   * @param throwable The exception to wrap in a Failure
   * @return A Failure Kind containing the specified exception
   */
  protected <A> Kind<TryKind.Witness, A> failureKind(Throwable throwable) {
    return TRY.widen(Try.failure(throwable));
  }

  /**
   * Converts a Kind to a Try instance.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * TRY.narrow() calls.
   *
   * @param <A> The type of the value
   * @param kind The Kind to convert
   * @return The underlying Try instance
   */
  protected <A> Try<A> narrowToTry(Kind<TryKind.Witness, A> kind) {
    return TRY.narrow(kind);
  }

  @Override
  protected Kind<TryKind.Witness, String> createValidKind() {
    return TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
  }

  @Override
  protected Kind<TryKind.Witness, String> createValidKind2() {
    return successKind(ALTERNATIVE_SUCCESS_VALUE);
  }

  @Override
  protected Function<String, Integer> createValidMapper() {
    return String::length;
  }

  @Override
  protected Function<String, Kind<TryKind.Witness, Integer>> createValidFlatMapper() {
    return s -> TRY.widen(Try.success(s.length()));
  }

  @Override
  protected Kind<TryKind.Witness, Function<String, Integer>> createValidFunctionKind() {
    return TRY.widen(Try.success(String::length));
  }

  @Override
  protected BiFunction<String, String, Integer> createValidCombiningFunction() {
    return (s1, s2) -> s1.length() + s2.length();
  }

  @Override
  protected String createTestValue() {
    return DEFAULT_SUCCESS_VALUE;
  }

  @Override
  protected Function<Integer, String> createSecondMapper() {
    return i -> "Transformed:" + i;
  }

  @Override
  protected Function<String, Kind<TryKind.Witness, Integer>> createTestFunction() {
    return s -> TRY.widen(Try.success(s.length()));
  }

  @Override
  protected Function<Integer, Kind<TryKind.Witness, Integer>> createChainFunction() {
    return i -> TRY.widen(Try.success(i * 2));
  }

  @Override
  protected BiPredicate<Kind<TryKind.Witness, ?>, Kind<TryKind.Witness, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      Try<?> t1 = TRY.narrow(k1);
      Try<?> t2 = TRY.narrow(k2);
      return t1.equals(t2);
    };
  }
}
