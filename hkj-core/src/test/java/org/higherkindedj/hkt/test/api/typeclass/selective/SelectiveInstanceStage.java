// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.selective;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 2: Configure the base Kind for testing.
 *
 * <p>Progressive disclosure: Shows only Kind configuration.
 *
 * @param <F> The Selective witness type
 * @param <A> The value type
 */
public final class SelectiveInstanceStage<F extends WitnessArity<TypeArity.Unary>, A> {
  private final Class<?> contextClass;
  private final Selective<F> selective;

  SelectiveInstanceStage(Class<?> contextClass, Selective<F> selective) {
    this.contextClass = contextClass;
    this.selective = selective;
  }

  /**
   * Provides the test Kind instance.
   *
   * <p>Progressive disclosure: Next step is {@code .withSelectiveOperations(...)}
   *
   * @param validKind A valid Kind instance for testing
   * @param <B> The mapped type
   * @return Next stage for configuring Selective operations
   */
  public <B> SelectiveDataStage<F, A, B> withKind(Kind<F, A> validKind) {
    return new SelectiveDataStage<>(contextClass, selective, validKind);
  }
}
