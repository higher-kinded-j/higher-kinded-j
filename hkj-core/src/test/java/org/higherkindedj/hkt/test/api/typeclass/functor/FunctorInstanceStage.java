// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.functor;

import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;

/**
 * Stage 2: Configure test data with Kind instance.
 *
 * <p>Progressive disclosure: Only shows test data configuration.
 *
 * @param <F> The Functor witness type
 * @param <A> The input type
 */
public final class FunctorInstanceStage<F, A> {
  private final Class<?> contextClass;
  private final Functor<F> functor;

  FunctorInstanceStage(Class<?> contextClass, Functor<F> functor) {
    this.contextClass = contextClass;
    this.functor = functor;
  }

  /**
   * Provides the test Kind instance.
   *
   * <p>Progressive disclosure: Next step is {@code .withMapper(mapper)}
   *
   * @param validKind A valid Kind instance for testing
   * @param <B> The output type for mapper
   * @return Next stage for configuring mapper
   */
  public <B> FunctorDataStage<F, A, B> withKind(Kind<F, A> validKind) {
    return new FunctorDataStage<>(contextClass, functor, validKind);
  }
}
