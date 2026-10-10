// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.util.validation.Operation.OF;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Applicative} interface for {@link Try}, using {@link TryKind.Witness}. It
 * extends {@link TryFunctor}.
 *
 * @see Try
 * @see TryKind.Witness
 * @see TryFunctor
 */
public class TryApplicative extends TryFunctor implements Applicative<TryKind.Witness> {

  /**
   * Lifts a value into a successful {@code Try} context, represented as {@code
   * Kind<TryKind.Witness, A>}.
   *
   * @param <A> The type of the value.
   * @param value The non-null value to lift, since a {@link Try.Success} always holds one.
   * @return A {@code Kind<TryKind.Witness, A>} representing {@code Try.success(value)}. Never null.
   * @throws NullPointerException if {@code value} is null.
   */
  @Override
  @SuppressWarnings("NullableProblems") // Try.Success forbids null
  public <A> Kind<TryKind.Witness, A> of(A value) {
    Validation.coreType().requireValue(value, TryApplicative.class, OF);
    return TRY.widen(Try.success(value));
  }

  /**
   * Applies a function wrapped in a {@code Kind<TryKind.Witness, Function<A, B>>} to a value
   * wrapped in a {@code Kind<TryKind.Witness, A>}.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The {@code Kind<TryKind.Witness, Function<A, B>>} containing the function. Must not
   *     be null.
   * @param fa The {@code Kind<TryKind.Witness, A>} containing the value. Must not be null.
   * @return A new {@code Kind<TryKind.Witness, B>} resulting from the application. If {@code ff} or
   *     {@code fa} is a {@link Try.Failure}, or if applying the function in {@code ff} to the value
   *     in {@code fa} (if both are {@link Try.Success}) results in an exception, then a {@link
   *     Try.Failure} is returned. Never null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped to valid {@code Try} representations.
   */
  @Override
  public <A, B> Kind<TryKind.Witness, B> ap(
      Kind<TryKind.Witness, ? extends Function<A, B>> ff, Kind<TryKind.Witness, A> fa) {

    Validation.kind().validateAp(ff, fa);

    Try<? extends Function<A, B>> tryF = TRY.narrow(ff);
    Try<A> tryA = TRY.narrow(fa);

    Try<B> resultTry = tryF.foldFailureFirst(Try::failure, tryA::map);
    return TRY.widen(resultTry);
  }
}
