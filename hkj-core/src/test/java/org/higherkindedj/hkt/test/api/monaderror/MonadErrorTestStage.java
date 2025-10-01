// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.monaderror;

import org.higherkindedj.hkt.MonadError;

/**
 * Stage 1: Configure the MonadError instance.
 *
 * <p>Entry point for MonadError testing with progressive disclosure.
 *
 * @param <F> The MonadError witness type
 * @param <E> The error type
 */
public final class MonadErrorTestStage<F, E> {
  private final Class<?> contextClass;

  public MonadErrorTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides the MonadError instance to test.
   *
   * <p>Progressive disclosure: Next step is {@code .withKind(kind)}
   *
   * @param monadError The MonadError instance
   * @param <A> The value type
   * @return Next stage for configuring test data
   */
  public <A> MonadErrorInstanceStage<F, E, A> instance(MonadError<F, E> monadError) {
    return new MonadErrorInstanceStage<>(contextClass, monadError);
  }
}
