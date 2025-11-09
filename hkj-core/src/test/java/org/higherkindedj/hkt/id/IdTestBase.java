// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

/**
 * Base class for Id type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all Id
 * type class tests, eliminating duplication across Functor, Applicative, Monad, and Selective
 * tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Id tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_VALUE} - The primary test value (42)
 *   <li>{@link #ALTERNATIVE_VALUE} - A secondary test value (24)
 * </ul>
 *
 * <h2>Helper Methods</h2>
 *
 * <p>Provides convenient methods for creating Id Kinds and converting between representations:
 *
 * <ul>
 *   <li>{@link #idOf(Object)} - Creates an Id Kind with a value
 *   <li>{@link #narrowToId(Kind)} - Converts a Kind back to an Id
 * </ul>
 */
abstract class IdTestBase extends TypeClassTestBase<Id.Witness, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default value for Id instances in tests. */
  protected static final Integer DEFAULT_VALUE = 42;

  /** Alternative value for Id instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_VALUE = 24;

  // ============================================================================
  // Helper Methods for Creating Id Kinds
  // ============================================================================

  /**
   * Creates an Id Kind containing the specified value.
   *
   * @param <A> The type of the value
   * @param value The value to wrap
   * @return An Id Kind containing the specified value
   */
  protected <A> Kind<Id.Witness, A> idOf(A value) {
    return ID.widen(Id.of(value));
  }

  /**
   * Converts an Id Kind to an Id instance.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * ID.narrow() calls.
   *
   * @param <A> The type of value
   * @param kind The Kind to convert
   * @return The underlying Id instance
   */
  protected <A> Id<A> narrowToId(Kind<Id.Witness, A> kind) {
    return ID.narrow(kind);
  }

  /**
   * Extracts the value from an Id Kind.
   *
   * <p>This is a convenience method that combines narrow and value extraction.
   *
   * @param <A> The type of value
   * @param kind The Kind to extract from
   * @return The value contained in the Id
   */
  protected <A> A extractValue(Kind<Id.Witness, A> kind) {
    return ID.narrow(kind).value();
  }

  // ============================================================================
  // Integer-based Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<Id.Witness, Integer> createValidKind() {
    return idOf(DEFAULT_VALUE);
  }

  @Override
  protected Kind<Id.Witness, Integer> createValidKind2() {
    return idOf(ALTERNATIVE_VALUE);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<Id.Witness, String>> createValidFlatMapper() {
    return i -> idOf("flat:" + i);
  }

  @Override
  protected Kind<Id.Witness, Function<Integer, String>> createValidFunctionKind() {
    return idOf(TestFunctions.INT_TO_STRING);
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected Function<Integer, Kind<Id.Witness, String>> createTestFunction() {
    return i -> idOf("test:" + i);
  }

  @Override
  protected Function<String, Kind<Id.Witness, String>> createChainFunction() {
    return s -> idOf(s + "!");
  }

  @Override
  protected BiPredicate<Kind<Id.Witness, ?>, Kind<Id.Witness, ?>>
      createEqualityChecker() {
    return (k1, k2) -> ID.narrow(k1).equals(ID.narrow(k2));
  }

  // ============================================================================
  // Additional Helper Methods
  // ============================================================================

  /**
   * Creates an Id Kind from an existing Id instance.
   *
   * @param <A> The type of value
   * @param id The Id to wrap
   * @return An Id Kind wrapping the provided Id
   */
  protected <A> Kind<Id.Witness, A> wrapId(Id<A> id) {
    return ID.widen(id);
  }
}
