// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.higherkindedj.hkt.util.validation.TransformerValidator;
import org.jspecify.annotations.Nullable;

/**
 * Provides a {@link Monad} instance for the {@link StateT} monad transformer.
 *
 * <p>The {@code StateT<S, F, A>} monad transformer wraps a computation that takes an initial state
 * of type {@code S} and produces a result of type {@code A} along with a new state, all within the
 * context of an underlying monad {@code F}.
 *
 * <p>This {@code Monad} instance enables chaining stateful operations. The underlying monad {@code
 * F} dictates how the stateful computations are executed (e.g., synchronously, asynchronously, with
 * error handling). This transformer sequences operations using the flatMap and map capabilities of
 * the underlying monad.
 *
 * @param <S> The type of the state threaded through the computations.
 * @param <F> The higher-kinded type witness for the underlying monad.
 * @see StateT
 * @see Monad
 */
public final class StateTMonad<S, F> implements Monad<StateTKind.Witness<S, F>> {

  private static final Class<StateTMonad> STATE_T_MONAD_CLASS = StateTMonad.class;
  private final Monad<F> monadF;

  // Private constructor, use factory method
  private StateTMonad(Monad<F> monadF) {
    this.monadF = TransformerValidator.requireOuterMonad(monadF, STATE_T_MONAD_CLASS, CONSTRUCTION);
  }

  /**
   * Creates a {@link Monad} instance for {@link StateT} given a {@link Monad} instance for the
   * underlying monad {@code F}.
   *
   * @param monadF The {@link Monad} instance for the underlying monad {@code F}.
   * @param <S> The type of the state.
   * @param <F> The higher-kinded type witness for the underlying monad {@code F}.
   * @return A {@code Monad<StateTKind.Witness<S, F>>} instance.
   * @throws NullPointerException if {@code monadF} is null.
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
   * @return A {@code StateT<S, F, A>} instance representing the lifted value.
   */
  @Override
  public <A> Kind<StateTKind.Witness<S, F>, A> of(@Nullable A a) {
    Function<S, Kind<F, StateTuple<S, A>>> runFn = s -> monadF.of(StateTuple.of(s, a));
    return StateT.<S, F, A>create(runFn, monadF);
  }

  /**
   * Transforms the value type of a {@code StateT<S, F, A>} from {@code A} to {@code B} using the
   * provided function {@code f}, without altering the state transformation behavior.
   *
   * @param f The function to apply to the value. Must not be {@code null}.
   * @param fa The {@code StateT<S, F, A>} instance whose value is to be transformed.
   * @param <A> The original value type.
   * @param <B> The new value type.
   * @return A new {@code StateT<S, F, B>} instance.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} is not a valid {@code
   *     StateT} representation.
   */
  @Override
  public <A, B> Kind<StateTKind.Witness<S, F>, B> map(
      Function<? super A, ? extends B> f, Kind<StateTKind.Witness<S, F>, A> fa) {
    FunctionValidator.requireMapper(f, "f", STATE_T_MONAD_CLASS, MAP);
    KindValidator.requireNonNull(fa, STATE_T_MONAD_CLASS, MAP);

    StateT<S, F, A> stateT = StateTKind.narrow(fa);
    Function<S, Kind<F, StateTuple<S, B>>> newRunFn =
        s ->
            monadF.map(
                stateTuple -> StateTuple.of(stateTuple.state(), f.apply(stateTuple.value())),
                stateT.runStateT(s));
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
   * <p><b>Important Note on Nulls:</b> The value {@code valA} extracted from the second computation
   * ({@code fa}) may be {@code null}. This implementation passes this potentially null value
   * directly to the function extracted from the first computation ({@code ff}). It is the
   * developer's responsibility to ensure that the provided function can handle a {@code null} input
   * if the preceding computations can result in a {@code null} value. Failure to do so may result
   * in a {@code NullPointerException} during execution.
   *
   * @param ff The {@code StateT<S, F, Function<A, B>>} containing the function to apply. The
   *     function wrapped within the StateT must not be null.
   * @param fa The {@code StateT<S, F, A>} containing the value to which the function is applied.
   *     Must not be {@code null}.
   * @param <A> The type of the input value for the function.
   * @param <B> The type of the result of the function application.
   * @return A new {@code StateT<S, F, B>} instance representing the result of applying the function
   *     and sequencing the state transformations.
   * @throws NullPointerException if {@code ff}, {@code fa}, or the function wrapped within {@code
   *     ff} is {@code null}.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} is not
   *     a valid {@code StateT} representation.
   */
  @Override
  public <A, B> Kind<StateTKind.Witness<S, F>, B> ap(
      Kind<StateTKind.Witness<S, F>, ? extends Function<A, B>> ff,
      Kind<StateTKind.Witness<S, F>, A> fa) {
    KindValidator.requireNonNull(ff, STATE_T_MONAD_CLASS, AP, "function");
    KindValidator.requireNonNull(fa, STATE_T_MONAD_CLASS, AP, "argument");

    StateT<S, F, ? extends Function<A, B>> stateTf = StateTKind.narrow(ff);
    StateT<S, F, A> stateTa = StateTKind.narrow(fa);

    Function<S, Kind<F, StateTuple<S, B>>> newRunFn =
        s0 ->
            monadF.flatMap(
                tupleF -> {
                  Function<A, B> function = tupleF.value();
                  S s1 = tupleF.state();

                  FunctionValidator.requireFunction(
                      function, "wrapped function", STATE_T_MONAD_CLASS, AP);

                  Kind<F, StateTuple<S, A>> resultA = stateTa.runStateT(s1);

                  return monadF.map(
                      tupleA -> {
                        S s2 = tupleA.state();
                        B finalValue = function.apply(tupleA.value());
                        return StateTuple.of(s2, finalValue);
                      },
                      resultA);
                },
                stateTf.runStateT(s0));

    return StateT.create(newRunFn, monadF);
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
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} is not a valid {@code
   *     StateT} representation.
   */
  @Override
  public <A, B> Kind<StateTKind.Witness<S, F>, B> flatMap(
      Function<? super A, ? extends Kind<StateTKind.Witness<S, F>, B>> f,
      Kind<StateTKind.Witness<S, F>, A> fa) {
    FunctionValidator.requireFlatMapper(f, "f", STATE_T_MONAD_CLASS, FLAT_MAP);
    KindValidator.requireNonNull(fa, STATE_T_MONAD_CLASS, FLAT_MAP);

    StateT<S, F, A> stateTa = StateTKind.narrow(fa);

    Function<S, Kind<F, StateTuple<S, B>>> newRunFn =
        s0 ->
            monadF.<StateTuple<S, A>, StateTuple<S, B>>flatMap(
                tupleA -> {
                  Kind<StateTKind.Witness<S, F>, B> kindB = f.apply(tupleA.value());
                  FunctionValidator.requireNonNullResult(kindB, "f", STATE_T_MONAD_CLASS, FLAT_MAP);
                  StateT<S, F, B> stateTb = StateTKind.narrow(kindB);
                  return stateTb.runStateT(tupleA.state());
                },
                stateTa.runStateT(s0));

    return StateT.<S, F, B>create(newRunFn, monadF);
  }
}
