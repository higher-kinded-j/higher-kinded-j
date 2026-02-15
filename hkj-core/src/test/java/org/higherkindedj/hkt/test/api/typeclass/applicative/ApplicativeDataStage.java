// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.applicative;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 3: Configure Applicative operations.
 *
 * <p>Progressive disclosure: Shows operation configuration.
 *
 * @param <F> The Applicative witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class ApplicativeDataStage<F extends WitnessArity<TypeArity.Unary>, A, B> {
  private final Class<?> contextClass;
  private final Applicative<F> applicative;
  private final Kind<F, A> validKind;

  ApplicativeDataStage(Class<?> contextClass, Applicative<F> applicative, Kind<F, A> validKind) {
    this.contextClass = contextClass;
    this.applicative = applicative;
    this.validKind = validKind;
  }

  /**
   * Provides Applicative operation functions.
   *
   * <p>Progressive disclosure: Next steps are optional configuration or execution.
   *
   * @param validKind2 A second Kind for map2 testing
   * @param mapper The map function (A -> B)
   * @param functionKind A Kind containing a function for ap testing
   * @param combiningFunction Combining function for map2
   * @return Configuration stage with execution options
   */
  public ApplicativeOperationsStage<F, A, B> withOperations(
      Kind<F, A> validKind2,
      Function<A, B> mapper,
      Kind<F, Function<A, B>> functionKind,
      BiFunction<A, A, B> combiningFunction) {

    return new ApplicativeOperationsStage<>(
        contextClass, applicative, validKind, validKind2, mapper, functionKind, combiningFunction);
  }
}
