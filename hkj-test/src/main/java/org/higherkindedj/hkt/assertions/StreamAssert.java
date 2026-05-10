// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.stream.StreamKind;

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
 * import static org.higherkindedj.hkt.assertions.StreamAssert.assertThatStream;
 *
 * Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 3));
 * assertThatStream(stream).isNotEmpty().hasSize(3).containsExactly(1, 2, 3);
 * }</pre>
 *
 * @param <T> The type of elements in the Stream
 */
public class StreamAssert<T> extends AbstractAssert<StreamAssert<T>, Kind<StreamKind.Witness, T>> {

  // Cached list to avoid multiple stream evaluations.
  private List<T> evaluatedList;

  /** Entry point. */
  public static <T> StreamAssert<T> assertThatStream(Kind<StreamKind.Witness, T> actual) {
    return new StreamAssert<>(actual);
  }

  protected StreamAssert(Kind<StreamKind.Witness, T> actual) {
    super(actual, StreamAssert.class);
  }

  private List<T> getEvaluatedList() {
    if (evaluatedList == null) {
      isNotNull();
      evaluatedList = STREAM.narrow(actual).collect(Collectors.toList());
    }
    return evaluatedList;
  }

  /** Verifies that the actual Stream is empty. Forces evaluation of the stream. */
  public StreamAssert<T> isEmpty() {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected Stream to be empty but had <%d> element%s: <%s>",
            list.size(), list.size() == 1 ? "" : "s", list)
        .isEmpty();
    return this;
  }

  /** Verifies that the actual Stream is not empty. Forces evaluation of the stream. */
  public StreamAssert<T> isNotEmpty() {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage("Expected Stream to not be empty but was empty")
        .isNotEmpty();
    return this;
  }

  /** Verifies that the actual Stream has the specified size. Forces evaluation of the stream. */
  public StreamAssert<T> hasSize(int expectedSize) {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected Stream to have size <%d> but had size <%d>. Elements: <%s>",
            expectedSize, list.size(), list)
        .hasSize(expectedSize);
    return this;
  }

  /** Verifies that the actual Stream contains exactly the specified elements in order. */
  @SafeVarargs
  public final StreamAssert<T> containsExactly(T... expectedElements) {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected Stream to contain exactly <%s> but was <%s>", List.of(expectedElements), list)
        .containsExactly(expectedElements);
    return this;
  }

  /** Verifies that the actual Stream contains the specified element(s). */
  @SafeVarargs
  public final StreamAssert<T> contains(T... elements) {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage("Expected Stream to contain <%s>. Elements: <%s>", List.of(elements), list)
        .contains(elements);
    return this;
  }

  /** Verifies that the actual Stream does not contain the specified element. */
  public StreamAssert<T> doesNotContain(T element) {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected Stream to not contain <%s> but did. Elements: <%s>", element, list)
        .doesNotContain(element);
    return this;
  }

  /** Verifies that all elements in the Stream satisfy the given predicate. */
  public StreamAssert<T> allMatch(Predicate<? super T> predicate, String description) {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected all elements to match <%s> but at least one did not. Elements: <%s>",
            description, list)
        .allMatch(predicate);
    return this;
  }

  /** Verifies that at least one element in the Stream satisfies the given predicate. */
  public StreamAssert<T> anyMatch(Predicate<? super T> predicate, String description) {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected at least one element to match <%s> but none did. Elements: <%s>",
            description, list)
        .anyMatch(predicate);
    return this;
  }

  /** Verifies that no elements in the Stream satisfy the given predicate. */
  public StreamAssert<T> noneMatch(Predicate<? super T> predicate, String description) {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected no elements to match <%s> but at least one did. Elements: <%s>",
            description, list)
        .noneMatch(predicate);
    return this;
  }

  /** Allows performing custom assertions on the evaluated stream elements. */
  public StreamAssert<T> satisfies(Consumer<List<T>> requirements) {
    Objects.requireNonNull(requirements, "requirements consumer cannot be null");
    requirements.accept(getEvaluatedList());
    return this;
  }

  /** Verifies that the actual Stream starts with the specified elements. */
  @SafeVarargs
  public final StreamAssert<T> startsWith(T... expectedStart) {
    List<T> list = getEvaluatedList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected Stream to start with <%s>. Full stream: <%s>", List.of(expectedStart), list)
        .startsWith(expectedStart);
    return this;
  }
}
