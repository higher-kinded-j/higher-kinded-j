// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import org.higherkindedj.hkt.validated.Validated;

/**
 * Stage 2: Complete Validated instance configuration.
 *
 * <p>Progressive disclosure: Shows completion of instance setup.
 *
 * @param <E> The error type
 * @param <A> The value type
 */
public final class ValidatedInstanceStage<E, A> {
  private final Class<?> contextClass;
  private final Validated<E, A> invalidInstance;
  private final Validated<E, A> validInstance;

  ValidatedInstanceStage(
      Class<?> contextClass, Validated<E, A> invalidInstance, Validated<E, A> validInstance) {
    this.contextClass = contextClass;
    this.invalidInstance = invalidInstance;
    this.validInstance = validInstance;
  }

  /**
   * Provides the Valid instance (if Invalid was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param validInstance A Valid instance
   * @return Next stage for configuring mappers
   */
  public ValidatedOperationsStage<E, A> withValid(Validated<E, A> validInstance) {
    if (this.invalidInstance == null) {
      throw new IllegalStateException("Cannot set Valid twice");
    }
    return new ValidatedOperationsStage<>(contextClass, invalidInstance, validInstance);
  }

  /**
   * Provides the Invalid instance (if Valid was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param invalidInstance An Invalid instance
   * @return Next stage for configuring mappers
   */
  public ValidatedOperationsStage<E, A> withInvalid(Validated<E, A> invalidInstance) {
    if (this.validInstance == null) {
      throw new IllegalStateException("Cannot set Invalid twice");
    }
    return new ValidatedOperationsStage<>(contextClass, invalidInstance, validInstance);
  }
}
