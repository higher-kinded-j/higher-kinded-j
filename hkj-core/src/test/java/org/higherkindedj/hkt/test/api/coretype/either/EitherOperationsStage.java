// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;

/**
 * Stage 3: Configure mapping functions for Either testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 */
public final class EitherOperationsStage<L, R> {
  private final Class<?> contextClass;
  private final Either<L, R> leftInstance;
  private final Either<L, R> rightInstance;

  EitherOperationsStage(
      Class<?> contextClass, Either<L, R> leftInstance, Either<L, R> rightInstance) {
    this.contextClass = contextClass;
    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
  }

  /**
   * Provides mapping functions for testing map and flatMap operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param mapper The mapping function (R -> String)
   * @param <S> The mapped type
   * @return Configuration stage with execution options
   */
  public <S> EitherTestConfigStage<L, R, S> withMappers(Function<R, S> mapper) {
    return new EitherTestConfigStage<>(contextClass, leftInstance, rightInstance, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like fold,
   * ifLeft, ifRight, getters).
   *
   * @return Configuration stage without mappers
   */
  public EitherTestConfigStage<L, R, String> withoutMappers() {
    return new EitherTestConfigStage<>(contextClass, leftInstance, rightInstance, null);
  }
}
