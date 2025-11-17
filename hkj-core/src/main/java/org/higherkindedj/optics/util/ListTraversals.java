// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * A final utility class providing static factory methods for creating {@link Traversal}s that focus
 * on specific portions of {@link List}s.
 *
 * <p>These combinators allow limiting which elements of a list are focused upon, enabling
 * operations like "modify only the first 3 elements" or "transform elements from index 2 to 5".
 *
 * <p>All methods follow consistent edge-case handling:
 *
 * <ul>
 *   <li>Negative indices are treated as 0 (identity behavior)
 *   <li>Indices beyond list size are clamped to list bounds
 *   <li>Empty lists always return identity (no modification)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Modify only the first 3 users
 * Traversal<List<User>, User> first3 = ListTraversals.taking(3);
 * List<User> modified = Traversals.modify(first3, User::activate, users);
 *
 * // Get the last 2 items
 * Traversal<List<Item>, Item> last2 = ListTraversals.takingLast(2);
 * List<Item> items = Traversals.getAll(last2, allItems);
 *
 * // Slice elements from index 1 to 4 (exclusive)
 * Traversal<List<String>, String> slice = ListTraversals.slicing(1, 4);
 * List<String> sliced = Traversals.getAll(slice, strings);
 * }</pre>
 */
@NullMarked
public final class ListTraversals {

  /** Private constructor to prevent instantiation. */
  private ListTraversals() {}

