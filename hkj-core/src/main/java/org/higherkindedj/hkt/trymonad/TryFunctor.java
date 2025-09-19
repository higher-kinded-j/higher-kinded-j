// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;

/**
 * Implements the {@link Functor} interface for {@link Try}, using {@link TryKind.Witness}.
 *
 * @see Try
 * @see TryKind.Witness
 */
public class TryFunctor implements Functor<TryKind.Witness> {

  /**
   * Maps a function over a {@code Kind<TryKind.Witness, A>}.
   *
   * @param <A> The input type of the {@code Try}.
   * @param <B> The output type after applying the function.
   * @param f The function to apply if the {@code Try} is a {@link Try.Success}. Must not be null.
   * @param fa The {@code Kind<TryKind.Witness, A>} to map over. Must not be null.
   * @return A new {@code Kind<TryKind.Witness, B>} representing the result of the map operation.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped
   *     to a valid {@code Try} representation.
   */
  @Override
  public <A, B> Kind<TryKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<TryKind.Witness, A> fa) {
    requireNonNullFunction(f, "function f for map");
    requireNonNullKind(fa, "source Kind for map");

    Try<A> tryA = TRY.narrow(fa);
    Try<B> resultTry = tryA.map(f);
    return TRY.widen(resultTry);
  }
}
