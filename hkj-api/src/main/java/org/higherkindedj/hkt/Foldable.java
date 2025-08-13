// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NonNull;

/**
 * A typeclass for data structures that can be folded to a summary value.
 *
 * <p>The key operation is {@link #foldMap}, which maps each element of a structure to a {@link
 * Monoid} and combines the results.
 *
 * @param <F> The higher-kinded type witness for the data structure (e.g., ListKind.Witness).
 */
public interface Foldable<F> {

  /**
   * Maps each element of the structure to a Monoid {@code M} and combines the results.
   *
   * @param monoid The Monoid used to combine the results.
   * @param f A function to map each element of type {@code A} to the Monoidal type {@code M}. This
   *     function can accept any supertype of {@code A} and return any subtype of {@code M}.
   * @param fa The foldable structure.
   * @param <A> The type of elements in the structure.
   * @param <M> The Monoidal type.
   * @return The aggregated result of type {@code M}.
   */
  <A, M> M foldMap(
      @NonNull Monoid<M> monoid,
      @NonNull Function<? super A, ? extends M> f,
      @NonNull Kind<F, A> fa);
}
