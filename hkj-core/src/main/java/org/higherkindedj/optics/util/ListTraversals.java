// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
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
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> modifyF(
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
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> modifyF(
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
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> modifyF(
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
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> modifyF(
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
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        final int size = source.size();

        // Clamp indices to valid bounds
        final int effectiveFrom = Math.max(0, Math.min(from, size));
        final int effectiveTo = Math.max(effectiveFrom, Math.min(to, size));

        if (effectiveFrom >= effectiveTo) {
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

  /**
   * Creates a {@code Traversal} that focuses on elements from the beginning of a list while a
   * predicate holds true.
   *
   * <p>This traversal focuses on the longest prefix of elements that all satisfy the given
   * predicate. Once an element fails the predicate, that element and all subsequent elements are
   * excluded from the focus (but preserved unchanged during modifications).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<Integer>, Integer> whileLessThan5 =
   *     ListTraversals.takingWhile(x -> x < 5);
   *
   * List<Integer> numbers = List.of(1, 2, 3, 7, 4, 8);
   * List<Integer> doubled = Traversals.modify(whileLessThan5, x -> x * 2, numbers);
   * // Result: [2, 4, 6, 7, 4, 8] - only first 3 elements doubled (stopped at 7)
   *
   * List<Integer> gotten = Traversals.getAll(whileLessThan5, numbers);
   * // Result: [1, 2, 3] - elements before first failure
   * }</pre>
   *
   * @param predicate The predicate to test elements against. Once an element fails, traversal
   *     stops.
   * @param <A> The element type of the list.
   * @return A {@code Traversal} focusing on the longest prefix of elements satisfying the
   *     predicate.
   */
  public static <A> Traversal<List<A>, A> takingWhile(final Predicate<? super A> predicate) {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        if (source.isEmpty()) {
          return applicative.of(source);
        }

        // Find the index where the predicate first fails
        final int splitPoint = findSplitPoint(source, predicate);

        if (splitPoint == 0) {
          // No elements satisfy predicate
          return applicative.of(source);
        }

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
   * Creates a {@code Traversal} that skips elements from the beginning of a list while a predicate
   * holds true, then focuses on the rest.
   *
   * <p>This traversal skips the longest prefix of elements that all satisfy the given predicate,
   * then focuses on all remaining elements. The skipped elements are preserved unchanged during
   * modifications.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<Integer>, Integer> afterLessThan5 =
   *     ListTraversals.droppingWhile(x -> x < 5);
   *
   * List<Integer> numbers = List.of(1, 2, 3, 7, 4, 8);
   * List<Integer> doubled = Traversals.modify(afterLessThan5, x -> x * 2, numbers);
   * // Result: [1, 2, 3, 14, 8, 16] - skipped first 3, doubled rest
   *
   * List<Integer> gotten = Traversals.getAll(afterLessThan5, numbers);
   * // Result: [7, 4, 8] - elements after prefix
   * }</pre>
   *
   * @param predicate The predicate to test elements against. Elements are skipped while this
   *     returns true.
   * @param <A> The element type of the list.
   * @return A {@code Traversal} focusing on elements after the longest prefix satisfying the
   *     predicate.
   */
  public static <A> Traversal<List<A>, A> droppingWhile(final Predicate<? super A> predicate) {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        if (source.isEmpty()) {
          return applicative.of(source);
        }

        // Find the index where the predicate first fails
        final int splitPoint = findSplitPoint(source, predicate);

        if (splitPoint >= source.size()) {
          // All elements satisfy predicate, nothing to focus on
          return applicative.of(source);
        }

        final List<A> prefix = source.subList(0, splitPoint);
        final List<A> suffix = source.subList(splitPoint, source.size());

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
   * Creates a {@code Traversal} that focuses on a single element at the specified index.
   *
   * <p>This is an affine traversal with 0-1 cardinality. If the index is within bounds, the
   * traversal focuses on that single element. If the index is out of bounds, the traversal focuses
   * on zero elements and modifications have no effect.
   *
   * <p>This differs from {@link org.higherkindedj.optics.Ixed} in that it returns a {@code
   * Traversal} rather than relying on the type class pattern. It can be freely composed with other
   * traversals.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<Integer>, Integer> secondElement = ListTraversals.element(1);
   *
   * List<Integer> numbers = List.of(10, 20, 30);
   * List<Integer> doubled = Traversals.modify(secondElement, x -> x * 2, numbers);
   * // Result: [10, 40, 30] - only second element (index 1) doubled
   *
   * List<Integer> gotten = Traversals.getAll(secondElement, numbers);
   * // Result: [20] - single element at index 1
   *
   * // Out of bounds - no modification
   * List<Integer> unchanged = Traversals.modify(
   *     ListTraversals.element(10),
   *     x -> x * 2,
   *     numbers
   * );
   * // Result: [10, 20, 30] - unchanged because index 10 is out of bounds
   * }</pre>
   *
   * @param index The zero-based index of the element to focus on.
   * @param <A> The element type of the list.
   * @return A {@code Traversal} focusing on the element at the specified index, if it exists.
   */
  public static <A> Traversal<List<A>, A> element(final int index) {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        if (index < 0 || index >= source.size()) {
          // Index out of bounds, no modification
          return applicative.of(source);
        }

        // Apply function to the element at the index
        final A element = source.get(index);
        final Kind<F, A> modifiedF = f.apply(element);

        // Map the result back into the list
        return applicative.map(
            newElement -> {
              final List<A> result = new ArrayList<>(source);
              result.set(index, newElement);
              return result;
            },
            modifiedF);
      }
    };
  }

  /**
   * Finds the index where a predicate first fails in a list.
   *
   * <p>This helper method iterates through the list and returns the index immediately after the
   * last element that satisfies the predicate. If all elements satisfy the predicate, it returns
   * the list size. If no elements satisfy the predicate, it returns 0.
   *
   * @param source The list to search
   * @param predicate The predicate to test elements against
   * @param <A> The element type
   * @return The split point index (0 to source.size())
   */
  private static <A> int findSplitPoint(
      final List<A> source, final Predicate<? super A> predicate) {
    int splitPoint = 0;
    for (int i = 0; i < source.size(); i++) {
      if (predicate.test(source.get(i))) {
        splitPoint = i + 1;
      } else {
        break; // Stop at first failure
      }
    }
    return splitPoint;
  }
}
