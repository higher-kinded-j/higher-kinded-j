// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.foldable;

import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;

/**
 * Stage 2: Configure test data with Kind instance.
 *
 * @param <F> The Foldable witness type
 * @param <A> The input type
 */
public final class FoldableInstanceStage<F, A> {
  private final Class<?> contextClass;
  private final Foldable<F> foldable;

  FoldableInstanceStage(Class<?> contextClass, Foldable<F> foldable) {
    this.contextClass = contextClass;
    this.foldable = foldable;
  }

  /**
   * Provides the test Kind instance.
   *
   * @param validKind A valid Kind instance for testing
   * @return Next stage for configuring operations
   */
  public FoldableDataStage<F, A> withKind(Kind<F, A> validKind) {
    return new FoldableDataStage<>(contextClass, foldable, validKind);
  }
}
