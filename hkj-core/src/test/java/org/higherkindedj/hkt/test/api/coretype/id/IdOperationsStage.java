// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import java.util.function.Function;
import org.higherkindedj.hkt.id.Id;

/**
 * Stage 2: Configure mapping functions for Id testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <A> The value type
 */
public final class IdOperationsStage<A> {
  private final Class<?> contextClass;
  private final Id<A> instance;

  IdOperationsStage(Class<?> contextClass, Id<A> instance) {
    this.contextClass = contextClass;
    this.instance = instance;
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
  public <B> IdTestConfigStage<A, B> withMappers(Function<A, B> mapper) {
    return new IdTestConfigStage<>(contextClass, instance, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers.
   *
   * @return Configuration stage without mappers
   */
  public IdTestConfigStage<A, A> withoutMappers() {
    return new IdTestConfigStage<>(contextClass, instance, null);
  }
}
