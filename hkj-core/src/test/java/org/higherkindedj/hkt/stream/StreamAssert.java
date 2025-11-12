// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;

/**
 * Custom AssertJ assertions for {@link java.util.stream.Stream} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Stream} instances
 * within the Kind abstraction, making test code more readable and providing better error messages.
 *
 * <p><b>Important: Stream Consumption</b>
 *
 * <p>These assertions force evaluation of the stream. After calling any assertion method, the
 * original stream is consumed and cannot be reused. Each assertion method internally collects the
 * stream to a List for comparison.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.stream.StreamAssert.assertThatStream;
 *
 * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 3));
 * assertThatStream(stream)
 *     .isNotEmpty()
 *     .hasSize(3)
 *     .containsExactly(1, 2, 3);
 *
 * Kind<StreamKind.Witness, String> empty = STREAM.widen(Stream.empty());
 * assertThatStream(empty).isEmpty();
 *
 * // Chaining with custom assertions
 * assertThatStream(stream)
 *     .hasSize(3)
 *     .satisfies(elements -> {
 *         assertThat(elements).allMatch(n -> n > 0);
 *     });
 * }</pre>
 *
 * @param <T> The type of elements in the Stream
 */
public class StreamAssert<T> extends AbstractAssert<StreamAssert<T>, Kind<StreamKind.Witness, T>> {

  // Cached list to avoid multiple stream evaluations
  private List<T> evaluatedList;

  /**
   * Creates a new {@code StreamAssert} instance.
   *
   * <p>This is the entry point for all Stream assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.stream.StreamAssert.assertThatStream;
   * }</pre>
   *
   * @param <T> The type of elements in the Stream
   * @param actual The Stream Kind instance to make assertions on
   * @return A new StreamAssert instance
   */
  public static <T> StreamAssert<T> assertThatStream(Kind<StreamKind.Witness, T> actual) {
    return new StreamAssert<>(actual);
  }

  protected StreamAssert(Kind<StreamKind.Witness, T> actual) {
    super(actual, StreamAssert.class);
  }

  /**
   * Forces evaluation of the stream and caches the result as a list.
   *
   * <p>This is called automatically by assertion methods to ensure we only evaluate the stream
   * once.
   *
   * @return The list of elements from the stream
   */
  private List<T> getEvaluatedList() {
    if (evaluatedList == null) {
      isNotNull();
      evaluatedList = STREAM.narrow(actual).collect(Collectors.toList());
    }
    return evaluatedList;
  }

