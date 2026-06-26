// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;
import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * {@link Monad} instance for {@link NonEmptyListKind.Witness}.
 *
 * <p>Unlike {@code List}, {@code NonEmptyList} has <b>no</b> {@code zero}/empty element, so this is
 * a plain {@link Monad} and deliberately <b>not</b> a {@code MonadZero}/{@code Alternative}: there
 * is no empty {@code NonEmptyList} to serve as an identity, and adding one would reintroduce the
 * empty-list footgun the type exists to remove.
 *
 * <p>The applicative {@link #ap} is the Cartesian product (every function applied to every value),
 * consistent with the {@code List} applicative. Note this is distinct from {@link
 * NonEmptyList#semigroup()} concatenation, which is what an accumulating error channel uses.
 */
public class NonEmptyListMonad implements Monad<NonEmptyListKind.Witness> {

  /** Singleton instance of {@code NonEmptyListMonad}. */
  public static final NonEmptyListMonad INSTANCE = new NonEmptyListMonad();

  /** Protected constructor to enforce the singleton pattern. */
  protected NonEmptyListMonad() {}

  /**
   * Lifts a single value into a singleton {@code NonEmptyList}.
   *
   * @param value the value to lift; must not be {@code null} ({@code NonEmptyList} never holds
   *     {@code null})
   * @param <A> the value type
   * @return a {@code Kind} wrapping {@code NonEmptyList.single(value)}
   * @throws NullPointerException if {@code value} is {@code null}
   */
  @Override
  public <A> Kind<NonEmptyListKind.Witness, A> of(A value) {
    return NON_EMPTY_LIST.widen(NonEmptyList.single(value));
  }

  /**
   * Applies each function in {@code ff} to each value in {@code fa} (Cartesian product), preserving
   * order. The result is non-empty by construction.
   *
   * @param ff a non-null {@code Kind} of functions
   * @param fa a non-null {@code Kind} of values
   * @param <A> the function input type
   * @param <B> the function output type
   * @return a non-null {@code Kind} of all results
   * @throws NullPointerException if {@code ff} or {@code fa} is null
   */
  @Override
  public <A, B> Kind<NonEmptyListKind.Witness, B> ap(
      Kind<NonEmptyListKind.Witness, ? extends Function<A, B>> ff,
      Kind<NonEmptyListKind.Witness, A> fa) {

    Validation.kind().validateAp(ff, fa);

    NonEmptyList<? extends Function<A, B>> functions = NON_EMPTY_LIST.narrow(ff);
    NonEmptyList<A> values = NON_EMPTY_LIST.narrow(fa);
    return NON_EMPTY_LIST.widen(functions.flatMap(func -> values.map(func::apply)));
  }

  /**
   * Maps a function over a {@code NonEmptyList}. Delegates to {@link NonEmptyListFunctor}.
   *
   * @param f the non-null function to apply
   * @param fa the non-null {@code Kind} to map over
   * @param <A> the input element type
   * @param <B> the output element type
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
   * Applies {@code f} to every element and concatenates the resulting non-empty lists. The result
   * is non-empty by construction.
   *
   * @param f a non-null function from {@code A} to a {@code Kind} of {@code B}; must not return
   *     {@code null}
   * @param ma the non-null input {@code Kind}
   * @param <A> the input element type
   * @param <B> the output element type
   * @return a non-null flattened {@code Kind}
   * @throws NullPointerException if {@code f} or {@code ma} is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or a result of {@code
   *     f} cannot be unwrapped
   */
  @Override
  public <A, B> Kind<NonEmptyListKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<NonEmptyListKind.Witness, B>> f,
      Kind<NonEmptyListKind.Witness, A> ma) {

    Validation.function().validateFlatMap(f, ma);

    NonEmptyList<A> nel = NON_EMPTY_LIST.narrow(ma);
    return NON_EMPTY_LIST.widen(
        nel.flatMap(
            a -> {
              Kind<NonEmptyListKind.Witness, B> kindB = f.apply(a);
              Validation.function().requireNonNullResult(kindB, "f", FLAT_MAP);
              return NON_EMPTY_LIST.narrow(kindB);
            }));
  }
}
