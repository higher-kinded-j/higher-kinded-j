// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import org.higherkindedj.hkt.unit.Unit;
import org.jspecify.annotations.Nullable;

/**
 * Represents a stateful computation {@code S -> (A, S)}. It wraps a function that takes an initial
 * state and returns a pair containing the computed value and the final state.
 *
 * @param <S> The type of the state. This type is expected to be non-null.
 * @param <A> The type of the computed value. This can be nullable.
 */
@FunctionalInterface
public interface State<S, A> {

  /**
   * Runs the state computation with the given initial state.
   *
   * @param initialState The non-null initial state.
   * @return A non-null {@link StateTuple} containing the computed value and the final state.
   */
  StateTuple<S, A> run(S initialState);

  /**
   * Creates a {@code State} instance from a function that performs the state transition. The
   * provided function takes an initial state {@code S} and returns a {@link StateTuple} containing
   * the computed value {@code A} and the new state {@code S}.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param runFunction The non-null function representing the stateful computation, mapping an
   *     initial state to a {@link StateTuple}.
   * @return A non-null {@code State<S, A>} instance.
   * @throws NullPointerException if {@code runFunction} is null.
   */
  static <S, A> State<S, A> of(Function<S, StateTuple<S, A>> runFunction) {
    requireNonNull(runFunction, "runFunction cannot be null");
    return runFunction::apply;
  }

  /**
   * Maps the result value of this stateful computation from type {@code A} to type {@code B}, while
   * preserving the state transition.
   *
   * <p>This operation is equivalent to:
   *
   * <pre>{@code
   * s -> {
   * StateTuple<S, A> result = this.run(s);
   * return new StateTuple<>(f.apply(result.value()), result.state());
   * }
   * }</pre>
   *
   * @param <B> The type of the new computed value.
   * @param f The non-null function to apply to the current computed value.
   * @return A new non-null {@code State<S, B>} instance with the mapped value and original state
   *     transition.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> State<S, B> map(Function<? super A, ? extends B> f) {
    requireNonNull(f, "mapper function cannot be null");
    return State.of(
        initialState -> {
          StateTuple<S, A> result = this.run(initialState);
          // Apply f to the value, keep the resulting state
          return new StateTuple<>(f.apply(result.value()), result.state());
        });
  }

  /**
   * Composes this {@code State} computation with a function that takes the result of this
   * computation (type {@code A}) and returns a new {@code State<S, B>} computation. The state is
   * threaded through both computations.
   *
   * <p>This operation is equivalent to:
   *
   * <pre>{@code
   * s0 -> {
   * StateTuple<S, A> result1 = this.run(s0); // (a, s1)
   * State<S, ? extends B> nextStateComputation = f.apply(result1.value());
   * return nextStateComputation.run(result1.state()); // (b, s2)
   * }
   * }</pre>
   *
   * @param <B> The type of the computed value from the next state computation.
   * @param f The non-null function that takes the result of the current computation and returns the
   *     next {@code State} computation. This function must not return null.
   * @return A new non-null {@code State<S, B>} instance representing the composed computation.
   * @throws NullPointerException if {@code f} is null or if the {@code State} returned by {@code f}
   *     is null.
   */
  default <B> State<S, B> flatMap(Function<? super A, ? extends State<S, ? extends B>> f) {
    requireNonNull(f, "flatMap mapper function cannot be null");
    return State.of(
        initialState -> {
          // Run the first state computation
          StateTuple<S, A> result1 = this.run(initialState);
          A valueA = result1.value();
          S stateS1 = result1.state();

          // Apply f to the value to get the next state computation
          State<S, ? extends B> nextState = f.apply(valueA);
          requireNonNull(nextState, "flatMap function returned null State instance");

          // Run the next state computation with the intermediate state S1
          StateTuple<S, ? extends B> finalResultTuple = nextState.run(stateS1);

          B finalValue = (B) finalResultTuple.value();
          S finalState = finalResultTuple.state();

          return new StateTuple<>(finalValue, finalState);
        });
  }

  // --- Static Helper Methods ---

  /**
   * Creates a {@code State} computation that returns the given value as its result and leaves the
   * state unchanged. This is the 'pure' or 'unit' operation for the State monad.
   *
   * <p>Equivalent to: {@code s -> new StateTuple<>(value, s)}
   *
   * @param <S> The type of the state.
   * @param <A> The type of the value to lift into the State context.
   * @param value The value to be returned by the State computation. Can be {@code null}.
   * @return A non-null {@code State<S, A>} that always returns the given value and original state.
   */
  static <S, A> State<S, A> pure(@Nullable A value) {
    return State.of(s -> new StateTuple<>(value, s));
  }

  /**
   * Creates a {@code State} computation that returns the current state as its result and leaves the
   * state unchanged.
   *
   * <p>Equivalent to: {@code s -> new StateTuple<>(s, s)}
   *
   * @param <S> The type of the state.
   * @return A non-null {@code State<S, S>} that returns the current state as its value.
   */
  static <S> State<S, S> get() {
    return State.of(s -> new StateTuple<>(s, s));
  }

  /**
   * Creates a {@code State} computation that replaces the current state with the given new state
   * and returns {@link Unit#INSTANCE}.
   *
   * <p>Equivalent to: {@code s -> new StateTuple<>(Unit.INSTANCE, newState)}
   *
   * @param <S> The type of the state.
   * @param newState The non-null new state to set.
   * @return A non-null {@code State<S, Unit>} that sets the state and returns {@link
   *     Unit#INSTANCE}.
   * @throws NullPointerException if {@code newState} is null.
   */
  static <S> State<S, Unit> set(S newState) {
    requireNonNull(newState, "newState cannot be null");
    // The old state `s` is ignored here, as `newState` replaces it.
    return State.of(s -> new StateTuple<>(Unit.INSTANCE, newState));
  }

  /**
   * Creates a {@code State} computation that modifies the current state using the given function
   * and returns {@link Unit#INSTANCE}.
   *
   * <p>Equivalent to: {@code s -> new StateTuple<>(Unit.INSTANCE, f.apply(s))}
   *
   * @param <S> The type of the state.
   * @param f The non-null function to apply to the current state to produce the new state. This
   *     function must return a non-null state.
   * @return A non-null {@code State<S, Unit>} that modifies the state and returns {@link
   *     Unit#INSTANCE}.
   * @throws NullPointerException if {@code f} is null.
   */
  static <S> State<S, Unit> modify(Function<S, S> f) {
    requireNonNull(f, "state modification function cannot be null");
    return State.of(s -> new StateTuple<>(Unit.INSTANCE, f.apply(s)));
  }

  /**
   * Creates a {@code State} computation that inspects the current state using a function, returns
   * the result of that function as its value, and leaves the state unchanged.
   *
   * <p>Equivalent to: {@code s -> new StateTuple<>(f.apply(s), s)}
   *
   * @param <S> The type of the state.
   * @param <A> The type of the value returned by the inspection function.
   * @param f The non-null function to apply to the current state to produce a value. This function
   *     can return a {@code null} value if {@code A} is nullable.
   * @return A non-null {@code State<S, A>} that returns the result of inspecting the state.
   * @throws NullPointerException if {@code f} is null.
   */
  static <S, A> State<S, A> inspect(Function<S, @Nullable A> f) {
    requireNonNull(f, "state inspection function cannot be null");
    return State.of(s -> new StateTuple<>(f.apply(s), s));
  }
}
