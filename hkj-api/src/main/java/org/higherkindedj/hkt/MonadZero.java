// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * A Monad that also has a "zero" or "empty" element and supports alternative/choice operations.
 *
 * <p>MonadZero combines the power of {@link Monad} with {@link Alternative}, providing both monadic
 * bind and choice operations. This is useful for monads that can be filtered and combined, such as
 * {@code List}, {@code Optional}, or {@code Maybe}.
 *
 * <p>The {@code zero()} method provides the empty value for that monad (e.g., an empty list or
 * {@code Optional.empty()}), which serves as the implementation for {@link Alternative#empty()}.
 *
 * <p>This interface is particularly important for enabling the {@code when()} (filtering) clause in
 * for-comprehensions, allowing computations to be short-circuited.
 *
 * <p><b>Key Operations:</b>
 *
 * <ul>
 *   <li>{@link #zero()} - The empty/failure element (implements {@link Alternative#empty()})
 *   <li>{@link Alternative#orElse(Kind, java.util.function.Supplier)} - Combine alternatives
 *   <li>{@link Alternative#guard(boolean)} - Conditional success
 * </ul>
 *
 * @param <F> The witness type of the Monad.
 * @see Monad
 * @see Alternative
 */
@NullMarked
public interface MonadZero<F extends WitnessArity<TypeArity.Unary>>
    extends Monad<F>, Alternative<F> {

  /**
   * Returns the "zero" or "empty" value for this Monad.
   *
   * <p>This method provides the implementation for {@link Alternative#empty()}. The {@code zero()}
   * method is retained for compatibility and semantic clarity in the context of MonadZero.
   *
   * @param <A> The type parameter of the Kind, which will not be present.
   * @return The empty value for the monad (non-null), e.g., {@code Optional.empty()}.
   */
  <A> Kind<F, A> zero();

  /**
   * Provides the {@link Alternative#empty()} implementation by delegating to {@link #zero()}.
   *
   * @param <A> The type parameter of the Kind
   * @return The empty value for this Alternative, same as {@link #zero()}
   */
  @Override
  default <A> Kind<F, A> empty() {
    return zero();
  }
}
