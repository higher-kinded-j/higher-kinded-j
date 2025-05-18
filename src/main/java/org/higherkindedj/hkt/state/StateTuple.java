// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A record representing the result of a stateful computation, typically used with the {@link State}
 * monad or {@link org.higherkindedj.hkt.trans.state_t.StateT} monad transformer.
 *
 * <p>A stateful computation, when run, produces a final computed value along with a new (or
 * updated) state. This {@code StateTuple} encapsulates these two pieces of information: the
 * resulting value and the final state.
 *
 * <p>The state component {@code S} is guaranteed to be non-null, as verified by the constructor.
 * The value component {@code A} can be {@code null} depending on the nature of the computation.
 *
 * <p>For example, if a {@code State<S, A>} computation is run with an initial state {@code s0}, it
 * might produce a {@code StateTuple<S, A>(resultValue, s1)}, where {@code resultValue} is the
 * outcome of type {@code A}, and {@code s1} is the new state of type {@code S}.
 *
 * @param <S> The type of the state. This state is guaranteed to be non-null.
 * @param <A> The type of the computed value. This value can be {@code null}.
 * @param value The computed value resulting from the stateful operation. May be {@code null}.
 * @param state The final state after the stateful operation has been run. Must not be {@code null}.
 * @see State
 * @see org.higherkindedj.hkt.trans.state_t.StateT
 */
public record StateTuple<S, A>(@Nullable A value, @NonNull S state) {

  /**
   * Compact constructor for {@link StateTuple}. Ensures that the {@code state} component is never
   * {@code null}. The {@code value} component can be {@code null}.
   *
   * @param value The computed value, may be {@code null}.
   * @param state The final state, must not be {@code null}.
   * @throws NullPointerException if {@code state} is {@code null}.
   */
  public StateTuple {
    Objects.requireNonNull(state, "Final state (S) in StateTuple cannot be null.");
  }

  /**
   * Static factory method to create a {@code StateTuple} instance.
   *
   * <p>This provides an alternative way to construct a {@code StateTuple}, potentially offering
   * better type inference in some contexts or a more fluent API style. The order of parameters is
   * (state, value) which might differ from the record's canonical constructor (value, state).
   *
   * @param state The final state. Must not be {@code null}.
   * @param value The final computed value. May be {@code null}.
   * @param <S> The type of the state.
   * @param <A> The type of the value.
   * @return A new, non-null {@link StateTuple} instance.
   * @throws NullPointerException if {@code state} is {@code null}.
   */
  public static <S, A> @NonNull StateTuple<S, A> of(@NonNull S state, @Nullable A value) {
    return new StateTuple<>(value, state);
  }
}
