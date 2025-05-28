// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trans.state_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.unit.Unit;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Provides a {@link Monad} instance for the {@link StateT} monad transformer.
 *
 * <p>The {@code StateT<S, F, A>} monad transformer wraps a computation that takes an initial state
 * of type {@code S} and produces a result of type {@code A} along with a new state, all within the
 * context of an underlying monad {@code F}.
 *
 * <p>This {@code Monad} instance allows {@code StateT} to be used in monadic compositions, enabling
 * sequences of stateful computations. It requires a {@code Monad<F>} instance for the underlying
 * monad {@code F} to lift operations into {@code StateT}. If the underlying monad {@code F} also
 * implements {@link MonadError}, this instance can leverage error-handling capabilities (e.g., in
 * {@link #ap(Kind, Kind)}).
 *
 * @param <S> The type of the state threaded through the computations.
 * @param <F> The higher-kinded type witness for the underlying monad. This monad dictates how the
 *     stateful computations are executed (e.g., synchronously, asynchronously, with error
 *     handling).
 * @see StateT
 * @see Monad
 * @see MonadError
 */
public final class StateTMonad<S, F> implements Monad<StateTKind.Witness<S, F>> {

  private final Monad<F> monadF;
  // Store monadF also as MonadError if it implements it, for raiseError access.
  private final @Nullable MonadError<F, ?> monadErrorF; // Wildcard for error type

  // Private constructor, use factory method
  private StateTMonad(Monad<F> monadF) {
    this.monadF = Objects.requireNonNull(monadF, "Underlying Monad<F> cannot be null");
    // Check if it's also a MonadError
    if (monadF instanceof MonadError) {
      // Store it with a wildcard for the error type, as we don't know it here.
      this.monadErrorF = (MonadError<F, ?>) monadF;
    } else {
      this.monadErrorF = null;
    }
  }

  /**
   * Creates a {@link Monad} instance for {@link StateT} given a {@link Monad} instance for the
   * underlying monad {@code F}.
   *
   * @param monadF The {@link Monad} instance for the underlying monad {@code F}. This instance
   *     provides the basic monadic operations (of, map, flatMap, ap) for {@code F}.
   * @param <S> The type of the state.
   * @param <F> The higher-kinded type witness for the underlying monad {@code F}.
   * @return A {@code Monad<StateTKind.Witness<S, F>>} instance capable of performing monadic
   *     operations on {@code StateT<S, F, A>} values.
   */
  public static <S, F> StateTMonad<S, F> instance(Monad<F> monadF) {
    return new StateTMonad<>(monadF);
  }

  /**
   * Lifts a pure value {@code a} into the {@code StateT<S, F, A>} monad.
   *
   * <p>The resulting {@code StateT} computation, when run with an initial state {@code s}, will
   * produce the value {@code a} and leave the state {@code s} unchanged. The operation is performed
   * within the context of the underlying monad {@code F} using its {@code of} method.
   *
   * @param a The pure value to lift. Can be {@code null}.
   * @param <A> The type of the value.
   * @return A {@code StateT<S, F, A>} instance representing the lifted value {@code a} alongside an
   *     unchanged state, wrapped in the underlying monad {@code F}. Specifically, it's {@code s ->
   *     monadF.of(StateTuple.of(s, a))}.
   */
  @Override
  public <A> Kind<StateTKind.Witness<S, F>, A> of(@Nullable A a) {
    // The run function takes state 's' and returns F<StateTuple(s, a)>
    Function<S, Kind<F, StateTuple<S, A>>> runFn = s -> monadF.of(StateTuple.of(s, a));
    return StateT.<S, F, A>create(runFn, monadF);
  }

  /**
   * Transforms the value type of a {@code StateT<S, F, A>} from {@code A} to {@code B} using the
   * provided function {@code f}, without altering the state transformation behavior.
   *
   * <p>The function {@code f} is applied to the value produced by the original {@code StateT}
   * computation. The state transformation remains the same as the original {@code fa}. This
   * operation relies on the {@code map} operation of the underlying monad {@code F}.
   *
   * @param f The function to apply to the value. Must not be {@code null}.
   * @param fa The {@code StateT<S, F, A>} instance whose value is to be transformed. Must not be
   *     {@code null}.
   * @param <A> The original value type.
   * @param <B> The new value type.
   * @return A new {@code StateT<S, F, B>} instance. When run, it executes the original state
   *     transformation and then applies {@code f} to the resulting value, all within the context of
   *     {@code F}. Specifically, its run function is {@code s -> monadF.map(stateTuple ->
   *     StateTuple.of(stateTuple.state(), f.apply(stateTuple.value())), stateT.runStateT(s))}.
   */
  @Override
  public <A, B> Kind<StateTKind.Witness<S, F>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<StateTKind.Witness<S, F>, A> fa) {
    StateT<S, F, A> stateT = StateTKind.narrow(fa);
    // Define the new run function:
    // s -> F.map(stateTuple -> StateTuple(stateTuple.state, f(stateTuple.value)),
    // stateT.runStateT(s))
    Function<S, Kind<F, StateTuple<S, B>>> newRunFn =
        s ->
            monadF.map(
                // Input type for map's function is StateTuple<S, A>
                // Output type is StateTuple<S, B>
                stateTuple -> StateTuple.of(stateTuple.state(), f.apply(stateTuple.value())),
                stateT.runStateT(s) // Input Kind is Kind<F, StateTuple<S, A>>
                );
    return StateT.<S, F, B>create(newRunFn, monadF);
  }

  /**
   * Applies a function wrapped in a {@code StateT<S, F, Function<A, B>>} to a value wrapped in a
   * {@code StateT<S, F, A>}.
   *
   * <p>This operation sequences the state transformations:
   *
   * <ol>
   *   <li>The {@code stateTf} (which contains the function) is run with an initial state {@code
   *       s0}, producing an intermediate state {@code s1} and a function {@code func}.
   *   <li>The {@code stateTa} (which contains the argument) is then run with state {@code s1},
   *       producing the final state {@code s2} and a value {@code valA}.
   *   <li>The function {@code func} is applied to {@code valA} to get the final result {@code B}.
   * </ol>
   *
   * All operations are performed within the context of the underlying monad {@code F}, using its
   * {@code flatMap} and {@code map} methods.
   *
   * <p>If the function {@code func} extracted from {@code stateTf} is {@code null}, and the
   * underlying monad {@code F} implements {@link MonadError}, this method attempts to produce an
   * "empty" or "error" result for {@code StateTuple<S, B>}. This is done by attempting to cast the
   * stored {@code MonadError<F, ?>} to {@code MonadError<F, Unit>} and calling {@code
   * raiseError(Unit.INSTANCE)}. This specific handling assumes that the relevant error types (like
   * those from OptionalMonad or MaybeMonad) are being transitioned to use {@link Unit}. If {@code
   * F} is not a {@code MonadError} or if the cast/call fails, an {@link IllegalStateException} is
   * thrown.
   *
   * @param ff The {@code StateT<S, F, Function<A, B>>} containing the function to apply. Must not
   *     be {@code null}.
   * @param fa The {@code StateT<S, F, A>} containing the value to which the function is applied.
   *     Must not be {@code null}.
   * @param <A> The type of the input value for the function.
   * @param <B> The type of the result of the function application.
   * @return A new {@code StateT<S, F, B>} instance representing the result of applying the function
   *     and sequencing the state transformations.
   * @throws IllegalStateException if the function extracted from {@code ff} is null and the
   *     underlying monad {@code F} is not a {@link MonadError} or cannot produce an appropriate
   *     empty/error value compatible with {@link Unit}.
   */
  @Override
  public <A, B> Kind<StateTKind.Witness<S, F>, B> ap(
      @NonNull Kind<StateTKind.Witness<S, F>, Function<A, B>> ff,
      @NonNull Kind<StateTKind.Witness<S, F>, A> fa) {
    StateT<S, F, Function<A, B>> stateTf = StateTKind.narrow(ff);
    StateT<S, F, A> stateTa = StateTKind.narrow(fa);

    Function<S, Kind<F, StateTuple<S, B>>> newRunFn =
        s0 ->
            // Run the function StateT first
            monadF.<StateTuple<S, Function<A, B>>, StateTuple<S, B>>flatMap(
                // Input: tupleF is StateTuple<S, Function<A, B>>
                // Output: Kind<F, StateTuple<S, B>>
                tupleF -> {
                  Function<A, B> function = tupleF.value();
                  S s1 = tupleF.state();

                  if (function == null) {
                    // If function is null, the result of this flatMap step should be empty.
                    if (this.monadErrorF == null) {
                      throw new IllegalStateException(
                          "MonadError<F> instance not available, cannot produce empty value for"
                              + " null function in ap.");
                    }
                    try {
                      @SuppressWarnings("unchecked")
                      MonadError<F, Unit> specificMonadError =
                          (MonadError<F, Unit>) this.monadErrorF;
                      return specificMonadError.<StateTuple<S, B>>raiseError(Unit.INSTANCE);
                    } catch (ClassCastException cce) {
                      throw new IllegalStateException(
                          "Underlying MonadError<F> does not have Unit error type as expected for"
                              + " null function handling.",
                          cce);
                    }
                  }

                  // Function is not null, proceed to run stateTa
                  Kind<F, StateTuple<S, A>> resultA = stateTa.runStateT(s1);

                  // Map over the result of stateTa
                  return monadF.<StateTuple<S, A>, StateTuple<S, B>>map(
                      tupleA -> {
                        // We know 'function' is not null here
                        B appliedValue = function.apply(tupleA.value());
                        S s2 = tupleA.state();
                        // Rely on underlying map to handle if appliedValue is
                        // null for types like Optional
                        return StateTuple.of(s2, appliedValue);
                      },
                      resultA);
                },
                // Initial run of the function StateT
                stateTf.runStateT(s0) // Kind<F, StateTuple<S, Function<A, B>>>
                );

    return StateT.<S, F, B>create(newRunFn, monadF);
  }

  /**
   * Sequentially composes two {@link StateT} computations.
   *
   * <p>This operation, also known as {@code bind} or {@code >>=} (in Haskell), allows chaining of
   * stateful computations where the result of the first computation ({@code fa}) is used to
   * generate the next computation using function {@code f}.
   *
   * <p>The process is as follows:
   *
   * <ol>
   *   <li>The initial {@code StateT<S, F, A>} computation ({@code fa}) is run with an initial state
   *       {@code s0}. This yields a value {@code a} and an intermediate state {@code s1}, all
   *       within the context of the underlying monad {@code F}.
   *   <li>The function {@code f} is applied to the value {@code a}. This function {@code f} must
   *       return a new {@code StateT<S, F, B>} computation.
   *   <li>This new {@code StateT<S, F, B>} computation is then run with the intermediate state
   *       {@code s1}. This produces the final value {@code b} and the final state {@code s2}.
   * </ol>
   *
   * This entire sequence is managed by the {@code flatMap} operation of the underlying monad {@code
   * F}.
   *
   * @param f A function that takes the result of type {@code A} from the first {@code StateT}
   *     computation ({@code fa}) and returns a new {@code StateT<S, F, B>} computation. Must not be
   *     {@code null}.
   * @param fa The initial {@code StateT<S, F, A>} computation. Must not be {@code null}.
   * @param <A> The value type of the initial computation.
   * @param <B> The value type of the computation returned by function {@code f}.
   * @return A new {@code StateT<S, F, B>} representing the composed computation. Its run function
   *     is effectively {@code s0 -> monadF.flatMap(tupleA ->
   *     StateTKind.narrow(f.apply(tupleA.value())).runStateT(tupleA.state()),
   *     StateTKind.narrow(fa).runStateT(s0))}.
   */
  @Override
  public <A, B> @NonNull Kind<StateTKind.Witness<S, F>, B> flatMap(
      @NonNull Function<A, Kind<StateTKind.Witness<S, F>, B>> f,
      @NonNull Kind<StateTKind.Witness<S, F>, A> fa) {
    StateT<S, F, A> stateTa = StateTKind.narrow(fa);

    Function<S, Kind<F, StateTuple<S, B>>> newRunFn =
        s0 ->
            monadF.<StateTuple<S, A>, StateTuple<S, B>>flatMap(
                // Argument 1: Function<A1, Kind<F, B1>>
                // Input: tupleA is StateTuple<S, A>
                // Output: Kind<F, StateTuple<S, B>> (which is Kind<F, B1>)
                tupleA -> {
                  // Apply the function f to the value from the first StateT
                  Kind<StateTKind.Witness<S, F>, B> kindB = f.apply(tupleA.value());
                  // Narrow it back to a concrete StateT
                  StateT<S, F, B> stateTb = StateTKind.narrow(kindB);
                  // Run the resulting StateT with the state from the first StateT
                  return stateTb.runStateT(tupleA.state());
                },
                // Argument 2: Kind<F, A1>
                // Initial run of the first StateT
                stateTa.runStateT(s0) // Kind<F, StateTuple<S, A>>
                );

    return StateT.<S, F, B>create(newRunFn, monadF);
  }
}
