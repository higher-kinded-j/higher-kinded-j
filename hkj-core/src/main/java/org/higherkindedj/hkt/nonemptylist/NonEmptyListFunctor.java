// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Functor} type class for {@link NonEmptyList}, using {@link
 * NonEmptyListKind.Witness} as the higher-kinded type witness.
 *
 * @see Functor
 * @see NonEmptyList
 * @see NonEmptyListMonad
 */
class NonEmptyListFunctor implements Functor<NonEmptyListKind.Witness> {

  /**
   * Singleton instance of {@code NonEmptyListFunctor}. Consider accessing Functor operations via
   * {@link NonEmptyListMonad#INSTANCE}.
   */
  public static final NonEmptyListFunctor INSTANCE = new NonEmptyListFunctor();

  /** Package-private constructor for the singleton and for {@link NonEmptyListMonad}. */
  NonEmptyListFunctor() {}

  /**
   * Applies a function to every element of a {@code NonEmptyList}, preserving order and
   * non-emptiness.
   *
   * @param <A> the input element type
   * @param <B> the output element type
   * @param f the non-null function to apply
   * @param fa the non-null {@code Kind<NonEmptyListKind.Witness, A>} to map over
   * @return a new non-null {@code Kind<NonEmptyListKind.Witness, B>}
   * @throws NullPointerException if {@code f} or {@code fa} is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} is not a valid {@code
   *     NonEmptyList} representation
   */
  @Override
  public <A, B> Kind<NonEmptyListKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<NonEmptyListKind.Witness, A> fa) {

    Validation.function().validateMap(f, fa);

    return NON_EMPTY_LIST.widen(NON_EMPTY_LIST.narrow(fa).map(f));
  }
}
