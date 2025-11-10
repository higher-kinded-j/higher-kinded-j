// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A utility interface providing static factory methods for common {@link Semigroup} instances.
 *
 * <p>Using an interface with static methods is a modern alternative to a final utility class and
 * prevents instantiation without needing a private constructor.
 */
public interface Semigroups {

  /**
   * Returns a {@code Semigroup} for {@link List} that concatenates two lists into a new list.
   *
   * @param <A> The element type of the lists.
   * @return A non-null {@code Semigroup} for list concatenation.
   */
  static <A> Semigroup<List<A>> list() {
    return (list1, list2) -> {
      List<A> combined = new ArrayList<>(list1);
      combined.addAll(list2);
      return combined;
    };
  }

  /**
   * Returns a {@code Semigroup} for {@link Set} that computes the union of two sets.
   *
   * @param <A> The element type of the sets.
   * @return A non-null {@code Semigroup} for set union.
   */
  static <A> Semigroup<Set<A>> set() {
    return (set1, set2) -> {
      Set<A> combined = new HashSet<>(set1);
      combined.addAll(set2);
      return combined;
    };
  }

  /**
   * Returns a {@code Semigroup} for basic {@link String} concatenation (s1 + s2).
   *
   * @return A non-null {@code Semigroup} for string concatenation.
   */
  static Semigroup<String> string() {
    return (s1, s2) -> s1 + s2;
  }

  /**
   * Returns a {@code Semigroup} for {@link String} that concatenates two strings with a given
   * delimiter.
   *
   * @param delimiter The string to place between the two concatenated strings.
   * @return A non-null {@code Semigroup} for delimited string concatenation.
   */
  static Semigroup<String> string(final String delimiter) {
    return (s1, s2) -> s1 + delimiter + s2;
  }

  /**
   * Returns a {@code Semigroup} that always returns the first of its two arguments.
   *
   * <p>This can be useful for creating an {@code Applicative} for {@code Validated} that has
   * "fail-fast" behaviour, similar to a {@code Monad}.
   *
   * @param <A> The type of the elements.
   * @return A non-null {@code Semigroup} that always selects the first element.
   */
  static <A> Semigroup<A> first() {
    return (a1, a2) -> a1;
  }

  /**
   * Returns a {@code Semigroup} that always returns the second (last) of its two arguments.
   *
   * @param <A> The type of the elements.
   * @return A non-null {@code Semigroup} that always selects the last element.
   */
  static <A> Semigroup<A> last() {
    return (a1, a2) -> a2;
  }
}
