// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.jspecify.annotations.NonNull;

/**
 * Implements {@link MonadError} for {@link Try}, using {@link TryKind.Witness} as the HKT marker
 * and {@link Throwable} as the error type. It extends {@link TryMonad}.
 *
 * @see Try
 * @see TryKind.Witness
 * @see TryMonad
 * @see Throwable
 */
public class TryMonadError extends TryMonad implements MonadError<TryKind.Witness, Throwable> {

  /**
   * Lifts a {@link Throwable} into a {@code Kind<TryKind.Witness, A>} representing a failed
   * computation.
   *
   * @param <A> The phantom type of the value (since it's an error).
   * @param error The non-null {@link Throwable} to raise.
   * @return A {@code Kind<TryKind.Witness, A>} representing {@code Try.failure(error)}.
   */
  @Override
  public <A> @NonNull Kind<TryKind.Witness, A> raiseError(@NonNull Throwable error) {
    return TRY.widen(Try.failure(error));
  }

  /**
   * Handles an error in a {@code Kind<TryKind.Witness, A>}. If {@code ma} is a {@link Try.Failure},
   * the {@code handler} function is applied to the {@link Throwable} to produce a recovery {@code
   * Kind<TryKind.Witness, A>}. If {@code ma} is a {@link Try.Success}, it is returned unchanged.
   *
   * @param <A> The type of the value.
   * @param ma The {@code Kind<TryKind.Witness, A>} computation that might have failed.
   * @param handler The function to apply to the {@link Throwable} if {@code ma} is a {@link
   *     Try.Failure}. This function returns a new {@code Kind<TryKind.Witness, A>}.
   * @return A {@code Kind<TryKind.Witness, A>} representing either the original success, or the
   *     result of the error handler.
   */
  @Override
  public <A> @NonNull Kind<TryKind.Witness, A> handleErrorWith(
      @NonNull Kind<TryKind.Witness, A> ma,
      @NonNull Function<Throwable, Kind<TryKind.Witness, A>> handler) {
    Try<A> tryA = TRY.narrow(ma);

    Try<A> resultTry =
        tryA.recoverWith(
            throwable -> {
              try {
                Kind<TryKind.Witness, A> recoveryKind = handler.apply(throwable);
                return TRY.narrow(recoveryKind);
              } catch (Throwable t) {
                return Try.failure(t);
              }
            });
    return TRY.widen(resultTry);
  }
}
