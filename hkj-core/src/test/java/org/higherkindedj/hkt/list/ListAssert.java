// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;

/**
 * Custom AssertJ assertions for {@link List} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code List} instances
 * within the Kind abstraction, making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.list.ListAssert.assertThatList;
 *
 * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
 * assertThatList(list)
 *     .isNotEmpty()
 *     .hasSize(3)
 *     .containsExactly(1, 2, 3);
 *
 * Kind<ListKind.Witness, String> empty = LIST.widen(List.of());
 * assertThatList(empty).isEmpty();
 *
 * // Chaining with custom assertions
 * assertThatList(list)
 *     .hasSize(3)
 *     .satisfies(elements -> {
 *         assertThat(elements).allMatch(n -> n > 0);
 *     });
 * }</pre>
 *
 * @param <T> The type of elements in the List
 */
public class ListAssert<T> extends AbstractAssert<ListAssert<T>, Kind<ListKind.Witness, T>> {

  /**
   * Creates a new {@code ListAssert} instance.
   *
   * <p>This is the entry point for all List assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.list.ListAssert.assertThatList;
   * }</pre>
   *
   * @param <T> The type of elements in the List
   * @param actual The List Kind instance to make assertions on
   * @return A new ListAssert instance
   */
  public static <T> ListAssert<T> assertThatList(Kind<ListKind.Witness, T> actual) {
    return new ListAssert<>(actual);
  }

  protected ListAssert(Kind<ListKind.Witness, T> actual) {
    super(actual, ListAssert.class);
  }

