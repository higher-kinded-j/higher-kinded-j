// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.reader.Reader;

/**
 * Stage for configuring Selective-specific test operations for Reader.
 *
 * <p>Progressive disclosure: Shows Selective operation configuration and execution options.
 *
 * @param <R> The environment type
 * @param <A> The input type
 * @param <B> The result type for Selective operations
 */
public final class ReaderSelectiveStage<R, A, B> {
  private final Class<?> contextClass;
  private final Reader<R, A> readerInstance;
  private final R environment;
  private final Reader<R, Choice<A, B>> choiceLeft;
  private final Reader<R, Choice<A, B>> choiceRight;
  private final Reader<R, Boolean> booleanTrue;
  private final Reader<R, Boolean> booleanFalse;

  ReaderSelectiveStage(
      Class<?> contextClass,
      Reader<R, A> readerInstance,
      R environment,
      Reader<R, Choice<A, B>> choiceLeft,
      Reader<R, Choice<A, B>> choiceRight,
      Reader<R, Boolean> booleanTrue,
      Reader<R, Boolean> booleanFalse) {
    this.contextClass = contextClass;
    this.readerInstance = readerInstance;
    this.environment = environment;
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
  public ReaderSelectiveConfigStage<R, A, B> withHandlers(
      Function<A, B> selectFunction, Function<A, B> leftHandler, Function<B, B> rightHandler) {
    return new ReaderSelectiveConfigStage<>(
        contextClass,
        readerInstance,
        environment,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler);
  }
}
