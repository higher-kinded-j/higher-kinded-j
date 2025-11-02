// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

/**
 * Base class for Maybe type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * Maybe type class tests, eliminating duplication across Functor, Applicative, Monad, MonadError,
 * Foldable, and Traverse tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Maybe tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_JUST_VALUE} - The primary test value (42)
 *   <li>{@link #ALTERNATIVE_JUST_VALUE} - A secondary test value (24)
 *   <li>{@link #DEFAULT_INT_VALUE} - Default integer for Selective tests (100)
 * </ul>
 *
 * <h2>String-based Fixtures</h2>
 *
 * <p>Since {@link MaybeTest} uses String as its primary type, this base class provides String-based
 * fixtures alongside the Integer-based fixtures from the parent class:
 *
 * <ul>
 *   <li>{@link #stringKind(String)} - Creates a Just Kind with a String value
 *   <li>{@link #stringToIntMapper()} - Maps String to Integer (String::length)
 *   <li>{@link #stringToIntFlatMapper()} - FlatMaps String to Kind&lt;Maybe, Integer&gt;
 *   <li>{@link #stringCombiningFunction()} - Combines two Strings into an Integer
 * </ul>
 */
abstract class MaybeTestBase extends TypeClassTestBase<MaybeKind.Witness, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default value for Just instances in tests. */
  protected static final Integer DEFAULT_JUST_VALUE = 42;

  /** Alternative value for Just instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_JUST_VALUE = 24;

  /** Default integer value for Selective tests. */
  protected static final Integer DEFAULT_INT_VALUE = 100;

  // ============================================================================
  // String-based Fixtures for MaybeTest
  // ============================================================================

  /** String-based Just Kind for tests that work with String values. */
  protected Kind<MaybeKind.Witness, String> stringKind(String value) {
    return MAYBE.widen(Maybe.just(value));
  }

  /** Mapper from String to Integer (String::length). */
  protected Function<String, Integer> stringToIntMapper() {
    return String::length;
  }

  /** FlatMapper from String to Kind&lt;Maybe, Integer&gt;. */
  protected Function<String, Kind<MaybeKind.Witness, Integer>> stringToIntFlatMapper() {
    return s -> MAYBE.widen(Maybe.just(s.length()));
  }

  /** Function Kind for String to Integer mapping. */
  protected Kind<MaybeKind.Witness, Function<String, Integer>> stringToIntFunctionKind() {
    return MAYBE.widen(Maybe.just(String::length));
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

  /** Test function from String to Kind&lt;Maybe, Integer&gt;. */
  protected Function<String, Kind<MaybeKind.Witness, Integer>> stringTestFunction() {
    return s -> MAYBE.widen(Maybe.just(s.length()));
  }

  /** Chain function from Integer to Kind&lt;Maybe, Integer&gt;. */
  protected Function<Integer, Kind<MaybeKind.Witness, Integer>> intChainFunction() {
    return i -> MAYBE.widen(Maybe.just(i * 2));
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
   * Creates a Just Kind with the specified value.
   *
   * @param value The value to wrap in a Just
   * @return A Just Kind containing the specified value
   */
  protected <A> Kind<MaybeKind.Witness, A> justKind(A value) {
    return MAYBE.widen(Maybe.just(value));
  }

  /**
   * Creates a Nothing Kind.
   *
   * @return A Nothing Kind
   */
  protected <A> Kind<MaybeKind.Witness, A> nothingKind() {
    return MAYBE.widen(Maybe.nothing());
  }

  /**
   * Converts a Kind to a Maybe instance.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * MAYBE.narrow() calls.
   *
   * @param <A> The type of the value
   * @param kind The Kind to convert
   * @return The underlying Maybe instance
   */
  protected <A> Maybe<A> narrowToMaybe(Kind<MaybeKind.Witness, A> kind) {
    return MAYBE.narrow(kind);
  }

  // ============================================================================
  // Integer-based Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<MaybeKind.Witness, Integer> createValidKind() {
    return MAYBE.widen(Maybe.just(DEFAULT_JUST_VALUE));
  }

  @Override
  protected Kind<MaybeKind.Witness, Integer> createValidKind2() {
    return justKind(ALTERNATIVE_JUST_VALUE);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<MaybeKind.Witness, String>> createValidFlatMapper() {
    return i -> MAYBE.widen(Maybe.just("flat:" + i));
  }

  @Override
  protected Kind<MaybeKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return MAYBE.widen(Maybe.just(TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_JUST_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected Function<Integer, Kind<MaybeKind.Witness, String>> createTestFunction() {
    return i -> MAYBE.widen(Maybe.just("test:" + i));
  }

  @Override
  protected Function<String, Kind<MaybeKind.Witness, String>> createChainFunction() {
    return s -> MAYBE.widen(Maybe.just(s + "!"));
  }

  @Override
  protected BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>>
      createEqualityChecker() {
    return (k1, k2) -> MAYBE.narrow(k1).equals(MAYBE.narrow(k2));
  }
}
