// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

/**
 * Base class for Stream type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * Stream type class tests, eliminating duplication across Functor, Applicative, Monad, and Traverse
 * tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Stream tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_VALUE} - The primary test value (42)
 *   <li>{@link #ALTERNATIVE_VALUE} - A secondary test value (24)
 *   <li>{@link #THIRD_VALUE} - A third test value (100)
 * </ul>
 *
 * <h2>Helper Methods</h2>
 *
 * <p>Provides convenient methods for creating Stream Kinds and converting between representations:
 *
 * <ul>
 *   <li>{@link #streamOf(Object...)} - Creates a Stream Kind from varargs
 *   <li>{@link #emptyStream()} - Creates an empty Stream Kind
 *   <li>{@link #singletonStream(Object)} - Creates a Stream Kind with one element
 *   <li>{@link #narrowToList(Kind)} - Converts a Stream Kind to List for testing
 * </ul>
 *
 * <h2>Important Note on Stream Consumption</h2>
 *
 * <p>Unlike List tests, Stream tests must be careful about stream consumption. Helper methods that
 * create new streams should be used for each test operation, as streams can only be consumed once.
 */
abstract class StreamTestBase extends TypeClassTestBase<StreamKind.Witness, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default value for Stream instances in tests. */
  protected static final Integer DEFAULT_VALUE = 42;

  /** Alternative value for Stream instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_VALUE = 24;

  /** Third value for tests requiring more than two values. */
  protected static final Integer THIRD_VALUE = 100;

  // ============================================================================
  // Helper Methods for Creating Stream Kinds
  // ============================================================================

  /**
   * Creates a Stream Kind containing the specified elements.
   *
   * <p><b>Important:</b> Each call creates a new stream. Streams can only be consumed once, so call
   * this method each time you need a fresh stream for testing.
   *
   * @param <A> The type of elements
   * @param elements The elements to include in the stream
   * @return A Stream Kind containing the specified elements
   */
  @SafeVarargs
  protected final <A> Kind<StreamKind.Witness, A> streamOf(A... elements) {
    return STREAM.widen(Stream.of(elements));
  }

  /**
   * Creates an empty Stream Kind.
   *
   * @param <A> The type of elements
   * @return An empty Stream Kind
   */
  protected <A> Kind<StreamKind.Witness, A> emptyStream() {
    return STREAM.widen(Stream.empty());
  }

  /**
   * Creates a Stream Kind containing a single element.
   *
   * @param <A> The type of the element
   * @param element The element to include
   * @return A Stream Kind containing the single element
   */
  protected <A> Kind<StreamKind.Witness, A> singletonStream(A element) {
    return STREAM.widen(Stream.of(element));
  }

  /**
   * Converts a Stream Kind to a List for testing purposes.
   *
   * <p><b>Warning:</b> This forces evaluation and consumes the stream. The stream cannot be reused
   * after this operation.
   *
   * <p>This is a convenience method to make test assertions easier, as comparing streams directly
   * is difficult due to single-use semantics.
   *
   * @param <A> The type of elements
   * @param kind The Stream Kind to convert
   * @return The stream elements as a List
   */
  protected <A> List<A> narrowToList(Kind<StreamKind.Witness, A> kind) {
    return STREAM.narrow(kind).collect(Collectors.toList());
  }

  /**
   * Creates a Stream Kind from a standard Java Stream.
   *
   * <p>Useful when you need to create a stream from an existing source or using Stream API
   * operations.
   *
   * @param <A> The type of elements
   * @param stream The stream to wrap
   * @return A Stream Kind wrapping the provided stream
   */
  protected <A> Kind<StreamKind.Witness, A> wrapStream(Stream<A> stream) {
    return STREAM.widen(stream);
  }

  /**
   * Creates a Stream Kind containing a range of integers from start (inclusive) to end (exclusive).
   *
   * <p>Example: {@code rangeStream(1, 4)} produces a stream [1, 2, 3]
   *
   * @param start The starting value (inclusive)
   * @param end The ending value (exclusive)
   * @return A Stream Kind containing the range
   */
  protected Kind<StreamKind.Witness, Integer> rangeStream(int start, int end) {
    return STREAM.widen(Stream.iterate(start, n -> n < end, n -> n + 1));
  }

  // ============================================================================
  // Integer-based Fixtures (from parent class requirements)
  // ============================================================================

  @Override
  protected Kind<StreamKind.Witness, Integer> createValidKind() {
    return streamOf(DEFAULT_VALUE, ALTERNATIVE_VALUE);
  }

  @Override
  protected Kind<StreamKind.Witness, Integer> createValidKind2() {
    return streamOf(THIRD_VALUE, 200);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<StreamKind.Witness, String>> createValidFlatMapper() {
    return i -> streamOf("flat:" + i, "mapped:" + i);
  }

  @Override
  protected Kind<StreamKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return streamOf(TestFunctions.INT_TO_STRING, i -> "Alt:" + i);
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
  protected Function<Integer, Kind<StreamKind.Witness, String>> createTestFunction() {
    return i -> streamOf("test:" + i, "value:" + i);
  }

  @Override
  protected Function<String, Kind<StreamKind.Witness, String>> createChainFunction() {
    return s -> streamOf(s + "!", s + "?");
  }

  @Override
  protected BiPredicate<Kind<StreamKind.Witness, ?>, Kind<StreamKind.Witness, ?>>
      createEqualityChecker() {
    // Streams must be converted to lists for equality comparison
    // This forces evaluation of both streams
    return (k1, k2) -> {
      List<?> list1 = STREAM.narrow(k1).collect(Collectors.toList());
      List<?> list2 = STREAM.narrow(k2).collect(Collectors.toList());
      return list1.equals(list2);
    };
  }

  // ============================================================================
  // Additional Helper Methods for Stream-Specific Testing
  // ============================================================================

  /**
   * Creates a finite stream for testing by limiting an infinite stream.
   *
   * <p>Useful for testing with potentially infinite sequences.
   *
   * @param seed The starting value
   * @param next The function to generate the next value
   * @param limit The maximum number of elements
   * @return A Stream Kind with at most {@code limit} elements
   */
  protected Kind<StreamKind.Witness, Integer> finiteIteration(
      Integer seed, Function<Integer, Integer> next, long limit) {
    return STREAM.widen(Stream.iterate(seed, next::apply).limit(limit));
  }

  /**
   * Creates a Stream Kind from a list for testing.
   *
   * <p>Useful when you have test data in a list and need to convert it to a stream.
   *
   * @param <A> The type of elements
   * @param list The list to convert
   * @return A Stream Kind containing the list elements
   */
  protected <A> Kind<StreamKind.Witness, A> streamFromList(List<A> list) {
    return STREAM.widen(list.stream());
  }

  /**
   * Validates that a stream fixture is properly configured.
   *
   * <p>Note: This consumes the stream, so should only be used in setup validation, not in actual
   * test methods.
   */
  protected void validateStreamFixtures() {
    // Validate that fixtures are properly initialized
    // Note: This consumes streams, so create fresh ones for actual tests
    assert createValidKind() != null : "validKind must not be null";
    assert createValidMapper() != null : "validMapper must not be null";
    assert createValidFlatMapper() != null : "validFlatMapper must not be null";
  }
}
