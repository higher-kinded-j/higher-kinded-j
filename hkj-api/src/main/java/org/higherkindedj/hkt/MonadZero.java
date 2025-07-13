// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

/**
 * An interface for monads that have a "zero" or "empty" element. This is crucial for filtering
 * operations, where a failing predicate results in the zero element.
 *
 * @param <M> The witness type for the Monad.
 */
public interface MonadZero<M> extends Monad<M> {

  /**
   * Returns the zero element for this monad. The result is polymorphic and can be safely cast to
   * any Kind&lt;M, T&gt;. For example, for List it is an empty list, for Maybe it is Nothing.
   *
   * @param <T> The desired inner type of the zero value.
   * @return The zero or empty value for the monad.
   */
  <T> Kind<M, T> zero();
}
