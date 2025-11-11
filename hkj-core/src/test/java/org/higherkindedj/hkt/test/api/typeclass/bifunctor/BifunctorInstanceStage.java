// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.bifunctor;

import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;

/**
 * Stage for providing the Kind2 instance to test with.
 *
 * @param <F> The Bifunctor witness type
 * @param <A> The first type parameter
 * @param <B> The second type parameter
 */
public final class BifunctorInstanceStage<F, A, B> {

  private final Class<?> contextClass;
  private final Bifunctor<F> bifunctor;

  BifunctorInstanceStage(Class<?> contextClass, Bifunctor<F> bifunctor) {
    this.contextClass = contextClass;
    this.bifunctor = bifunctor;
  }

  /**
   * Provides a valid Kind2 instance for testing.
   *
   * @param kind2 The Kind2 instance containing values of type A and B
   * @return Next stage for providing mappers
   */
  public BifunctorDataStage<F, A, B> withKind2(Kind2<F, A, B> kind2) {
    return new BifunctorDataStage<>(contextClass, bifunctor, kind2);
  }
}
