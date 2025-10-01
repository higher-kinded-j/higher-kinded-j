// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.traverse;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;

/**
 * Stage 4: Configure traverse and foldable operations.
 *
 * @param <F> The Traverse witness type
 * @param <G> The Applicative witness type
 * @param <A> The input type
 * @param <B> The output type
 * @param <M> The Monoid type
 */
public final class TraverseOperationsStage<F, G, A, B, M> {
  private final Class<?> contextClass;
  private final Traverse<F> traverse;
  private final Kind<F, A> validKind;
  private final Function<A, B> mapper;

  public TraverseOperationsStage(
          Class<?> contextClass, Traverse<F> traverse, Kind<F, A> validKind, Function<A, B> mapper) {
    this.contextClass = contextClass;
    this.traverse = traverse;
    this.validKind = validKind;
    this.mapper = mapper;
  }

  /**
   * Provides traverse-specific functions.
   *
   * @param applicative The Applicative for traverse
   * @param traverseFunction The traverse function
   * @param monoid The Monoid for foldMap
   * @param foldMapFunction The foldMap function
   * @return Configuration stage with execution options
   */
  public TraverseTestConfigStage<F, G, A, B, M> withTraverseOperations(
      Applicative<G> applicative,
      Function<A, Kind<G, B>> traverseFunction,
      Monoid<M> monoid,
      Function<A, M> foldMapFunction) {

    return new TraverseTestConfigStage<>(
        contextClass,
        traverse,
        validKind,
        mapper,
        applicative,
        traverseFunction,
        monoid,
        foldMapFunction);
  }
}
