package org.higherkindedj.hkt.state;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for working with the {@link State} monad in the context of Higher-Kinded Types
 * (HKT) simulation.
 *
 * <p>This class provides essential static methods to:
 *
 * <ul>
 *   <li>Wrap concrete {@link State}{@code <S, A>} instances into their HKT representation, {@link
 *       StateKind}{@code <S, A>}.
 *   <li>Unwrap {@link StateKind}{@code <S, A>} (which is a {@link Kind}&lt;{@link
 *       StateKind.Witness}, A&gt;) back to the concrete {@link State}{@code <S, A>} type.
 *   <li>Offer factory methods for creating {@link StateKind} instances that represent common {@code
 *       State} operations like {@code pure}, {@code get}, {@code set}, {@code modify}, and {@code
 *       inspect}.
 *   <li>Provide methods to run {@code StateKind} computations ({@code runState}, {@code evalState},
 *       {@code execState}).
 * </ul>
 *
 * The HKT marker (witness type) used for {@code State} is {@link StateKind.Witness}. This class is
 * final and cannot be instantiated.
 *
 * @see State
 * @see StateKind
 * @see StateKind.Witness
 * @see Kind
 */
public final class StateKindHelper {

  // Error Messages
  /** Error message for when a null {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for State";

  /**
   * Error message prefix for when the {@link Kind} instance is not the expected {@link StateHolder}
   * type.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a StateHolder: ";

  /**
   * Error message for when a {@link StateHolder} internally contains a null {@link State} instance.
   */
  public static final String INVALID_HOLDER_STATE_MSG = "StateHolder contained null State instance";

  private StateKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * An internal record that implements {@link StateKind}{@code <S, A>} to hold the concrete {@link
   * State}{@code <S, A>} instance.
   *
   * <p>This serves as the carrier for {@code State} objects within the HKT simulation. By
   * implementing {@code StateKind<S, A>}, it also conforms to {@code Kind<StateKind.Witness, A>},
   * allowing it to be used with generic HKT abstractions like {@link org.higherkindedj.hkt.Functor}
   * or {@link org.higherkindedj.hkt.Monad}.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param stateInstance The non-null, actual {@link State}{@code <S, A>} instance being wrapped.
   */
  record StateHolder<S, A>(@NonNull State<S, A> stateInstance) implements StateKind<S, A> {}

