// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.lazy;

import org.higherkindedj.hkt.lazy.Lazy;

/**
 * Stage 1: Configure Lazy test instances.
 *
 * <p>Entry point for Lazy core type testing with progressive disclosure.
 *
 * @param <A> The value type
 */
public final class LazyCoreTestStage<A> {
  private final Class<?> contextClass;

  public LazyCoreTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides a deferred Lazy instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withNow(...)}
   *
   * @param deferredInstance A deferred Lazy instance
   * @return Next stage for configuring now instance
   */
  public LazyInstanceStage<A> withDeferred(Lazy<A> deferredInstance) {
    return new LazyInstanceStage<>(contextClass, deferredInstance, null);
  }

  /**
   * Provides an already-evaluated Lazy instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withDeferred(...)}
   *
   * @param nowInstance An already-evaluated Lazy instance
   * @return Next stage for configuring deferred instance
   */
  public LazyInstanceStage<A> withNow(Lazy<A> nowInstance) {
    return new LazyInstanceStage<>(contextClass, null, nowInstance);
  }
}
