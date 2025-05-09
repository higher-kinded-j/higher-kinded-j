package org.higherkindedj.hkt.state;

import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents the result of a State computation: a final value and a final state. Likely defined
 * within State.java or a related model file.
 *
 * @param <S> The state type.
 * @param <A> The value type.
 */
public record StateTuple<S, A>(@Nullable A value, @NonNull S state) {

  /** Compact constructor for validation. Ensures state is never null. */
  public StateTuple {
    Objects.requireNonNull(state, "Final state cannot be null");
  }

  /**
   * Static factory method to create a StateTuple instance.
   *
   * @param state The final state.
   * @param value The final value.
   * @param <S> The state type.
   * @param <A> The value type.
   * @return A new StateTuple instance.
   */
  public static <S, A> StateTuple<S, A> of(@NonNull S state, @Nullable A value) {
    return new StateTuple<>(value, state);
  }
}
