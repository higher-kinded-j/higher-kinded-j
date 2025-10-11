// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.traverse;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;

/**
 * Stage 5: Configure foldable operations.
 *
 * <p>This stage captures the monoid type parameter M, completing type inference.
 *
 * @param <F> The Traverse witness type
 * @param <G> The Applicative witness type
 * @param <A> The input type
 * @param <B> The output type
 */
public final class TraverseApplicativeStage<F, G, A, B> {
  private final Class<?> contextClass;
  private final Traverse<F> traverse;
  private final Kind<F, A> validKind;
  private final Function<A, B> mapper;
  private final Applicative<G> applicative;
  private final Function<A, Kind<G, B>> traverseFunction;

  TraverseApplicativeStage(
      Class<?> contextClass,
      Traverse<F> traverse,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Applicative<G> applicative,
      Function<A, Kind<G, B>> traverseFunction) {

    this.contextClass = contextClass;
    this.traverse = traverse;
    this.validKind = validKind;
    this.mapper = mapper;
    this.applicative = applicative;
    this.traverseFunction = traverseFunction;
  }

  /**
   * Provides monoid and foldMap function.
   *
   * <p>Progressive disclosure: Next steps are optional configuration or execution.
   *
   * <p>The type parameter M is automatically inferred from the monoid parameter.
   *
   * @param monoid The Monoid for foldMap
   * @param foldMapFunction The foldMap function
   * @param <M> The Monoid type (automatically inferred)
   * @return Configuration stage with execution options
   */
  public <M> TraverseTestConfigStage<F, G, A, B, M> withFoldableOperations(
      Monoid<M> monoid, Function<A, M> foldMapFunction) {

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
