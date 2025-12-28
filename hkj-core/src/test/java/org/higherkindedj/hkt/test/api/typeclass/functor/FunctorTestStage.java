// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.functor;

import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 1: Configure the Functor instance.
 *
 * <p>Entry point for Functor testing with progressive disclosure.
 *
 * @param <F> The Functor witness type
 */
public final class FunctorTestStage<F extends WitnessArity<TypeArity.Unary>> {
  private final Class<?> contextClass;

  public FunctorTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides the Functor instance to test.
   *
   * <p>Progressive disclosure: Next step is {@code .withKind(kind)}
   *
   * @param functor The Functor instance
   * @param <A> The value type
   * @return Next stage for configuring test data
   */
  public <A> FunctorInstanceStage<F, A> instance(Functor<F> functor) {
    return new FunctorInstanceStage<>(contextClass, functor);
  }
}
