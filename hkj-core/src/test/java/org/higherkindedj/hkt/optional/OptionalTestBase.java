// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

/**
 * Base class for Optional type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * Optional type class tests, eliminating duplication across Functor, Applicative, Monad,
 * MonadError, and Traverse tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Optional tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_PRESENT_VALUE} - The primary test value (42)
 *   <li>{@link #ALTERNATIVE_PRESENT_VALUE} - A secondary test value (24)
 * </ul>
 *
 * <h2>Helper Methods</h2>
 *
 * <p>Provides convenient methods for creating Optional Kinds and converting between
 * representations:
 *
 * <ul>
 *   <li>{@link #presentOf(Object)} - Creates an Optional Kind with a value
 *   <li>{@link #emptyOptional()} - Creates an empty Optional Kind
 *   <li>{@link #optionalOf(Object)} - Creates an Optional Kind from potentially null value
 *   <li>{@link #narrowToOptional(Kind)} - Converts a Kind back to an Optional
 * </ul>
 */
abstract class OptionalTestBase extends TypeClassTestBase<OptionalKind.Witness, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default value for present Optional instances in tests. */
  protected static final Integer DEFAULT_PRESENT_VALUE = 42;

  /** Alternative value for present Optional instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_PRESENT_VALUE = 24;

  // ============================================================================
  // Helper Methods for Creating Optional Kinds
  // ============================================================================

  /**
   * Creates an Optional Kind containing the specified value.
   *
   * @param <A> The type of the value
   * @param value The value to wrap (must not be null)
   * @return An Optional Kind containing the specified value
   */
  protected <A> Kind<OptionalKind.Witness, A> presentOf(A value) {
    return OPTIONAL.widen(Optional.of(value));
  }

  /**
   * Creates an empty Optional Kind.
   *
   * @param <A> The type of value
   * @return An empty Optional Kind
   */
  protected <A> Kind<OptionalKind.Witness, A> emptyOptional() {
    return OPTIONAL.widen(Optional.empty());
  }

  /**
   * Creates an Optional Kind from a potentially null value.
   *
   * <p>If the value is null, returns an empty Optional Kind. Otherwise, returns a present Optional
   * Kind.
   *
   * @param <A> The type of the value
   * @param value The value to wrap (may be null)
   * @return An Optional Kind that is present if value is non-null, empty otherwise
   */
  protected <A> Kind<OptionalKind.Witness, A> optionalOf(A value) {
    return OPTIONAL.widen(Optional.ofNullable(value));
  }

  /**
   * Converts an Optional Kind to a standard Java Optional.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * OPTIONAL.narrow() calls.
   *
   * @param <A> The type of value
   * @param kind The Kind to convert
   * @return The underlying Optional instance
   */
  protected <A> Optional<A> narrowToOptional(Kind<OptionalKind.Witness, A> kind) {
    return OPTIONAL.narrow(kind);
  }

  // ============================================================================
  // Integer-based Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<OptionalKind.Witness, Integer> createValidKind() {
    return presentOf(DEFAULT_PRESENT_VALUE);
  }

  @Override
  protected Kind<OptionalKind.Witness, Integer> createValidKind2() {
    return presentOf(ALTERNATIVE_PRESENT_VALUE);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<OptionalKind.Witness, String>> createValidFlatMapper() {
    return i -> presentOf("flat:" + i);
  }

  @Override
  protected Kind<OptionalKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return presentOf(TestFunctions.INT_TO_STRING);
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_PRESENT_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected Function<Integer, Kind<OptionalKind.Witness, String>> createTestFunction() {
    return i -> presentOf("test:" + i);
  }

  @Override
  protected Function<String, Kind<OptionalKind.Witness, String>> createChainFunction() {
    return s -> presentOf(s + "!");
  }

  @Override
  protected BiPredicate<Kind<OptionalKind.Witness, ?>, Kind<OptionalKind.Witness, ?>>
      createEqualityChecker() {
    return (k1, k2) -> OPTIONAL.narrow(k1).equals(OPTIONAL.narrow(k2));
  }

  // ============================================================================
  // Additional Helper Methods
  // ============================================================================

  /**
   * Creates an Optional Kind from a standard Java Optional.
   *
   * @param <A> The type of value
   * @param optional The optional to wrap
   * @return An Optional Kind wrapping the provided optional
   */
  protected <A> Kind<OptionalKind.Witness, A> wrapOptional(Optional<A> optional) {
    return OPTIONAL.widen(optional);
  }

  /**
   * Checks if an Optional Kind is present.
   *
   * @param kind The Optional Kind to check
   * @return true if the Optional is present, false if empty
   */
  protected boolean isPresent(Kind<OptionalKind.Witness, ?> kind) {
    return OPTIONAL.narrow(kind).isPresent();
  }

  /**
   * Checks if an Optional Kind is empty.
   *
   * @param kind The Optional Kind to check
   * @return true if the Optional is empty, false if present
   */
  protected boolean isEmpty(Kind<OptionalKind.Witness, ?> kind) {
    return OPTIONAL.narrow(kind).isEmpty();
  }
}
