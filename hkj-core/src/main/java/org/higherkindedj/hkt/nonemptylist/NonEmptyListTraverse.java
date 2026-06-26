// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Implements the {@link Traverse} and {@link Foldable} type classes for {@link NonEmptyList}, using
 * {@link NonEmptyListKind.Witness} as the higher-kinded type witness.
 *
 * <p>Because a {@code NonEmptyList} always has at least one element, the traversed and folded
 * results are non-empty by construction — the {@code head} is always present.
 */
@NullMarked
public enum NonEmptyListTraverse implements Traverse<NonEmptyListKind.Witness> {
  /**
   * Singleton instance, exposing {@code Traverse} and {@code Foldable} for {@code NonEmptyList}.
   */
  INSTANCE;

  /**
   * Maps a function over a {@code NonEmptyList}. Inherited from {@link
   * org.higherkindedj.hkt.Functor} via {@link Traverse}; delegates to {@link NonEmptyListFunctor}.
   *
   * @param <A> the input element type
   * @param <B> the output element type
   * @param f the non-null function to apply
   * @param fa the non-null {@code Kind} to map over
   * @return a non-null mapped {@code Kind}
   * @throws NullPointerException if {@code f} or {@code fa} is null
   */
  @Override
  public <A, B> Kind<NonEmptyListKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<NonEmptyListKind.Witness, A> fa) {

    Validation.function().validateMap(f, fa);

    return NonEmptyListFunctor.INSTANCE.map(f, fa);
  }

  /**
   * Traverses a {@code NonEmptyList} left to right, applying the effectful {@code f} to each
   * element and collecting the results in the {@link Applicative} {@code G}. The {@code head} is
   * visited first, so effects (including accumulating ones such as {@code Validated}) compose in
   * element order, and the resulting list is non-empty by construction.
   *
   * <p>The tail is traversed via the proven {@link ListTraverse}, then combined with the head's
   * effect using {@link Applicative#map2}.
   *
   * @param <G> the applicative context witness
   * @param <A> the input element type
   * @param <B> the output element type
   * @param applicative the non-null {@link Applicative} for {@code G}
   * @param f the non-null effectful function
   * @param ta the non-null {@code Kind} to traverse
   * @return a {@code Kind<G, Kind<NonEmptyListKind.Witness, B>>}; never null
   * @throws NullPointerException if {@code applicative}, {@code f}, or {@code ta} is null
   */
  @Override
  public <G extends WitnessArity<TypeArity.Unary>, A, B>
      Kind<G, Kind<NonEmptyListKind.Witness, B>> traverse(
          Applicative<G> applicative,
          Function<? super A, ? extends Kind<G, ? extends B>> f,
          Kind<NonEmptyListKind.Witness, A> ta) {

    Validation.function().validateTraverse(applicative, f, ta);

    NonEmptyList<A> nel = NON_EMPTY_LIST.narrow(ta);

    @SuppressWarnings("unchecked") // f produces G of (? extends B); B is the chosen result element
    Kind<G, B> headEffect = (Kind<G, B>) f.apply(nel.head());

    Kind<ListKind.Witness, A> tailKind = LIST.widen(nel.tail());
    Kind<G, Kind<ListKind.Witness, B>> tailEffect =
        ListTraverse.INSTANCE.traverse(applicative, f, tailKind);

    return applicative.map2(
        headEffect,
        tailEffect,
        (head, tailList) -> NON_EMPTY_LIST.widen(NonEmptyList.of(head, LIST.narrow(tailList))));
  }

  /**
   * Maps each element to a {@link Monoid} {@code M} and combines the results, left to right from
   * the {@code head}.
   *
   * @param <A> the element type
   * @param <M> the monoidal type
   * @param monoid the non-null {@code Monoid} used to combine results
   * @param f the non-null mapping function
   * @param fa the non-null {@code Kind} to fold
   * @return the aggregated result; never null
   * @throws NullPointerException if {@code monoid}, {@code f}, or {@code fa} is null
   */
  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<NonEmptyListKind.Witness, A> fa) {

    Validation.function().validateFoldMap(monoid, f, fa);

    NonEmptyList<A> nel = NON_EMPTY_LIST.narrow(fa);
    M accumulator = f.apply(nel.head());
    for (A a : nel.tail()) {
      accumulator = monoid.combine(accumulator, f.apply(a));
    }
    return accumulator;
  }
}
