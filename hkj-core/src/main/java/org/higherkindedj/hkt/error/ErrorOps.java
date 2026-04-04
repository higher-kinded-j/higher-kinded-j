// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.error;

import java.util.Objects;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.inject.Inject;
import org.jspecify.annotations.NullMarked;

/**
 * Smart constructors for {@link ErrorOp} operations, lifting them into the Free monad.
 *
 * <p>For standalone use (single-effect programs):
 *
 * <pre>{@code
 * Free<ErrorOpKind.Witness<BusinessError>, Void> program = ErrorOps.raise(new NotFoundError());
 * }</pre>
 *
 * <p>For combined-effect use via {@link Bound}:
 *
 * <pre>{@code
 * ErrorOps.Bound<BusinessError, AppEffects> errors = ErrorOps.boundTo(errorInject);
 * Free<AppEffects, Void> combined = errors.raise(new NotFoundError());
 * }</pre>
 *
 * @see ErrorOp
 * @see ErrorOpKind
 */
@NullMarked
public final class ErrorOps {

  private static final ErrorOpFunctor<?> FUNCTOR = ErrorOpFunctor.instance();

  private ErrorOps() {}

  /**
   * Raises an error, lifting it into a Free program that immediately short-circuits.
   *
   * @param error The error to raise. Must not be null.
   * @param <E> The error type
   * @param <A> The phantom result type
   * @return A Free program that raises the error
   */
  @SuppressWarnings("unchecked")
  public static <E, A> Free<ErrorOpKind.Witness<E>, A> raise(E error) {
    Objects.requireNonNull(error, "error must not be null");
    ErrorOp<E, A> op = new ErrorOp.Raise<>(error);
    return Free.liftF(ErrorOpKindHelper.ERROR_OP.widen(op), (ErrorOpFunctor<E>) FUNCTOR);
  }

  /**
   * Creates a Bound instance for combined-effect programs.
   *
   * @param inject The Inject instance for embedding ErrorOp into the combined effect type
   * @param <E> The error type
   * @param <G> The combined effect type
   * @return A Bound instance
   */
  public static <E, G extends WitnessArity<TypeArity.Unary>> Bound<E, G> boundTo(
      Inject<ErrorOpKind.Witness<E>, G> inject, Functor<G> functorG) {
    return new Bound<>(inject, functorG);
  }

  /**
   * Bound instance for using ErrorOp in combined-effect programs. Each method lifts an ErrorOp
   * instruction into the combined effect type G via the provided Inject instance.
   *
   * @param <E> The error type
   * @param <G> The combined effect type
   */
  public static final class Bound<E, G extends WitnessArity<TypeArity.Unary>> {
    private final Inject<ErrorOpKind.Witness<E>, G> inject;
    private final Functor<G> functorG;

    Bound(Inject<ErrorOpKind.Witness<E>, G> inject, Functor<G> functorG) {
      this.inject = Objects.requireNonNull(inject, "inject must not be null");
      this.functorG = Objects.requireNonNull(functorG, "functorG must not be null");
    }

    /**
     * Raises an error in the combined effect type.
     *
     * @param error The error to raise. Must not be null.
     * @param <A> The phantom result type
     * @return A Free program in the combined effect type that raises the error
     */
    public <A> Free<G, A> raise(E error) {
      Free<ErrorOpKind.Witness<E>, A> standalone = ErrorOps.raise(error);
      return Free.translate(standalone, inject::inject, functorG);
    }
  }
}
