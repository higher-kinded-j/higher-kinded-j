// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.indexed.Pair;
import org.jspecify.annotations.NullMarked;

/**
 * A final utility class providing {@link Prism} and {@link Affine} instances for functional list
 * decomposition patterns.
 *
 * <p>This class provides the classic functional programming patterns for working with lists:
 *
 * <ul>
 *   <li><b>cons (head/tail)</b>: Decompose a list as (first element, remaining elements)
 *   <li><b>snoc (init/last)</b>: Decompose a list as (all but last, last element)
 * </ul>
 *
 * <p>These patterns enable functional programming idioms like pattern matching and recursive
 * algorithms in Java. All operations are safe for empty lists, returning {@code Optional.empty()}
 * when the list cannot be decomposed.
 *
 * <h2>Decomposition Patterns</h2>
 *
 * <pre>{@code
 * // Cons: [A, B, C, D] → Pair(A, [B, C, D])
 * Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();
 * Optional<Pair<String, List<String>>> result = cons.getOptional(List.of("a", "b", "c"));
 * // result = Optional.of(Pair("a", ["b", "c"]))
 *
 * // Snoc: [A, B, C, D] → Pair([A, B, C], D)
 * Prism<List<String>, Pair<List<String>, String>> snoc = ListPrisms.snoc();
 * Optional<Pair<List<String>, String>> result = snoc.getOptional(List.of("a", "b", "c"));
 * // result = Optional.of(Pair(["a", "b"], "c"))
 * }</pre>
 *
 * <h2>Convenience Accessors</h2>
 *
 * <p>For simpler access patterns, use the convenience affines and prisms:
 *
 * <pre>{@code
 * Affine<List<String>, String> head = ListPrisms.head();
 * Affine<List<String>, String> last = ListPrisms.last();
 * Prism<List<String>, List<String>> tail = ListPrisms.tail();
 * Prism<List<String>, List<String>> init = ListPrisms.init();
 * }</pre>
 *
 * <h2>Stack-Safe Operations</h2>
 *
 * <p>For processing very large lists, this class provides trampoline-based operations that avoid
 * stack overflow:
 *
 * <pre>{@code
 * List<Integer> largeList = IntStream.range(0, 1_000_000).boxed().toList();
 * Integer sum = ListPrisms.foldRight(largeList, 0, Integer::sum);
 * List<Integer> doubled = ListPrisms.mapTrampoline(largeList, x -> x * 2);
 * }</pre>
 *
 * @see Prism
 * @see Affine
 * @see Pair
 */
@NullMarked
public final class ListPrisms {
  /** Private constructor to prevent instantiation. */
  private ListPrisms() {}

  // ===== Core Decomposition Prisms =====

  /**
   * Creates a prism that decomposes a non-empty list into its head (first element) and tail
   * (remaining elements).
   *
   * <p>This is the classic "cons" pattern from functional programming. The prism matches non-empty
   * lists and decomposes them into a pair of (head, tail).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();
   *
   * // Decompose a list
   * List<String> names = List.of("Alice", "Bob", "Charlie");
   * Optional<Pair<String, List<String>>> result = cons.getOptional(names);
   * // result = Optional.of(Pair("Alice", ["Bob", "Charlie"]))
   *
   * // Empty list returns empty
   * Optional<Pair<String, List<String>>> empty = cons.getOptional(List.of());
   * // empty = Optional.empty()
   *
   * // Build a list by prepending
   * List<String> built = cons.build(Pair.of("First", List.of("Second", "Third")));
   * // built = ["First", "Second", "Third"]
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return A prism that decomposes a list as (head, tail).
   */
  public static <A> Prism<List<A>, Pair<A, List<A>>> cons() {
    return Prism.of(
        list -> {
          if (list.isEmpty()) {
            return Optional.empty();
          }
          A head = list.getFirst();
          List<A> tail = list.subList(1, list.size());
          return Optional.of(Pair.of(head, copyList(tail)));
        },
        pair -> {
          List<A> result = new ArrayList<>(pair.second().size() + 1);
          result.add(pair.first());
          result.addAll(pair.second());
          return copyList(result);
        });
  }

  /**
   * Alias for {@link #cons()} using Java-friendly naming.
   *
   * <p>This method is identical to {@code cons()} but uses terminology that may be more familiar to
   * Java developers.
   *
   * @param <A> The element type of the list.
   * @return A prism that decomposes a list as (head, tail).
   * @see #cons()
   */
  public static <A> Prism<List<A>, Pair<A, List<A>>> headTail() {
    return cons();
  }

