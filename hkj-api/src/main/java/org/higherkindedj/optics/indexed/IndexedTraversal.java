// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * An indexed traversal that focuses on zero or more elements within a structure, providing access
 * to both the index and value for each focused element.
 *
 * <p>An {@code IndexedTraversal} is the indexed equivalent of a {@link Traversal}. While a regular
 * traversal allows you to modify multiple elements in a structure, an indexed traversal also
 * provides the index/position of each element, enabling position-aware transformations.
 *
 * <p>Common use cases include:
 *
 * <ul>
 *   <li>Numbering items: {@code (i, item) -> item + " #" + i}
 *   <li>Position-based filtering: {@code filterIndex(i -> i % 2 == 0)} for even positions
 *   <li>Weighted modifications: {@code (i, value) -> value * weights[i]}
 *   <li>Debugging: Tracking which elements are being modified
 * </ul>
 *
 * <p>Like regular traversals, indexed traversals compose naturally with other optics, allowing you
 * to build complex indexed access patterns.
 *
 * @param <I> The index type (e.g., Integer for lists, K for Map&lt;K, V&gt;)
 * @param <S> The source/target structure type
 * @param <A> The focused element type
 */
@NullMarked
public interface IndexedTraversal<I, S, A> extends IndexedOptic<I, S, A> {

  /**
   * {@inheritDoc}
   *
   * <p>This is the core operation of an {@code IndexedTraversal}. It modifies all focused parts
   * {@code A} within a structure {@code S} by applying a function that receives both the index and
   * value, returning a value in an {@link Applicative} context {@code F}.
   *
   * @param <F> The witness type for the {@link Applicative} context
   * @param f The effectful function to apply to each focused part, receiving both index and value
   * @param source The whole structure to operate on
   * @param app The {@link Applicative} instance for the context {@code F}
   * @return The updated structure {@code S}, itself wrapped in the context {@code F}
   */
  @Override
  <F> Kind<F, S> imodifyF(BiFunction<I, A, Kind<F, A>> f, S source, Applicative<F> app);

  /**
   * Composes this {@code IndexedTraversal<I, S, A>} with another {@code IndexedTraversal<J, A, B>}
   * to create a new {@code IndexedTraversal<Pair<I, J>, S, B>} with paired indices.
   *
   * <p>This specialized overload ensures the result is correctly typed as an {@code
   * IndexedTraversal} with paired indices.
   *
   * @param other The {@link IndexedTraversal} to compose with
   * @param <J> The index type of the other traversal
   * @param <B> The focus type of the other traversal
   * @return A new {@link IndexedTraversal} with paired indices
   */
  default <J, B> IndexedTraversal<Pair<I, J>, S, B> iandThen(IndexedTraversal<J, A, B> other) {
    IndexedTraversal<I, S, A> self = this;
    return new IndexedTraversal<>() {
      @Override
      public <F> Kind<F, S> imodifyF(
          BiFunction<Pair<I, J>, B, Kind<F, B>> f, S source, Applicative<F> app) {
        return self.imodifyF(
            (i, a) -> other.imodifyF((j, b) -> f.apply(new Pair<>(i, j), b), a, app), source, app);
      }
    };
  }

  /**
   * Composes this {@code IndexedTraversal<I, S, A>} with a regular {@code Traversal<A, B>} to
   * create a new {@code IndexedTraversal<I, S, B>} that preserves the outer index.
   *
   * <p>This is useful when you want to focus deeper into a structure but only care about the index
   * from the outer level. For example, focusing on user emails while tracking user positions.
   *
   * @param other The {@link Traversal} to compose with
   * @param <B> The type of the final focused parts
   * @return A new {@link IndexedTraversal} preserving the outer index
   */
  default <B> IndexedTraversal<I, S, B> andThen(Traversal<A, B> other) {
    IndexedTraversal<I, S, A> self = this;
    return new IndexedTraversal<>() {
      @Override
      public <F> Kind<F, S> imodifyF(BiFunction<I, B, Kind<F, B>> f, S source, Applicative<F> app) {
        return self.imodifyF((i, a) -> other.modifyF(b -> f.apply(i, b), a, app), source, app);
      }
    };
  }

  /**
   * Filters elements based on their index, creating a new indexed traversal that only focuses on
   * elements whose indices match the predicate.
   *
   * <p>This is particularly useful for position-based filtering:
   *
   * <pre>{@code
   * IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
   *
   * // Focus on even positions only
   * IndexedTraversal<Integer, List<String>, String> evens =
   *     ilist.filterIndex(i -> i % 2 == 0);
   *
   * // Focus on first 3 elements
   * IndexedTraversal<Integer, List<String>, String> firstThree =
   *     ilist.filterIndex(i -> i < 3);
   *
   * // Focus on specific indices
   * Set<Integer> indices = Set.of(0, 2, 5);
   * IndexedTraversal<Integer, List<String>, String> specific =
   *     ilist.filterIndex(indices::contains);
   * }</pre>
   *
   * @param predicate Predicate on the index
   * @return A new indexed traversal that only focuses on matching indices
   */
  default IndexedTraversal<I, S, A> filterIndex(Predicate<? super I> predicate) {
    IndexedTraversal<I, S, A> self = this;
    return new IndexedTraversal<>() {
      @Override
      public <F> Kind<F, S> imodifyF(BiFunction<I, A, Kind<F, A>> f, S source, Applicative<F> app) {
        return self.imodifyF((i, a) -> predicate.test(i) ? f.apply(i, a) : app.of(a), source, app);
      }
    };
  }

