// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monad;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 1: Configure the Monad instance.
 *
 * <p>Entry point for Monad testing with progressive disclosure.
 *
 * @param <F> The Monad witness type
 */
public final class MonadTestStage<F extends WitnessArity<TypeArity.Unary>> {
  private final Class<?> contextClass;

  public MonadTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides the Monad instance to test.
   *
   * <p>Progressive disclosure: Next step is {@code .withKind(kind)}
   *
   * @param monad The Monad instance
   * @param <A> The value type
   * @return Next stage for configuring test data
   */
  public <A> MonadInstanceStage<F, A> instance(Monad<F> monad) {
    return new MonadInstanceStage<>(contextClass, monad);
  }
}
