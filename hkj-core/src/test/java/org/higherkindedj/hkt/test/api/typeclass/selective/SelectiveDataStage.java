// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 3: Configure Selective-specific operations.
 *
 * <p>Progressive disclosure: Shows Selective operation configuration.
 *
 * @param <F> The Selective witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class SelectiveDataStage<F extends WitnessArity<TypeArity.Unary>, A, B> {
  private final Class<?> contextClass;
  private final Selective<F> selective;
  private final Kind<F, A> validKind;

  SelectiveDataStage(Class<?> contextClass, Selective<F> selective, Kind<F, A> validKind) {
    this.contextClass = contextClass;
    this.selective = selective;
    this.validKind = validKind;
  }

  /**
   * Provides Selective operation parameters.
   *
   * <p>Progressive disclosure: Next step is {@code .withOperations(...)}
   *
   * @param validChoiceKind A Kind containing a Choice for select testing
   * @param validFunctionKind A Kind containing a function for select testing
   * @param <C> The result type for branch operations
   * @return Next stage for configuring all operations
   */
  public <C> SelectiveOperationsStage<F, A, B, C> withSelectiveOperations(
      Kind<F, Choice<A, B>> validChoiceKind, Kind<F, Function<A, B>> validFunctionKind) {

    return new SelectiveOperationsStage<>(
        contextClass, selective, validKind, validChoiceKind, validFunctionKind);
  }
}
