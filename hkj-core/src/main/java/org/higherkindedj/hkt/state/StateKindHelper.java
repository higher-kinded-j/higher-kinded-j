// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.unit.Unit;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link StateConverterOps} for widen/narrow operations, and providing additional
 * factory and utility instance methods for {@link State} types.
 *
 * <p>Access these operations via the singleton {@code STATE}. For example: {@code
 * StateKindHelper.STATE.widen(State.pure("value"));}
 */
public enum StateKindHelper implements StateConverterOps {
  STATE;

  // Error Messages
  /** Error message for when a null {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for State";

  /**
   * Error message prefix for when the {@link Kind} instance is not the expected {@link StateHolder}
   * type.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a StateHolder: ";

  /**
   * Error message for when a {@link StateHolder} internally contains a null {@link State} instance.
   * This should not be reachable if widen ensures non-null State instances are wrapped.
   */
  public static final String INVALID_HOLDER_STATE_MSG = "StateHolder contained null State instance";

  /**
   * An internal record that implements {@link StateKind}{@code <S, A>} to hold the concrete {@link
   * State}{@code <S, A>} instance. Changed to package-private for potential test access.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param stateInstance The non-null, actual {@link State}{@code <S, A>} instance being wrapped.
   */
  record StateHolder<S, A>(State<S, A> stateInstance) implements StateKind<S, A> {}

  /**
   * Widens a concrete {@link State}{@code <S, A>} instance into its higher-kinded representation,
   * {@link Kind<StateKind.Witness<S>, A>}. Implements {@link StateConverterOps#widen}.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param state The non-null, concrete {@link State}{@code <S, A>} instance to widen.
   * @return A non-null {@link Kind<StateKind.Witness<S>, A>} representing the wrapped {@code State}
   *     computation.
   * @throws NullPointerException if {@code state} is {@code null}.
   */
  @Override
  public <S, A> Kind<StateKind.Witness<S>, A> widen(State<S, A> state) {
    Objects.requireNonNull(state, "Input State cannot be null for widen");
    return new StateHolder<>(state);
  }

  /**
   * Narrows a {@link Kind}&lt;{@link StateKind.Witness}, A&gt; (which is effectively a {@link
   * StateKind}&lt;S, A&gt;) back to its concrete {@link State}{@code <S, A>} type. Implements
   * {@link StateConverterOps#narrow}.
   *
   * @param <S> The type of the state. This is often inferred.
   * @param <A> The type of the computed value associated with the {@code Kind}.
   * @param kind The {@code Kind<StateKind.Witness<S>, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link State}{@code <S, A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, or not an instance of
   *     {@link StateHolder}. The {@code StateHolder} guarantees its internal {@code stateInstance}
   *     is non-null.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <S, A> State<S, A> narrow(@Nullable Kind<StateKind.Witness<S>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(StateKindHelper.INVALID_KIND_NULL_MSG);
      case StateKindHelper.StateHolder<?, A> holder -> (State<S, A>) holder.stateInstance();
      default ->
          throw new KindUnwrapException(
              StateKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Lifts a pure value {@code A} into a {@link Kind}{@code <StateKind.Witness<S>, A>} context.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the pure value being lifted.
   * @param value The value to lift.
   * @return A non-null {@link Kind<StateKind.Witness<S>, A>} representing the pure computation.
   */
  public <S, A> Kind<StateKind.Witness<S>, A> pure(@Nullable A value) {
    return this.widen(State.pure(value));
  }

  /**
   * Creates a {@link Kind}{@code <StateKind.Witness<S>, S>} that yields the current state.
   *
   * @param <S> The type of the state.
   * @return A non-null {@link Kind<StateKind.Witness<S>, S>} that yields the current state.
   */
  public <S> Kind<StateKind.Witness<S>, S> get() {
    return this.widen(State.get());
  }

  /**
   * Creates a {@link Kind}{@code <StateKind.Witness<S>, Unit>} that replaces the current state.
   *
   * @param <S> The type of the state.
   * @param newState The non-null new state to be set.
   * @return A non-null {@link Kind<StateKind.Witness<S>, Unit>} that performs the state update.
   */
  public <S> Kind<StateKind.Witness<S>, Unit> set(S newState) {
    return this.widen(State.set(newState));
  }

  /**
   * Creates a {@link Kind}{@code <StateKind.Witness<S>, Unit>} that modifies the current state.
   *
   * @param <S> The type of the state.
   * @param f The non-null function to transform the current state.
   * @return A non-null {@link Kind<StateKind.Witness<S>, Unit>} that performs the state
   *     modification.
   */
  public <S> Kind<StateKind.Witness<S>, Unit> modify(Function<S, S> f) {
    return this.widen(State.modify(f));
  }

  /**
   * Creates a {@link Kind}{@code <StateKind.Witness<S>, A>} that inspects the current state.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the value produced by inspecting the state.
   * @param f The non-null function to apply to the current state.
   * @return A non-null {@link Kind<StateKind.Witness<S>, A>} that returns the result of inspecting
   *     the state.
   */
  public <S, A> Kind<StateKind.Witness<S>, A> inspect(Function<S, @Nullable A> f) {
    return this.widen(State.inspect(f));
  }

  /**
   * Runs the {@link State}{@code <S, A>} computation held within the {@code Kind} wrapper.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<StateKind.Witness<S>, A>} holding the {@code State} computation.
   * @param initialState The non-null initial state.
   * @return A non-null {@link StateTuple}{@code <S, A>}.
   * @throws KindUnwrapException if {@code kind} is invalid.
   * @throws NullPointerException if {@code initialState} is {@code null}.
   */
  public <S, A> StateTuple<S, A> runState(
      @Nullable Kind<StateKind.Witness<S>, A> kind, S initialState) {
    Objects.requireNonNull(initialState, "Initial state cannot be null for runState");
    return this.narrow(kind).run(initialState);
  }

  /**
   * Evaluates the {@link State}{@code <S, A>} computation and returns only the final value.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<StateKind.Witness<S>, A>} holding the {@code State} computation.
   * @param initialState The non-null initial state.
   * @return The final computed value.
   * @throws KindUnwrapException if {@code kind} is invalid.
   * @throws NullPointerException if {@code initialState} is {@code null}.
   */
  public <S, A> @Nullable A evalState(
      @Nullable Kind<StateKind.Witness<S>, A> kind, S initialState) {
    return this.runState(kind, initialState).value();
  }

  /**
   * Executes the {@link State}{@code <S, A>} computation and returns only the final state.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<StateKind.Witness<S>, A>} holding the {@code State} computation.
   * @param initialState The non-null initial state.
   * @return The non-null final state.
   * @throws KindUnwrapException if {@code kind} is invalid.
   * @throws NullPointerException if {@code initialState} is {@code null}.
   */
  public <S, A> S execState(@Nullable Kind<StateKind.Witness<S>, A> kind, S initialState) {
    return this.runState(kind, initialState).state();
  }
}
