// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.Operation;
import org.higherkindedj.hkt.util.validation.Validation;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.jspecify.annotations.Nullable;

/**
 * A record representing the result of a stateful computation, typically used with the {@link State}
 * monad or {@link org.higherkindedj.hkt.state_t.StateT} monad transformer.
 *
 * <p>A stateful computation, when run, produces a final computed value along with a new (or
 * updated) state. This {@code StateTuple} encapsulates these two pieces of information: the
 * resulting value and the final state.
 *
 * <p>The state component {@code S} is guaranteed to be non-null, as verified by the constructor.
 * The value component {@code A} can be {@code null} if the type {@code A} itself permits null
 * values (e.g., {@code String}). If {@code A} is {@link Unit}, the value will be {@link
 * Unit#INSTANCE}.
 *
 * <p>For example, if a {@code State<S, A>} computation is run with an initial state {@code s0}, it
 * might produce a {@code StateTuple<S, A>(resultValue, s1)}, where {@code resultValue} is the
 * outcome of type {@code A}, and {@code s1} is the new state of type {@code S}.
 *
 * @param <S> The type of the state. This state is guaranteed to be non-null.
 * @param <A> The type of the computed value. This value can be {@code null} if the type {@code A}
 *     supports it (e.g. {@code String}); if {@code A} is {@link Unit}, this will be {@link
 *     Unit#INSTANCE}.
 * @param value The computed value resulting from the stateful operation. May be {@code null} (e.g.,
 *     if {@code A} is {@code String}); if {@code A} is {@link Unit}, this will be {@link
 *     Unit#INSTANCE}.
 * @param state The final state after the stateful operation has been run. Must not be {@code null}.
 * @see State
 * @see org.higherkindedj.hkt.state_t.StateT
 * @see Unit
 */
@GenerateLenses
public record StateTuple<S, A>(@Nullable A value, S state) {

  /**
   * Compact constructor for {@link StateTuple}. Ensures that the {@code state} component is never
   * {@code null}. The {@code value} component can be {@code null} if type {@code A} allows it.
   *
   * @param value The computed value, may be {@code null} (if {@code A} is a nullable type like
   *     {@code String}). If {@code A} is {@link Unit}, this must be {@link Unit#INSTANCE}.
   * @param state The final state, must not be {@code null}.
   * @throws NullPointerException if {@code state} is {@code null}.
   */
  public StateTuple {
    Validation.coreType().requireValue(state, StateTuple.class, Operation.CONSTRUCTION);
  }

  /**
   * Static factory method to create a {@code StateTuple} instance.
   *
   * <p>This provides an alternative way to construct a {@code StateTuple}, potentially offering
   * better type inference in some contexts or a more fluent API style. The order of parameters is
   * (state, value) which might differ from the record's canonical constructor (value, state).
   *
   * @param state The final state. Must not be {@code null}.
   * @param value The final computed value. May be {@code null} (if {@code A} is a nullable type
   *     like {@code String}). If {@code A} is {@link Unit}, this should be {@link Unit#INSTANCE}.
   * @param <S> The type of the state.
   * @param <A> The type of the value.
   * @return A new, non-null {@link StateTuple} instance.
   * @throws NullPointerException if {@code state} is {@code null}.
   */
  public static <S, A> StateTuple<S, A> of(S state, @Nullable A value) {
    Validation.coreType().requireValue(state, StateTuple.class, Operation.OF);
    return new StateTuple<>(value, state);
  }
}
