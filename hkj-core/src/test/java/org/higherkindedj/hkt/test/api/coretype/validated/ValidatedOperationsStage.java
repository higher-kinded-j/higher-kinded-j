// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import java.util.function.Function;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Stage 3: Configure mapping functions for Validated testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <E> The error type
 * @param <A> The value type
 */
public final class ValidatedOperationsStage<E, A> {
  private final Class<?> contextClass;
  private final Validated<E, A> invalidInstance;
  private final Validated<E, A> validInstance;

  ValidatedOperationsStage(
      Class<?> contextClass, Validated<E, A> invalidInstance, Validated<E, A> validInstance) {
    this.contextClass = contextClass;
    this.invalidInstance = invalidInstance;
    this.validInstance = validInstance;
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
  public <B> ValidatedTestConfigStage<E, A, B> withMappers(Function<A, B> mapper) {
    return new ValidatedTestConfigStage<>(contextClass, invalidInstance, validInstance, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like fold,
   * ifValid, ifInvalid, getters).
   *
   * @return Configuration stage without mappers
   */
  public ValidatedTestConfigStage<E, A, String> withoutMappers() {
    return new ValidatedTestConfigStage<>(contextClass, invalidInstance, validInstance, null);
  }
}
