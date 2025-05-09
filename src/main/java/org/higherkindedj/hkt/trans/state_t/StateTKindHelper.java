package org.higherkindedj.hkt.trans.state_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.state.StateTuple;
import org.jspecify.annotations.NonNull; // Assuming JSpecify for nullness annotations

/**
 * A utility class providing helper methods for working with {@link StateT} and its higher-kinded
 * type representation, {@link StateTKind}.
 *
 * <p>This class offers static methods for:
 *
 * <ul>
 *   <li>Safely converting between {@link StateT} and its {@link Kind} representation ({@link
 *       #wrap(StateT)} and {@link #unwrap(Kind)}).
 *   <li>Constructing {@link StateT} instances (e.g., {@link #stateT(Function, Monad)}, {@link
 *       #lift(Monad, Kind)}).
 *   <li>Running {@link StateT} computations from their {@link Kind} representation (e.g., {@link
 *       #runStateT(Kind, Object)}, {@link #evalStateT(Kind, Object)}, {@link #execStateT(Kind,
 *       Object)}).
 * </ul>
 *
 * <p>It serves as a bridge between the concrete {@link StateT} type and the more abstract {@link
 * Kind} interface used in generic functional programming patterns.
 *
 * <p>Operations that are specific to the {@link StateT} monad's behavior, such as {@code get},
 * {@code put}, {@code modify}, are typically found within the {@link StateTMonad} instance itself,
 * as they rely on the monadic context (specifically, the {@code Monad<F>} for the underlying
 * monad).
 *
 * @see StateT
 * @see StateTKind
 * @see StateTMonad
 * @see Kind
 */
public final class StateTKindHelper {

  /** Private constructor to prevent instantiation of this utility class. All methods are static. */
  private StateTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Wraps a concrete {@link StateT} instance into its higher-kinded representation, {@code
   * Kind<StateTKind.Witness<S, F>, A>}.
   *
   * <p>Since {@link StateT} already implements {@link StateTKind}, this method primarily serves as
   * a type-safe upcast and includes a null check for the input {@code StateT}.
   *
   * @param stateT The non-null {@link StateT} instance to wrap.
   * @param <S> The state type of the {@code StateT}.
   * @param <F> The higher-kinded type witness for the underlying monad of {@code StateT}.
   * @param <A> The value type of the {@code StateT}.
   * @return The provided {@code stateT} instance, cast to its {@link Kind} representation.
   * @throws NullPointerException if {@code stateT} is {@code null}.
   */
  public static <S, F, A> @NonNull Kind<StateTKind.Witness<S, F>, A> wrap(
      @NonNull StateT<S, F, A> stateT) {
    Objects.requireNonNull(stateT, "StateT instance to wrap cannot be null.");
    // StateT<S, F, A> implements StateTKind<S, F, A>, which extends Kind<StateTKind.Witness<S, F>,
    // A>.
    // So, a direct cast or return is type-safe after the null check.
    return stateT;
  }

  /**
   * Unwraps (narrows) a higher-kinded representation {@code Kind<StateTKind.Witness<S, F>, A>} back
   * to a concrete {@link StateT} instance.
   *
   * <p>This method relies on {@link StateTKind#narrow(Kind)} for the actual cast. Callers must
   * ensure that the provided {@code Kind} instance is genuinely a {@link StateT}.
   *
   * @param kind The non-null higher-kinded {@code StateT} representation.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return The concrete {@link StateT} instance.
   * @throws NullPointerException if {@code kind} is {@code null}.
   * @throws ClassCastException if {@code kind} is not a valid {@link StateT} instance (as per
   *     {@link StateTKind#narrow(Kind)}).
   */
  public static <S, F, A> @NonNull StateT<S, F, A> unwrap(
      @NonNull Kind<StateTKind.Witness<S, F>, A> kind) {
    // StateTKind.narrow performs its own null check if implemented robustly,
    // but an explicit one here is good practice if `kind` parameter is annotated @NonNull.
    Objects.requireNonNull(kind, "Kind instance to unwrap cannot be null.");
    return StateTKind.narrow(kind);
  }

  /**
   * Creates a {@link StateT} instance directly from its core state-transition function.
   *
   * <p>The provided function {@code runStateTFn} defines the behavior of the {@code StateT}: given
   * an initial state {@code S}, it produces a computation in the underlying monad {@code F} which
   * results in a {@link StateTuple} containing the new state and the computed value.
   *
   * @param runStateTFn The non-null function {@code S -> Kind<F, StateTuple<S, A>>} that defines
   *     the stateful computation.
   * @param monadF The non-null {@link Monad} instance for the underlying monad {@code F}. This is
   *     required by {@link StateT} to perform its operations.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return A new, non-null {@link StateT} instance.
   * @throws NullPointerException if {@code runStateTFn} or {@code monadF} is {@code null}.
   */
  public static <S, F, A> @NonNull StateT<S, F, A> stateT(
      @NonNull Function<S, Kind<F, StateTuple<S, A>>> runStateTFn, @NonNull Monad<F> monadF) {
    // StateT.create will perform its own null checks for its parameters.
    return StateT.create(runStateTFn, monadF);
  }

