// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;

/**
 * Custom AssertJ assertions for {@link List} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code List} instances
 * within the Kind abstraction, making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.ListAssert.assertThatList;
 *
 * Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
 * assertThatList(list)
 *     .isNotEmpty()
 *     .hasSize(3)
 *     .containsExactly(1, 2, 3);
 *
 * Kind<ListKind.Witness, String> empty = LIST.widen(List.of());
 * assertThatList(empty).isEmpty();
 * }</pre>
 *
 * @param <T> The type of elements in the List
 */
public class ListAssert<T> extends AbstractAssert<ListAssert<T>, Kind<ListKind.Witness, T>> {

  /** Entry point. */
  public static <T> ListAssert<T> assertThatList(Kind<ListKind.Witness, T> actual) {
    return new ListAssert<>(actual);
  }

  protected ListAssert(Kind<ListKind.Witness, T> actual) {
    super(actual, ListAssert.class);
  }

  /** Verifies that the actual List is empty. */
  public ListAssert<T> isEmpty() {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected List to be empty but had <%d> element%s: <%s>",
            list.size(), list.size() == 1 ? "" : "s", list)
        .isEmpty();
    return this;
  }

  /** Verifies that the actual List is not empty. */
  public ListAssert<T> isNotEmpty() {
    isNotNull();
    Assertions.assertThat(LIST.narrow(actual))
        .withFailMessage("Expected List to be not empty but was empty")
        .isNotEmpty();
    return this;
  }

  /** Verifies that the actual List has the expected size. */
  public ListAssert<T> hasSize(int expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected List to have size <%d> but had <%d>. List contents: <%s>",
            expected, list.size(), list)
        .hasSize(expected);
    return this;
  }

  /** Verifies that the actual List contains exactly the given values in the same order. */
  @SafeVarargs
  public final ListAssert<T> containsExactly(T... expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected List to contain exactly <%s> but was <%s>", Arrays.toString(expected), list)
        .containsExactly(expected);
    return this;
  }

  /** Verifies that the actual List contains the given value. */
  public ListAssert<T> contains(T value) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected List to contain <%s> but it did not. List contents: <%s>", value, list)
        .contains(value);
    return this;
  }

  /** Verifies that the actual List contains all the given values. */
  @SafeVarargs
  public final ListAssert<T> contains(T... values) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected List to contain all of <%s> but was <%s>", Arrays.toString(values), list)
        .contains(values);
    return this;
  }

  /** Verifies that the actual List contains only the given values (in any order). */
  @SafeVarargs
  public final ListAssert<T> containsOnly(T... expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected List to contain only <%s> but was <%s>", Arrays.toString(expected), list)
        .containsOnly(expected);
    return this;
  }

  /** Verifies that the actual List does not contain the given value. */
  public ListAssert<T> doesNotContain(T value) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected List not to contain <%s> but it did. List contents: <%s>", value, list)
        .doesNotContain(value);
    return this;
  }

  /** Verifies that all elements in the List satisfy the given predicate. */
  public ListAssert<T> allMatch(Predicate<? super T> predicate) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected all elements to match predicate but at least one did not: <%s>", list)
        .allMatch(predicate);
    return this;
  }

  /** Verifies that at least one element in the List satisfies the given predicate. */
  public ListAssert<T> anyMatch(Predicate<? super T> predicate) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected at least one element to match predicate but none did. List contents: <%s>",
            list)
        .anyMatch(predicate);
    return this;
  }

  /** Verifies that the List satisfies the given requirements. */
  public ListAssert<T> satisfies(Consumer<List<T>> requirements) {
    isNotNull();
    requirements.accept(LIST.narrow(actual));
    return this;
  }

  /** Verifies that the actual List starts with the given values. */
  @SafeVarargs
  public final ListAssert<T> startsWith(T... expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected List to start with <%s>. List contents: <%s>",
            Arrays.toString(expected), list)
        .startsWith(expected);
    return this;
  }

  /** Verifies that the actual List ends with the given values. */
  @SafeVarargs
  public final ListAssert<T> endsWith(T... expected) {
    isNotNull();
    List<T> list = LIST.narrow(actual);
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected List to end with <%s>. List contents: <%s>", Arrays.toString(expected), list)
        .endsWith(expected);
    return this;
  }
}