  /**
   * Unwraps a {@link Kind}&lt;{@link StateKind.Witness}, A&gt; (which is effectively a {@link
   * StateKind}&lt;S, A&gt;) back to its concrete {@link State}{@code <S, A>} type.
   *
   * <p>The type parameter {@code S} (state type) is typically inferred from the context in which
   * the resulting {@code State<S, A>} is used.
   *
   * @param <S> The type of the state. This is often inferred.
   * @param <A> The type of the computed value associated with the {@code Kind}.
   * @param kind The {@code Kind<StateKind.Witness, A>} instance to unwrap. May be {@code null}, in
   *     which case an exception is thrown.
   * @return The underlying, non-null {@link State}{@code <S, A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, is not an instance of
   *     {@link StateHolder}, or if the holder's internal {@code State} instance is {@code null}
   *     (which indicates an invalid state).
   */
  @SuppressWarnings(
      "unchecked") // Necessary for casting to State<S, A> due to S not being part of Kind's
  // signature for State.
  public static <S, A> @NonNull State<S, A> unwrap(@Nullable Kind<StateKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(StateKindHelper.INVALID_KIND_NULL_MSG);
      case StateHolder<?, A> holder -> (State<S, A>) holder.stateInstance();
      default ->
          throw new KindUnwrapException(
              StateKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link State}{@code <S, A>} instance into its higher-kinded representation,
   * {@link StateKind}{@code <S, A>}.
   *
   * <p>The returned {@code StateKind<S, A>} also implements {@code Kind<StateKind.Witness, A>},
   * making it usable in generic HKT contexts.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param state The non-null, concrete {@link State}{@code <S, A>} instance to wrap.
   * @return A non-null {@link StateKind}{@code <S, A>} representing the wrapped {@code State}
   *     computation.
   * @throws NullPointerException if {@code state} is {@code null}.
   */
  public static <S, A> @NonNull StateKind<S, A> wrap(@NonNull State<S, A> state) {
    Objects.requireNonNull(state, "Input State cannot be null for wrap");
    return new StateHolder<>(state);
  }

  /**
   * Lifts a pure value {@code A} into a {@link StateKind}{@code <S, A>} context. The resulting
   * {@code State} computation, when run, will return the given {@code value} and leave the state
   * {@code S} unchanged.
   *
   * <p>This is equivalent to {@code State.pure(value)} followed by {@link #wrap(State)}.
   *
   * @param <S> The type of the state, which remains unaffected by this computation.
   * @param <A> The type of the pure value being lifted.
   * @param value The value to lift into the {@code State} context. May be {@code null} if {@code A}
   *     is a nullable type.
   * @return A non-null {@link StateKind}{@code <S, A>} representing the pure computation.
   */
  public static <S, A> @NonNull StateKind<S, A> pure(@Nullable A value) {
    return wrap(State.pure(value));
  }

  /**
   * Creates a {@link StateKind}{@code <S, S>} that, when run, returns the current state {@code S}
   * as its computed value and leaves the state unchanged.
   *
   * <p>This is equivalent to {@code State.get()} followed by {@link #wrap(State)}.
   *
   * @param <S> The type of the state, which is also the type of the value returned by this
   *     computation.
   * @return A non-null {@link StateKind}{@code <S, S>} that yields the current state.
   */
  public static <S> @NonNull StateKind<S, S> get() {
    return wrap(State.get());
  }

  /**
   * Creates a {@link StateKind}{@code <S, Void>} that, when run, replaces the current state with
   * {@code newState} and returns {@link Void} (represented as {@code null}) as its computed value.
   *
   * <p>This is equivalent to {@code State.set(newState)} followed by {@link #wrap(State)}.
   *
   * @param <S> The type of the state.
   * @param newState The non-null new state to be set.
   * @return A non-null {@link StateKind}{@code <S, Void>} that performs the state update.
   * @throws NullPointerException if {@code newState} is {@code null}.
   */
  public static <S> @NonNull StateKind<S, Void> set(@NonNull S newState) {
    // State.set itself will handle the NullPointerException for newState.
    return wrap(State.set(newState));
  }

  /**
   * Creates a {@link StateKind}{@code <S, Void>} that, when run, modifies the current state {@code
   * S} using the provided function {@code f}, and returns {@link Void} (represented as {@code
   * null}) as its computed value.
   *
   * <p>This is equivalent to {@code State.modify(f)} followed by {@link #wrap(State)}.
   *
   * @param <S> The type of the state.
   * @param f The non-null function to transform the current state. It must take a non-null state
   *     and return a non-null new state.
   * @return A non-null {@link StateKind}{@code <S, Void>} that performs the state modification.
   * @throws NullPointerException if {@code f} is {@code null}.
   */
  public static <S> @NonNull StateKind<S, Void> modify(
      @NonNull Function<@NonNull S, @NonNull S> f) {
    // State.modify itself will handle the NullPointerException for f.
    return wrap(State.modify(f));
  }

  /**
   * Creates a {@link StateKind}{@code <S, A>} that, when run, inspects the current state {@code S}
   * using the function {@code f}, returns the result of {@code f.apply(currentState)} as its
   * computed value, and leaves the state unchanged.
   *
   * <p>This is equivalent to {@code State.inspect(f)} followed by {@link #wrap(State)}.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the value produced by inspecting the state.
   * @param f The non-null function to apply to the current state to produce a value. This function
   *     takes a non-null state and can return a {@code @Nullable A} if {@code A} is a nullable
   *     type.
   * @return A non-null {@link StateKind}{@code <S, A>} that returns the result of inspecting the
   *     state.
   * @throws NullPointerException if {@code f} is {@code null}.
   */
  public static <S, A> @NonNull StateKind<S, A> inspect(
      @NonNull Function<@NonNull S, @Nullable A> f) {
    // State.inspect itself will handle the NullPointerException for f.
    return wrap(State.inspect(f));
  }

  /**
   * Runs the {@link State}{@code <S, A>} computation held within the {@code Kind} wrapper, starting
   * with the given {@code initialState}.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<StateKind.Witness, A>} (effectively a {@link StateKind}{@code <S,
   *     A>}) holding the {@code State} computation. May be {@code null}.
   * @param initialState The non-null initial state to run the computation with.
   * @return A non-null {@link State.StateTuple}{@code <S, A>} containing both the final computed
   *     value and the final state.
   * @throws KindUnwrapException if {@code kind} is invalid (e.g., {@code null}, or not a valid
   *     representation of a {@code State}).
   * @throws NullPointerException if {@code initialState} is {@code null}.
   */
  public static <S, A> State.@NonNull StateTuple<S, A> runState(
      @Nullable Kind<StateKind.Witness, A> kind, @NonNull S initialState) {
    Objects.requireNonNull(initialState, "Initial state cannot be null for runState");
    State<S, A> stateToRun = unwrap(kind); // unwrap handles null kind
    // The State<S,A>.run method returns StateTuple<S,A>, which aligns with the expected @NonNull
    // contract.
    // No cast needed for the result of stateToRun.run() if State.run() is correctly typed.
    // The original cast `(State.StateTuple<S, A>)` might have been due to State.run() returning raw
    // Function<S, StateTuple>.
    // Assuming State.run() returns StateTuple<S,A>.
    return stateToRun.run(initialState);
  }

  /**
   * Evaluates the {@link State}{@code <S, A>} computation held within the {@code Kind} wrapper,
   * starting with {@code initialState}, and returns only the final computed value {@code A}. The
   * final state is discarded.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<StateKind.Witness, A>} (effectively a {@link StateKind}{@code <S,
   *     A>}) holding the {@code State} computation. May be {@code null}.
   * @param initialState The non-null initial state to run the computation with.
   * @return The final computed value from the {@code State} computation. This can be {@code null}
   *     if {@code A} is a nullable type.
   * @throws KindUnwrapException if {@code kind} is invalid.
   * @throws NullPointerException if {@code initialState} is {@code null}.
   */
  public static <S, A> @Nullable A evalState(
      @Nullable Kind<StateKind.Witness, A> kind, @NonNull S initialState) {
    return runState(kind, initialState).value();
  }

  /**
   * Executes the {@link State}{@code <S, A>} computation held within the {@code Kind} wrapper,
   * starting with {@code initialState}, and returns only the final state {@code S}. The final
   * computed value is discarded.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value (which is ignored in the result).
   * @param kind The {@code Kind<StateKind.Witness, A>} (effectively a {@link StateKind}{@code <S,
   *     A>}) holding the {@code State} computation. May be {@code null}.
   * @param initialState The non-null initial state to run the computation with.
   * @return The non-null final state from the {@code State} computation.
   * @throws KindUnwrapException if {@code kind} is invalid.
   * @throws NullPointerException if {@code initialState} is {@code null}.
   */
  public static <S, A> @NonNull S execState(
      @Nullable Kind<StateKind.Witness, A> kind, @NonNull S initialState) {
    return runState(kind, initialState).state();
  }
}
