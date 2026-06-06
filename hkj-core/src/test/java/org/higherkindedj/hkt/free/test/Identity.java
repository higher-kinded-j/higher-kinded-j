// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free.test;

import java.util.function.Function;

/**
 * Simple Identity type for testing Free monad.
 *
 * <p>Identity is the simplest possible functor - it just wraps a value without adding any
 * additional behavior.
 *
 * @param <A> The wrapped value type
 */
public record Identity<A>(A value) {

  /**
   * Maps a function over the wrapped value.
   *
   * @param f The function to apply
   * @param <B> The result type
   * @return A new Identity with the mapped value
   */
  public <B> Identity<B> map(Function<? super A, ? extends B> f) {
    return new Identity<>(f.apply(value));
  }

  /**
   * FlatMaps a function over the wrapped value.
   *
   * @param f The function to apply
   * @param <B> The result type
   * @return The result of applying the function
   */
  public <B> Identity<B> flatMap(Function<? super A, Identity<B>> f) {
    return f.apply(value);
  }
}
