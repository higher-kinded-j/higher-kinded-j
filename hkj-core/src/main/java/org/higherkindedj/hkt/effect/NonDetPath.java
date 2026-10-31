// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * A fluent path wrapper for {@link List} representing non-deterministic computations.
 *
 * <p>{@code NonDetPath} treats lists as computations that can produce multiple results. The {@code
 * via} operation performs flatMap, combining all possible results from each element.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Non-deterministic algorithms
 *   <li>Search problems with multiple solutions
 *   <li>Generating combinations/permutations
 *   <li>Parsing with ambiguous grammars
 * </ul>
 *
 * <h2>Creating NonDetPath instances</h2>
 *
 * <pre>{@code
 * // From a list
 * NonDetPath<Integer> numbers = NonDetPath.of(List.of(1, 2, 3));
 *
 * // From varargs
 * NonDetPath<String> letters = NonDetPath.of("a", "b", "c");
 *
 * // Single value
 * NonDetPath<Integer> single = NonDetPath.pure(42);
 *
 * // Empty
 * NonDetPath<Integer> empty = NonDetPath.empty();
 *
 * // Range
 * NonDetPath<Integer> range = NonDetPath.range(1, 10);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * // Generate all pairs (cartesian product)
 * NonDetPath<Integer> numbers = NonDetPath.of(1, 2, 3);
 * NonDetPath<String> letters = NonDetPath.of("a", "b");
 *
 * NonDetPath<String> pairs = numbers.via(n ->
 *     letters.map(l -> n + l));
 *
 * List<String> result = pairs.run();
 * // ["1a", "1b", "2a", "2b", "3a", "3b"]
 * }</pre>
 *
 * @param <A> the element type
 */
public final class NonDetPath<A> implements Chainable<NonDetPath<?>, A> {

  private final List<A> list;

  /**
   * Creates a new NonDetPath wrapping the given list.
   *
   * @param list the list to wrap; must not be null
   */
  NonDetPath(List<A> list) {
    this.list = List.copyOf(Objects.requireNonNull(list, "list must not be null"));
  }

  // ===== Factory Methods =====

  /**
   * Creates a NonDetPath from a list.
   *
   * @param list the list to wrap; must not be null
   * @param <A> the element type
   * @return a NonDetPath wrapping the list
   * @throws NullPointerException if list is null
   */
  public static <A> NonDetPath<A> of(List<A> list) {
    return new NonDetPath<>(list);
  }

  /**
   * Creates a NonDetPath from varargs.
   *
   * @param elements the elements
   * @param <A> the element type
   * @return a NonDetPath containing the elements
   */
  @SafeVarargs
  public static <A> NonDetPath<A> of(A... elements) {
    return new NonDetPath<>(Arrays.asList(elements));
  }

  /**
   * Creates a NonDetPath with a single element.
   *
   * @param value the single element
   * @param <A> the element type
   * @return a NonDetPath containing one element
   */
  public static <A> NonDetPath<A> pure(A value) {
    return new NonDetPath<>(List.of(value));
  }

  /**
   * Creates an empty NonDetPath.
   *
   * @param <A> the element type
   * @return an empty NonDetPath
   */
  public static <A> NonDetPath<A> empty() {
    return new NonDetPath<>(List.of());
  }

  /**
   * Creates a NonDetPath from a range of integers.
   *
   * @param startInclusive the start value (inclusive)
   * @param endExclusive the end value (exclusive)
   * @return a NonDetPath containing integers in the range
   */
  public static NonDetPath<Integer> range(int startInclusive, int endExclusive) {
    List<Integer> elements = new ArrayList<>();
    for (int i = startInclusive; i < endExclusive; i++) {
      elements.add(i);
    }
    return new NonDetPath<>(elements);
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
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
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
   * Returns the last element, or empty if the path is empty.
   *
   * @return an Optional containing the last element if present
   */
  public Optional<A> lastOption() {
    return list.isEmpty() ? Optional.empty() : Optional.of(list.getLast());
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
   * @return true if all elements match (or the path is empty)
   * @throws NullPointerException if predicate is null
   */
  public boolean allMatch(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return list.stream().allMatch(predicate);
  }

  // ===== Composable implementation =====

  @Override
  public <B> NonDetPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    List<B> mapped = list.stream().map(mapper).collect(Collectors.toList());
    return new NonDetPath<>(mapped);
  }

  @Override
  public NonDetPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    list.forEach(consumer);
    return this;
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> NonDetPath<C> zipWith(
      Combinable<? extends NonDetPath<?>, B> other,
      BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof NonDetPath<?> otherList)) {
      throw new IllegalArgumentException("Cannot zipWith non-NonDetPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    NonDetPath<B> typedOther = (NonDetPath<B>) otherList;

    // Cartesian product - all combinations
    List<C> combined = new ArrayList<>();
    for (A a : this.list) {
      for (B b : typedOther.list) {
        combined.add(combiner.apply(a, b));
      }
    }
    return new NonDetPath<>(combined);
  }

  /**
   * Combines this path with two others using a ternary function.
   *
   * <p>Produces all combinations (cartesian product).
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's elements
   * @param <C> the type of the third path's elements
   * @param <D> the type of the combined result
   * @return a new path containing all combinations
   */
  public <B, C, D> NonDetPath<D> zipWith3(
      NonDetPath<B> second,
      NonDetPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    List<D> combined = new ArrayList<>();
    for (A a : this.list) {
      for (B b : second.list) {
        for (C c : third.list) {
          combined.add(combiner.apply(a, b, c));
        }
      }
    }
    return new NonDetPath<>(combined);
  }

  // ===== Chainable implementation =====

  /**
   * {@inheritDoc}
   *
   * <p>The mapper must return a {@code NonDetPath}; a step of another kind, a {@code ListPath}
   * included, does not compile.
   */
  @Override
  public <B> NonDetPath<B> via(
      Function<? super A, ? extends Chainable<? extends NonDetPath<?>, B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    List<B> flatMapped =
        list.stream()
            .flatMap(
                a -> {
                  Chainable<? extends NonDetPath<?>, B> result = mapper.apply(a);
                  Objects.requireNonNull(result, "mapper must not return null");

                  if (!(result instanceof NonDetPath<?> listPath)) {
                    throw new IllegalArgumentException(
                        "via mapper must return NonDetPath, got: " + result.getClass());
                  }

                  @SuppressWarnings("unchecked")
                  NonDetPath<B> typedResult = (NonDetPath<B>) listPath;
                  return typedResult.list.stream();
                })
            .collect(Collectors.toList());

    return new NonDetPath<>(flatMapped);
  }

  @Override
  public <B> NonDetPath<B> then(
      Supplier<? extends Chainable<? extends NonDetPath<?>, B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(ignored -> supplier.get());
  }

  // ===== List-Specific Operations =====

  /**
   * Filters elements based on a predicate.
   *
   * @param predicate the condition to test; must not be null
   * @return a new NonDetPath with only matching elements
   * @throws NullPointerException if predicate is null
   */
  public NonDetPath<A> filter(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    List<A> filtered = list.stream().filter(predicate).collect(Collectors.toList());
    return new NonDetPath<>(filtered);
  }

  /**
   * Takes the first n elements.
   *
   * @param n the number of elements to take
   * @return a new NonDetPath with at most n elements
   */
  public NonDetPath<A> take(int n) {
    return new NonDetPath<>(list.stream().limit(n).collect(Collectors.toList()));
  }

  /**
   * Drops the first n elements.
   *
   * @param n the number of elements to skip
   * @return a new NonDetPath without the first n elements
   */
  public NonDetPath<A> drop(int n) {
    return new NonDetPath<>(list.stream().skip(n).collect(Collectors.toList()));
  }

  /**
   * Takes elements while predicate is true.
   *
   * @param predicate the condition; must not be null
   * @return a new NonDetPath with elements taken while predicate holds
   * @throws NullPointerException if predicate is null
   */
  public NonDetPath<A> takeWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new NonDetPath<>(list.stream().takeWhile(predicate).collect(Collectors.toList()));
  }

