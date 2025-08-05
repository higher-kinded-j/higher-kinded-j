// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * A Monad that also has a "zero" or "empty" element, which allows it to represent failure or an
 * empty result.
 *
 * <p>This is useful for monads that can be filtered, such as {@code List}, {@code Optional}, or
 * {@code Maybe}. The {@code zero()} method provides the empty value for that monad (e.g., an empty
 * list or {@code Optional.empty()}).
 *
 * <p>This interface is particularly important for enabling the {@code when()} (filtering) clause in
 * for-comprehensions, allowing computations to be short-circuited.
 *
 * @param <F> The witness type of the Monad.
 * @see Monad
 */
@NullMarked
public interface MonadZero<F> extends Monad<F> {

  /**
   * Returns the "zero" or "empty" value for this Monad.
   *
   * @param <A> The type parameter of the Kind, which will not be present.
   * @return The empty value for the monad (non-null), e.g., {@code Optional.empty()}.
   */
  <A> Kind<F, A> zero();
}
