// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 1: Configure the Selective instance.
 *
 * <p>Entry point for Selective testing with progressive disclosure.
 *
 * @param <F> The Selective witness type
 */
public final class SelectiveTestStage<F extends WitnessArity<TypeArity.Unary>> {
  private final Class<?> contextClass;

  public SelectiveTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides the Selective instance to test.
   *
   * <p>Progressive disclosure: Next step is {@code .withKind(kind)}
   *
   * @param selective The Selective instance
   * @param <A> The value type
   * @return Next stage for configuring test data
   */
  public <A> SelectiveInstanceStage<F, A> instance(Selective<F> selective) {
    return new SelectiveInstanceStage<>(contextClass, selective);
  }
}