  /**
   * Creates a prism that decomposes a non-empty list into its init (all but last) and last element.
   *
   * <p>This is the "snoc" pattern (cons spelled backwards) from functional programming. The prism
   * matches non-empty lists and decomposes them into a pair of (init, last).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();
   *
   * // Decompose a list
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   * Optional<Pair<List<Integer>, Integer>> result = snoc.getOptional(numbers);
   * // result = Optional.of(Pair([1, 2, 3, 4], 5))
   *
   * // Empty list returns empty
   * Optional<Pair<List<Integer>, Integer>> empty = snoc.getOptional(List.of());
   * // empty = Optional.empty()
   *
   * // Build a list by appending
   * List<Integer> built = snoc.build(Pair.of(List.of(1, 2, 3), 4));
   * // built = [1, 2, 3, 4]
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return A prism that decomposes a list as (init, last).
   */
  public static <A> Prism<List<A>, Pair<List<A>, A>> snoc() {
    return Prism.of(
        list -> {
          if (list.isEmpty()) {
            return Optional.empty();
          }
          A last = list.getLast();
          List<A> init = list.subList(0, list.size() - 1);
          return Optional.of(Pair.of(copyList(init), last));
        },
        pair -> {
          List<A> result = new ArrayList<>(pair.first().size() + 1);
          result.addAll(pair.first());
          result.add(pair.second());
          return copyList(result);
        });
  }

  /**
   * Alias for {@link #snoc()} using Java-friendly naming.
   *
   * <p>This method is identical to {@code snoc()} but uses terminology that may be more familiar to
   * Java developers.
   *
   * @param <A> The element type of the list.
   * @return A prism that decomposes a list as (init, last).
   * @see #snoc()
   */
  public static <A> Prism<List<A>, Pair<List<A>, A>> initLast() {
    return snoc();
  }

  // ===== Convenience Affines and Prisms =====

