// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.applicative;

import org.higherkindedj.hkt.Applicative;

/**
 * Stage 1: Configure the Applicative instance.
 *
 * <p>Entry point for Applicative testing with progressive disclosure.
 *
 * @param <F> The Applicative witness type
 */
public final class ApplicativeTestStage<F> {
  private final Class<?> contextClass;

  public ApplicativeTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides the Applicative instance to test.
   *
   * <p>Progressive disclosure: Next step is {@code .withKind(kind)}
   *
   * @param applicative The Applicative instance
   * @param <A> The value type
   * @return Next stage for configuring test data
   */
  public <A> ApplicativeInstanceStage<F, A> instance(Applicative<F> applicative) {
    return new ApplicativeInstanceStage<>(contextClass, applicative);
  }
}
