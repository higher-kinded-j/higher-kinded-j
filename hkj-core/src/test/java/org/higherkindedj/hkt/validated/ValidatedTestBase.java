// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

/**
 * Base class for Validated type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * Validated type class tests, eliminating duplication across Functor, Applicative, Monad,
 * MonadError, Foldable, and Traverse tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Validated tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_VALID_VALUE} - The primary test value (42)
 *   <li>{@link #ALTERNATIVE_VALID_VALUE} - A secondary test value (24)
 *   <li>{@link #DEFAULT_ERROR} - The primary test error ("error")
 *   <li>{@link #ALTERNATIVE_ERROR} - A secondary test error ("alternative-error")
 * </ul>
 *
 * <h2>Semigroup Configuration</h2>
 *
 * <p>The base class provides a default {@link Semigroup} for error accumulation:
 *
 * <ul>
 *   <li>{@link #createDefaultSemigroup()} - Returns a string concatenation semigroup with ", "
 *       delimiter
 * </ul>
 *
 * <h2>String-based Fixtures</h2>
 *
 * <p>Since {@link ValidatedTest} uses String as its primary type, this base class provides
 * String-based fixtures alongside the Integer-based fixtures from the parent class:
 *
 * <ul>
 *   <li>{@link #stringKind(String)} - Creates a Valid Kind with a String value
 *   <li>{@link #stringToIntMapper()} - Maps String to Integer (String::length)
 *   <li>{@link #stringToIntFlatMapper()} - FlatMaps String to Kind&lt;Validated, Integer&gt;
 *   <li>{@link #stringCombiningFunction()} - Combines two Strings into an Integer
 * </ul>
 */
abstract class ValidatedTestBase
    extends TypeClassTestBase<ValidatedKind.Witness<String>, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default value for Valid instances in tests. */
  protected static final Integer DEFAULT_VALID_VALUE = 42;

  /** Alternative value for Valid instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_VALID_VALUE = 24;

  /** Default error value for Invalid instances in tests. */
  protected static final String DEFAULT_ERROR = "error";

  /** Alternative error value for Invalid instances when testing with multiple errors. */
  protected static final String ALTERNATIVE_ERROR = "alternative-error";

  // ============================================================================
  // Semigroup Configuration
  // ============================================================================

  /**
   * Creates the default {@link Semigroup} for error accumulation.
   *
   * <p>Uses string concatenation with ", " as the delimiter for combining errors.
   *
   * @return A semigroup that concatenates strings with ", " delimiter
   */
  protected Semigroup<String> createDefaultSemigroup() {
    return Semigroups.string(", ");
  }

  // ============================================================================
  // String-based Fixtures for ValidatedTest
  // ============================================================================

  /**
   * String-based Valid Kind for tests that work with String values.
   *
   * @param value The string value to wrap in a Valid
   * @return A Valid Kind containing the specified string
   */
  protected Kind<ValidatedKind.Witness<String>, String> stringKind(String value) {
    return VALIDATED.widen(Validated.valid(value));
  }

  /** Mapper from String to Integer (String::length). */
  protected Function<String, Integer> stringToIntMapper() {
    return String::length;
  }

  /** FlatMapper from String to Kind&lt;Validated, Integer&gt;. */
  protected Function<String, Kind<ValidatedKind.Witness<String>, Integer>> stringToIntFlatMapper() {
    return s -> VALIDATED.widen(Validated.valid(s.length()));
  }

  /** Function Kind for String to Integer mapping. */
  protected Kind<ValidatedKind.Witness<String>, Function<String, Integer>>
      stringToIntFunctionKind() {
    return VALIDATED.widen(Validated.valid(String::length));
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

  /** Test function from String to Kind&lt;Validated, Integer&gt;. */
  protected Function<String, Kind<ValidatedKind.Witness<String>, Integer>> stringTestFunction() {
    return s -> VALIDATED.widen(Validated.valid(s.length()));
  }

  /** Chain function from Integer to Kind&lt;Validated, Integer&gt;. */
  protected Function<Integer, Kind<ValidatedKind.Witness<String>, Integer>> intChainFunction() {
    return i -> VALIDATED.widen(Validated.valid(i * 2));
  }

  /**
   * Creates a Valid Kind with the specified value.
   *
   * @param value The value to wrap in a Valid
   * @return A Valid Kind containing the specified value
   */
  protected <A> Kind<ValidatedKind.Witness<String>, A> validKind(A value) {
    return VALIDATED.widen(Validated.valid(value));
  }

  /**
   * Creates an Invalid Kind with the specified error.
   *
   * @param error The error to wrap in an Invalid
   * @return An Invalid Kind containing the specified error
   */
  protected <A> Kind<ValidatedKind.Witness<String>, A> invalidKind(String error) {
    return VALIDATED.widen(Validated.invalid(error));
  }

  /**
   * Creates an Invalid Kind with the default error.
   *
   * @return An Invalid Kind containing the default error
   */
  protected <A> Kind<ValidatedKind.Witness<String>, A> invalidKind() {
    return invalidKind(DEFAULT_ERROR);
  }

  /**
   * Converts a Kind to a Validated instance.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * VALIDATED.narrow() calls.
   *
   * @param <A> The type of the value
   * @param kind The Kind to convert
   * @return The underlying Validated instance
   */
  protected <A> Validated<String, A> narrowToValidated(
      Kind<ValidatedKind.Witness<String>, A> kind) {
    return VALIDATED.narrow(kind);
  }

  // ============================================================================
  // Integer-based Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<ValidatedKind.Witness<String>, Integer> createValidKind() {
    return VALIDATED.widen(Validated.valid(DEFAULT_VALID_VALUE));
  }

  @Override
  protected Kind<ValidatedKind.Witness<String>, Integer> createValidKind2() {
    return validKind(ALTERNATIVE_VALID_VALUE);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<ValidatedKind.Witness<String>, String>> createValidFlatMapper() {
    return i -> VALIDATED.widen(Validated.valid("mapped:" + i));
  }

  @Override
  protected Kind<ValidatedKind.Witness<String>, Function<Integer, String>>
      createValidFunctionKind() {
    return VALIDATED.widen(Validated.valid(TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_VALID_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected Function<Integer, Kind<ValidatedKind.Witness<String>, String>> createTestFunction() {
    return i -> VALIDATED.widen(Validated.valid("test:" + i));
  }

  @Override
  protected Function<String, Kind<ValidatedKind.Witness<String>, String>> createChainFunction() {
    return s -> VALIDATED.widen(Validated.valid(s + "!"));
  }

  @Override
  protected BiPredicate<
          Kind<ValidatedKind.Witness<String>, ?>, Kind<ValidatedKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> VALIDATED.narrow(k1).equals(VALIDATED.narrow(k2));
  }
}
