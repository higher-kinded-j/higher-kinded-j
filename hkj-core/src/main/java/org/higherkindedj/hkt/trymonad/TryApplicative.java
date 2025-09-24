// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.util.validation.Operation.AP;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Applicative} interface for {@link Try}, using {@link TryKind.Witness}. It
 * extends {@link TryFunctor}.
 *
 * @see Try
 * @see TryKind.Witness
 * @see TryFunctor
 */
public class TryApplicative extends TryFunctor implements Applicative<TryKind.Witness> {

  private static final Class<TryApplicative> TRY_APPLICATIVE_CLASS = TryApplicative.class;

  /**
   * Lifts a value into a successful {@code Try} context, represented as {@code
   * Kind<TryKind.Witness, A>}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null}.
   * @return A {@code Kind<TryKind.Witness, A>} representing {@code Try.success(value)}. Never null.
   */
  @Override
  public <A> Kind<TryKind.Witness, A> of(@Nullable A value) {
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

    KindValidator.requireNonNull(ff, TRY_APPLICATIVE_CLASS, AP, "function");
    KindValidator.requireNonNull(fa, TRY_APPLICATIVE_CLASS, AP, "argument");

    Try<? extends Function<A, B>> tryF = TRY.narrow(ff);
    Try<A> tryA = TRY.narrow(fa);

    Try<B> resultTry =
        tryF.fold(f -> tryA.fold(a -> Try.of(() -> f.apply(a)), Try::failure), Try::failure);
    return TRY.widen(resultTry);
  }
}
