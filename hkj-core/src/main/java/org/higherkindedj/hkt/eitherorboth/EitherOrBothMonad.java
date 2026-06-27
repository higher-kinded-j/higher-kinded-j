// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * {@link Monad} instance for {@link EitherOrBoth}, with the "Left" type {@code L} fixed as the
 * accumulating warning/error channel. All operations are right-biased.
 *
 * <p>Because accumulating the left channel of a {@link EitherOrBoth.Both} requires combining
 * values, this instance carries a {@link Semigroup Semigroup&lt;L&gt;} (supplied via {@link
 * #instance(Semigroup)}), exactly as {@code ValidatedMonad} does. The common case uses {@code
 * NonEmptyList.semigroup()}.
 *
 * <p><b>Lawful, monad-consistent {@code ap}.</b> Unlike {@code Validated} (whose {@code ap}
 * accumulates across failures and therefore diverges from {@code flatMap}), this instance's {@link
 * #ap} is derived from {@link #flatMap} and so the Monad laws (including {@code ap}/{@code flatMap}
 * consistency) hold for any associative {@link Semigroup}. Concretely, {@code ap} short-circuits
 * when the function side is a {@link EitherOrBoth.Left}; it accumulates only in the {@link
 * EitherOrBoth.Both} cases. The fully-parallel, collect-every-error accumulation lives on {@code
 * EitherOrBothPath}'s accumulating combinators, not here.
 *
 * @param <L> the fixed "Left" (warning/error) type
 * @see EitherOrBoth#flatMap(Semigroup, Function)
 * @see EitherOrBothFunctor
 */
public class EitherOrBothMonad<L> extends EitherOrBothFunctor<L>
    implements Monad<EitherOrBothKind.Witness<L>> {

  private static final Class<?> EITHER_OR_BOTH_MONAD_CLASS = EitherOrBothMonad.class;

  private final Semigroup<L> semigroup;

  /**
   * Constructs a monad instance accumulating the left channel with {@code semigroup}.
   *
   * @param semigroup the non-null semigroup for combining left values
   * @throws NullPointerException if {@code semigroup} is null
   */
  protected EitherOrBothMonad(Semigroup<L> semigroup) {
    this.semigroup =
        Validation.coreType().requireValue(semigroup, EITHER_OR_BOTH_MONAD_CLASS, CONSTRUCTION);
  }

  /**
   * Returns an {@code EitherOrBothMonad} accumulating the left channel with {@code semigroup}.
   *
   * @param semigroup the non-null semigroup for combining left values
   * @param <L> the left type
   * @return a new {@code EitherOrBothMonad}
   * @throws NullPointerException if {@code semigroup} is null
   */
  public static <L> EitherOrBothMonad<L> instance(Semigroup<L> semigroup) {
    return new EitherOrBothMonad<>(semigroup);
  }

  /**
   * Lifts a value into the right (success) channel: {@code Right(value)}.
   *
   * @param value the non-null value to lift
   * @param <R> the right type
   * @return a {@code Kind} wrapping {@code Right(value)}
   * @throws NullPointerException if {@code value} is null
   */
  @Override
  @SuppressWarnings("NullableProblems") // EitherOrBoth forbids null
  public <R> Kind<EitherOrBothKind.Witness<L>, R> of(R value) {
    Validation.coreType().requireValue(value, EITHER_OR_BOTH_MONAD_CLASS, OF);
    return EITHER_OR_BOTH.widen(EitherOrBoth.right(value));
  }

  /**
   * Right-biased monadic bind, accumulating the left channel via the carried semigroup. See {@link
   * EitherOrBoth#flatMap(Semigroup, Function)} for the full semantics matrix.
   *
   * @param f the function to apply to the right value; must not be null and must not return null
   * @param ma the input {@code Kind}; must not be null
   * @param <A> the input right type
   * @param <B> the output right type
   * @return the bound {@code Kind}; never null
   * @throws NullPointerException if {@code f} or {@code ma} is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} cannot be unwrapped
   *     or {@code f} returns null
   */
  @Override
  public <A, B> Kind<EitherOrBothKind.Witness<L>, B> flatMap(
      Function<? super A, ? extends Kind<EitherOrBothKind.Witness<L>, B>> f,
      Kind<EitherOrBothKind.Witness<L>, A> ma) {
    Validation.function().validateFlatMap(f, ma);

    EitherOrBoth<L, A> eobA = EITHER_OR_BOTH.narrow(ma);
    EitherOrBoth<L, B> result =
        eobA.flatMap(
            semigroup,
            a -> {
              Kind<EitherOrBothKind.Witness<L>, B> kindB = f.apply(a);
              Validation.function().requireNonNullResult(kindB, "f", FLAT_MAP);
              return EITHER_OR_BOTH.narrow(kindB);
            });
    return EITHER_OR_BOTH.widen(result);
  }

  /**
   * Applies a wrapped function to a wrapped value, monad-consistently (derived from {@link
   * #flatMap}). Short-circuits when the function side is a {@link EitherOrBoth.Left}; accumulates
   * the left channel via the carried semigroup in the {@link EitherOrBoth.Both} cases.
   *
   * @param ff the {@code Kind} containing the function; must not be null
   * @param fa the {@code Kind} containing the argument; must not be null
   * @param <A> the function input type
   * @param <B> the function output type
   * @return a {@code Kind} of the result; never null
   * @throws NullPointerException if {@code ff} or {@code fa} is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped
   */
  @Override
  public <A, B> Kind<EitherOrBothKind.Witness<L>, B> ap(
      Kind<EitherOrBothKind.Witness<L>, ? extends Function<A, B>> ff,
      Kind<EitherOrBothKind.Witness<L>, A> fa) {
    Validation.kind().validateAp(ff, fa);

    EitherOrBoth<L, ? extends Function<A, B>> eobF = EITHER_OR_BOTH.narrow(ff);
    EitherOrBoth<L, A> eobA = EITHER_OR_BOTH.narrow(fa);

    EitherOrBoth<L, B> result = eobF.flatMap(semigroup, eobA::map);
    return EITHER_OR_BOTH.widen(result);
  }

  // map is inherited from EitherOrBothFunctor and is correct.
}
