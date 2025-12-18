// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * A fluent path wrapper for {@link List} with standard list monad semantics.
 *
 * <p>{@code ListPath} wraps a list and provides fluent composition with list monad behavior. The
 * {@code via} operation performs flatMap, concatenating all results. The {@code zipWith} operation
 * pairs elements positionally (shortest list wins).
 *
 * <h2>Comparison with NonDetPath</h2>
 *
 * <ul>
 *   <li>{@code ListPath.zipWith} - pairs elements positionally: [1,2] zip [a,b] = [(1,a), (2,b)]
 *   <li>{@code NonDetPath.zipWith} - Cartesian product: [1,2] zip [a,b] = [(1,a), (1,b), (2,a),
 *       (2,b)]
 * </ul>
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Batch processing with parallel structure
 *   <li>Mapping over collections with fluent API
 *   <li>Zipping corresponding elements from multiple lists
 *   <li>Sequential transformations on collections
 * </ul>
 *
 * <h2>Creating ListPath instances</h2>
 *
 * <pre>{@code
 * // From a list
 * ListPath<Integer> numbers = ListPath.of(List.of(1, 2, 3));
 *
 * // From varargs
 * ListPath<String> letters = ListPath.of("a", "b", "c");
 *
 * // Single value
 * ListPath<Integer> single = ListPath.pure(42);
 *
 * // Empty
 * ListPath<Integer> empty = ListPath.empty();
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * // Zip two lists positionally
 * ListPath<Integer> nums = ListPath.of(1, 2, 3);
 * ListPath<String> strs = ListPath.of("a", "b", "c");
 *
 * ListPath<String> zipped = nums.zipWith(strs, (n, s) -> n + s);
 * // Result: ["1a", "2b", "3c"]
 *
 * // FlatMap (concatenate results)
 * ListPath<Integer> result = nums.via(n -> ListPath.of(n, n * 10));
 * // Result: [1, 10, 2, 20, 3, 30]
 * }</pre>
 *
 * @param <A> the element type
 */
public final class ListPath<A> implements Chainable<A> {

  private final List<A> list;

  /**
   * Creates a new ListPath wrapping the given list.
   *
   * @param list the list to wrap; must not be null
   */
  ListPath(List<A> list) {
    this.list = List.copyOf(Objects.requireNonNull(list, "list must not be null"));
  }

  // ===== Factory Methods =====

  /**
   * Creates a ListPath from a list.
   *
   * @param list the list to wrap; must not be null
   * @param <A> the element type
   * @return a ListPath wrapping the list
   * @throws NullPointerException if list is null
   */
  public static <A> ListPath<A> of(List<A> list) {
    return new ListPath<>(list);
  }

  /**
   * Creates a ListPath from varargs.
   *
   * @param elements the elements
   * @param <A> the element type
   * @return a ListPath containing the elements
   */
  @SafeVarargs
  public static <A> ListPath<A> of(A... elements) {
    return new ListPath<>(Arrays.asList(elements));
  }

  /**
   * Creates a ListPath with a single element.
   *
   * @param value the single element
   * @param <A> the element type
   * @return a ListPath containing one element
   */
  public static <A> ListPath<A> pure(A value) {
    return new ListPath<>(List.of(value));
  }

  /**
   * Creates an empty ListPath.
   *
   * @param <A> the element type
   * @return an empty ListPath
   */
  public static <A> ListPath<A> empty() {
    return new ListPath<>(List.of());
  }

  /**
   * Creates a ListPath from a range of integers.
   *
   * @param startInclusive the start value (inclusive)
   * @param endExclusive the end value (exclusive)
   * @return a ListPath containing integers in the range
   */
  public static ListPath<Integer> range(int startInclusive, int endExclusive) {
    List<Integer> elements = new ArrayList<>();
    for (int i = startInclusive; i < endExclusive; i++) {
      elements.add(i);
    }
    return new ListPath<>(elements);
  }

  // ===== Terminal Operations =====

  /**
   * Returns the underlying list.
   *
   * @return the wrapped list (immutable)
   */
  public List<A> run() {
    return list;
  }

  /**
   * Returns the first element, or empty if the list is empty.
   *
   * @return an Optional containing the first element if present
   */
  public Optional<A> headOption() {
    return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
  }

  /**
   * Returns the last element, or empty if the list is empty.
   *
   * @return an Optional containing the last element if present
   */
  public Optional<A> lastOption() {
    return list.isEmpty() ? Optional.empty() : Optional.of(list.getLast());
  }

