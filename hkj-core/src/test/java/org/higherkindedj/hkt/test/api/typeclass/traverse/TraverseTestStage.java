// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.traverse;

import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

public final class TraverseTestStage<F extends WitnessArity<TypeArity.Unary>> {
  private final Class<?> contextClass;

  public TraverseTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  public <A> TraverseInstanceStage<F, A> instance(Traverse<F> traverse) {
    return new TraverseInstanceStage<>(contextClass, traverse);
  }
}
