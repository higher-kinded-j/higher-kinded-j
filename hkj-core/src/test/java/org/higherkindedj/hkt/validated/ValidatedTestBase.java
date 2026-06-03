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
import org.higherkindedj.hkt.test.fixtures.TestFunctions;
import org.higherkindedj.hkt.test.fixtures.TypeClassTestBase;

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
  // Kind Factories
  // ============================================================================

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
