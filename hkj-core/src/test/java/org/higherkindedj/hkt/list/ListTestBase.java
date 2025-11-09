// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

/**
 * Base class for List type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * List type class tests, eliminating duplication across Functor, Applicative, Monad, and Traverse
 * tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all List tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_VALUE} - The primary test value (42)
 *   <li>{@link #ALTERNATIVE_VALUE} - A secondary test value (24)
 *   <li>{@link #THIRD_VALUE} - A third test value (100)
 * </ul>
 *
 * <h2>Helper Methods</h2>
 *
 * <p>Provides convenient methods for creating List Kinds and converting between representations:
 *
 * <ul>
 *   <li>{@link #listOf(Object...)} - Creates a List Kind from varargs
 *   <li>{@link #emptyList()} - Creates an empty List Kind
 *   <li>{@link #singletonList(Object)} - Creates a List Kind with one element
 *   <li>{@link #narrowToList(Kind)} - Converts a Kind back to a List
 * </ul>
 */
abstract class ListTestBase extends TypeClassTestBase<ListKind.Witness, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default value for List instances in tests. */
  protected static final Integer DEFAULT_VALUE = 42;

  /** Alternative value for List instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_VALUE = 24;

  /** Third value for tests requiring more than two values. */
  protected static final Integer THIRD_VALUE = 100;

  // ============================================================================
  // Helper Methods for Creating List Kinds
  // ============================================================================

  /**
   * Creates a List Kind containing the specified elements.
   *
   * @param <A> The type of elements
   * @param elements The elements to include in the list
   * @return A List Kind containing the specified elements
   */
  @SafeVarargs
  protected final <A> Kind<ListKind.Witness, A> listOf(A... elements) {
    return LIST.widen(Arrays.asList(elements));
  }

  /**
   * Creates an empty List Kind.
   *
   * @param <A> The type of elements
   * @return An empty List Kind
   */
  protected <A> Kind<ListKind.Witness, A> emptyList() {
    return LIST.widen(Collections.emptyList());
  }

  /**
   * Creates a List Kind containing a single element.
   *
   * @param <A> The type of the element
   * @param element The element to include
   * @return A List Kind containing the single element
   */
  protected <A> Kind<ListKind.Witness, A> singletonList(A element) {
    return LIST.widen(Collections.singletonList(element));
  }

  /**
   * Converts a List Kind to a standard Java List.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * LIST.narrow() calls.
   *
   * @param <A> The type of elements
   * @param kind The Kind to convert
   * @return The underlying List instance
   */
  protected <A> List<A> narrowToList(Kind<ListKind.Witness, A> kind) {
    return LIST.narrow(kind);
  }

  // ============================================================================
  // Integer-based Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<ListKind.Witness, Integer> createValidKind() {
    return listOf(DEFAULT_VALUE, ALTERNATIVE_VALUE);
  }

  @Override
  protected Kind<ListKind.Witness, Integer> createValidKind2() {
    return listOf(THIRD_VALUE, 200);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<ListKind.Witness, String>> createValidFlatMapper() {
    return i -> listOf("flat:" + i, "mapped:" + i);
  }

  @Override
  protected Kind<ListKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return listOf(TestFunctions.INT_TO_STRING, i -> "Alt:" + i);
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
  protected Function<Integer, Kind<ListKind.Witness, String>> createTestFunction() {
    return i -> listOf("test:" + i, "value:" + i);
  }

  @Override
  protected Function<String, Kind<ListKind.Witness, String>> createChainFunction() {
    return s -> listOf(s + "!", s + "?");
  }

  @Override
  protected BiPredicate<Kind<ListKind.Witness, ?>, Kind<ListKind.Witness, ?>>
      createEqualityChecker() {
    return (k1, k2) -> LIST.narrow(k1).equals(LIST.narrow(k2));
  }

  // ============================================================================
  // Additional Helper Methods
  // ============================================================================

  /**
   * Creates a List Kind from a standard Java List.
   *
   * @param <A> The type of elements
   * @param list The list to wrap
   * @return A List Kind wrapping the provided list
   */
  protected <A> Kind<ListKind.Witness, A> wrapList(List<A> list) {
    return LIST.widen(list);
  }

  /**
   * Creates a List Kind containing elements from start (inclusive) to end (exclusive).
   *
   * <p>Example: {@code rangeList(1, 4)} produces a list [1, 2, 3]
   *
   * @param start The starting value (inclusive)
   * @param end The ending value (exclusive)
   * @return A List Kind containing the range
   */
  protected Kind<ListKind.Witness, Integer> rangeList(int start, int end) {
    List<Integer> range = new java.util.ArrayList<>();
    for (int i = start; i < end; i++) {
      range.add(i);
    }
    return LIST.widen(range);
  }
}
