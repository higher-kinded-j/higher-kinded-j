// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * The MonadState type class abstracts the capability of stateful computation.
 *
 * <p>This is an MTL-style capability interface: rather than requiring a concrete transformer stack
 * like {@code StateT}, code can be written against {@code MonadState} to declare that it needs
 * stateful capabilities without specifying how they are provided.
 *
 * <p>Laws:
 *
 * <ul>
 *   <li><b>Get-put:</b> {@code flatMap(s -> put(s), get()) == of(Unit.INSTANCE)}
 *   <li><b>Put-get:</b> {@code flatMap(_ -> get(), put(s)) == flatMap(_ -> of(s), put(s))}
 *   <li><b>Put-put:</b> {@code flatMap(_ -> put(s2), put(s1)) == put(s2)}
 *   <li><b>Modify coherence:</b> {@code modify(f) == flatMap(s -> put(f.apply(s)), get())}
 * </ul>
 *
 * @param <F> The witness type for the monad.
 * @param <S> The type of the state.
 * @see Monad
 */
@NullMarked
public interface MonadState<F extends WitnessArity<TypeArity.Unary>, S> extends Monad<F> {

  /**
   * Returns the current state.
   *
   * @return A monadic value containing the current state.
   */
  Kind<F, S> get();

  /**
   * Replaces the current state with the given value.
   *
   * @param s The new state value.
   * @return A monadic value containing {@link Unit#INSTANCE}.
   */
  Kind<F, Unit> put(S s);

  /**
   * Transforms the current state using the given function.
   *
   * <p>This is equivalent to {@code flatMap(s -> put(f.apply(s)), get())}.
   *
   * @param f The function to transform the state. Must not be null.
   * @return A monadic value containing {@link Unit#INSTANCE}.
   */
  default Kind<F, Unit> modify(Function<S, S> f) {
    return flatMap(s -> put(f.apply(s)), get());
  }

  /**
   * Extracts a value from the current state using the given function.
   *
   * <p>This is equivalent to {@code map(f, get())}.
   *
   * @param f The function to extract a value from the state. Must not be null.
   * @param <A> The type of the extracted value.
   * @return A monadic value containing the extracted value.
   */
  default <A> Kind<F, A> gets(Function<S, A> f) {
    return map(f, get());
  }

  /**
   * Extracts a value from the current state. This is an alias for {@link #gets(Function)}.
   *
   * @param f The function to extract a value from the state. Must not be null.
   * @param <A> The type of the extracted value.
   * @return A monadic value containing the extracted value.
   */
  default <A> Kind<F, A> inspect(Function<S, A> f) {
    return gets(f);
  }
}