  /**
   * Creates an affine that focuses on the first element of a list.
   *
   * <p>Unlike the prism from {@link Prisms#listHead()}, this affine properly updates the list when
   * setting, preserving all other elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<List<String>, String> head = ListPrisms.head();
   *
   * // Get the first element
   * Optional<String> first = head.getOptional(List.of("a", "b", "c"));
   * // first = Optional.of("a")
   *
   * // Modify the first element
   * List<String> modified = head.modify(String::toUpperCase, List.of("a", "b", "c"));
   * // modified = ["A", "b", "c"]
   *
   * // Set on empty list creates singleton
   * List<String> created = head.set("new", List.of());
   * // created = ["new"]
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return An affine focusing on the first element.
   */
  public static <A> Affine<List<A>, A> head() {
    return Affine.of(
        list -> list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst()),
        (list, value) -> {
          if (list.isEmpty()) {
            return List.of(value);
          }
          List<A> result = new ArrayList<>(list);
          result.set(0, value);
          return copyList(result);
        });
  }

  /**
   * Creates an affine that focuses on the last element of a list.
   *
   * <p>This affine properly updates the list when setting, preserving all other elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<List<Integer>, Integer> last = ListPrisms.last();
   *
   * // Get the last element
   * Optional<Integer> lastElem = last.getOptional(List.of(1, 2, 3, 4, 5));
   * // lastElem = Optional.of(5)
   *
   * // Modify the last element
   * List<Integer> modified = last.modify(x -> x * 10, List.of(1, 2, 3));
   * // modified = [1, 2, 30]
   *
   * // Set on empty list creates singleton
   * List<Integer> created = last.set(42, List.of());
   * // created = [42]
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return An affine focusing on the last element.
   */
  public static <A> Affine<List<A>, A> last() {
    return Affine.of(
        list -> list.isEmpty() ? Optional.empty() : Optional.of(list.getLast()),
        (list, value) -> {
          if (list.isEmpty()) {
            return List.of(value);
          }
          List<A> result = new ArrayList<>(list);
          result.set(result.size() - 1, value);
          return copyList(result);
        });
  }

  /**
   * Creates an affine that focuses on the tail (all elements except the first) of a list.
   *
   * <p>Note: This is an Affine rather than a Prism because the prism laws would be violated. A
   * Prism requires {@code getOptional(build(a)) == Optional.of(a)}, but for tail, building from a
   * tail alone loses the head information.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<List<String>, List<String>> tail = ListPrisms.tail();
   *
   * // Get the tail
   * Optional<List<String>> t = tail.getOptional(List.of("a", "b", "c"));
   * // t = Optional.of(["b", "c"])
   *
   * // Empty list returns empty
   * Optional<List<String>> empty = tail.getOptional(List.of());
   * // empty = Optional.empty()
   *
   * // Single element has empty tail
   * Optional<List<String>> single = tail.getOptional(List.of("solo"));
   * // single = Optional.of([])
   *
   * // Set a new tail
   * List<String> updated = tail.set(List.of("x", "y"), List.of("a", "b", "c"));
   * // updated = ["a", "x", "y"]
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return An affine focusing on the tail of a list.
   */
  public static <A> Affine<List<A>, List<A>> tail() {
    return Affine.of(
        list -> {
          if (list.isEmpty()) {
            return Optional.empty();
          }
          return Optional.of(copyList(list.subList(1, list.size())));
        },
        (list, newTail) -> {
          if (list.isEmpty()) {
            return list;
          }
          List<A> result = new ArrayList<>(newTail.size() + 1);
          result.add(list.getFirst());
          result.addAll(newTail);
          return copyList(result);
        });
  }

  /**
   * Creates an affine that focuses on the init (all elements except the last) of a list.
   *
   * <p>Note: This is an Affine rather than a Prism because the prism laws would be violated. A
   * Prism requires {@code getOptional(build(a)) == Optional.of(a)}, but for init, building from an
   * init alone loses the last element information.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<List<Integer>, List<Integer>> init = ListPrisms.init();
   *
   * // Get the init
   * Optional<List<Integer>> i = init.getOptional(List.of(1, 2, 3, 4, 5));
   * // i = Optional.of([1, 2, 3, 4])
   *
   * // Empty list returns empty
   * Optional<List<Integer>> empty = init.getOptional(List.of());
   * // empty = Optional.empty()
   *
   * // Single element has empty init
   * Optional<List<Integer>> single = init.getOptional(List.of(42));
   * // single = Optional.of([])
   *
   * // Set a new init
   * List<Integer> updated = init.set(List.of(10, 20), List.of(1, 2, 3));
   * // updated = [10, 20, 3]
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return An affine focusing on the init of a list.
   */
  public static <A> Affine<List<A>, List<A>> init() {
    return Affine.of(
        list -> {
          if (list.isEmpty()) {
            return Optional.empty();
          }
          return Optional.of(copyList(list.subList(0, list.size() - 1)));
        },
        (list, newInit) -> {
          if (list.isEmpty()) {
            return list;
          }
          List<A> result = new ArrayList<>(newInit.size() + 1);
          result.addAll(newInit);
          result.add(list.getLast());
          return copyList(result);
        });
  }

  /**
   * Creates a prism that matches only empty lists.
   *
   * <p>This prism is the complement to {@link #cons()} and {@link #snoc()}, matching the empty case
   * for complete pattern matching on list structure.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<List<String>, Unit> empty = ListPrisms.empty();
   *
   * // Match empty list
   * boolean isEmpty = empty.matches(List.of());       // true
   * boolean isNotEmpty = empty.matches(List.of("a")); // false
   *
   * // Can be used for pattern matching
   * if (ListPrisms.<String>empty().matches(list)) {
   *     return "Empty list";
   * } else {
   *     Pair<String, List<String>> ht = ListPrisms.<String>cons()
   *         .getOptional(list).orElseThrow();
   *     return "Head: " + ht.first();
   * }
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return A prism that matches empty lists.
   */
  public static <A> Prism<List<A>, Unit> empty() {
    return Prism.of(
        list -> list.isEmpty() ? Optional.of(Unit.INSTANCE) : Optional.empty(), unit -> List.of());
  }

  // ===== Stack-Safe Trampoline Operations =====

  /**
   * Performs a right-associative fold over a list using trampolines for stack safety.
   *
   * <p>This is equivalent to {@code foldr} in Haskell. The fold processes elements from right to
   * left, which is natural for operations like building a new list or computing right-associative
   * operations.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   *
   * // Sum all elements
   * Integer sum = ListPrisms.foldRight(numbers, 0, Integer::sum);
   * // sum = 15
   *
   * // Build a string (right-to-left)
   * String str = ListPrisms.foldRight(List.of("a", "b", "c"), "", (s, acc) -> s + acc);
   * // str = "abc"
   * }</pre>
   *
   * @param list The list to fold.
   * @param initial The initial accumulator value.
   * @param f The combining function: (element, accumulator) -> new accumulator.
   * @param <A> The element type.
   * @param <B> The accumulator type.
   * @return The final accumulated value.
   */
  public static <A, B> B foldRight(List<A> list, B initial, BiFunction<A, B, B> f) {
    return foldRightTrampoline(list, 0, initial, f).run();
  }

  private static <A, B> Trampoline<B> foldRightTrampoline(
      List<A> list, int index, B acc, BiFunction<A, B, B> f) {
    if (index >= list.size()) {
      return Trampoline.done(acc);
    }
    return Trampoline.defer(
        () ->
            foldRightTrampoline(list, index + 1, acc, f)
                .map(restAcc -> f.apply(list.get(index), restAcc)));
  }

  /**
   * Maps a function over a list using trampolines for stack safety.
   *
   * <p>This is equivalent to the standard {@code map} operation but is safe for arbitrarily large
   * lists that would cause stack overflow with naive recursion.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<Integer> numbers = IntStream.range(0, 1_000_000).boxed().toList();
   *
   * // Double all elements (stack-safe)
   * List<Integer> doubled = ListPrisms.mapTrampoline(numbers, x -> x * 2);
   * }</pre>
   *
   * @param list The list to transform.
   * @param f The mapping function.
   * @param <A> The input element type.
   * @param <B> The output element type.
   * @return A new list with the function applied to each element.
   */
  public static <A, B> List<B> mapTrampoline(List<A> list, Function<A, B> f) {
    return mapTrampolineHelper(list, 0, f, new ArrayList<>(list.size())).run();
  }

  private static <A, B> Trampoline<List<B>> mapTrampolineHelper(
      List<A> list, int index, Function<A, B> f, ArrayList<B> acc) {
    if (index >= list.size()) {
      return Trampoline.done(copyList(acc));
    }
    acc.add(f.apply(list.get(index)));
    return Trampoline.defer(() -> mapTrampolineHelper(list, index + 1, f, acc));
  }

  /**
   * Filters a list using trampolines for stack safety.
   *
   * <p>This is equivalent to the standard {@code filter} operation but is safe for arbitrarily
   * large lists.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<Integer> numbers = IntStream.range(0, 1_000_000).boxed().toList();
   *
   * // Keep only even numbers (stack-safe)
   * List<Integer> evens = ListPrisms.filterTrampoline(numbers, x -> x % 2 == 0);
   * }</pre>
   *
   * @param list The list to filter.
   * @param predicate The predicate to test elements.
   * @param <A> The element type.
   * @return A new list containing only elements that satisfy the predicate.
   */
  public static <A> List<A> filterTrampoline(List<A> list, Predicate<A> predicate) {
    return filterTrampolineHelper(list, 0, predicate, new ArrayList<>()).run();
  }

  private static <A> Trampoline<List<A>> filterTrampolineHelper(
      List<A> list, int index, Predicate<A> predicate, ArrayList<A> acc) {
    if (index >= list.size()) {
      return Trampoline.done(copyList(acc));
    }
    A element = list.get(index);
    if (predicate.test(element)) {
      acc.add(element);
    }
    return Trampoline.defer(() -> filterTrampolineHelper(list, index + 1, predicate, acc));
  }

  /**
   * Reverses a list using trampolines for stack safety.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   * List<Integer> reversed = ListPrisms.reverseTrampoline(numbers);
   * // reversed = [5, 4, 3, 2, 1]
   * }</pre>
   *
   * @param list The list to reverse.
   * @param <A> The element type.
   * @return A new list with elements in reverse order.
   */
  public static <A> List<A> reverseTrampoline(List<A> list) {
    return reverseTrampolineHelper(list, list.size() - 1, new ArrayList<>(list.size())).run();
  }

  private static <A> Trampoline<List<A>> reverseTrampolineHelper(
      List<A> list, int index, ArrayList<A> acc) {
    if (index < 0) {
      return Trampoline.done(copyList(acc));
    }
    acc.add(list.get(index));
    return Trampoline.defer(() -> reverseTrampolineHelper(list, index - 1, acc));
  }

  /**
   * FlatMaps a function over a list using trampolines for stack safety.
   *
   * <p>This is equivalent to the standard {@code flatMap} operation but is safe for arbitrarily
   * large lists.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<Integer> numbers = List.of(1, 2, 3);
   * List<Integer> expanded = ListPrisms.flatMapTrampoline(numbers, x -> List.of(x, x * 10));
   * // expanded = [1, 10, 2, 20, 3, 30]
   * }</pre>
   *
   * @param list The list to transform.
   * @param f The function that produces a list for each element.
   * @param <A> The input element type.
   * @param <B> The output element type.
   * @return A new list with all results concatenated.
   */
  public static <A, B> List<B> flatMapTrampoline(List<A> list, Function<A, List<B>> f) {
    return flatMapTrampolineHelper(list, 0, f, new ArrayList<>()).run();
  }

  private static <A, B> Trampoline<List<B>> flatMapTrampolineHelper(
      List<A> list, int index, Function<A, List<B>> f, ArrayList<B> acc) {
    if (index >= list.size()) {
      return Trampoline.done(copyList(acc));
    }
    acc.addAll(f.apply(list.get(index)));
    return Trampoline.defer(() -> flatMapTrampolineHelper(list, index + 1, f, acc));
  }

  /**
   * Zips two lists together using a combining function, with stack safety.
   *
   * <p>The resulting list has the length of the shorter input list.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<String> names = List.of("Alice", "Bob", "Charlie");
   * List<Integer> ages = List.of(30, 25, 35);
   *
   * List<String> combined = ListPrisms.zipWithTrampoline(names, ages,
   *     (name, age) -> name + " is " + age);
   * // combined = ["Alice is 30", "Bob is 25", "Charlie is 35"]
   * }</pre>
   *
   * @param list1 The first list.
   * @param list2 The second list.
   * @param f The combining function.
   * @param <A> The first list element type.
   * @param <B> The second list element type.
   * @param <C> The result element type.
   * @return A new list of combined elements.
   */
  public static <A, B, C> List<C> zipWithTrampoline(
      List<A> list1, List<B> list2, BiFunction<A, B, C> f) {
    int minSize = Math.min(list1.size(), list2.size());
    return zipWithTrampolineHelper(list1, list2, 0, minSize, f, new ArrayList<>(minSize)).run();
  }

  private static <A, B, C> Trampoline<List<C>> zipWithTrampolineHelper(
      List<A> list1, List<B> list2, int index, int limit, BiFunction<A, B, C> f, ArrayList<C> acc) {
    if (index >= limit) {
      return Trampoline.done(copyList(acc));
    }
    acc.add(f.apply(list1.get(index), list2.get(index)));
    return Trampoline.defer(() -> zipWithTrampolineHelper(list1, list2, index + 1, limit, f, acc));
  }

  /**
   * Takes the first n elements from a list using trampolines for stack safety.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   * List<Integer> firstThree = ListPrisms.takeTrampoline(numbers, 3);
   * // firstThree = [1, 2, 3]
   * }</pre>
   *
   * @param list The source list.
   * @param n The number of elements to take.
   * @param <A> The element type.
   * @return A new list with at most n elements.
   */
  public static <A> List<A> takeTrampoline(List<A> list, int n) {
    int limit = Math.min(n, list.size());
    return takeTrampolineHelper(list, 0, limit, new ArrayList<>(limit)).run();
  }

  private static <A> Trampoline<List<A>> takeTrampolineHelper(
      List<A> list, int index, int limit, ArrayList<A> acc) {
    if (index >= limit) {
      return Trampoline.done(copyList(acc));
    }
    acc.add(list.get(index));
    return Trampoline.defer(() -> takeTrampolineHelper(list, index + 1, limit, acc));
  }

  /**
   * Drops the first n elements from a list using trampolines for stack safety.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<Integer> numbers = List.of(1, 2, 3, 4, 5);
   * List<Integer> lastTwo = ListPrisms.dropTrampoline(numbers, 3);
   * // lastTwo = [4, 5]
   * }</pre>
   *
   * @param list The source list.
   * @param n The number of elements to drop.
   * @param <A> The element type.
   * @return A new list with the first n elements removed.
   */
  public static <A> List<A> dropTrampoline(List<A> list, int n) {
    if (n >= list.size()) {
      return List.of();
    }
    return dropTrampolineHelper(list, n, new ArrayList<>(list.size() - n)).run();
  }

  private static <A> Trampoline<List<A>> dropTrampolineHelper(
      List<A> list, int index, ArrayList<A> acc) {
    if (index >= list.size()) {
      return Trampoline.done(copyList(acc));
    }
    acc.add(list.get(index));
    return Trampoline.defer(() -> dropTrampolineHelper(list, index + 1, acc));
  }

  /**
   * Creates an unmodifiable copy of the given list that allows null elements.
   *
   * <p>Unlike {@link List#copyOf(java.util.Collection)}, this method permits null elements in the
   * source list. The returned list is unmodifiable.
   *
   * @param <A> the element type
   * @param list the list to copy
   * @return an unmodifiable copy of the list
   */
  private static <A> List<A> copyList(List<A> list) {
    return Collections.unmodifiableList(new ArrayList<>(list));
  }
}
