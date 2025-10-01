// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.traverse;

import org.higherkindedj.hkt.Traverse;

public final class TraverseTestStage<F> {
  private final Class<?> contextClass;

  public TraverseTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  public <A> TraverseInstanceStage<F, A> instance(Traverse<F> traverse) {
    return new TraverseInstanceStage<>(contextClass, traverse);
  }
}