  /**
   * Creates a {@code Traversal} that focuses on at most the first {@code n} elements of a list.
   *
   * <p>Elements beyond the first {@code n} are preserved unchanged during modifications but are not
   * included in query operations like {@code getAll}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<Integer>, Integer> first3 = ListTraversals.taking(3);
   *
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   * List<Integer> doubled = Traversals.modify(first3, x -> x * 2, numbers);
   * // Result: [2, 4, 6, 4, 5] - only first 3 doubled
   *
   * List<Integer> gotten = Traversals.getAll(first3, numbers);
   * // Result: [1, 2, 3] - only first 3 returned
   * }</pre>
   *
   * @param n The maximum number of elements to focus on. If {@code n <= 0}, returns an identity
   *     traversal that focuses on no elements. If {@code n >= list.size()}, focuses on all
   *     elements.
   * @param <A> The element type of the list.
   * @return A {@code Traversal} focusing on at most the first {@code n} elements.
   */
  public static <A> Traversal<List<A>, A> taking(final int n) {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        if (n <= 0 || source.isEmpty()) {
          return applicative.of(source);
        }

        final int splitPoint = Math.min(n, source.size());
        final List<A> prefix = source.subList(0, splitPoint);
        final List<A> suffix = source.subList(splitPoint, source.size());

        // Traverse prefix with effects
        final Kind<F, List<A>> modifiedPrefixF = Traversals.traverseList(prefix, f, applicative);

        // Combine with unmodified suffix
        return applicative.map(
            newPrefix -> {
              final List<A> result = new ArrayList<>(source.size());
              result.addAll(newPrefix);
              result.addAll(suffix);
              return result;
            },
            modifiedPrefixF);
      }
    };
  }

  /**
   * Creates a {@code Traversal} that focuses on all elements after skipping the first {@code n}.
   *
   * <p>The first {@code n} elements are preserved unchanged during modifications but are not
   * included in query operations like {@code getAll}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<Integer>, Integer> afterFirst2 = ListTraversals.dropping(2);
   *
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   * List<Integer> doubled = Traversals.modify(afterFirst2, x -> x * 2, numbers);
   * // Result: [1, 2, 6, 8, 10] - skipped first 2, doubled rest
   *
   * List<Integer> gotten = Traversals.getAll(afterFirst2, numbers);
   * // Result: [3, 4, 5] - skipped first 2
   * }</pre>
   *
   * @param n The number of elements to skip. If {@code n <= 0}, focuses on all elements. If {@code
   *     n >= list.size()}, returns an identity traversal focusing on no elements.
   * @param <A> The element type of the list.
   * @return A {@code Traversal} focusing on elements after skipping the first {@code n}.
   */
  public static <A> Traversal<List<A>, A> dropping(final int n) {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        if (n <= 0) {
          // Drop nothing, focus on all elements
          return Traversals.traverseList(source, f, applicative);
        }

        if (n >= source.size()) {
          return applicative.of(source);
        }

        final List<A> prefix = source.subList(0, n);
        final List<A> suffix = source.subList(n, source.size());

        // Traverse suffix with effects
        final Kind<F, List<A>> modifiedSuffixF = Traversals.traverseList(suffix, f, applicative);

        // Combine with unmodified prefix
        return applicative.map(
            newSuffix -> {
              final List<A> result = new ArrayList<>(source.size());
              result.addAll(prefix);
              result.addAll(newSuffix);
              return result;
            },
            modifiedSuffixF);
      }
    };
  }

  /**
   * Creates a {@code Traversal} that focuses on at most the last {@code n} elements of a list.
   *
   * <p>Elements before the last {@code n} are preserved unchanged during modifications but are not
   * included in query operations like {@code getAll}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<Integer>, Integer> last3 = ListTraversals.takingLast(3);
   *
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   * List<Integer> doubled = Traversals.modify(last3, x -> x * 2, numbers);
   * // Result: [1, 2, 6, 8, 10] - only last 3 doubled
   *
   * List<Integer> gotten = Traversals.getAll(last3, numbers);
   * // Result: [3, 4, 5] - only last 3 returned
   * }</pre>
   *
   * @param n The maximum number of elements from the end to focus on. If {@code n <= 0}, returns an
   *     identity traversal that focuses on no elements. If {@code n >= list.size()}, focuses on all
   *     elements.
   * @param <A> The element type of the list.
   * @return A {@code Traversal} focusing on at most the last {@code n} elements.
   */
  public static <A> Traversal<List<A>, A> takingLast(final int n) {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        if (n <= 0 || source.isEmpty()) {
          return applicative.of(source);
        }

        final int splitPoint = Math.max(0, source.size() - n);
        final List<A> prefix = source.subList(0, splitPoint);
        final List<A> suffix = source.subList(splitPoint, source.size());

        // Traverse suffix (last n elements) with effects
        final Kind<F, List<A>> modifiedSuffixF = Traversals.traverseList(suffix, f, applicative);

        // Combine with unmodified prefix
        return applicative.map(
            newSuffix -> {
              final List<A> result = new ArrayList<>(source.size());
              result.addAll(prefix);
              result.addAll(newSuffix);
              return result;
            },
            modifiedSuffixF);
      }
    };
  }

  /**
   * Creates a {@code Traversal} that focuses on all elements except the last {@code n}.
   *
   * <p>The last {@code n} elements are preserved unchanged during modifications but are not
   * included in query operations like {@code getAll}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<Integer>, Integer> exceptLast2 = ListTraversals.droppingLast(2);
   *
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   * List<Integer> doubled = Traversals.modify(exceptLast2, x -> x * 2, numbers);
   * // Result: [2, 4, 6, 4, 5] - doubled all except last 2
   *
   * List<Integer> gotten = Traversals.getAll(exceptLast2, numbers);
   * // Result: [1, 2, 3] - all except last 2
   * }</pre>
   *
   * @param n The number of elements from the end to exclude. If {@code n <= 0}, focuses on all
   *     elements. If {@code n >= list.size()}, returns an identity traversal focusing on no
   *     elements.
   * @param <A> The element type of the list.
   * @return A {@code Traversal} focusing on all elements except the last {@code n}.
   */
  public static <A> Traversal<List<A>, A> droppingLast(final int n) {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        if (n <= 0) {
          // Drop nothing from end, focus on all elements
          return Traversals.traverseList(source, f, applicative);
        }

        if (n >= source.size()) {
          return applicative.of(source);
        }

        final int splitPoint = source.size() - n;
        final List<A> prefix = source.subList(0, splitPoint);
        final List<A> suffix = source.subList(splitPoint, source.size());

        // Traverse prefix with effects
        final Kind<F, List<A>> modifiedPrefixF = Traversals.traverseList(prefix, f, applicative);

        // Combine with unmodified suffix
        return applicative.map(
            newPrefix -> {
              final List<A> result = new ArrayList<>(source.size());
              result.addAll(newPrefix);
              result.addAll(suffix);
              return result;
            },
            modifiedPrefixF);
      }
    };
  }

  /**
   * Creates a {@code Traversal} that focuses on elements within a specified index range.
   *
   * <p>Elements outside the range {@code [from, to)} are preserved unchanged during modifications
   * but are not included in query operations like {@code getAll}.
   *
   * <p>The range is half-open: {@code from} is inclusive, {@code to} is exclusive (consistent with
   * {@link List#subList}).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<Integer>, Integer> slice = ListTraversals.slicing(1, 4);
   *
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   * List<Integer> doubled = Traversals.modify(slice, x -> x * 2, numbers);
   * // Result: [1, 4, 6, 8, 5] - doubled elements at indices 1, 2, 3
   *
   * List<Integer> gotten = Traversals.getAll(slice, numbers);
   * // Result: [2, 3, 4] - elements at indices 1, 2, 3
   * }</pre>
   *
   * @param from The starting index (inclusive). Clamped to 0 if negative.
   * @param to The ending index (exclusive). Clamped to list size if beyond bounds. If {@code to <=
   *     from}, returns an identity traversal focusing on no elements.
   * @param <A> The element type of the list.
   * @return A {@code Traversal} focusing on elements within the specified range.
   */
  public static <A> Traversal<List<A>, A> slicing(final int from, final int to) {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        final int size = source.size();

        // Clamp indices to valid bounds
        final int effectiveFrom = Math.max(0, Math.min(from, size));
        final int effectiveTo = Math.max(effectiveFrom, Math.min(to, size));

        if (effectiveFrom >= effectiveTo || source.isEmpty()) {
          return applicative.of(source);
        }

        final List<A> prefix = source.subList(0, effectiveFrom);
        final List<A> middle = source.subList(effectiveFrom, effectiveTo);
        final List<A> suffix = source.subList(effectiveTo, size);

        // Traverse middle (sliced portion) with effects
        final Kind<F, List<A>> modifiedMiddleF = Traversals.traverseList(middle, f, applicative);

        // Combine all three parts
        return applicative.map(
            newMiddle -> {
              final List<A> result = new ArrayList<>(source.size());
              result.addAll(prefix);
              result.addAll(newMiddle);
              result.addAll(suffix);
              return result;
            },
            modifiedMiddleF);
      }
    };
  }
}
