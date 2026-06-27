// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * {@link Functor} instance for {@link EitherOrBoth}, biased towards the "Right" value.
 *
 * <p>Mapping transforms the right value of a {@link EitherOrBoth.Right} or {@link
 * EitherOrBoth.Both} while leaving the left (warning) channel untouched; a {@link
 * EitherOrBoth.Left} is propagated unchanged.
 *
 * @param <L> the fixed "Left" (warning/error) type for which this functor instance is defined
 * @see EitherOrBoth
 * @see EitherOrBothMonad
 */
public class EitherOrBothFunctor<L> implements Functor<EitherOrBothKind.Witness<L>> {

  private static final EitherOrBothFunctor<?> INSTANCE = new EitherOrBothFunctor<>();

  /** Protected constructor to enforce the singleton pattern and allow subclassing. */
  protected EitherOrBothFunctor() {}

  /**
   * Returns the singleton functor instance for the fixed left type {@code L}.
   *
   * @param <L> the fixed left type
   * @return the singleton {@code EitherOrBothFunctor}
   */
  @SuppressWarnings("unchecked")
  public static <L> EitherOrBothFunctor<L> instance() {
    return (EitherOrBothFunctor<L>) INSTANCE;
  }

  /**
   * Applies {@code f} to the right value, leaving the left channel unchanged.
   *
   * @param f the function to apply to the right value; must not be null
   * @param fa the input {@code Kind}; must not be null
   * @param <A> the input right type
   * @param <B> the output right type
   * @return the transformed {@code Kind}; never null
   * @throws NullPointerException if {@code f} or {@code fa} is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped
   */
  @Override
  public <A, B> Kind<EitherOrBothKind.Witness<L>, B> map(
      Function<? super A, ? extends B> f, Kind<EitherOrBothKind.Witness<L>, A> fa) {
    Validation.function().validateMap(f, fa);
    return EITHER_OR_BOTH.widen(EITHER_OR_BOTH.narrow(fa).map(f));
  }
}
