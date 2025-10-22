// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.Validation;
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

  private static final Class<State> STATE_CLASS = State.class;

  /**
   * An internal record that implements {@link StateKind}{@code <S, A>} to hold the concrete {@link
   * State}{@code <S, A>} instance. Changed to package-private for potential test access.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param stateInstance The non-null, actual {@link State}{@code <S, A>} instance being wrapped.
   */
  record StateHolder<S, A>(State<S, A> stateInstance) implements StateKind<S, A> {
    /**
     * Constructs a {@code StateHolder}.
     *
     * @param stateInstance The {@link State} to hold. Must not be null.
     * @throws NullPointerException if the provided {@code stateInstance} is null.
     */
    StateHolder {
      Validation.kind().requireForWiden(stateInstance, STATE_CLASS);
    }
  }

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
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null}, or not an instance of {@link StateHolder}. The {@code StateHolder} guarantees its
   *     internal {@code stateInstance} is non-null.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <S, A> State<S, A> narrow(@Nullable Kind<StateKind.Witness<S>, A> kind) {
    return Validation.kind().narrow(kind, STATE_CLASS, this::extractState);
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
   * @throws NullPointerException if {@code newState} is null.
   */
  public <S> Kind<StateKind.Witness<S>, Unit> set(S newState) {
    // State.set already validates newState is non-null, but we can add validation here too
    return this.widen(State.set(newState));
  }

  /**
   * Creates a {@link Kind}{@code <StateKind.Witness<S>, Unit>} that modifies the current state.
   *
   * @param <S> The type of the state.
   * @param f The non-null function to transform the current state.
   * @return A non-null {@link Kind<StateKind.Witness<S>, Unit>} that performs the state
   *     modification.
   * @throws NullPointerException if {@code f} is null.
   */
  public <S> Kind<StateKind.Witness<S>, Unit> modify(Function<S, S> f) {
    Validation.function().requireFunction(f, "f", STATE_CLASS, MODIFY);
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
   * @throws NullPointerException if {@code f} is null.
   */
  public <S, A> Kind<StateKind.Witness<S>, A> inspect(Function<S, @Nullable A> f) {
    Validation.function().requireFunction(f, "f", STATE_CLASS, INSPECT);
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
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is invalid.
   * @throws NullPointerException if {@code initialState} is {@code null}.
   */
  public <S, A> StateTuple<S, A> runState(
      @Nullable Kind<StateKind.Witness<S>, A> kind, S initialState) {
    Validation.kind().requireNonNull(kind, STATE_CLASS, RUN_STATE);
    // Note: initialState validation is handled by StateTuple constructor
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
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is invalid.
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
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is invalid.
   * @throws NullPointerException if {@code initialState} is {@code null}.
   */
  public <S, A> S execState(@Nullable Kind<StateKind.Witness<S>, A> kind, S initialState) {
    return this.runState(kind, initialState).state();
  }

  /** Internal narrowing implementation that performs the actual type checking and extraction. */
  @SuppressWarnings("unchecked")
  private <S, A> State<S, A> extractState(Kind<StateKind.Witness<S>, A> kind) {
    return switch (kind) {
      case StateKindHelper.StateHolder<?, ?> holder -> (State<S, A>) holder.stateInstance();
      default -> throw new ClassCastException(); // Will be caught and wrapped by KindValidator
    };
  }
}