  /**
   * Drops elements while predicate is true.
   *
   * @param predicate the condition; must not be null
   * @return a new NonDetPath with elements after predicate stops holding
   * @throws NullPointerException if predicate is null
   */
  public NonDetPath<A> dropWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new NonDetPath<>(list.stream().dropWhile(predicate).collect(Collectors.toList()));
  }

  /**
   * Returns distinct elements.
   *
   * @return a new NonDetPath with duplicates removed
   */
  public NonDetPath<A> distinct() {
    return new NonDetPath<>(list.stream().distinct().collect(Collectors.toList()));
  }

  /**
   * Concatenates with another NonDetPath.
   *
   * @param other the other NonDetPath; must not be null
   * @return a new NonDetPath containing elements from both
   * @throws NullPointerException if other is null
   */
  public NonDetPath<A> concat(NonDetPath<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    List<A> combined = new ArrayList<>(list);
    combined.addAll(other.list);
    return new NonDetPath<>(combined);
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
   * Reduces the list to a single value of the same type using an identity element and an
   * associative combining operator.
   *
   * <p>This is the monoid-style fold. It mirrors {@code ListPath.fold} and {@code
   * VStreamPath.fold}, so the same reduction reads identically across sequence-like paths.
   *
   * <pre>{@code
   * int sum = NonDetPath.of(1, 2, 3).map(x -> x * 2).fold(0, Integer::sum); // 12
   * }</pre>
   *
   * @param identity the identity element returned for an empty list
   * @param op the associative combining operator; must not be null
   * @return the reduced value
   * @throws NullPointerException if op is null
   */
  public A fold(A identity, BinaryOperator<A> op) {
    Objects.requireNonNull(op, "op must not be null");
    A result = identity;
    for (A a : list) {
      result = op.apply(result, a);
    }
    return result;
  }

  /**
   * Maps each element to a monoidal value and combines them using the supplied {@link Monoid}.
   *
   * <p>Returns {@code monoid.empty()} for an empty list. This is the {@link
   * org.higherkindedj.hkt.Foldable Foldable}-style fold and matches {@code ListPath.foldMap}.
   *
   * <pre>{@code
   * String csv = NonDetPath.of(1, 2, 3).foldMap(Monoids.string(), i -> i + ",");
   * }</pre>
   *
   * @param monoid the monoid used to combine mapped values; must not be null
   * @param f the mapping function; must not be null
   * @param <M> the monoidal result type
   * @return the combined result
   * @throws NullPointerException if monoid or f is null
   */
  public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f) {
    Objects.requireNonNull(monoid, "monoid must not be null");
    Objects.requireNonNull(f, "f must not be null");
    M result = monoid.empty();
    for (A a : list) {
      result = monoid.combine(result, f.apply(a));
    }
    return result;
  }

  /**
   * Reverses the order of elements.
   *
   * @return a new NonDetPath with reversed elements
   */
  public NonDetPath<A> reverse() {
    List<A> reversed = new ArrayList<>(list);
    Collections.reverse(reversed);
    return new NonDetPath<>(reversed);
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

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof NonDetPath<?> other)) return false;
    return list.equals(other.list);
  }

  @Override
  public int hashCode() {
    return list.hashCode();
  }

  @Override
  public String toString() {
    return "NonDetPath(" + PathToString.elements(list) + ")";
  }
}
