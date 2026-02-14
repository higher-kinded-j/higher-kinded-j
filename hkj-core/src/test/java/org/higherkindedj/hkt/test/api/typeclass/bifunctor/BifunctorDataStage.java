// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.bifunctor;

import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage for providing mapping functions for both type parameters.
 *
 * @param <F> The Bifunctor witness type
 * @param <A> The first type parameter
 * @param <B> The second type parameter
 */
public final class BifunctorDataStage<F extends WitnessArity<TypeArity.Binary>, A, B> {

  private final Class<?> contextClass;
  private final Bifunctor<F> bifunctor;
  private final Kind2<F, A, B> validKind;

  BifunctorDataStage(Class<?> contextClass, Bifunctor<F> bifunctor, Kind2<F, A, B> validKind) {
    this.contextClass = contextClass;
    this.bifunctor = bifunctor;
    this.validKind = validKind;
  }

  /**
   * Provides a mapping function for the first type parameter.
   *
   * @param firstMapper Function to transform values of type A
   * @param <C> The result type for the first parameter
   * @return Stage for providing second mapper
   */
  public <C> BifunctorSecondMapperStage<F, A, B, C> withFirstMapper(Function<A, C> firstMapper) {
    return new BifunctorSecondMapperStage<>(contextClass, bifunctor, validKind, firstMapper);
  }
}
