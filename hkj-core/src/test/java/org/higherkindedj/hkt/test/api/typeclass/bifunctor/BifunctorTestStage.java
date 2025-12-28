// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.bifunctor;

import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Entry point for Bifunctor implementation testing.
 *
 * <p>Usage pattern:
 *
 * <pre>{@code
 * TypeClassTest.bifunctor(MyBifunctor.class)
 *     .instance(bifunctor)
 *     .withKind2(validKind)
 *     .withFirstMapper(leftMapper)
 *     .withSecondMapper(rightMapper)
 *     .testAll();
 * }</pre>
 *
 * @param <F> The Bifunctor witness type
 */
public final class BifunctorTestStage<F extends WitnessArity<TypeArity.Binary>> {

  private final Class<?> contextClass;

  public BifunctorTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides the Bifunctor instance to test.
   *
   * @param bifunctor The Bifunctor implementation
   * @param <A> The first type parameter
   * @param <B> The second type parameter
   * @return Next stage for providing test data
   */
  public <A, B> BifunctorInstanceStage<F, A, B> instance(Bifunctor<F> bifunctor) {
    return new BifunctorInstanceStage<>(contextClass, bifunctor);
  }
}
