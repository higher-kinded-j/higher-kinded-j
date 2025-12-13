// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A capability interface representing types that support value transformation.
 *
 * <p>This is the base capability for all path types, corresponding to the Functor typeclass. Types
 * implementing this interface can transform their contained values while preserving the effect
 * structure.
 *
 * <h2>Operations</h2>
 *
 * <ul>
 *   <li>{@link #map(Function)} - Transform the contained value
 *   <li>{@link #peek(Consumer)} - Observe the value without modifying it (for debugging)
 * </ul>
 *
 * <h2>Laws</h2>
 *
 * <p>Implementations must satisfy the Functor laws:
 *
 * <ul>
 *   <li><b>Identity:</b> {@code path.map(x -> x)} equals {@code path}
 *   <li><b>Composition:</b> {@code path.map(f).map(g)} equals {@code path.map(f.andThen(g))}
 * </ul>
 *
 * @param <A> the type of the contained value
 */
public interface Composable<A> {

  /**
   * Transforms the contained value using the provided function.
   *
   * <p>This is the fundamental mapping operation. If the path contains a value, the function is
   * applied to it. If the path represents an error or absence of value, the function is not called
   * and the error/absence is propagated.
   *
   * <p>Example:
   *
   * <pre>{@code
   * MaybePath<Integer> length = Path.maybe(name).map(String::length);
   * }</pre>
   *
   * @param mapper the function to apply to the contained value; must not be null
   * @param <B> the type of the transformed value
   * @return a new path containing the transformed value
   * @throws NullPointerException if mapper is null
   */
  <B> Composable<B> map(Function<? super A, ? extends B> mapper);

  /**
   * Observes the contained value without modifying it.
   *
   * <p>This operation is useful for debugging and logging. The consumer is only called if the path
   * contains a value; it is not called for error states or absence of value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Path.maybe(userId)
   *     .peek(id -> logger.debug("Processing user: {}", id))
   *     .via(id -> userRepo.findById(id));
   * }</pre>
   *
   * @param consumer the action to perform on the contained value; must not be null
   * @return this path unchanged (for method chaining)
   * @throws NullPointerException if consumer is null
   */
  Composable<A> peek(Consumer<? super A> consumer);
}
