// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import java.util.function.BiFunction;

/**
 * A capability interface representing types that support combining independent computations.
 *
 * <p>This capability extends {@link Composable} and corresponds to the Applicative typeclass. Types
 * implementing this interface can combine multiple independent paths into a single result.
 *
 * <h2>Operations</h2>
 *
 * <ul>
 *   <li>{@link #zipWith(Combinable, BiFunction)} - Combine two paths with a binary function
 * </ul>
 *
 * <h2>Key Distinction from Chainable</h2>
 *
 * <p>Unlike {@link Chainable#via(java.util.function.Function)} where the second computation depends
 * on the first's result, {@code zipWith} combines <em>independent</em> computations. This
 * distinction enables certain optimizations (like parallel execution) in some implementations.
 *
 * @param <A> the type of the contained value
 */
public interface Combinable<A> extends Composable<A> {

  /**
   * Combines this path with another using the provided function.
   *
   * <p>If both paths contain values, the function is applied to combine them. If either path
   * represents an error or absence, the result reflects that error/absence (the exact behavior
   * depends on the specific path type).
   *
   * <p>Example:
   *
   * <pre>{@code
   * MaybePath<String> fullName = firstName.zipWith(lastName,
   *     (first, last) -> first + " " + last);
   * }</pre>
   *
   * @param other the other path to combine with; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the other path's value
   * @param <C> the type of the combined result
   * @return a new path containing the combined result
   * @throws NullPointerException if other or combiner is null
   */
  <B, C> Combinable<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner);
}
