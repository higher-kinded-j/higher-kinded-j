// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_op;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;
import static org.higherkindedj.hkt.util.validation.Operation.FROM_KIND;

import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Interprets {@link StateOp} into the {@link IO} monad using an {@link AtomicReference} for
 * thread-safe mutable state.
 *
 * <p>Each operation is interpreted by delegating to {@link StateOp#interpretState(Object)}, then
 * updating the {@code AtomicReference} with the new state.
 *
 * <p>Usage with {@code Free.foldMap}:
 *
 * <pre>{@code
 * AtomicReference<MyState> stateRef = new AtomicReference<>(initialState);
 * IOStateOpInterpreter<MyState> interpreter = new IOStateOpInterpreter<>(stateRef);
 * IOMonad ioMonad = IOMonad.INSTANCE;
 * Kind<IOKind.Witness, Result> result = program.foldMap(interpreter, ioMonad);
 * Result value = IO_OP.narrow(result).unsafeRunSync();
 * MyState finalState = stateRef.get();
 * }</pre>
 *
 * @param <S> The state type
 */
@NullMarked
public class IOStateOpInterpreter<S> implements Natural<StateOpKind.Witness<S>, IOKind.Witness> {

  private final AtomicReference<S> stateRef;

  /**
   * Creates an interpreter backed by the given AtomicReference.
   *
   * @param stateRef The mutable state reference. Must not be null.
   */
  public IOStateOpInterpreter(AtomicReference<S> stateRef) {
    this.stateRef = Validation.function().require(stateRef, "stateRef", CONSTRUCTION);
  }

  @Override
  public <A> Kind<IOKind.Witness, A> apply(Kind<StateOpKind.Witness<S>, A> fa) {
    Validation.kind().requireNonNull(fa, FROM_KIND);
    StateOp<S, A> op = StateOpKindHelper.STATE_OP.narrow(fa);
    IO<A> io =
        IO.delay(
            () -> {
              StateTuple<S, A> result = op.interpretState(stateRef.get());
              stateRef.set(result.state());
              return result.value();
            });
    return IO_OP.widen(io);
  }
}
