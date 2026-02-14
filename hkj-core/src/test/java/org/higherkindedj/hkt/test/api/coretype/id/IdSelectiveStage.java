// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.id.Id;

/**
 * Stage for configuring Selective-specific test operations for Id.
 *
 * <p>Progressive disclosure: Shows Selective operation configuration and execution options.
 *
 * @param <A> The value type
 * @param <B> The result type for Selective operations
 */
public final class IdSelectiveStage<A, B> {
  private final Class<?> contextClass;
  private final Id<A> instance;
  private final Id<Choice<A, B>> choiceLeft;
  private final Id<Choice<A, B>> choiceRight;
  private final Id<Boolean> booleanTrue;
  private final Id<Boolean> booleanFalse;

  IdSelectiveStage(
      Class<?> contextClass,
      Id<A> instance,
      Id<Choice<A, B>> choiceLeft,
      Id<Choice<A, B>> choiceRight,
      Id<Boolean> booleanTrue,
      Id<Boolean> booleanFalse) {
    this.contextClass = contextClass;
    this.instance = instance;
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
  public IdSelectiveConfigStage<A, B> withHandlers(
      Function<A, B> selectFunction, Function<A, B> leftHandler, Function<B, B> rightHandler) {
    return new IdSelectiveConfigStage<>(
        contextClass,
        instance,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler);
  }
}
