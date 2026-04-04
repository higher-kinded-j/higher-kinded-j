// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.error;

import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Functor instance for {@link ErrorOp}.
 *
 * <p>Since {@code ErrorOp.Raise} never produces a value (it short-circuits), mapping over it is a
 * no-op: the error is preserved and the result type changes only at the type level (cast-through).
 *
 * @param <E> The error type
 */
@NullMarked
public final class ErrorOpFunctor<E> implements Functor<ErrorOpKind.Witness<E>> {

  @SuppressWarnings("rawtypes")
  private static final ErrorOpFunctor INSTANCE = new ErrorOpFunctor<>();

  private ErrorOpFunctor() {}

  @SuppressWarnings("unchecked")
  public static <E> ErrorOpFunctor<E> instance() {
    return (ErrorOpFunctor<E>) INSTANCE;
  }

  /**
   * Maps a function over the value inside this ErrorOp. Since Raise never produces a value, this is
   * a cast-through: the error is preserved unchanged.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A, B> Kind<ErrorOpKind.Witness<E>, B> map(
      Function<? super A, ? extends B> f, Kind<ErrorOpKind.Witness<E>, A> fa) {
    Validation.function().require(f, "f", MAP);
    Validation.kind().requireNonNull(fa, MAP);
    // Raise<E, A> -> Raise<E, B>: the error is the same, only the phantom type changes.
    ErrorOp<E, A> op = ErrorOpKindHelper.ERROR_OP.narrow(fa);
    return (Kind<ErrorOpKind.Witness<E>, B>)
        ErrorOpKindHelper.ERROR_OP.widen((ErrorOp<E, B>) (ErrorOp<?, B>) op);
  }
}
