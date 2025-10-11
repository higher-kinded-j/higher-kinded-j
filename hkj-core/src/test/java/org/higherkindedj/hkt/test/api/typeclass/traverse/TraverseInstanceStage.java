// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.traverse;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;

/**
 * Stage 2: Configure test data with Kind instance.
 *
 * @param <F> The Traverse witness type
 * @param <A> The input type
 */
public final class TraverseInstanceStage<F, A> {
  private final Class<?> contextClass;
  private final Traverse<F> traverse;

  TraverseInstanceStage(Class<?> contextClass, Traverse<F> traverse) {
    this.contextClass = contextClass;
    this.traverse = traverse;
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
  public <B> TraverseDataStage<F, A, B> withKind(Kind<F, A> validKind) {
    return new TraverseDataStage<>(contextClass, traverse, validKind);
  }
}
