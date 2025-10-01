// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.foldable;

import java.util.function.Function;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;

/**
 * Stage 3: Configure Foldable operations.
 *
 * @param <F> The Foldable witness type
 * @param <A> The input type
 */
public final class FoldableDataStage<F, A> {
  private final Class<?> contextClass;
  private final Foldable<F> foldable;
  private final Kind<F, A> validKind;

  FoldableDataStage(Class<?> contextClass, Foldable<F> foldable, Kind<F, A> validKind) {
    this.contextClass = contextClass;
    this.foldable = foldable;
    this.validKind = validKind;
  }

  /**
   * Provides Foldable operations.
   *
   * @param monoid The Monoid for folding
   * @param foldMapFunction The foldMap function
   * @param <M> The Monoid type
   * @return Configuration stage with execution options
   */
  public <M> FoldableTestConfigStage<F, A, M> withOperations(
      Monoid<M> monoid, Function<A, M> foldMapFunction) {

    return new FoldableTestConfigStage<>(
        contextClass, foldable, validKind, monoid, foldMapFunction);
  }
}
