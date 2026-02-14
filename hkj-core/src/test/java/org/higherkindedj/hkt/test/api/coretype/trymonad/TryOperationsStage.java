// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.trymonad;

import java.util.function.Function;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * Stage 3: Configure mapping functions for Try testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <T> The value type
 */
public final class TryOperationsStage<T> {
  private final Class<?> contextClass;
  private final Try<T> successInstance;
  private final Try<T> failureInstance;

  TryOperationsStage(Class<?> contextClass, Try<T> successInstance, Try<T> failureInstance) {
    this.contextClass = contextClass;
    this.successInstance = successInstance;
    this.failureInstance = failureInstance;
  }

  /**
   * Provides mapping functions for testing map and flatMap operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param mapper The mapping function (T -> S)
   * @param <S> The mapped type
   * @return Configuration stage with execution options
   */
  public <S> TryTestConfigStage<T, S> withMappers(Function<T, S> mapper) {
    return new TryTestConfigStage<>(contextClass, successInstance, failureInstance, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like fold,
   * orElse, get).
   *
   * @return Configuration stage without mappers
   */
  public TryTestConfigStage<T, String> withoutMappers() {
    return new TryTestConfigStage<>(contextClass, successInstance, failureInstance, null);
  }
}
