package org.higherkindedj.hkt.state;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for working with {@link State} in the context of higher-kinded types (HKT). It
 * provides methods for wrapping and unwrapping {@code State} instances to and from their {@link
 * Kind} representation, using {@link StateKind.Witness} as the HKT marker. This class also offers
 * factory methods for creating {@code Kind} representations of common {@code State} operations.
 *
 * @see State
 * @see StateKind
 * @see StateKind.Witness
 */
public final class StateKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for State";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a StateHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "StateHolder contained null State instance";

  private StateKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing {@link StateKind} to hold the concrete {@link State} instance.
   * This is used by {@link #wrap(State)} and {@link #unwrap(Kind)}. By implementing {@code
   * StateKind<S, A>}, it implicitly becomes {@code Kind<StateKind.Witness, A>}.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param stateInstance The non-null, actual {@link State} instance.
   */
  record StateHolder<S, A>(@NonNull State<S, A> stateInstance) implements StateKind<S, A> {}

  /**
   * Unwraps a {@code Kind<StateKind.Witness, A>} back to its concrete {@link State}{@code <S, A>}
   * type.
   *
   * @param <S> The type of the state (inferred from the context where {@code State<S,A>} is
   *     expected).
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<StateKind.Witness, A>} instance to unwrap. Can be {@code null}.
   * @return The underlying, non-null {@code State<S, A>} instance.
   * @throws KindUnwrapException if the input {@code kind} not an instance of
   *     {@code StateHolder}, or if the holder's internal {@code State} instance is {@code null}.
   */
  @SuppressWarnings("unchecked")
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
   * {@code StateKind<S, A>} (which is also a {@code Kind<StateKind.Witness, A>}).
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param state The non-null, concrete {@code State<S, A>} instance to wrap.
   * @return A non-null {@code StateKind<S, A>} representing the wrapped {@code State} computation.
   * @throws NullPointerException if {@code state} is null.
   */
  public static <S, A> @NonNull StateKind<S, A> wrap(@NonNull State<S, A> state) {
    Objects.requireNonNull(state, "Input State cannot be null for wrap");
    return new StateHolder<>(state);
  }

  /**
   * Lifts a pure value into a {@code StateKind} context. The resulting {@code State} computation
   * will return the given value and leave the state unchanged.
   *
   * <p>This is equivalent to {@code State.pure(value)} followed by {@link #wrap(State)}. The
   * returned type {@code StateKind<S, A>} also implements {@code Kind<StateKind.Witness, A>}.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null}.
   * @return A non-null {@code StateKind<S, A>} representing the pure computation.
   */
  public static <S, A> @NonNull StateKind<S, A> pure(@Nullable A value) {
    return wrap(State.pure(value));
  }

  /**
   * Creates a {@code StateKind} that, when run, returns the current state as its value and leaves
   * the state unchanged.
   *
   * <p>This is equivalent to {@code State.get()} followed by {@link #wrap(State)}. The returned
   * type {@code StateKind<S, S>} also implements {@code Kind<StateKind.Witness, S>}.
   *
   * @param <S> The type of the state.
   * @return A non-null {@code StateKind<S, S>} that yields the current state.
   */
  public static <S> @NonNull StateKind<S, S> get() {
    return wrap(State.get());
  }

  /**
   * Creates a {@code StateKind} that, when run, replaces the current state with {@code newState}
   * and returns {@code Void} as its value.
   *
   * <p>This is equivalent to {@code State.set(newState)} followed by {@link #wrap(State)}. The
   * returned type {@code StateKind<S, Void>} also implements {@code Kind<StateKind.Witness, Void>}.
   *
   * @param <S> The type of the state.
   * @param newState The non-null new state to set.
   * @return A non-null {@code StateKind<S, Void>} that sets the state.
   * @throws NullPointerException if {@code newState} is null.
   */
  public static <S> @NonNull StateKind<S, Void> set(@NonNull S newState) {
    return wrap(State.set(newState));
  }

  /**
   * Creates a {@code StateKind} that, when run, modifies the current state using the given function
   * {@code f} and returns {@code Void} as its value.
   *
   * <p>This is equivalent to {@code State.modify(f)} followed by {@link #wrap(State)}. The returned
   * type {@code StateKind<S, Void>} also implements {@code Kind<StateKind.Witness, Void>}.
   *
   * @param <S> The type of the state.
   * @param f The non-null function to transform the current state. It must return a non-null new
   *     state.
   * @return A non-null {@code StateKind<S, Void>} that modifies the state.
   * @throws NullPointerException if {@code f} is null.
   */
  public static <S> @NonNull StateKind<S, Void> modify(
      @NonNull Function<@NonNull S, @NonNull S> f) {
    return wrap(State.modify(f));
  }

  /**
   * Creates a {@code StateKind} that, when run, inspects the current state using the function
   * {@code f}, returns the result of {@code f} as its value, and leaves the state unchanged.
   *
   * <p>This is equivalent to {@code State.inspect(f)} followed by {@link #wrap(State)}. The
   * returned type {@code StateKind<S, A>} also implements {@code Kind<StateKind.Witness, A>}.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the value returned by the inspection function.
   * @param f The non-null function to apply to the current state to produce a value. This function
   *     can return a {@code null} value if {@code A} is nullable.
   * @return A non-null {@code StateKind<S, A>} that returns the result of inspecting the state.
   * @throws NullPointerException if {@code f} is null.
   */
  public static <S, A> @NonNull StateKind<S, A> inspect(
      @NonNull Function<@NonNull S, @Nullable A> f) {
    return wrap(State.inspect(f));
  }

  /**
   * Runs the {@link State} computation held within the {@code Kind} wrapper with the given initial
   * state.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<StateKind.Witness, A>} holding the {@code State} computation. Can
   *     be null.
   * @param initialState The non-null initial state to run the computation with.
   * @return A non-null {@link State.StateTuple} containing the final value and state.
   * @throws KindUnwrapException if {@code kind} is invalid (e.g., null, wrong type).
   * @throws NullPointerException if {@code initialState} is null.
   */
  public static <S, A> State.@NonNull StateTuple<S, A> runState(
      @Nullable Kind<StateKind.Witness, A> kind, @NonNull S initialState) {
    // Removed: Objects.requireNonNull(kind, "Input Kind cannot be null for runState");
    // unwrap will now handle the null kind and throw KindUnwrapException.
    Objects.requireNonNull(initialState, "Initial state cannot be null for runState");

    State<S, A> stateToRun = unwrap(kind);

    @SuppressWarnings("unchecked")
    State.StateTuple<S, A> result = (State.StateTuple<S, A>) stateToRun.run(initialState);
    return result;
  }

  /**
   * Evaluates the {@link State} computation held within the {@code Kind} wrapper, starting with
   * {@code initialState}, and returns only the final computed value.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<StateKind.Witness, A>} holding the {@code State} computation. Can
   *     be null.
   * @param initialState The non-null initial state.
   * @return The final value from the computation. Can be {@code null} if {@code A} is nullable.
   * @throws KindUnwrapException if {@code kind} is invalid.
   * @throws NullPointerException if {@code initialState} is null.
   */
  public static <S, A> @Nullable A evalState(
      @Nullable Kind<StateKind.Witness, A> kind, @NonNull S initialState) {
    return runState(kind, initialState).value();
  }

  /**
   * Executes the {@link State} computation held within the {@code Kind} wrapper, starting with
   * {@code initialState}, and returns only the final state.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value (ignored in the result).
   * @param kind The {@code Kind<StateKind.Witness, A>} holding the {@code State} computation. Can
   *     be null.
   * @param initialState The non-null initial state.
   * @return The non-null final state from the computation.
   * @throws KindUnwrapException if {@code kind} is invalid.
   * @throws NullPointerException if {@code initialState} is null.
   */
  public static <S, A> @NonNull S execState(
      @Nullable Kind<StateKind.Witness, A> kind, @NonNull S initialState) {
    return runState(kind, initialState).state();
  }
}
