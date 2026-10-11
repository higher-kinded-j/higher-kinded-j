// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.validation.Operation;
import org.higherkindedj.hkt.util.validation.Validation;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A record representing the result of a stateful computation, typically used with the {@link State}
 * monad or {@link org.higherkindedj.hkt.state_t.StateT} monad transformer.
 *
 * <p>A stateful computation, when run, produces a final computed value along with a new (or
 * updated) state. This {@code StateTuple} encapsulates these two pieces of information: the
 * resulting value and the final state.
 *
 * <p>Both components are guaranteed to be non-null, as verified by the constructor. A computation
 * with nothing to return gives {@link Unit#INSTANCE} as its value.
 *
 * <p>For example, if a {@code State<S, A>} computation is run with an initial state {@code s0}, it
 * might produce a {@code StateTuple<S, A>(resultValue, s1)}, where {@code resultValue} is the
 * outcome of type {@code A}, and {@code s1} is the new state of type {@code S}.
 *
 * @param <S> The type of the state. This state is guaranteed to be non-null.
 * @param <A> The type of the computed value. This value is guaranteed to be non-null; if {@code A}
 *     is {@link Unit}, it is {@link Unit#INSTANCE}.
 * @param value The computed value resulting from the stateful operation. Must not be {@code null}.
 * @param state The final state after the stateful operation has been run. Must not be {@code null}.
 * @see State
 * @see org.higherkindedj.hkt.state_t.StateT
 * @see Unit
 */
@GenerateLenses
public record StateTuple<S, A>(A value, S state) {

  /**
   * Compact constructor for {@link StateTuple}. Ensures that neither component is {@code null}.
   *
   * @param value The computed value, must not be {@code null}. If {@code A} is {@link Unit}, this
   *     must be {@link Unit#INSTANCE}.
   * @param state The final state, must not be {@code null}.
   * @throws NullPointerException if {@code value} or {@code state} is {@code null}.
   */
  public StateTuple {
    Validation.coreType().requireValue(value, StateTuple.class, Operation.CONSTRUCTION);
    Validation.coreType().requireValue(state, "state", StateTuple.class, Operation.CONSTRUCTION);
  }

  /**
   * Static factory method to create a {@code StateTuple} instance.
   *
   * <p>This provides an alternative way to construct a {@code StateTuple}, potentially offering
   * better type inference in some contexts or a more fluent API style. The order of parameters is
   * (state, value) which might differ from the record's canonical constructor (value, state).
   *
   * @param state The final state. Must not be {@code null}.
   * @param value The final computed value. Must not be {@code null}. If {@code A} is {@link Unit},
   *     this should be {@link Unit#INSTANCE}.
   * @param <S> The type of the state.
   * @param <A> The type of the value.
   * @return A new, non-null {@link StateTuple} instance.
   * @throws NullPointerException if {@code state} or {@code value} is {@code null}.
   */
  public static <S, A> StateTuple<S, A> of(S state, A value) {
    Validation.coreType().requireValue(state, "state", StateTuple.class, Operation.OF);
    Validation.coreType().requireValue(value, StateTuple.class, Operation.OF);
    return new StateTuple<>(value, state);
  }
}
