// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.io;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.io.IO;

/**
 * Stage for configuring Selective-specific test operations for IO.
 *
 * <p>Progressive disclosure: Shows Selective operation configuration and execution options.
 *
 * @param <A> The input type
 * @param <B> The result type for Selective operations
 */
public final class IOSelectiveStage<A, B> {
  private final Class<?> contextClass;
  private final IO<A> ioInstance;
  private final IO<Choice<A, B>> choiceLeft;
  private final IO<Choice<A, B>> choiceRight;
  private final IO<Boolean> booleanTrue;
  private final IO<Boolean> booleanFalse;

  IOSelectiveStage(
      Class<?> contextClass,
      IO<A> ioInstance,
      IO<Choice<A, B>> choiceLeft,
      IO<Choice<A, B>> choiceRight,
      IO<Boolean> booleanTrue,
      IO<Boolean> booleanFalse) {
    this.contextClass = contextClass;
    this.ioInstance = ioInstance;
    this.choiceLeft = choiceLeft;
    this.choiceRight = choiceRight;
    this.booleanTrue = booleanTrue;
    this.booleanFalse = booleanFalse;
  }

  /**
   * Provides function handlers for Selective operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param selectFunction Function for select operation (A -> B)
   * @param leftHandler Function for branch left case (A -> B)
   * @param rightHandler Function for branch right case (B -> B)
   * @return Configuration stage with execution options
   */
  public IOSelectiveConfigStage<A, B> withHandlers(
      Function<A, B> selectFunction, Function<A, B> leftHandler, Function<B, B> rightHandler) {
    return new IOSelectiveConfigStage<>(
        contextClass,
        ioInstance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler);
  }
}