  /**
   * Verifies that the actual {@code Stream} is empty.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, String> stream = STREAM.widen(Stream.empty());
   * assertThatStream(stream).isEmpty();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Stream is null or not empty
   */
  public StreamAssert<T> isEmpty() {
    List<T> list = getEvaluatedList();
    if (!list.isEmpty()) {
      failWithMessage(
          "Expected Stream to be empty but had <%d> element%s: <%s>",
          list.size(), list.size() == 1 ? "" : "s", list);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Stream} is not empty.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2));
   * assertThatStream(stream).isNotEmpty();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Stream is null or empty
   */
  public StreamAssert<T> isNotEmpty() {
    List<T> list = getEvaluatedList();
    if (list.isEmpty()) {
      failWithMessage("Expected Stream to not be empty but was empty");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Stream} has the specified size.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 3));
   * assertThatStream(stream).hasSize(3);
   * }</pre>
   *
   * @param expectedSize The expected number of elements
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Stream is null or does not have the expected size
   */
  public StreamAssert<T> hasSize(int expectedSize) {
    List<T> list = getEvaluatedList();
    if (list.size() != expectedSize) {
      failWithMessage(
          "Expected Stream to have size <%d> but had size <%d>. Elements: <%s>",
          expectedSize, list.size(), list);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Stream} contains exactly the specified elements in the same
   * order.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, String> stream = STREAM.widen(Stream.of("a", "b", "c"));
   * assertThatStream(stream).containsExactly("a", "b", "c");
   * }</pre>
   *
   * @param expectedElements The expected elements in order
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Stream is null or does not contain exactly the expected
   *     elements in order
   */
  @SafeVarargs
  public final StreamAssert<T> containsExactly(T... expectedElements) {
    List<T> list = getEvaluatedList();
    List<T> expected = List.of(expectedElements);

    if (!list.equals(expected)) {
      failWithMessage("Expected Stream to contain exactly <%s> but was <%s>", expected, list);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Stream} contains the specified element(s).
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 3));
   * assertThatStream(stream).contains(2);
   * assertThatStream(stream).contains(1, 3); // Multiple elements
   * }</pre>
   *
   * @param elements The element(s) to check for
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Stream is null or does not contain all elements
   */
  @SafeVarargs
  public final StreamAssert<T> contains(T... elements) {
    List<T> list = getEvaluatedList();
    for (T element : elements) {
      if (!list.contains(element)) {
        failWithMessage(
            "Expected Stream to contain <%s> but did not. Elements: <%s>", element, list);
      }
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Stream} does not contain the specified element.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 3));
   * assertThatStream(stream).doesNotContain(4);
   * }</pre>
   *
   * @param element The element to check for absence
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Stream is null or contains the element
   */
  public StreamAssert<T> doesNotContain(T element) {
    List<T> list = getEvaluatedList();
    if (list.contains(element)) {
      failWithMessage("Expected Stream to not contain <%s> but did. Elements: <%s>", element, list);
    }
    return this;
  }

  /**
   * Verifies that all elements in the actual {@code Stream} satisfy the given predicate.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(2, 4, 6));
   * assertThatStream(stream).allMatch(n -> n % 2 == 0, "all even");
   * }</pre>
   *
   * @param predicate The predicate to test
   * @param description Description of the condition for error messages
   * @return This assertion object for method chaining
   * @throws AssertionError if any element does not match the predicate
   */
  public StreamAssert<T> allMatch(Predicate<? super T> predicate, String description) {
    List<T> list = getEvaluatedList();
    List<T> nonMatching = list.stream().filter(predicate.negate()).collect(Collectors.toList());

    if (!nonMatching.isEmpty()) {
      failWithMessage(
          "Expected all elements to match <%s> but <%d> element%s did not: <%s>",
          description, nonMatching.size(), nonMatching.size() == 1 ? "" : "s", nonMatching);
    }
    return this;
  }

  /**
   * Verifies that at least one element in the actual {@code Stream} satisfies the given predicate.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 3));
   * assertThatStream(stream).anyMatch(n -> n > 2, "at least one > 2");
   * }</pre>
   *
   * @param predicate The predicate to test
   * @param description Description of the condition for error messages
   * @return This assertion object for method chaining
   * @throws AssertionError if no element matches the predicate
   */
  public StreamAssert<T> anyMatch(Predicate<? super T> predicate, String description) {
    List<T> list = getEvaluatedList();
    boolean anyMatch = list.stream().anyMatch(predicate);

    if (!anyMatch) {
      failWithMessage(
          "Expected at least one element to match <%s> but none did. Elements: <%s>",
          description, list);
    }
    return this;
  }

  /**
   * Verifies that no elements in the actual {@code Stream} satisfy the given predicate.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 3, 5));
   * assertThatStream(stream).noneMatch(n -> n % 2 == 0, "none even");
   * }</pre>
   *
   * @param predicate The predicate to test
   * @param description Description of the condition for error messages
   * @return This assertion object for method chaining
   * @throws AssertionError if any element matches the predicate
   */
  public StreamAssert<T> noneMatch(Predicate<? super T> predicate, String description) {
    List<T> list = getEvaluatedList();
    List<T> matching = list.stream().filter(predicate).collect(Collectors.toList());

    if (!matching.isEmpty()) {
      failWithMessage(
          "Expected no elements to match <%s> but <%d> element%s did: <%s>",
          description, matching.size(), matching.size() == 1 ? "" : "s", matching);
    }
    return this;
  }

  /**
   * Allows performing custom assertions on the evaluated stream elements.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 3));
   * assertThatStream(stream).satisfies(elements -> {
   *     assertThat(elements).hasSize(3);
   *     assertThat(elements.get(0)).isEqualTo(1);
   * });
   * }</pre>
   *
   * @param requirements The consumer that performs custom assertions on the list
   * @return This assertion object for method chaining
   */
  public StreamAssert<T> satisfies(Consumer<List<T>> requirements) {
    Objects.requireNonNull(requirements, "requirements consumer cannot be null");
    List<T> list = getEvaluatedList();
    requirements.accept(list);
    return this;
  }

  /**
   * Verifies that the actual {@code Stream} starts with the specified elements.
   *
   * <p><b>Warning:</b> This forces evaluation of the stream.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 3, 4));
   * assertThatStream(stream).startsWith(1, 2);
   * }</pre>
   *
   * @param expectedStart The expected starting elements
   * @return This assertion object for method chaining
   * @throws AssertionError if the stream doesn't start with the expected elements
   */
  @SafeVarargs
  public final StreamAssert<T> startsWith(T... expectedStart) {
    List<T> list = getEvaluatedList();
    List<T> expected = List.of(expectedStart);

    if (list.size() < expected.size()) {
      failWithMessage(
          "Expected Stream to start with <%s> but stream only has <%d> element%s: <%s>",
          expected, list.size(), list.size() == 1 ? "" : "s", list);
    }

    List<T> actualStart = list.subList(0, expected.size());
    if (!actualStart.equals(expected)) {
      failWithMessage(
          "Expected Stream to start with <%s> but started with <%s>. Full stream: <%s>",
          expected, actualStart, list);
    }
    return this;
  }
}