  /**
   * Returns whether this list is empty.
   *
   * @return true if empty
   */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /**
   * Returns the size of this list.
   *
   * @return the number of elements
   */
  public int size() {
    return list.size();
  }

  /**
   * Returns whether any element matches the predicate.
   *
   * @param predicate the condition to test; must not be null
   * @return true if any element matches
   * @throws NullPointerException if predicate is null
   */
  public boolean anyMatch(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return list.stream().anyMatch(predicate);
  }

  /**
   * Returns whether all elements match the predicate.
   *
   * @param predicate the condition to test; must not be null
   * @return true if all elements match (or list is empty)
   * @throws NullPointerException if predicate is null
   */
  public boolean allMatch(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return list.stream().allMatch(predicate);
  }

  // ===== Composable implementation =====

  @Override
  public <B> ListPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    List<B> mapped = list.stream().map(mapper).collect(Collectors.toList());
    return new ListPath<>(mapped);
  }

  @Override
  public ListPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    list.forEach(consumer);
    return this;
  }

  // ===== Combinable implementation =====

  /**
   * Zips this list with another, pairing elements positionally.
   *
   * <p>The resulting list has length equal to the shorter input list.
   *
   * @param other the other Combinable; must be a ListPath
   * @param combiner the function to combine paired elements; must not be null
   * @param <B> the type of the other list's elements
   * @param <C> the type of the combined result
   * @return a new ListPath with paired and combined elements
   * @throws NullPointerException if other or combiner is null
   * @throws IllegalArgumentException if other is not a ListPath
   */
  @Override
  public <B, C> ListPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof ListPath<?> otherList)) {
      throw new IllegalArgumentException("Cannot zipWith non-ListPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    ListPath<B> typedOther = (ListPath<B>) otherList;

    // Positional zip - pair corresponding elements
    int minSize = Math.min(this.list.size(), typedOther.list.size());
    List<C> combined = new ArrayList<>(minSize);
    for (int i = 0; i < minSize; i++) {
      combined.add(combiner.apply(this.list.get(i), typedOther.list.get(i)));
    }
    return new ListPath<>(combined);
  }

  /**
   * Zips this list with two others, combining corresponding elements.
   *
   * <p>The resulting list has length equal to the shortest input list.
   *
   * @param second the second list; must not be null
   * @param third the third list; must not be null
   * @param combiner the function to combine elements; must not be null
   * @param <B> the type of the second list's elements
   * @param <C> the type of the third list's elements
   * @param <D> the type of the combined result
   * @return a new ListPath with combined elements
   */
  public <B, C, D> ListPath<D> zipWith3(
      ListPath<B> second,
      ListPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    int minSize = Math.min(Math.min(this.list.size(), second.list.size()), third.list.size());
    List<D> combined = new ArrayList<>(minSize);
    for (int i = 0; i < minSize; i++) {
      combined.add(combiner.apply(this.list.get(i), second.list.get(i), third.list.get(i)));
    }
    return new ListPath<>(combined);
  }

  // ===== Chainable implementation =====

  @Override
  public <B> ListPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    List<B> flatMapped =
        list.stream()
            .flatMap(
                a -> {
                  Chainable<B> result = mapper.apply(a);
                  Objects.requireNonNull(result, "mapper must not return null");

                  if (!(result instanceof ListPath<?> listPath)) {
                    throw new IllegalArgumentException(
                        "via mapper must return ListPath, got: " + result.getClass());
                  }

                  @SuppressWarnings("unchecked")
                  ListPath<B> typedResult = (ListPath<B>) listPath;
                  return typedResult.list.stream();
                })
            .collect(Collectors.toList());

    return new ListPath<>(flatMapped);
  }

  @Override
  public <B> ListPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(ignored -> supplier.get());
  }

  // ===== List-Specific Operations =====

  /**
   * Filters elements based on a predicate.
   *
   * @param predicate the condition to test; must not be null
   * @return a new ListPath with only matching elements
   * @throws NullPointerException if predicate is null
   */
  public ListPath<A> filter(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    List<A> filtered = list.stream().filter(predicate).collect(Collectors.toList());
    return new ListPath<>(filtered);
  }

  /**
   * Takes the first n elements.
   *
   * @param n the number of elements to take
   * @return a new ListPath with at most n elements
   */
  public ListPath<A> take(int n) {
    return new ListPath<>(list.stream().limit(n).collect(Collectors.toList()));
  }

  /**
   * Drops the first n elements.
   *
   * @param n the number of elements to skip
   * @return a new ListPath without the first n elements
   */
  public ListPath<A> drop(int n) {
    return new ListPath<>(list.stream().skip(n).collect(Collectors.toList()));
  }

  /**
   * Takes elements while predicate is true.
   *
   * @param predicate the condition; must not be null
   * @return a new ListPath with elements taken while predicate holds
   * @throws NullPointerException if predicate is null
   */
  public ListPath<A> takeWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new ListPath<>(list.stream().takeWhile(predicate).collect(Collectors.toList()));
  }

  /**
   * Drops elements while predicate is true.
   *
   * @param predicate the condition; must not be null
   * @return a new ListPath with elements after predicate stops holding
   * @throws NullPointerException if predicate is null
   */
  public ListPath<A> dropWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new ListPath<>(list.stream().dropWhile(predicate).collect(Collectors.toList()));
  }

  /**
   * Returns distinct elements.
   *
   * @return a new ListPath with duplicates removed
   */
  public ListPath<A> distinct() {
    return new ListPath<>(list.stream().distinct().collect(Collectors.toList()));
  }

  /**
   * Concatenates with another ListPath.
   *
   * @param other the other ListPath; must not be null
   * @return a new ListPath containing elements from both
   * @throws NullPointerException if other is null
   */
  public ListPath<A> concat(ListPath<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    List<A> combined = new ArrayList<>(list);
    combined.addAll(other.list);
    return new ListPath<>(combined);
  }

  /**
   * Folds the list from the left.
   *
   * @param initial the initial accumulator value
   * @param f the folding function; must not be null
   * @param <B> the accumulator and result type
   * @return the folded result
   * @throws NullPointerException if f is null
   */
  public <B> B foldLeft(B initial, BiFunction<B, A, B> f) {
    Objects.requireNonNull(f, "f must not be null");
    B result = initial;
    for (A a : list) {
      result = f.apply(result, a);
    }
    return result;
  }

  /**
   * Folds the list from the right.
   *
   * @param initial the initial accumulator value
   * @param f the folding function; must not be null
   * @param <B> the accumulator and result type
   * @return the folded result
   * @throws NullPointerException if f is null
   */
  public <B> B foldRight(B initial, BiFunction<A, B, B> f) {
    Objects.requireNonNull(f, "f must not be null");
    B result = initial;
    for (int i = list.size() - 1; i >= 0; i--) {
      result = f.apply(list.get(i), result);
    }
    return result;
  }

  /**
   * Reverses the order of elements.
   *
   * @return a new ListPath with reversed elements
   */
  public ListPath<A> reverse() {
    List<A> reversed = new ArrayList<>(list);
    Collections.reverse(reversed);
    return new ListPath<>(reversed);
  }

  /**
   * Returns the element at the given index.
   *
   * @param index the index
   * @return an Optional containing the element if index is valid
   */
  public Optional<A> get(int index) {
    if (index < 0 || index >= list.size()) {
      return Optional.empty();
    }
    return Optional.of(list.get(index));
  }

  // ===== Conversions =====

  /**
   * Converts to MaybePath with the first element.
   *
   * @return a MaybePath containing the first element if present
   */
  public MaybePath<A> toMaybePath() {
    return headOption()
        .map(a -> new MaybePath<>(Maybe.just(a)))
        .orElse(new MaybePath<>(Maybe.nothing()));
  }

  /**
   * Converts to an IOPath that returns this list.
   *
   * @return an IOPath that produces this list
   */
  public IOPath<List<A>> toIOPath() {
    return new IOPath<>(() -> list);
  }

  /**
   * Converts to StreamPath.
   *
   * @return a StreamPath containing the same elements
   */
  public StreamPath<A> toStreamPath() {
    return StreamPath.fromList(list);
  }

  /**
   * Converts to NonDetPath (for Cartesian product semantics).
   *
   * @return a NonDetPath containing the same elements
   */
  public NonDetPath<A> toNonDetPath() {
    return NonDetPath.of(list);
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ListPath<?> other)) return false;
    return list.equals(other.list);
  }

  @Override
  public int hashCode() {
    return list.hashCode();
  }

  @Override
  public String toString() {
    return "ListPath(" + list + ")";
  }
}
