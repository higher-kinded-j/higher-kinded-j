// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.test.api.coretype.common.BaseSelectiveTestConfigStage;

/**
 * Configuration stage for Reader Selective tests.
 *
 * <p>Progressive disclosure: All required parameters configured. Shows test selection and execution
 * options.
 *
 * @param <R> The environment type
 * @param <A> The input type
 * @param <B> The result type
 */
public final class ReaderSelectiveConfigStage<R, A, B>
    extends BaseSelectiveTestConfigStage<ReaderSelectiveConfigStage<R, A, B>> {

  private final Class<?> contextClass;
  private final Reader<R, A> readerInstance;
  private final R environment;
  private final Reader<R, Choice<A, B>> choiceLeft;
  private final Reader<R, Choice<A, B>> choiceRight;
  private final Reader<R, Boolean> booleanTrue;
  private final Reader<R, Boolean> booleanFalse;
  private final Function<A, B> selectFunction;
  private final Function<A, B> leftHandler;
  private final Function<B, B> rightHandler;

  ReaderSelectiveConfigStage(
      Class<?> contextClass,
      Reader<R, A> readerInstance,
      R environment,
      Reader<R, Choice<A, B>> choiceLeft,
      Reader<R, Choice<A, B>> choiceRight,
      Reader<R, Boolean> booleanTrue,
      Reader<R, Boolean> booleanFalse,
      Function<A, B> selectFunction,
      Function<A, B> leftHandler,
      Function<B, B> rightHandler) {
    this.contextClass = contextClass;
    this.readerInstance = readerInstance;
    this.environment = environment;
    this.choiceLeft = choiceLeft;
    this.choiceRight = choiceRight;
    this.booleanTrue = booleanTrue;
    this.booleanFalse = booleanFalse;
    this.selectFunction = selectFunction;
    this.leftHandler = leftHandler;
    this.rightHandler = rightHandler;
  }

  @Override
  protected ReaderSelectiveConfigStage<R, A, B> self() {
    return this;
  }

  @Override
  public void testAll() {
    buildExecutor().executeAll();
  }

  @Override
  public ReaderSelectiveValidationStage<R, A, B> configureValidation() {
    return new ReaderSelectiveValidationStage<>(this);
  }

  private ReaderSelectiveTestExecutor<R, A, B> buildExecutor() {
    return new ReaderSelectiveTestExecutor<>(
        contextClass,
        readerInstance,
        environment,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler,
        includeSelect,
        includeBranch,
        includeWhenS,
        includeIfS,
        includeValidations,
        includeEdgeCases);
  }

  ReaderSelectiveTestExecutor<R, A, B> buildExecutorWithValidation(
      ReaderSelectiveValidationStage<R, A, B> validationStage) {
    return new ReaderSelectiveTestExecutor<>(
        contextClass,
        readerInstance,
        environment,
        choiceLeft,
        choiceRight,
        booleanTrue,
        booleanFalse,
        selectFunction,
        leftHandler,
        rightHandler,
        includeSelect,
        includeBranch,
        includeWhenS,
        includeIfS,
        includeValidations,
        includeEdgeCases,
        validationStage);
  }
}
