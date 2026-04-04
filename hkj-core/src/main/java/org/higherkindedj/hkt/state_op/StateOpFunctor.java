// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_op;

import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Functor instance for {@link StateOp}.
 *
 * <p>Delegates to {@link StateOp#mapK(Function)} which composes the mapping function with the
 * continuation carried by each StateOp variant.
 *
 * @param <S> The state type
 */
@NullMarked
public final class StateOpFunctor<S> implements Functor<StateOpKind.Witness<S>> {

  @SuppressWarnings("rawtypes")
  private static final StateOpFunctor INSTANCE = new StateOpFunctor<>();

  private StateOpFunctor() {}

  @SuppressWarnings("unchecked")
  public static <S> StateOpFunctor<S> instance() {
    return (StateOpFunctor<S>) INSTANCE;
  }

  @Override
  public <A, B> Kind<StateOpKind.Witness<S>, B> map(
      Function<? super A, ? extends B> f, Kind<StateOpKind.Witness<S>, A> fa) {
    Validation.function().require(f, "f", MAP);
    Validation.kind().requireNonNull(fa, MAP);
    StateOp<S, A> op = StateOpKindHelper.STATE_OP.narrow(fa);
    return StateOpKindHelper.STATE_OP.widen(op.mapK(f));
  }
}
