// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NullMarked;

/**
 * Implements the {@link Traverse} and {@link Foldable} typeclasses for {@link java.util.List},
 * using {@link ListKind.Witness} as the higher-kinded type witness.
 */
@NullMarked
public enum ListTraverse implements Traverse<ListKind.Witness> {
  /**
   * Singleton instance of {@code ListTraverse}. This instance can be used to access {@code
   * Traverse} and {@code Foldable} operations for lists.
   */
  INSTANCE;

  /**
   * Maps a function over a list in a higher-kinded context. This operation is inherited from {@link
   * Functor} via {@link Traverse}.
   *
   * @param <A> The type of elements in the input list.
   * @param <B> The type of elements in the output list after applying the function.
   * @param f The non-null function to apply to each element of the list.
   * @param fa The non-null {@code Kind<ListKind.Witness, A>} representing the input list.
   * @return A new non-null {@code Kind<ListKind.Witness, B>} containing a list with the results of
   *     applying the function {@code f}.
   * @throws NullPointerException if f or fa is null.
   */
  @Override
  public <A, B> Kind<ListKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<ListKind.Witness, A> fa) {
    requireNonNullFunction(f, "function f for map");
    requireNonNullKind(fa, "source Kind for map");
    // For lists, mapping is equivalent to the Functor implementation.
    return ListFunctor.INSTANCE.map(f, fa);
  }

  /**
   * Traverses a list from left to right, applying an effectful function {@code f} to each element
   * and collecting the results within the context of the {@link Applicative} {@code G}.
   *
   * @param <G> The higher-kinded type witness for the {@link Applicative} context.
   * @param <A> The type of elements in the input list {@code ta}.
   * @param <B> The type of elements in the resulting list, wrapped within the context {@code G}.
   * @param applicative The non-null {@link Applicative} instance for the context {@code G}.
   * @param f A non-null function from {@code A} to {@code Kind<G, ? extends B>}, producing an
   *     effectful value for each element.
   * @param ta The non-null {@code Kind<ListKind.Witness, A>} (a list of {@code A}s) to traverse.
   * @return A {@code Kind<G, Kind<ListKind.Witness, B>>}. This represents the list of results (each
   *     of type {@code B}), with the entire resulting list structure itself wrapped in the
   *     applicative context {@code G}.
   * @throws NullPointerException if applicative, f, or ta is null.
   */
  @Override
  public <G, A, B> Kind<G, Kind<ListKind.Witness, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<ListKind.Witness, A> ta) {

    requireNonNullForWiden(applicative, "Applicative");
    requireNonNullFunction(f, "traverse function");
    requireNonNullKind(ta, "source Kind for traverse");

    List<A> listA = LIST.narrow(ta);
    Kind<G, List<B>> result = applicative.of(new LinkedList<>());
    for (A a : listA) {
      Kind<G, ? extends B> effectOfB = f.apply(a);
      result =
          applicative.map2(
              result,
              effectOfB,
              (listB, b) -> {
                // Create a new list to avoid mutation issues and append the new element.
                // Appending to a LinkedList is an efficient O(1) operation.
                List<B> newList = new LinkedList<>(listB);
                newList.add((B) b);
                return newList;
              });
    }
    return applicative.map(LIST::widen, result);
  }

  /**
   * Maps each element of the list to a {@link Monoid} {@code M} and combines the results.
   *
   * @param <A> The type of elements in the list.
   * @param <M> The Monoidal type to which elements are mapped and combined.
   * @param monoid The {@code Monoid} used to combine the results. Must not be null.
   * @param f A function to map each element of type {@code A} to the Monoidal type {@code M}. Must
   *     not be null.
   * @param fa The {@code Kind<ListKind.Witness, A>} representing the list to fold. Must not be
   *     null.
   * @return The aggregated result of type {@code M}.
   * @throws NullPointerException if monoid, f, or fa is null.
   */
  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<ListKind.Witness, A> fa) {
    requireNonNullForWiden(monoid, "Monoid");
    requireNonNullFunction(f, "foldMap function");
    requireNonNullKind(fa, "source Kind for foldMap");

    M accumulator = monoid.empty();
    for (A a : LIST.narrow(fa)) {
      accumulator = monoid.combine(accumulator, f.apply(a));
    }
    return accumulator;
  }
}
