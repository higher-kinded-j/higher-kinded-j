// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import org.higherkindedj.hkt.id.Id;

/**
 * Stage 1: Configure Id test instance.
 *
 * <p>Entry point for Id core type testing with progressive disclosure.
 *
 * @param <A> The value type
 */
public final class IdCoreTestStage<A> {
  private final Class<?> contextClass;

  public IdCoreTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides an Id instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param instance An Id instance (can have null value)
   * @return Next stage for configuring mappers
   */
  public IdOperationsStage<A> withInstance(Id<A> instance) {
    return new IdOperationsStage<>(contextClass, instance);
  }
}
