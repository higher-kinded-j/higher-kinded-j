// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;

/**
 * Implements the {@link Traverse} type class for {@link java.util.List}, using {@link
 * ListKind.Witness} as the higher-kinded type witness.
 *
 * <p>The {@code Traverse} type class allows for traversing a data structure (in this case, a List)
 * from left to right, performing an {@link Applicative} effect for each element and collecting the
 * results. It also provides a {@code map} operation, as {@code Traverse} extends {@link
 * org.higherkindedj.hkt.Functor}.
 *
 * <p>This implementation relies on an {@link Applicative} instance for the target effect type
 * {@code G} to combine the results of applying the effectful function to each element of the list.
 *
 * @see Traverse
 * @see List
 * @see ListKind
 * @see ListFunctor
 * @see Applicative
 */
public class ListTraverse implements Traverse<ListKind.Witness> {

  /**
   * Singleton instance of {@code ListTraverse}. This instance can be used to access {@code
   * Traverse} operations for lists.
   */
  public static final ListTraverse INSTANCE = new ListTraverse();

  /** Private constructor to prevent direct instantiation and enforce singleton pattern. */
  private ListTraverse() {}

  /**
   * Maps a function over a list in a higher-kinded context. This operation is inherited from {@link
   * org.higherkindedj.hkt.Functor} via {@link Traverse} and delegates to {@link
   * ListFunctor#INSTANCE}.
   *
   * @param <A> The type of elements in the input list.
   * @param <B> The type of elements in the output list after applying the function.
   * @param f The non-null function to apply to each element of the list.
   * @param fa The non-null {@code Kind<ListKind.Witness, A>} (which is a {@code ListKind<A>})
   *     containing the input list.
   * @return A new non-null {@code Kind<ListKind.Witness, B>} containing a list with the results of
   *     applying the function {@code f}.
   */
  @Override
  public <A, B> @NonNull Kind<ListKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<ListKind.Witness, A> fa) {
    return ListFunctor.INSTANCE.map(f, fa);
  }

  /**
   * Traverses a list from left to right, applying an effectful function {@code f} to each element
   * and collecting the results within the context of the {@link Applicative} {@code G}.
   *
   * <p>For example, if {@code ta} is a {@code ListKind<A>} and {@code f} is a function {@code A ->
   * Kind<G, B>} (e.g., {@code A -> OptionalKind<B>}), then this method will produce a {@code
   * Kind<G, ListKind<B>>} (e.g., {@code OptionalKind<ListKind<B>>}). If any application of {@code
   * f} results in an "empty" or "failure" state for the {@code Applicative<G>} (like {@code
   * Optional.empty()}), the entire traversal typically short-circuits to that empty/failure state.
   *
   * @param <G> The higher-kinded type witness for the {@link Applicative} context.
   * @param <A> The type of elements in the input list {@code ta}.
   * @param <B> The type of elements in the resulting list, wrapped within the context {@code G}.
   * @param applicative The non-null {@link Applicative} instance for the context {@code G}. This is
   *     used to lift values and combine effects.
   * @param ta The non-null {@code Kind<ListKind.Witness, A>} (a list of {@code A}s) to traverse.
   * @param f A non-null function from {@code A} to {@code Kind<G, ? extends B>}, producing an
   *     effectful value for each element in {@code ta}.
   * @return A {@code Kind<G, Kind<ListKind.Witness, B>>}. This represents the list of results (each
   *     of type {@code B}), with the entire resulting list structure itself wrapped in the
   *     applicative context {@code G}. For example, if {@code G} is {@code OptionalKind}, the
   *     result could be an {@code OptionalKind} containing a {@code ListKind} of {@code B}s, or an
   *     empty {@code OptionalKind} if any step failed. The returned {@code Kind} is non-null.
   */
  @Override
  public <G, A, B> @NonNull Kind<G, Kind<ListKind.Witness, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<ListKind.Witness, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f) {

    List<A> listA = ListKind.narrow(ta).unwrap();

    if (listA.isEmpty()) {
      // If the input list is empty, return an applicative effect containing an empty ListKind.
      ListKind<B> emptyListKind = ListKind.of(Collections.emptyList());
      return applicative.of(emptyListKind);
    }

    // Start with an applicative effect containing an empty list of B's.
    // This will be the accumulator for our results.
    Kind<G, List<B>> accumulatedEffects = applicative.of(new ArrayList<>());

    for (A item : listA) {
      // Apply the effectful function f to the current item.
      // effectOfItemRaw is Kind<G, ? extends B>
      Kind<G, ? extends B> effectOfItemRaw = f.apply(item);

      // Ensure effectOfItem is Kind<G, B> for combining with accumulatedEffects.
      // The cast (B)val is safe because 'val' comes from '? extends B'.
      Kind<G, B> effectOfItem = applicative.map(val -> (B) val, effectOfItemRaw);

      // We want to combine:
      // accumulatedEffects: Kind<G, List<B>> (current list of results in context G)
      // effectOfItem:       Kind<G, B>       (current item's result in context G)
      // into a new Kind<G, List<B>>.

      // Define a function that takes the current list (List<B>) and the new item (B),
      // and returns a new list with the item appended.
      // This function itself needs to be applied within the applicative context.
      // combineToListFn: List<B> -> (B -> List<B>)
      Function<List<B>, Function<B, List<B>>> combineToListFn =
          currentList ->
              newItem -> {
                List<B> newList = new ArrayList<>(currentList.size() + 1);
                newList.addAll(currentList);
                newList.add(newItem);
                return newList;
              };

      // 1. Lift `combineToListFn` into the context G by mapping it over `accumulatedEffects`.
      //    `map(combineToListFn, accumulatedEffects)` yields `Kind<G, Function<B, List<B>>>`.
      //    This is an effectful function `G(B -> List<B>)`.
      Kind<G, Function<B, List<B>>> liftedCombineFn =
          applicative.map(combineToListFn, accumulatedEffects);

      // 2. Apply this effectful function to the effectful current item `effectOfItem`.
      //    `ap( Kind<G, Function<B, List<B>>> , Kind<G, B> )` results in `Kind<G, List<B>>`.
      //    This combines the accumulated list with the new item, all within context G.
      accumulatedEffects = applicative.ap(liftedCombineFn, effectOfItem);
    }

    // After iterating through all items, `accumulatedEffects` is `Kind<G, List<B>>`.
    // The traverse method should return `Kind<G, Kind<ListKind.Witness, B>>`.
    // So, we map over `accumulatedEffects` to wrap the inner `List<B>` with `ListKind.of()`.
    return applicative.map(ListKind::of, accumulatedEffects);
  }
}