  /**
   * Verifies that the actual {@code List} is empty.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, String> list = LIST.widen(List.of());
   * assertThatList(list).isEmpty();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual List is null or not empty
   */
  public ListAssert<T> isEmpty() {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    if (!list.isEmpty()) {
      failWithMessage(
          "Expected List to be empty but had <%d> element%s: <%s>",
          list.size(), list.size() == 1 ? "" : "s", list);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code List} is not empty.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2));
   * assertThatList(list).isNotEmpty();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual List is null or empty
   */
  public ListAssert<T> isNotEmpty() {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    if (list.isEmpty()) {
      failWithMessage("Expected List to be not empty but was empty");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code List} has the expected size.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
   * assertThatList(list).hasSize(3);
   * }</pre>
   *
   * @param expected The expected size
   * @return This assertion object for method chaining
   * @throws AssertionError if the List size doesn't match
   */
  public ListAssert<T> hasSize(int expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    int actualSize = list.size();
    if (actualSize != expected) {
      failWithMessage(
          "Expected List to have size <%d> but had <%d>. List contents: <%s>",
          expected, actualSize, list);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code List} contains exactly the given values in the same order.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, String> list = LIST.widen(List.of("a", "b", "c"));
   * assertThatList(list).containsExactly("a", "b", "c");
   * }</pre>
   *
   * @param expected The expected values in order
   * @return This assertion object for method chaining
   * @throws AssertionError if the List doesn't contain exactly these values in order
   */
  @SafeVarargs
  public final ListAssert<T> containsExactly(T... expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);

    if (list.size() != expected.length) {
      failWithMessage(
          "Expected List to contain exactly <%d> element%s but had <%d>. Expected: <%s>, Actual: <%s>",
          expected.length,
          expected.length == 1 ? "" : "s",
          list.size(),
          Arrays.toString(expected),
          list);
      return this;
    }

    for (int i = 0; i < expected.length; i++) {
      if (!Objects.equals(list.get(i), expected[i])) {
        failWithMessage(
            "Expected List to contain exactly <%s> but element at index <%d> was different. Expected: <%s>, Actual: <%s>",
            Arrays.toString(expected), i, expected[i], list.get(i));
      }
    }
    return this;
  }

  /**
   * Verifies that the actual {@code List} contains the given value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
   * assertThatList(list).contains(2);
   * }</pre>
   *
   * @param value The value to check for
   * @return This assertion object for method chaining
   * @throws AssertionError if the List doesn't contain the value
   */
  public ListAssert<T> contains(T value) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    if (!list.contains(value)) {
      failWithMessage(
          "Expected List to contain <%s> but it did not. List contents: <%s>", value, list);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code List} contains all the given values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
   * assertThatList(list).contains(2, 3);
   * }</pre>
   *
   * @param values The values to check for
   * @return This assertion object for method chaining
   * @throws AssertionError if the List doesn't contain all the values
   */
  @SafeVarargs
  public final ListAssert<T> contains(T... values) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    for (T value : values) {
      if (!list.contains(value)) {
        failWithMessage(
            "Expected List to contain all of <%s> but did not contain <%s>. List contents: <%s>",
            Arrays.toString(values), value, list);
      }
    }
    return this;
  }

  /**
   * Verifies that the actual {@code List} contains only the given values (in any order) and nothing else.
   * The actual list may contain duplicates of the expected values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
   * assertThatList(list).containsOnly(3, 1, 2); // Order doesn't matter
   *
   * Kind<ListKind.Witness, Integer> listWithDupes = LIST.widen(List.of(1, 1, 2, 2));
   * assertThatList(listWithDupes).containsOnly(1, 2); // Duplicates in actual are allowed
   * }</pre>
   *
   * @param expected The expected values
   * @return This assertion object for method chaining
   * @throws AssertionError if the List contains values not in expected or is missing expected values
   */
  @SafeVarargs
  public final ListAssert<T> containsOnly(T... expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    List<T> expectedList = Arrays.asList(expected);

    // Check all values in actual list are in the expected set
    for (T value : list) {
      if (!expectedList.contains(value)) {
        failWithMessage(
            "Expected List to contain only <%s> but also found <%s>. List contents: <%s>",
            Arrays.toString(expected), value, list);
        return this;
      }
    }

    // Check all expected values appear at least once in the actual list
    for (T value : expected) {
      if (!list.contains(value)) {
        failWithMessage(
            "Expected List to contain only <%s> but did not contain <%s>. List contents: <%s>",
            Arrays.toString(expected), value, list);
        return this;
      }
    }

    return this;
  }

  /**
   * Verifies that the actual {@code List} does not contain the given value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
   * assertThatList(list).doesNotContain(5);
   * }</pre>
   *
   * @param value The value to check for
   * @return This assertion object for method chaining
   * @throws AssertionError if the List contains the value
   */
  public ListAssert<T> doesNotContain(T value) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    if (list.contains(value)) {
      failWithMessage(
          "Expected List not to contain <%s> but it did. List contents: <%s>", value, list);
    }
    return this;
  }

  /**
   * Verifies that all elements in the List satisfy the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(2, 4, 6));
   * assertThatList(list).allMatch(n -> n % 2 == 0);
   * }</pre>
   *
   * @param predicate The predicate to test
   * @return This assertion object for method chaining
   * @throws AssertionError if any element doesn't satisfy the predicate
   */
  public ListAssert<T> allMatch(Predicate<? super T> predicate) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    for (int i = 0; i < list.size(); i++) {
      T element = list.get(i);
      if (!predicate.test(element)) {
        failWithMessage(
            "Expected all elements to match predicate but element at index <%d> did not: <%s>",
            i, element);
      }
    }
    return this;
  }

  /**
   * Verifies that at least one element in the List satisfies the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
   * assertThatList(list).anyMatch(n -> n > 2);
   * }</pre>
   *
   * @param predicate The predicate to test
   * @return This assertion object for method chaining
   * @throws AssertionError if no element satisfies the predicate
   */
  public ListAssert<T> anyMatch(Predicate<? super T> predicate) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    for (T element : list) {
      if (predicate.test(element)) {
        return this;
      }
    }
    failWithMessage(
        "Expected at least one element to match predicate but none did. List contents: <%s>",
        list);
    return this;
  }

  /**
   * Verifies that the List satisfies the given requirements.
   *
   * <p>This is useful for complex assertions on the List without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
   * assertThatList(list).satisfies(elements -> {
   *     assertThat(elements).hasSizeGreaterThan(2);
   *     assertThat(elements).allMatch(n -> n > 0);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the List
   * @return This assertion object for method chaining
   * @throws AssertionError if the requirements are not satisfied
   */
  public ListAssert<T> satisfies(Consumer<List<T>> requirements) {
    isNotNull();
    requirements.accept(LIST.narrow(actual));
    return this;
  }

  /**
   * Verifies that the actual {@code List} starts with the given values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, String> list = LIST.widen(List.of("a", "b", "c", "d"));
   * assertThatList(list).startsWith("a", "b");
   * }</pre>
   *
   * @param expected The expected starting values
   * @return This assertion object for method chaining
   * @throws AssertionError if the List doesn't start with these values
   */
  @SafeVarargs
  public final ListAssert<T> startsWith(T... expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);

    if (list.size() < expected.length) {
      failWithMessage(
          "Expected List to start with <%s> but list is too short. List contents: <%s>",
          Arrays.toString(expected), list);
      return this;
    }

    for (int i = 0; i < expected.length; i++) {
      if (!Objects.equals(list.get(i), expected[i])) {
        failWithMessage(
            "Expected List to start with <%s> but element at index <%d> was different. Expected: <%s>, Actual: <%s>",
            Arrays.toString(expected), i, expected[i], list.get(i));
      }
    }
    return this;
  }

  /**
   * Verifies that the actual {@code List} ends with the given values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<ListKind.Witness, String> list = LIST.widen(List.of("a", "b", "c", "d"));
   * assertThatList(list).endsWith("c", "d");
   * }</pre>
   *
   * @param expected The expected ending values
   * @return This assertion object for method chaining
   * @throws AssertionError if the List doesn't end with these values
   */
  @SafeVarargs
  public final ListAssert<T> endsWith(T... expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);

    if (list.size() < expected.length) {
      failWithMessage(
          "Expected List to end with <%s> but list is too short. List contents: <%s>",
          Arrays.toString(expected), list);
      return this;
    }

    int offset = list.size() - expected.length;
    for (int i = 0; i < expected.length; i++) {
      if (!Objects.equals(list.get(offset + i), expected[i])) {
        failWithMessage(
            "Expected List to end with <%s> but element at position <%d> from end was different. Expected: <%s>, Actual: <%s>",
            Arrays.toString(expected),
            expected.length - i,
            expected[i],
            list.get(offset + i));
      }
    }
    return this;
  }
}
