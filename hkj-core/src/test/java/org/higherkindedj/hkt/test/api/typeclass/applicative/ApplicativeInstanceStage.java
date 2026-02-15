// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.applicative;

import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Stage 2: Configure test data with Kind instance.
 *
 * <p>Progressive disclosure: Only shows test data configuration.
 *
 * @param <F> The Applicative witness type
 * @param <A> The input type
 */
public final class ApplicativeInstanceStage<F extends WitnessArity<TypeArity.Unary>, A> {
  private final Class<?> contextClass;
  private final Applicative<F> applicative;

  ApplicativeInstanceStage(Class<?> contextClass, Applicative<F> applicative) {
    this.contextClass = contextClass;
    this.applicative = applicative;
  }

  /**
   * Provides the test Kind instance.
   *
   * <p>Progressive disclosure: Next step is {@code .withOperations(...)}
   *
   * @param validKind A valid Kind instance for testing
   * @param <B> The output type for mapper
   * @return Next stage for configuring operations
   */
  public <B> ApplicativeDataStage<F, A, B> withKind(Kind<F, A> validKind) {
    return new ApplicativeDataStage<>(contextClass, applicative, validKind);
  }
}