  /**
   * Lifts a computation {@code Kind<F, A>} from the underlying monad {@code F} into a {@link
   * StateT} context.
   *
   * <p>The resulting {@code StateT} computation, when run, will execute the {@code fa} computation
   * and pair its result with the initial state, leaving the state unchanged.
   *
   * @param monadF The non-null {@link Monad} instance for the underlying monad {@code F}.
   * @param fa The non-null computation {@code Kind<F, A>} in the underlying monad.
   * @param <S> The state type (this type parameter is for the resulting {@code StateT}).
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type of the computation {@code fa}.
   * @return A new, non-null {@link StateT} instance that wraps the lifted computation.
   * @throws NullPointerException if {@code monadF} or {@code fa} is {@code null}.
   */
  public static <S, F, A> @NonNull StateT<S, F, A> lift(
      @NonNull Monad<F> monadF, @NonNull Kind<F, A> fa) {
    Objects.requireNonNull(monadF, "Monad<F> for lift cannot be null.");
    Objects.requireNonNull(fa, "Kind<F, A> to lift cannot be null.");

    // The state 's' is passed through. The value 'a' from 'fa' is paired with 's'.
    // StateTuple.of(state, value)
    Function<S, Kind<F, StateTuple<S, A>>> runFn = s -> monadF.map(a -> StateTuple.of(s, a), fa);
    return stateT(runFn, monadF);
  }

  // --- Runner methods ---

  /**
   * Runs the {@link StateT} computation (provided as a {@link Kind}) with an initial state,
   * returning the result within the context of the underlying monad {@code F}.
   *
   * <p>This is a convenience method that mirrors {@link StateT#runStateT(Object)} but operates on
   * the {@link Kind} representation.
   *
   * @param kind The non-null {@code StateT} computation, as a {@code Kind}.
   * @param initialState The initial state of type {@code S}. (Nullability depends on {@code S}).
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return A {@code Kind<F, StateTuple<S, A>>} representing the outcome (final value and state) in
   *     the context of the underlying monad {@code F}. This will be non-null if the underlying
   *     operations guarantee non-null results.
   * @throws NullPointerException if {@code kind} is {@code null}.
   * @throws ClassCastException if {@code kind} is not a valid {@link StateT} instance.
   */
  public static <S, F, A> @NonNull Kind<F, StateTuple<S, A>> runStateT(
      @NonNull Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    return unwrap(kind).runStateT(initialState);
  }

  /**
   * Runs the {@link StateT} computation (provided as a {@link Kind}) with an initial state and
   * extracts only the final computed value, discarding the final state.
   *
   * <p>This is a convenience method that mirrors {@link StateT#evalStateT(Object)} but operates on
   * the {@link Kind} representation.
   *
   * @param kind The non-null {@code StateT} computation, as a {@code Kind}.
   * @param initialState The initial state of type {@code S}. (Nullability depends on {@code S}).
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return A {@code Kind<F, A>} representing the final value in the context of the underlying
   *     monad {@code F}. This will be non-null if the underlying operations guarantee non-null
   *     results.
   * @throws NullPointerException if {@code kind} is {@code null}.
   * @throws ClassCastException if {@code kind} is not a valid {@link StateT} instance.
   */
  public static <S, F, A> @NonNull Kind<F, A> evalStateT(
      @NonNull Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    return unwrap(kind).evalStateT(initialState);
  }

  /**
   * Runs the {@link StateT} computation (provided as a {@link Kind}) with an initial state and
   * extracts only the final state, discarding the computed value.
   *
   * <p>This is a convenience method that mirrors {@link StateT#execStateT(Object)} but operates on
   * the {@link Kind} representation.
   *
   * @param kind The non-null {@code StateT} computation, as a {@code Kind}.
   * @param initialState The initial state of type {@code S}. (Nullability depends on {@code S}).
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type (discarded in the result).
   * @return A {@code Kind<F, S>} representing the final state in the context of the underlying
   *     monad {@code F}. This will be non-null if the underlying operations guarantee non-null
   *     results and the state type S is non-null.
   * @throws NullPointerException if {@code kind} is {@code null}.
   * @throws ClassCastException if {@code kind} is not a valid {@link StateT} instance.
   */
  public static <S, F, A> @NonNull Kind<F, S> execStateT(
      @NonNull Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    return unwrap(kind).execStateT(initialState);
  }
}
