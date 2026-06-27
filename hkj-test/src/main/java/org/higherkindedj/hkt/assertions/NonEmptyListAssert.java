// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;

/**
 * Custom AssertJ assertions for {@link NonEmptyList}.
 *
 * <p>Provides fluent assertion methods for {@code NonEmptyList}, accepting either a concrete {@code
 * NonEmptyList} or its {@link Kind} representation. Because a {@code NonEmptyList} is never empty,
 * {@link #isNotEmpty()} is a documented invariant rather than a real check, and {@link #hasHead} /
 * {@link #hasLast} target the type's total accessors.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.NonEmptyListAssert.assertThatNonEmptyList;
 *
 * assertThatNonEmptyList(NonEmptyList.of(1, 2, 3))
 *     .hasSize(3)
 *     .hasHead(1)
 *     .hasLast(3)
 *     .containsExactly(1, 2, 3);
 * }</pre>
 *
 * @param <T> the element type of the {@code NonEmptyList}
 */
public class NonEmptyListAssert<T> extends AbstractAssert<NonEmptyListAssert<T>, NonEmptyList<T>> {

  /** Entry point for a concrete {@link NonEmptyList}. */
  public static <T> NonEmptyListAssert<T> assertThatNonEmptyList(NonEmptyList<T> actual) {
    return new NonEmptyListAssert<>(actual);
  }

  /** Entry point for the {@link Kind} representation; narrows before asserting. */
  public static <T> NonEmptyListAssert<T> assertThatNonEmptyList(
      Kind<NonEmptyListKind.Witness, T> actual) {
    return new NonEmptyListAssert<>(NON_EMPTY_LIST.narrow(actual));
  }

  protected NonEmptyListAssert(NonEmptyList<T> actual) {
    super(actual, NonEmptyListAssert.class);
  }

  /**
   * Documents the guaranteed invariant that a {@code NonEmptyList} is never empty. Always passes
   * (after the null check); offered for fluency and intent, not as a real check.
   */
  public NonEmptyListAssert<T> isNotEmpty() {
    isNotNull();
    return this;
  }

  /** Verifies the (total) head equals the expected value. */
  public NonEmptyListAssert<T> hasHead(T expected) {
    isNotNull();
    Assertions.assertThat(actual.head())
        .withFailMessage(
            "Expected head <%s> but was <%s>. NonEmptyList: <%s>", expected, actual.head(), actual)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies the (total) last element equals the expected value. */
  public NonEmptyListAssert<T> hasLast(T expected) {
    isNotNull();
    Assertions.assertThat(actual.last())
        .withFailMessage(
            "Expected last <%s> but was <%s>. NonEmptyList: <%s>", expected, actual.last(), actual)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies the {@code NonEmptyList} has the expected size. */
  public NonEmptyListAssert<T> hasSize(int expected) {
    isNotNull();
    List<T> list = actual.toJavaList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected size <%d> but was <%d>. NonEmptyList: <%s>", expected, list.size(), list)
        .hasSize(expected);
    return this;
  }

  /** Verifies the elements are exactly the given values in the same order. */
  @SafeVarargs
  public final NonEmptyListAssert<T> containsExactly(T... expected) {
    isNotNull();
    List<T> list = actual.toJavaList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected NonEmptyList to contain exactly <%s> but was <%s>",
            Arrays.toString(expected), list)
        .containsExactly(expected);
    return this;
  }

  /** Verifies the {@code NonEmptyList} contains the given value. */
  public NonEmptyListAssert<T> contains(T value) {
    isNotNull();
    List<T> list = actual.toJavaList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected NonEmptyList to contain <%s> but it did not. Contents: <%s>", value, list)
        .contains(value);
    return this;
  }

  /** Verifies the {@code NonEmptyList} contains all the given values. */
  @SafeVarargs
  public final NonEmptyListAssert<T> contains(T... values) {
    isNotNull();
    List<T> list = actual.toJavaList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected NonEmptyList to contain all of <%s> but was <%s>",
            Arrays.toString(values), list)
        .contains(values);
    return this;
  }

  /** Verifies the {@code NonEmptyList} contains only the given values (in any order). */
  @SafeVarargs
  public final NonEmptyListAssert<T> containsOnly(T... expected) {
    isNotNull();
    List<T> list = actual.toJavaList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected NonEmptyList to contain only <%s> but was <%s>",
            Arrays.toString(expected), list)
        .containsOnly(expected);
    return this;
  }

  /** Verifies the {@code NonEmptyList} does not contain the given value. */
  public NonEmptyListAssert<T> doesNotContain(T value) {
    isNotNull();
    List<T> list = actual.toJavaList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected NonEmptyList not to contain <%s> but it did. Contents: <%s>", value, list)
        .doesNotContain(value);
    return this;
  }

  /** Verifies all elements satisfy the given predicate. */
  public NonEmptyListAssert<T> allMatch(Predicate<? super T> predicate) {
    isNotNull();
    List<T> list = actual.toJavaList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected all elements to match predicate but at least one did not: <%s>", list)
        .allMatch(predicate);
    return this;
  }

  /** Verifies at least one element satisfies the given predicate. */
  public NonEmptyListAssert<T> anyMatch(Predicate<? super T> predicate) {
    isNotNull();
    List<T> list = actual.toJavaList();
    Assertions.assertThat(list)
        .withFailMessage(
            "Expected at least one element to match predicate but none did. Contents: <%s>", list)
        .anyMatch(predicate);
    return this;
  }

  /** Verifies the {@code NonEmptyList} satisfies the given requirements. */
  public NonEmptyListAssert<T> satisfies(Consumer<NonEmptyList<T>> requirements) {
    isNotNull();
    requirements.accept(actual);
    return this;
  }
}
