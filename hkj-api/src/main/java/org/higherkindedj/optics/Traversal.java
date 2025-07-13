// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;

/**
 * A **Traversal** is a versatile optic that can focus on zero or more parts 'A' within a larger
 * structure 'S'. Think of it as a functional "search-and-replace" or a bulk update tool 🗺️.
 *
 * <p>A Traversal is the right tool for "has-many" relationships, such as operating on every element
 * in a {@code List} or {@code Set}. It is the most general of the core optics, capable of modifying
 * all focused parts simultaneously within an {@link Applicative} context.
 *
 * <p>Both {@link Lens} and {@link Prism} can be viewed as specialized {@code Traversal}s:
 *
 * <ul>
 *   <li>A {@code Lens} is a {@code Traversal} that focuses on exactly one item.
 *   <li>A {@code Prism} is a {@code Traversal} that focuses on zero or one item.
 * </ul>
 *
 * <p>It extends the generic {@link Optic}, specializing it for {@code S = T} and {@code A = B}.
 *
 * @param <S> The type of the whole structure (e.g., a {@code List<User>}).
 * @param <A> The type of the focused parts (e.g., the {@code User} elements).
 */
public interface Traversal<S, A> extends Optic<S, S, A, A> {

  /**
   * {@inheritDoc}
   *
   * <p>This is the core operation of a {@code Traversal}. It modifies all focused parts {@code A}
   * within a structure {@code S} by applying a function that returns a value in an {@link
   * Applicative} context {@code F}.
   *
   * <p>The {@link Applicative} instance is crucial as it defines how to combine the results of
   * modifying multiple parts (e.g., fail-fast for {@code Optional}, accumulate for {@code
   * Validated}, or run in parallel for a concurrent data type).
   *
   * @param <F> The witness type for the {@link Applicative} context.
   * @param f The effectful function to apply to each focused part.
   * @param source The whole structure to operate on.
   * @param applicative The {@link Applicative} instance for the context {@code F}.
   * @return The updated structure {@code S}, itself wrapped in the context {@code F}.
   */
  @Override
  <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S source, Applicative<F> applicative);

  /**
   * Composes this {@code Traversal<S, A>} with another {@code Traversal<A, B>} to create a new
   * {@code Traversal<S, B>}.
   *
   * <p>This specialized overload is kept for convenience to ensure the result is correctly and
   * conveniently typed as a {@code Traversal}. For example, composing a traversal for a {@code
   * List<Team>} with one for a {@code List<Player>} results in a traversal for every {@code Player}
   * in the nested structure.
   *
   * @param other The {@link Traversal} to compose with.
   * @param <B> The type of the final focused parts.
   * @return A new, composed {@link Traversal}.
   */
  default <B> Traversal<S, B> andThen(final Traversal<A, B> other) {
    // Use the generic 'andThen' from the parent Optic interface
    // and wrap the result back into the Traversal interface.
    final Optic<S, S, B, B> composedOptic = Optic.super.andThen(other);
    return new Traversal<>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<B, Kind<F, B>> f, S s, Applicative<F> app) {
        // The actual composition logic is handled by the parent.
        return composedOptic.modifyF(f, s, app);
      }
    };
  }
}
