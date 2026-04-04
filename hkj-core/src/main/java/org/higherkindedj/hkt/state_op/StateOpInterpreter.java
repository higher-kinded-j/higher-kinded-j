// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_op;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;
import static org.higherkindedj.hkt.util.validation.Operation.FROM_KIND;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Interprets {@link StateOp} into the {@link State} monad.
 *
 * <p>Each optic-parameterised operation is interpreted by delegating to {@link
 * StateOp#interpretState(Object)}, which each record implements with full type knowledge of its
 * internal parameters. This avoids raw-type pattern matching and wildcard capture issues.
 *
 * <p>Usage with {@code Free.foldMap}:
 *
 * <pre>{@code
 * StateOpInterpreter<MyState> interpreter = new StateOpInterpreter<>();
 * StateMonad<MyState> monad = new StateMonad<>();
 * Kind<StateKind.Witness<MyState>, Result> result =
 *     program.foldMap(interpreter, monad);
 * StateTuple<MyState, Result> tuple =
 *     StateKindHelper.STATE.runState(result, initialState);
 * }</pre>
 *
 * @param <S> The state type
 */
@NullMarked
public class StateOpInterpreter<S>
    implements Natural<StateOpKind.Witness<S>, StateKind.Witness<S>> {

  @Override
  public <A> Kind<StateKind.Witness<S>, A> apply(Kind<StateOpKind.Witness<S>, A> fa) {
    Validation.kind().requireNonNull(fa, FROM_KIND);
    StateOp<S, A> op = StateOpKindHelper.STATE_OP.narrow(fa);
    State<S, A> state = op::interpretState;
    return STATE.widen(state);
  }
}
