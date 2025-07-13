// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.jspecify.annotations.NonNull;

/**
 * Implements the {@link MonadError} interface for the {@link Try} data type.
 *
 * <p>This class provides the full set of monadic operations for {@code Try}, enabling the chaining
 * of failable computations in a functional style. It uses {@link TryKind.Witness} as its
 * higher-kinded type witness and {@link Throwable} as its error type {@code E}.
 *
 * <p>As a {@link MonadError} instance, it provides:
 *
 * <ul>
 *   <li><b>flatMap:</b> For sequencing operations where each step can fail.
 *   <li><b>raiseError:</b> For lifting a {@link Throwable} directly into a {@link Try.Failure}.
 *   <li><b>handleErrorWith:</b> For recovering from a {@link Try.Failure} with another {@code Try}
 *       computation.
 * </ul>
 *
 * <p>This class extends {@link TryApplicative} and is implemented as a stateless singleton,
 * accessible via the {@link #INSTANCE} field.
 *
 * @see Try
 * @see TryKind.Witness
 * @see MonadError
 * @see TryApplicative
 * @see Throwable
 */
public class TryMonad extends TryApplicative implements MonadError<TryKind.Witness, Throwable> {

  /** Singleton instance of {@code TryMonad}. */
  public static final TryMonad INSTANCE = new TryMonad();

  /** Private constructor to enforce the singleton pattern. */
  private TryMonad() {
    // Private constructor
  }

  /**
   * Sequentially composes two {@code Try} actions, passing the result of the first into a function
   * that produces the second, and flattening the result.
   *
   * <p>If {@code ma} is a {@link Try.Success}, its value is passed to the function {@code f}. If
   * {@code f} returns a {@link Try.Success}, that becomes the result. If {@code f} throws an
   * exception or returns a {@link Try.Failure}, that failure is captured as the result. If {@code
   * ma} is already a {@link Try.Failure}, its error is propagated, and {@code f} is not called.
   *
   * @param <A> The value type of the first {@code Try}.
   * @param <B> The value type of the {@code Try} produced by the function {@code f}.
   * @param f The non-null function that takes the successful result of {@code ma} and returns a new
   *     {@code Kind<TryKind.Witness, B>}.
   * @param ma The first {@code Kind<TryKind.Witness, A>} to be flat-mapped over.
   * @return A new {@code Kind<TryKind.Witness, B>} representing the composed operation. Never null.
   */
  @Override
  public <A, B> @NonNull Kind<TryKind.Witness, B> flatMap(
      @NonNull Function<A, Kind<TryKind.Witness, B>> f, @NonNull Kind<TryKind.Witness, A> ma) {
    Try<A> tryA = TRY.narrow(ma);

    Try<B> resultTry =
        tryA.flatMap(
            a -> {
              try {
                Kind<TryKind.Witness, B> kindB = f.apply(a);
                return TRY.narrow(kindB);
              } catch (Throwable t) {
                return Try.failure(t);
              }
            });
    return TRY.widen(resultTry);
  }

  /**
   * Lifts a {@link Throwable} into a {@code Kind<TryKind.Witness, A>}, creating a {@link
   * Try.Failure}.
   *
   * @param <A> The phantom type of the value (since this is an error state).
   * @param error The non-null {@link Throwable} to raise.
   * @return A {@code Kind<TryKind.Witness, A>} representing {@code Try.failure(error)}.
   */
  @Override
  public <A> @NonNull Kind<TryKind.Witness, A> raiseError(@NonNull Throwable error) {
    return TRY.widen(Try.failure(error));
  }

  /**
   * Handles an error in a {@code Kind<TryKind.Witness, A>}. If {@code ma} is a {@link Try.Failure},
   * the {@code handler} function is applied to its {@link Throwable} to produce a recovery {@code
   * Kind<TryKind.Witness, A>}. If {@code ma} is a {@link Try.Success}, it is returned unchanged.
   *
   * @param <A> The type of the value.
   * @param ma The {@code Kind<TryKind.Witness, A>} that might have failed.
   * @param handler The non-null function that takes a {@link Throwable} and returns a new {@code
   *     Kind<TryKind.Witness, A>}, providing a chance to recover.
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
