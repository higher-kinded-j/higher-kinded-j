// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.bifunctor;

import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;

/**
 * Stage for providing mapping function for the second type parameter.
 *
 * @param <F> The Bifunctor witness type
 * @param <A> The first type parameter
 * @param <B> The second type parameter
 * @param <C> The mapped first type parameter
 */
public final class BifunctorSecondMapperStage<F, A, B, C> {

  private final Class<?> contextClass;
  private final Bifunctor<F> bifunctor;
  private final Kind2<F, A, B> validKind;
  private final Function<A, C> firstMapper;

  BifunctorSecondMapperStage(
      Class<?> contextClass,
      Bifunctor<F> bifunctor,
      Kind2<F, A, B> validKind,
      Function<A, C> firstMapper) {
    this.contextClass = contextClass;
    this.bifunctor = bifunctor;
    this.validKind = validKind;
    this.firstMapper = firstMapper;
  }

  /**
   * Provides a mapping function for the second type parameter.
   *
   * @param secondMapper Function to transform values of type B
   * @param <D> The result type for the second parameter
   * @return Configuration stage for optional settings and test execution
   */
  public <D> BifunctorTestConfigStage<F, A, B, C, D> withSecondMapper(Function<B, D> secondMapper) {
    return new BifunctorTestConfigStage<>(
        contextClass, bifunctor, validKind, firstMapper, secondMapper);
  }
}
