// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.lazy;

import org.higherkindedj.hkt.lazy.Lazy;

/**
 * Stage 2: Complete Lazy instance configuration.
 *
 * <p>Progressive disclosure: Shows completion of instance setup.
 *
 * @param <A> The value type
 */
public final class LazyInstanceStage<A> {
  private final Class<?> contextClass;
  private final Lazy<A> deferredInstance;
  private final Lazy<A> nowInstance;

  LazyInstanceStage(Class<?> contextClass, Lazy<A> deferredInstance, Lazy<A> nowInstance) {
    this.contextClass = contextClass;
    this.deferredInstance = deferredInstance;
    this.nowInstance = nowInstance;
  }

  /**
   * Provides the now instance (if deferred was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param nowInstance An already-evaluated Lazy instance
   * @return Next stage for configuring mappers
   */
  public LazyOperationsStage<A> withNow(Lazy<A> nowInstance) {
    if (this.deferredInstance == null) {
      throw new IllegalStateException("Cannot set now twice");
    }
    return new LazyOperationsStage<>(contextClass, deferredInstance, nowInstance);
  }

  /**
   * Provides the deferred instance (if now was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param deferredInstance A deferred Lazy instance
   * @return Next stage for configuring mappers
   */
  public LazyOperationsStage<A> withDeferred(Lazy<A> deferredInstance) {
    if (this.nowInstance == null) {
      throw new IllegalStateException("Cannot set deferred twice");
    }
    return new LazyOperationsStage<>(contextClass, deferredInstance, nowInstance);
  }
}
