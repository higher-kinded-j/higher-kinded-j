// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;

/**
 * A **Fold** is a read-only optic for querying and extracting data from a structure. Think of it as
 * a functional "query" or "getter" 🔍 that can focus on zero or more parts within a larger whole.
 *
 * <p>A Fold is the right tool for read-only operations such as:
 *
 * <ul>
 *   <li>Extracting all values of a certain type from a complex structure
 *   <li>Searching for elements matching a predicate
 *   <li>Counting elements
 *   <li>Checking if any/all elements satisfy a condition
 *   <li>Aggregating values using a {@link Monoid}
 * </ul>
 *
 * <p>Unlike {@link Lens} or {@link Traversal}, a Fold is strictly read-only and cannot modify the
 * structure. However, any {@link Lens}, {@link Prism}, {@link Iso}, or {@link Traversal} can be
 * viewed as a Fold using their respective {@code asFold()} methods.
 *
 * <p>It extends the generic {@link Optic}, specializing it for {@code S = T} and {@code A = B}.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * record Order(List<Item> items) {}
 * record Item(String name, int price) {}
 *
 * // Create a Fold that focuses on all items in an order
 * Fold<Order, Item> itemsFold = ...;
 *
 * Order order = new Order(List.of(
 *   new Item("Apple", 100),
 *   new Item("Banana", 50),
 *   new Item("Cherry", 150)
 * ));
 *
 * // Extract all items
 * List<Item> allItems = itemsFold.getAll(order);
 *
 * // Find expensive items
 * Optional<Item> expensive = itemsFold.find(item -> item.price() > 100, order);
 *
 * // Count items
 * int count = itemsFold.length(order);
 *
 * // Check if any item is expensive
 * boolean hasExpensive = itemsFold.exists(item -> item.price() > 100, order);
 * }</pre>
 *
 * @param <S> The type of the whole structure (e.g., {@code Order}).
 * @param <A> The type of the focused parts (e.g., {@code Item}).
 */
public interface Fold<S, A> extends Optic<S, S, A, A> {

