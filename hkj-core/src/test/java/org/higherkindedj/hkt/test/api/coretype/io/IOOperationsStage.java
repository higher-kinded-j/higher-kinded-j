// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.io;

import java.util.function.Function;
import org.higherkindedj.hkt.io.IO;

/**
 * Stage 2: Configure mapping function for IO testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <A> The value type
 */
public final class IOOperationsStage<A> {
  private final Class<?> contextClass;
  private final IO<A> ioInstance;

  IOOperationsStage(Class<?> contextClass, IO<A> ioInstance) {
    this.contextClass = contextClass;
    this.ioInstance = ioInstance;
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
  public <B> IOTestConfigStage<A, B> withMapper(Function<A, B> mapper) {
    return new IOTestConfigStage<>(contextClass, ioInstance, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like delay,
   * unsafeRunSync).
   *
   * @return Configuration stage without mapper
   */
  public IOTestConfigStage<A, String> withoutMapper() {
    return new IOTestConfigStage<>(contextClass, ioInstance, null);
  }

  /**
   * Configures Selective-specific test operations for IO.
   *
   * <p>Progressive disclosure: Next step is {@code .withHandlers(...)}
   *
   * @param choiceLeft IO containing Choice with Left value
   * @param choiceRight IO containing Choice with Right value
   * @param booleanTrue IO containing true
   * @param booleanFalse IO containing false
   * @param <B> The result type for Selective operations
   * @return Stage for configuring Selective handlers
   */
  public <B> IOSelectiveStage<A, B> withSelectiveOperations(
      IO<org.higherkindedj.hkt.Choice<A, B>> choiceLeft,
      IO<org.higherkindedj.hkt.Choice<A, B>> choiceRight,
      IO<Boolean> booleanTrue,
      IO<Boolean> booleanFalse) {
    return new IOSelectiveStage<>(
        contextClass, ioInstance, choiceLeft, choiceRight, booleanTrue, booleanFalse);
  }
}
