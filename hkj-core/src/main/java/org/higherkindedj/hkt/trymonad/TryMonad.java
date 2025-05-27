// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;

/**
 * Implements the {@link Monad} interface for {@link Try}, using {@link TryKind.Witness}. It extends
 * {@link TryApplicative}.
 *
 * @see Try
 * @see TryKind.Witness
 * @see TryApplicative
 */
public class TryMonad extends TryApplicative implements Monad<TryKind.Witness> {

  /**
   * Sequentially composes two {@code Try} actions, passing the result of the first into a function
   * that produces the second, and flattening the result.
   *
   * @param <A> The input type of the first {@code Try}.
   * @param <B> The output type of the {@code Try} produced by the function {@code f}.
   * @param f The function that takes the successful result of {@code ma} and returns a new {@code
   *     Kind<TryKind.Witness, B>}.
   * @param ma The first {@code Kind<TryKind.Witness, A>}.
   * @return A new {@code Kind<TryKind.Witness, B>} representing the composed and flattened
   *     operation. If {@code ma} is a {@link Try.Failure}, or if applying {@code f} results in an
   *     exception or a {@link Try.Failure}, then a {@link Try.Failure} is returned.
   */
  @Override
  public <A, B> @NonNull Kind<TryKind.Witness, B> flatMap(
      @NonNull Function<A, Kind<TryKind.Witness, B>> f, @NonNull Kind<TryKind.Witness, A> ma) {
    Try<A> tryA = unwrap(ma);

    Try<B> resultTry =
        tryA.flatMap(
            a -> {
              try {
                Kind<TryKind.Witness, B> kindB = f.apply(a);
                return unwrap(kindB);
              } catch (Throwable t) {
                return Try.failure(t);
              }
            });
    return wrap(resultTry);
  }
}
