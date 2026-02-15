// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.HANDLE_ERROR_WITH;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} type class for {@link VTask}, using {@link VTaskKind.Witness}
 * as the higher-kinded type witness and {@link Throwable} as the error type.
 *
 * <p>This implementation provides the ability to sequence VTask computations where subsequent
 * computations depend on the result of previous ones, while also supporting error handling through
 * the MonadError interface.
 *
 * <p>Key capabilities:
 *
 * <ul>
 *   <li><b>flatMap:</b> Sequences VTask computations, passing the result of one to produce the
 *       next.
 *   <li><b>raiseError:</b> Creates a VTask that fails with a given exception.
 *   <li><b>handleErrorWith:</b> Recovers from errors using a handler function.
 * </ul>
 *
 * <p>This class is a stateless singleton, accessible via {@link #INSTANCE}.
 *
 * @see MonadError
 * @see VTaskApplicative
 * @see VTask
 * @see VTaskKind
 * @see VTaskKind.Witness
 * @see VTaskKindHelper
 */
public class VTaskMonad extends VTaskApplicative
    implements MonadError<VTaskKind.Witness, Throwable> {

  private static final Class<VTaskMonad> VTASK_MONAD_CLASS = VTaskMonad.class;

  /** Singleton instance of {@code VTaskMonad}. */
  public static final VTaskMonad INSTANCE = new VTaskMonad();

  /** Protected constructor to enforce the singleton pattern. */
  protected VTaskMonad() {
    super();
  }

  /**
   * Sequentially composes two VTask computations, where the second computation (produced by
   * function {@code f}) depends on the result of the first computation ({@code ma}).
   *
   * <p>When the resulting VTask is executed, it first runs the first VTask computation to get a
   * value, then applies the function {@code f} to that value to get a new VTask computation, and
   * finally runs that new VTask computation to get the final result.
   *
   * <p>This operation maintains lazy evaluation - no computations are performed until {@code run()}
   * is called on the resulting VTask.
   *
   * @param <A> The type of the result of the first computation {@code ma}.
   * @param <B> The type of the result of the second computation returned by function {@code f}.
   * @param f A function that takes the result of the first computation and returns a new {@code
   *     Kind<VTaskKind.Witness, B>} representing the next computation. Must not be null.
   * @param ma A {@code Kind<VTaskKind.Witness, A>} representing the first VTask computation. Must
   *     not be null.
   * @return A {@code Kind<VTaskKind.Witness, B>} representing the sequenced VTask computation.
   *     Never null.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the {@code Kind}
   *     returned by {@code f} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<VTaskKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<VTaskKind.Witness, B>> f, Kind<VTaskKind.Witness, A> ma) {

    Validation.function().validateFlatMap(f, ma, VTASK_MONAD_CLASS);

    VTask<A> vtaskA = VTASK.narrow(ma);
    VTask<B> vtaskB =
        vtaskA.flatMap(
            a -> {
              var kindB = f.apply(a);
              Validation.function()
                  .requireNonNullResult(kindB, "f", VTASK_MONAD_CLASS, FLAT_MAP, Kind.class);
              return VTASK.narrow(kindB);
            });
    return VTASK.widen(vtaskB);
  }

  /**
   * Lifts an error into the VTask context, creating a VTask that fails with the given throwable
   * when executed.
   *
   * @param <A> The phantom type parameter of the value (since this represents an error state).
   * @param error The throwable to fail with. Must not be null.
   * @return A {@code Kind<VTaskKind.Witness, A>} representing a failed VTask. Never null.
   * @throws NullPointerException if {@code error} is null.
   */
  @Override
  public <A> Kind<VTaskKind.Witness, A> raiseError(@Nullable Throwable error) {
    Validation.coreType().requireError(error, VTASK_MONAD_CLASS, HANDLE_ERROR_WITH);
    return VTASK.widen(VTask.fail(error));
  }

  /**
   * Handles an error within the VTask context. If the VTask succeeds, the result is returned
   * unchanged. If the VTask fails, the handler function is applied to the exception to potentially
   * recover with a new VTask.
   *
   * @param <A> The type of the value within the VTask.
   * @param ma The VTask potentially containing an error. Must not be null.
   * @param handler A function that takes an error and returns a new VTask, potentially recovering
   *     from the error. Must not be null.
   * @return The original VTask if successful, or the result of the handler if it failed. Never
   *     null.
   * @throws NullPointerException if {@code ma} or {@code handler} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the result of
   *     {@code handler} cannot be unwrapped.
   */
  @Override
  public <A> Kind<VTaskKind.Witness, A> handleErrorWith(
      Kind<VTaskKind.Witness, A> ma,
      Function<? super Throwable, ? extends Kind<VTaskKind.Witness, A>> handler) {

    Validation.kind().requireNonNull(ma, VTASK_MONAD_CLASS, HANDLE_ERROR_WITH);
    Validation.function().requireFunction(handler, "handler", VTASK_MONAD_CLASS, HANDLE_ERROR_WITH);

    VTask<A> vtaskA = VTASK.narrow(ma);
    VTask<A> recovered =
        vtaskA.recoverWith(
            error -> {
              var kindB = handler.apply(error);
              Validation.function()
                  .requireNonNullResult(
                      kindB, "handler", VTASK_MONAD_CLASS, HANDLE_ERROR_WITH, Kind.class);
              return VTASK.narrow(kindB);
            });
    return VTASK.widen(recovered);
  }
}
