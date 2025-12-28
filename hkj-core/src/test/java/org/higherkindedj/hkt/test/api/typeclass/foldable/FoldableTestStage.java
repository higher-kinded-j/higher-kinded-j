// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.foldable;

import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

public final class FoldableTestStage<F extends WitnessArity<TypeArity.Unary>> {
  private final Class<?> contextClass;

  public FoldableTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  public <A> FoldableInstanceStage<F, A> instance(Foldable<F> foldable) {
    return new FoldableInstanceStage<>(contextClass, foldable);
  }
}
