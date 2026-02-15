// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import org.higherkindedj.hkt.validated.Validated;

/**
 * Stage 1: Configure Validated test instances.
 *
 * <p>Entry point for Validated core type testing with progressive disclosure.
 *
 * @param <E> The error type
 * @param <A> The value type
 */
public final class ValidatedCoreTestStage<E, A> {
  private final Class<?> contextClass;

  public ValidatedCoreTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides an Invalid instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withValid(...)}
   *
   * @param invalidInstance An Invalid instance (can have null value)
   * @return Next stage for configuring Valid instance
   */
  public ValidatedInstanceStage<E, A> withInvalid(Validated<E, A> invalidInstance) {
    return new ValidatedInstanceStage<>(contextClass, invalidInstance, null);
  }

  /**
   * Provides a Valid instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withInvalid(...)}
   *
   * @param validInstance A Valid instance (can have null value)
   * @return Next stage for configuring Invalid instance
   */
  public ValidatedInstanceStage<E, A> withValid(Validated<E, A> validInstance) {
    return new ValidatedInstanceStage<>(contextClass, null, validInstance);
  }
}
