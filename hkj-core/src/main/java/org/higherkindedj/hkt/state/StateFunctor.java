// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Functor} type class for {@link StateKind}{@code <S, A>}, allowing the
 * transformation of the computed value {@code A} within a stateful computation, while leaving the
 * state transformation logic intact.
 *
 * <p>An instance of {@code StateFunctor} is specific to a particular state type {@code S}, which is
 * fixed when the functor is instantiated. The HKT marker (witness type) used is {@link
 * StateKind.Witness}.
 *
 * @param <S> The type of the state that this {@code StateFunctor} instance operates upon. This
 *     state type is fixed for the lifetime of the functor instance.
 * @see Functor
 * @see State
 * @see StateKind
 * @see StateKind.Witness
 * @see StateKindHelper
 */
public class StateFunctor<S> implements Functor<StateKind.Witness<S>> {

  private static Class<StateFunctor> STATE_FUNCTOR_CLASS = StateFunctor.class;

  /**
   * Applies a function {@code f} to the computed value of a {@link State}{@code <S, A>}
   * computation, transforming it into a {@link State}{@code <S, B>} computation.
   *
   * <p>The {@code map} operation for {@code State} works by running the original state computation,
   * applying the function {@code f} to its resulting value, and then pairing this new value with
   * the final state from the original computation. The state transformation itself is preserved.
   *
   * <p>This method uses {@link StateKindHelper#narrow(org.higherkindedj.hkt.Kind)
   * StateKindHelper.STATE.narrow(Kind)} to retrieve the underlying {@link State}{@code <S, A>} and
   * {@link State#map(Function)} to perform the transformation, finally re-wrapping the result using
   * {@link StateKindHelper#widen(org.higherkindedj.hkt.state.State)
   * StateKindHelper.STATE.widen(State)}.
   *
   * @param <A> The type of the value in the input {@code State} computation (and its {@link Kind}
   *     representation).
   * @param <B> The type of the value in the resulting {@code State} computation (and its {@link
   *     Kind} representation) after applying the function {@code f}.
   * @param f The non-null function to apply to the computed value of the {@code State} computation.
   *     It transforms a value of type {@code A} to a value of type {@code B}.
   * @param fa The {@code Kind<StateKind.Witness<S>, A>} which represents the input {@link
   *     State}{@code <S, A>} computation. Must not be {@code null}.
   * @return A new non-null {@code Kind<StateKind.Witness<S>, B>} representing the transformed
   *     {@link State}{@code <S, B>} computation.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped
   *     to a valid {@code State} representation.
   * @throws NullPointerException if {@code f} or {@code fa} is {@code null}.
   */
  @Override
  public <A, B> Kind<StateKind.Witness<S>, B> map(
      Function<? super A, ? extends B> f, Kind<StateKind.Witness<S>, A> fa) {

    Validation.function().requireMapper(f, "f", STATE_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, STATE_FUNCTOR_CLASS, MAP);

    State<S, A> stateA = STATE.narrow(fa);
    State<S, B> stateB = stateA.map(f);
    return STATE.widen(stateB);
  }
}