  /**
   * Folds all focused parts into a summary value using a {@link Monoid}.
   *
   * <p>This is the fundamental operation of a Fold. It maps each focused part {@code A} to a
   * monoidal value {@code M} using the function {@code f}, then combines all these values using the
   * monoid's {@code combine} operation.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Sum all prices
   * Monoid<Integer> sumMonoid = Monoid.of(0, Integer::sum);
   * int totalPrice = itemsFold.foldMap(sumMonoid, Item::price, order);
   * }</pre>
   *
   * @param monoid The {@link Monoid} used to combine the mapped values.
   * @param f The function to map each focused part {@code A} to the monoidal type {@code M}.
   * @param source The source structure.
   * @param <M> The monoidal type.
   * @return The aggregated result of type {@code M}.
   */
  <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source);

  /**
   * {@inheritDoc}
   *
   * <p>For a {@code Fold}, the {@code modifyF} operation is read-only: it extracts values using the
   * {@code Const} applicative but does not actually modify the structure. The returned structure is
   * always identical to the input.
   *
   * <p>This method exists to satisfy the {@link Optic} interface and enable composition with other
   * optics. However, for pure read operations, prefer using {@link #foldMap}, {@link #getAll}, or
   * other query methods.
   */
  @Override
  default <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
    // For Fold, modifyF must traverse and apply effects from f, even though
    // we don't use the results to modify the structure (it's read-only).
    // We combine effects using a Monoid that sequences them via the Applicative.
    Monoid<Kind<F, Void>> effectMonoid =
        new Monoid<>() {
          @Override
          public Kind<F, Void> empty() {
            return app.of(null);
          }

          @Override
          public Kind<F, Void> combine(Kind<F, Void> a, Kind<F, Void> b) {
            return app.map2(a, b, (v1, v2) -> null);
          }
        };

    Kind<F, Void> effects = foldMap(effectMonoid, a -> app.map(ignored -> null, f.apply(a)), s);

    return app.map(ignored -> s, effects);
  }

  /**
   * Extracts all focused parts from the source structure into a {@link List}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<Item> allItems = itemsFold.getAll(order);
   * }</pre>
   *
   * @param source The source structure.
   * @return A {@code List} containing all focused parts, in the order they were encountered.
   */
  default List<A> getAll(S source) {
    // Use mutable accumulator for O(k) performance instead of O(k²) from list copying
    final List<A> result = new ArrayList<>();
    Monoid<Void> accumulatorMonoid =
        new Monoid<>() {
          @Override
          public Void empty() {
            return null;
          }

          @Override
          public Void combine(Void a, Void b) {
            return null;
          }
        };

    foldMap(
        accumulatorMonoid,
        a -> {
          result.add(a);
          return null;
        },
        source);
    return result;
  }

  /**
   * Returns the first focused part, if any.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Optional<Item> firstItem = itemsFold.preview(order);
   * }</pre>
   *
   * @param source The source structure.
   * @return An {@link Optional} containing the first focused part, or {@code Optional.empty()} if
   *     there are no focuses.
   */
  default Optional<A> preview(S source) {
    return foldMap(firstOptionalMonoid(), Optional::of, source);
  }

  /**
   * Finds the first focused part that matches the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Optional<Item> expensive = itemsFold.find(item -> item.price() > 100, order);
   * }</pre>
   *
   * @param predicate The predicate to test each focused part.
   * @param source The source structure.
   * @return An {@link Optional} containing the first matching part, or {@code Optional.empty()} if
   *     no part matches.
   */
  default Optional<A> find(Predicate<? super A> predicate, S source) {
    return foldMap(
        firstOptionalMonoid(), a -> predicate.test(a) ? Optional.of(a) : Optional.empty(), source);
  }

  /**
   * Checks if there are no focused parts in the structure.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean noItems = itemsFold.isEmpty(order);
   * }</pre>
   *
   * @param source The source structure.
   * @return {@code true} if there are no focused parts, {@code false} otherwise.
   */
  default boolean isEmpty(S source) {
    return length(source) == 0;
  }

  /**
   * Counts the number of focused parts in the structure.
   *
   * <p>Example:
   *
   * <pre>{@code
   * int itemCount = itemsFold.length(order);
   * }</pre>
   *
   * @param source The source structure.
   * @return The number of focused parts.
   */
  default int length(S source) {
    return foldMap(sumIntMonoid(), a -> 1, source);
  }

  /**
   * Checks if any focused part matches the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean hasExpensive = itemsFold.exists(item -> item.price() > 100, order);
   * }</pre>
   *
   * @param predicate The predicate to test each focused part.
   * @param source The source structure.
   * @return {@code true} if at least one focused part matches, {@code false} otherwise.
   */
  default boolean exists(Predicate<? super A> predicate, S source) {
    return foldMap(anyBooleanMonoid(), predicate::test, source);
  }

  /**
   * Checks if all focused parts match the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * boolean allAffordable = itemsFold.all(item -> item.price() <= 100, order);
   * }</pre>
   *
   * @param predicate The predicate to test each focused part.
   * @param source The source structure.
   * @return {@code true} if all focused parts match (or if there are no focused parts), {@code
   *     false} otherwise.
   */
  default boolean all(Predicate<? super A> predicate, S source) {
    return foldMap(allBooleanMonoid(), predicate::test, source);
  }

  /**
   * Composes this {@code Fold<S, A>} with another {@code Fold<A, B>} to create a new {@code Fold<S,
   * B>}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Fold<Order, Item> itemsFold = ...;
   * Fold<Item, String> nameFold = ...;
   * Fold<Order, String> namesFold = itemsFold.andThen(nameFold);
   *
   * List<String> allNames = namesFold.getAll(order);
   * }</pre>
   *
   * @param other The {@link Fold} to compose with.
   * @param <B> The type of the final focused parts.
   * @return A new, composed {@link Fold}.
   */
  default <B> Fold<S, B> andThen(final Fold<A, B> other) {
    Fold<S, A> self = this;
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super B, ? extends M> f, S source) {
        return self.foldMap(monoid, a -> other.foldMap(monoid, f, a), source);
      }
    };
  }

  /**
   * Creates a new {@code Fold} that only focuses on elements matching the given predicate.
   *
   * <p>This is a composable filtering combinator for read-only queries. Elements that don't match
   * the predicate are excluded from all fold operations (getAll, foldMap, exists, etc.).
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Fold from Order to all Items
   * Fold<Order, Item> itemsFold = Fold.of(Order::items);
   *
   * // Filter to expensive items only
   * Fold<Order, Item> expensiveItems = itemsFold.filtered(item -> item.price() > 100);
   *
   * // Usage:
   * int count = expensiveItems.length(order);
   * // Returns count of expensive items only
   *
   * List<Item> expensive = expensiveItems.getAll(order);
   * // Returns only items with price > 100
   *
   * int totalExpensive = expensiveItems.foldMap(sumMonoid, Item::price, order);
   * // Sum of only expensive items
   * }</pre>
   *
   * @param predicate The predicate to filter elements by
   * @return A new {@code Fold} that only focuses on matching elements
   */
  default Fold<S, A> filtered(Predicate<? super A> predicate) {
    Fold<S, A> self = this;
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        return self.foldMap(monoid, a -> predicate.test(a) ? f.apply(a) : monoid.empty(), source);
      }
    };
  }

  /**
   * Creates a new {@code Fold} that only focuses on elements where a nested query satisfies the
   * given predicate.
   *
   * <p>This advanced filtering combinator allows filtering based on properties accessed through
   * another optic (Fold), enabling queries like "all items from orders where the order total
   * exceeds $500".
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Fold from Customer to all Items across all orders
   * Fold<Customer, Item> customerItems = customerOrdersFold.andThen(orderItemsFold);
   *
   * // Fold from Item to its category tags
   * Fold<Item, String> itemTags = Fold.of(Item::tags);
   *
   * // Filter to items that have any "premium" tag
   * Fold<Customer, Item> premiumItems =
   *     customerItems.filterBy(itemTags, tag -> tag.equals("premium"));
   *
   * // Get all premium items
   * List<Item> premium = premiumItems.getAll(customer);
   * }</pre>
   *
   * @param query The {@link Fold} to query each focused element
   * @param predicate The predicate to test the queried values
   * @param <B> The type of values queried by the Fold
   * @return A new {@code Fold} that only focuses on elements where the query matches
   */
  default <B> Fold<S, A> filterBy(Fold<A, B> query, Predicate<? super B> predicate) {
    Fold<S, A> self = this;
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        return self.foldMap(
            monoid, a -> query.exists(predicate, a) ? f.apply(a) : monoid.empty(), source);
      }
    };
  }

  /**
   * Creates a {@code Fold} from a function that extracts a list of focused parts.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Fold<Order, Item> itemsFold = Fold.of(Order::items);
   * }</pre>
   *
   * @param getAll A function that extracts all focused parts from the structure.
   * @param <S> The type of the whole structure.
   * @param <A> The type of the focused parts.
   * @return A new {@code Fold} instance.
   */
  static <S, A> Fold<S, A> of(Function<S, List<A>> getAll) {
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        M result = monoid.empty();
        for (A a : getAll.apply(source)) {
          result = monoid.combine(result, f.apply(a));
        }
        return result;
      }
    };
  }

  // Private helper monoids to reduce code duplication

  /**
   * Returns a monoid that keeps the first non-empty Optional.
   *
   * @param <T> The type contained in the Optional.
   * @return A monoid for finding the first element.
   */
  private static <T> Monoid<Optional<T>> firstOptionalMonoid() {
    return new Monoid<>() {
      @Override
      public Optional<T> empty() {
        return Optional.empty();
      }

      @Override
      public Optional<T> combine(Optional<T> a, Optional<T> b) {
        return a.isPresent() ? a : b;
      }
    };
  }

  /**
   * Returns a monoid for summing integers.
   *
   * @return A monoid for integer addition.
   */
  private static Monoid<Integer> sumIntMonoid() {
    return new Monoid<>() {
      @Override
      public Integer empty() {
        return 0;
      }

      @Override
      public Integer combine(Integer a, Integer b) {
        return a + b;
      }
    };
  }

  /**
   * Returns a monoid for boolean OR (disjunction).
   *
   * @return A monoid that returns true if any value is true.
   */
  private static Monoid<Boolean> anyBooleanMonoid() {
    return new Monoid<>() {
      @Override
      public Boolean empty() {
        return false;
      }

      @Override
      public Boolean combine(Boolean a, Boolean b) {
        return a || b;
      }
    };
  }

  /**
   * Returns a monoid for boolean AND (conjunction).
   *
   * @return A monoid that returns true if all values are true.
   */
  private static Monoid<Boolean> allBooleanMonoid() {
    return new Monoid<>() {
      @Override
      public Boolean empty() {
        return true;
      }

      @Override
      public Boolean combine(Boolean a, Boolean b) {
        return a && b;
      }
    };
  }
}
