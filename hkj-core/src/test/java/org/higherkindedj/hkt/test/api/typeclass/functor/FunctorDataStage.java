// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.functor;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 3: Configure mapper function.
 *
 * <p>Progressive disclosure: Shows mapper configuration.
 *
 * @param <F> The Functor witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class FunctorDataStage<F extends WitnessArity<TypeArity.Unary>, A, B> {
  private final Class<?> contextClass;
  private final Functor<F> functor;
  private final Kind<F, A> validKind;

  FunctorDataStage(Class<?> contextClass, Functor<F> functor, Kind<F, A> validKind) {
    this.contextClass = contextClass;
    this.functor = functor;
    this.validKind = validKind;
  }

  /**
   * Provides the mapper function for testing.
   *
   * <p>Progressive disclosure: Next steps are optional configuration or test execution.
   *
   * @param mapper The mapping function (A -> B)
   * @return Configuration stage with test selection and execution options
   */
  public FunctorTestConfigStage<F, A, B> withMapper(Function<A, B> mapper) {
    return new FunctorTestConfigStage<>(contextClass, functor, validKind, mapper);
  }
}
