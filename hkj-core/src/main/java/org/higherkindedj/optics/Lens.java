// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;

/**
 * A Lens focuses on a single part 'A' within a larger structure 'S'. It provides a way to get, set,
 * or modify that part immutably.
 *
 * @param <S> The type of the whole structure (e.g., WorkflowContext).
 * @param <A> The type of the focused part (e.g., ValidatedOrder).
 */
public interface Lens<S, A> {

  /**
   * Gets the part 'A' from the whole structure 'S'.
   *
   * @param source The whole structure.
   * @return The focused part.
   */
  A get(S source);

  /**
   * Sets the part 'A' within the whole structure 'S', returning a new 'S'.
   *
   * @param newValue The new value for the part.
   * @param source The whole structure.
   * @return A new structure with the part updated.
   */
  S set(A newValue, S source);

  /**
   * Modifies the part 'A' using a function, returning a new 'S'. This can be defined in terms of
   * get and set.
   *
   * @param modifier A function to apply to the focused part.
   * @param source The whole structure.
   * @return A new structure with the part modified.
   */
  default S modify(Function<A, A> modifier, S source) {
    return set(modifier.apply(get(source)), source);
  }

  /**
   * Modifies the part 'A' within a Functor context 'F'. This is the most powerful operation,
   * allowing for effectful modifications.
   *
   * @param f The function to apply to the part, returning a value in a context 'F'.
   * @param source The whole structure.
   * @param functor The Functor instance for the context 'F'.
   * @param <F> The witness type for the Functor context.
   * @return The updated structure 'S' inside the Functor context 'F'.
   */
  <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S source, Functor<F> functor);

  /**
   * Composes this lens with another lens. This allows focusing deeper into a nested structure.
   *
   * @param other The other lens to compose with.
   * @param <B> The part type of the other lens.
   * @return A new Lens that focuses from 'S' to 'B'.
   */
  default <B> Lens<S, B> andThen(Lens<A, B> other) {
    Lens<S, A> self = this;
    return new Lens<>() {
      @Override
      public B get(S source) {
        return other.get(self.get(source));
      }

      @Override
      public S set(B newValue, S source) {
        A newA = other.set(newValue, self.get(source));
        return self.set(newA, source);
      }

      @Override
      public <F> Kind<F, S> modifyF(Function<B, Kind<F, B>> f, S source, Functor<F> functor) {
        return self.modifyF(a -> other.modifyF(f, a, functor), source, functor);
      }
    };
  }

  /**
   * Static factory method to create a Lens from a getter and a setter function. This is a
   * convenient way to create lens instances for immutable data structures.
   *
   * @param getter A function that takes the whole structure `S` and returns the part `A`.
   * @param setter A function that takes the whole structure `S` and a new part `A`, and returns a
   *     new structure `S`.
   * @param <S> The type of the whole structure.
   * @param <A> The type of the focused part.
   * @return A new Lens instance.
   */
  static <S, A> Lens<S, A> of(Function<S, A> getter, BiFunction<S, A, S> setter) {
    return new Lens<>() {
      @Override
      public A get(S source) {
        return getter.apply(source);
      }

      @Override
      public S set(A newValue, S source) {
        return setter.apply(source, newValue);
      }

      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S source, Functor<F> functor) {
        Kind<F, A> fa = f.apply(this.get(source));
        return functor.map(a -> this.set(a, source), fa);
      }
    };
  }
}
