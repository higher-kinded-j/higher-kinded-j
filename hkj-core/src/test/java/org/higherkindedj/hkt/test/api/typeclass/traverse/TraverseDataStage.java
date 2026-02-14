// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.traverse;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 3: Configure basic functor operation.
 *
 * @param <F> The Traverse witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class TraverseDataStage<F extends WitnessArity<TypeArity.Unary>, A, B> {
  private final Class<?> contextClass;
  private final Traverse<F> traverse;
  private final Kind<F, A> validKind;

  TraverseDataStage(Class<?> contextClass, Traverse<F> traverse, Kind<F, A> validKind) {
    this.contextClass = contextClass;
    this.traverse = traverse;
    this.validKind = validKind;
  }

  /**
   * Provides map operation function.
   *
   * <p>Progressive disclosure: Next step is {@code .withApplicative(...)}
   *
   * @param mapper The map function (A -> B)
   * @return Next stage for configuring applicative operations
   */
  public TraverseMapperStage<F, A, B> withOperations(Function<A, B> mapper) {
    return new TraverseMapperStage<>(contextClass, traverse, validKind, mapper);
  }
}