  /**
   * Filters elements based on their value, creating a new indexed traversal that only focuses on
   * elements matching the predicate while preserving their indices.
   *
   * <p>This is similar to {@link Traversal#filtered(Predicate)} but maintains the index
   * information.
   *
   * <pre>{@code
   * IndexedTraversal<Integer, List<User>, User> iusers = IndexedTraversals.forList();
   *
   * // Focus on active users only, preserving their list positions
   * IndexedTraversal<Integer, List<User>, User> activeUsers =
   *     iusers.filtered(User::isActive);
   *
   * // Modify active users with position awareness
   * imodify(activeUsers, (index, user) -> user.withLabel("Active #" + index), users);
   * }</pre>
   *
   * @param predicate Predicate on the focused value
   * @return A new indexed traversal that only focuses on matching values
   */
  default IndexedTraversal<I, S, A> filtered(Predicate<? super A> predicate) {
    IndexedTraversal<I, S, A> self = this;
    return new IndexedTraversal<>() {
      @Override
      public <F> Kind<F, S> imodifyF(BiFunction<I, A, Kind<F, A>> f, S source, Applicative<F> app) {
        return self.imodifyF((i, a) -> predicate.test(a) ? f.apply(i, a) : app.of(a), source, app);
      }
    };
  }

  /**
   * Filters elements based on both index and value, providing maximum flexibility in element
   * selection.
   *
   * <pre>{@code
   * IndexedTraversal<Integer, List<Item>, Item> items = IndexedTraversals.forList();
   *
   * // Focus on items that are both in first half and marked as important
   * IndexedTraversal<Integer, List<Item>, Item> filtered =
   *     items.filteredWithIndex((index, item) -> index < items.size() / 2 && item.isImportant());
   * }</pre>
   *
   * @param predicate Predicate that takes both index and value
   * @return A new indexed traversal that only focuses on matching elements
   */
  default IndexedTraversal<I, S, A> filteredWithIndex(
      BiFunction<? super I, ? super A, Boolean> predicate) {
    IndexedTraversal<I, S, A> self = this;
    return new IndexedTraversal<>() {
      @Override
      public <F> Kind<F, S> imodifyF(BiFunction<I, A, Kind<F, A>> f, S source, Applicative<F> app) {
        return self.imodifyF(
            (i, a) -> predicate.apply(i, a) ? f.apply(i, a) : app.of(a), source, app);
      }
    };
  }

  /**
   * Views this {@code IndexedTraversal} as a regular (non-indexed) {@link Traversal} by discarding
   * index information.
   *
   * <p>This is useful when you need to pass an indexed traversal to code that expects a regular
   * traversal.
   *
   * @return A {@link Traversal} that ignores index information
   */
  default Traversal<S, A> asTraversal() {
    IndexedTraversal<I, S, A> self = this;
    return new Traversal<>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S source, Applicative<F> app) {
        return self.imodifyF((i, a) -> f.apply(a), source, app);
      }
    };
  }

  /**
   * Views this {@code IndexedTraversal} as an {@link IndexedFold} for read-only indexed operations.
   *
   * @return An {@link IndexedFold} that provides read-only indexed access
   */
  default IndexedFold<I, S, A> asIndexedFold() {
    IndexedTraversal<I, S, A> self = this;
    return new IndexedFold<I, S, A>() {
      @Override
      public <M> M ifoldMap(
          Monoid<M> monoid, BiFunction<? super I, ? super A, ? extends M> f, S source) {
        // We need to traverse all elements and fold them using the monoid.
        // Since we can't reference Id/IdMonad from hkj-core, we define a minimal
        // identity-like applicative inline.
        final List<Pair<I, A>> collected = new ArrayList<>();

        // Define a simple identity wrapper that implements Kind
        // This is a local class to avoid circular dependencies
        @SuppressWarnings("rawtypes")
        final class IdWrapper implements Kind {
          final Object value;

          IdWrapper(Object value) {
            this.value = value;
          }
        }

        // Create a minimal Applicative for IdWrapper
        Applicative<IdWrapper> idApp =
            new Applicative<>() {
              @Override
              @SuppressWarnings("unchecked")
              public <A1> Kind<IdWrapper, A1> of(A1 a) {
                return (Kind<IdWrapper, A1>) new IdWrapper(a);
              }

              @Override
              @SuppressWarnings("unchecked")
              public <A1, B1> Kind<IdWrapper, B1> map(
                  Function<? super A1, ? extends B1> fn, Kind<IdWrapper, A1> fa) {
                IdWrapper wrapper = (IdWrapper) fa;
                return (Kind<IdWrapper, B1>) new IdWrapper(fn.apply((A1) wrapper.value));
              }

              @Override
              @SuppressWarnings("unchecked")
              public <A1, B1> Kind<IdWrapper, B1> ap(
                  Kind<IdWrapper, ? extends Function<A1, B1>> ff, Kind<IdWrapper, A1> fa) {
                IdWrapper wrapperF = (IdWrapper) ff;
                IdWrapper wrapperA = (IdWrapper) fa;
                Function<A1, B1> fn = (Function<A1, B1>) wrapperF.value;
                return (Kind<IdWrapper, B1>) new IdWrapper(fn.apply((A1) wrapperA.value));
              }
            };

        // Use the traversal to collect all index-value pairs
        self.imodifyF(
            (i, a) -> {
              collected.add(new Pair<>(i, a));
              @SuppressWarnings("unchecked")
              Kind<IdWrapper, A> result = (Kind<IdWrapper, A>) new IdWrapper(a);
              return result;
            },
            source,
            idApp);

        // Fold over the collected pairs using the provided monoid
        M result = monoid.empty();
        for (Pair<I, A> pair : collected) {
          result = monoid.combine(result, f.apply(pair.first(), pair.second()));
        }
        return result;
      }
    };
  }
}
