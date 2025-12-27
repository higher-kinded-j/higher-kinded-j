// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.maybe_t.MaybeT;

/**
 * Stage 3: Configure mapping functions for MaybeT testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <F> The outer monad witness type
 * @param <A> The type of the value potentially held by the inner Maybe
 */
public final class MaybeTOperationsStage<F extends WitnessArity<TypeArity.Unary>, A> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final MaybeT<F, A> justInstance;
  private final MaybeT<F, A> nothingInstance;

  MaybeTOperationsStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      MaybeT<F, A> justInstance,
      MaybeT<F, A> nothingInstance) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
  }

  /**
   * Provides mapping functions for testing map and flatMap operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param mapper The mapping function (A -> B)
   * @param <B> The mapped type
   * @return Configuration stage with execution options
   */
  public <B> MaybeTTestConfigStage<F, A, B> withMappers(Function<A, B> mapper) {
    return new MaybeTTestConfigStage<>(
        contextClass, outerMonad, justInstance, nothingInstance, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like
   * factory methods, value accessor).
   *
   * @return Configuration stage without mappers
   */
  public MaybeTTestConfigStage<F, A, String> withoutMappers() {
    return new MaybeTTestConfigStage<>(
        contextClass, outerMonad, justInstance, nothingInstance, null);
  }
}
