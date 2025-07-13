// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;

/**
 * A **Lens** is an optic that provides a focused view into a part of a data structure. Think of it
 * as a functional, composable "magnifying glass" 🔎 for a field that is guaranteed to exist.
 *
 * <p>A Lens is the right tool for "has-a" relationships, such as a field within a record or a
 * property of a class (e.g., a {@code User} has an {@code Address}). It is defined by two core,
 * well-behaved operations: getting the part, and setting the part in an immutable way.
 *
 * <p>It extends the generic {@link Optic}, specializing it for {@code S = T} and {@code A = B}.
 *
 * @param <S> The source type of the whole structure (e.g., {@code User}).
 * @param <A> The target type of the focused part (e.g., {@code Address}).
 */
public interface Lens<S, A> extends Optic<S, S, A, A> {

  /**
   * Gets the focused part {@code A} from the whole structure {@code S}.
   *
   * @param source The whole structure.
   * @return The focused part.
   */
  A get(S source);

  /**
   * Sets a new value for the focused part {@code A}, returning a new, updated structure {@code S}.
   *
   * <p>This operation must be immutable; the original {@code source} object is not changed.
   *
   * @param newValue The new value for the focused part.
   * @param source The original structure.
   * @return A new structure with the focused part updated.
   */
  S set(A newValue, S source);

  /**
   * Modifies the focused part {@code A} using a pure function.
   *
   * <p>This is a convenient shortcut for {@code set(modifier.apply(get(source)), source)}.
   *
   * @param modifier The function to apply to the focused part.
   * @param source The whole structure.
   * @return A new structure with the modified part.
   */
  default S modify(Function<A, A> modifier, S source) {
    return set(modifier.apply(get(source)), source);
  }

  /**
   * Modifies the focused part {@code A} with a function that returns a new value within an
   * effectful context {@code F} (a {@link Functor}).
   *
   * <p>This is the core effectful operation, allowing for updates that might be asynchronous (e.g.,
   * {@code CompletableFuture}) or failable (e.g., {@code Optional}).
   *
   * @param <F> The witness type for the {@link Functor} context.
   * @param f The function to apply, returning the new part in a context.
   * @param source The whole structure.
   * @param functor The {@link Functor} instance for the context {@code F}.
   * @return The updated structure {@code S}, itself wrapped in the context {@code F}.
   */
  <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S source, Functor<F> functor);

  /**
   * {@inheritDoc}
   *
   * <p>This default implementation satisfies the {@link Optic} interface by delegating to the
   * {@link Functor}-based {@code modifyF} method, as every {@link Applicative} is also a {@link
   * Functor}.
   */
  @Override
  default <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
    return this.modifyF(f, s, (Functor<F>) app);
  }

  /**
   * Views this {@code Lens} as a {@link Traversal}.
   *
   * <p>This is always possible because a {@code Lens} is fundamentally a {@code Traversal} that
   * focuses on exactly one element.
   *
   * @return A {@link Traversal} that represents this {@code Lens}.
   */
  default Traversal<S, A> asTraversal() {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
        return Lens.this.modifyF(f, s, app);
      }
    };
  }

  /**
   * Composes this {@code Lens<S, A>} with another {@code Lens<A, B>} to create a new {@code Lens<S,
   * B>}.
   *
   * <p>This specialized version is kept for efficiency and to ensure the result is correctly and
   * conveniently typed as a {@code Lens}.
   *
   * @param other The {@link Lens} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Lens} that focuses from {@code S} to {@code B}.
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
        return self.set(other.set(newValue, self.get(source)), source);
      }

      @Override
      public <F> Kind<F, S> modifyF(Function<B, Kind<F, B>> f, S source, Functor<F> functor) {
        return self.modifyF(a -> other.modifyF(f, a, functor), source, functor);
      }
    };
  }

  /**
   * Creates a {@code Lens} from its two fundamental operations: a getter and a setter.
   *
   * @param getter A function to extract the part {@code A} from the structure {@code S}.
   * @param setter A function to immutably update the part {@code A} within the structure {@code S}.
   * @param <S> The type of the whole structure.
   * @param <A> The type of the focused part.
   * @return A new {@code Lens} instance.
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
