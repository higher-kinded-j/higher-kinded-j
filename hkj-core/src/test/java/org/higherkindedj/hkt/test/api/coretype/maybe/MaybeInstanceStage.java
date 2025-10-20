// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Stage 2: Complete Maybe instance configuration.
 *
 * <p>Progressive disclosure: Shows completion of instance setup.
 *
 * @param <T> The value type
 */
public final class MaybeInstanceStage<T> {
  private final Class<?> contextClass;
  private final Maybe<T> justInstance;
  private final Maybe<T> nothingInstance;

  MaybeInstanceStage(Class<?> contextClass, Maybe<T> justInstance, Maybe<T> nothingInstance) {
    this.contextClass = contextClass;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
  }

  /**
   * Provides the Nothing instance (if Just was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMapper(...)}
   *
   * @param nothingInstance A Nothing instance
   * @return Next stage for configuring mapper
   */
  public MaybeOperationsStage<T> withNothing(Maybe<T> nothingInstance) {
    if (this.justInstance == null) {
      throw new IllegalStateException("Cannot set Nothing twice");
    }
    return new MaybeOperationsStage<>(contextClass, justInstance, nothingInstance);
  }

  /**
   * Provides the Just instance (if Nothing was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMapper(...)}
   *
   * @param justInstance A Just instance
   * @return Next stage for configuring mapper
   */
  public MaybeOperationsStage<T> withJust(Maybe<T> justInstance) {
    if (this.nothingInstance == null) {
      throw new IllegalStateException("Cannot set Just twice");
    }
    return new MaybeOperationsStage<>(contextClass, justInstance, nothingInstance);
  }
}
