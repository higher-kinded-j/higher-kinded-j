package org.simulation.hkt.state;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a stateful computation S -> (A, S). It wraps a function that takes an initial state
 * and returns a pair containing the computed value and the final state.
 *
 * @param <S> The type of the state.
 * @param <A> The type of the computed value.
 */
@FunctionalInterface
public interface State<S, A> {

  /**
   * Represents the result pair of a stateful computation.
   *
   * @param <S> State type.
   * @param <A> Value type.
   * @param value The computed value (@Nullable based on A).
   * @param state The final state (@NonNull assumed).
   */
  record StateTuple<S, A>(@Nullable A value, @NonNull S state) {
    public StateTuple {
      Objects.requireNonNull(state, "Final state cannot be null");
    }
  }

  /**
   * Runs the state computation with the given initial state.
   *
   * @param initialState The initial state. (@NonNull assumed)
   * @return A tuple containing the computed value and the final state. (@NonNull)
   */
  @NonNull StateTuple<S, A> run(@NonNull S initialState);

  /** Creates a State instance from a function S -> (A, S). */
  static <S, A> @NonNull State<S, A> of(
      @NonNull Function<@NonNull S, @NonNull StateTuple<S, A>> runFunction) {
    Objects.requireNonNull(runFunction, "runFunction cannot be null");
    return runFunction::apply;
  }

  /**
   * Maps the result value A to B while keeping the state transition. map(f) is equivalent to s -> {
   * (a, s') = run(s); return (f(a), s'); }
   */
  default <B> @NonNull State<S, B> map(@NonNull Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "mapper function cannot be null");
    return State.of(
        initialState -> {
          StateTuple<S, A> result = this.run(initialState);
          // Apply f to the value, keep the resulting state
          return new StateTuple<>(f.apply(result.value()), result.state());
        });
  }

  /**
   * Composes this State computation with another function that returns a State. flatMap(f) is
   * equivalent to s0 -> { (a, s1) = run(s0); return f(a).run(s1); }
   */
  default <B> @NonNull State<S, B> flatMap(
      @NonNull Function<? super A, ? extends State<S, ? extends B>> f) {
    Objects.requireNonNull(f, "flatMap mapper function cannot be null");
    return State.of(
        initialState -> {
          // Run the first state computation
          StateTuple<S, A> result1 = this.run(initialState);
          @Nullable A valueA = result1.value();
          @NonNull S stateS1 = result1.state();

          // Apply f to the value to get the next state computation
          State<S, ? extends B> nextState = f.apply(valueA);
          Objects.requireNonNull(nextState, "flatMap function returned null State");

          // Run the next state computation with the intermediate state S1
          StateTuple<S, ? extends B> finalResultTuple = nextState.run(stateS1);

          // Explicitly extract the value and state to help the compiler infer B correctly
          // The cast is needed because the inner computation returns ? extends B,
          // but the outer flatMap context expects B.
          @SuppressWarnings("unchecked")
          B finalValue = (B) finalResultTuple.value();
          @NonNull S finalState = finalResultTuple.state();

          // Create and return the final tuple with the specific type B
          return new StateTuple<>(finalValue, finalState);
        });
  }

  // --- Static Helper Methods ---

  /**
   * Creates a State that returns the given value and leaves the state unchanged. This is the 'pure'
   * or 'of' operation for the State monad.
   */
  static <S, A> @NonNull State<S, A> pure(@Nullable A value) {
    return State.of(s -> new StateTuple<>(value, s));
  }

  /**
   * Creates a State that returns the current state as the value and leaves the state unchanged. get
   * = s -> (s, s)
   */
  static <S> @NonNull State<S, S> get() {
    return State.of(s -> new StateTuple<>(s, s));
  }

  /**
   * Creates a State that replaces the current state with the given new state and returns no value
   * (Unit). set(newState) = s -> (null, newState)
   */
  static <S> @NonNull State<S, Void> set(@NonNull S newState) {
    Objects.requireNonNull(newState, "newState cannot be null");
    return State.of(s -> new StateTuple<>(null, newState));
  }

  /**
   * Creates a State that modifies the current state using the given function and returns no value
   * (Unit). modify(f) = s -> (null, f(s))
   */
  static <S> @NonNull State<S, Void> modify(@NonNull Function<@NonNull S, @NonNull S> f) {
    Objects.requireNonNull(f, "state modification function cannot be null");
    return State.of(s -> new StateTuple<>(null, f.apply(s)));
  }

  /**
   * Creates a State that inspects the current state using a function without changing it.
   * inspect(f) = s -> (f(s), s)
   */
  static <S, A> @NonNull State<S, A> inspect(@NonNull Function<@NonNull S, @Nullable A> f) {
    Objects.requireNonNull(f, "state inspection function cannot be null");
    return State.of(s -> new StateTuple<>(f.apply(s), s));
  }
}
