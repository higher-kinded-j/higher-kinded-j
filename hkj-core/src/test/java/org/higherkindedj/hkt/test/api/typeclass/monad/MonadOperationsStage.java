// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monad;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

public final class MonadOperationsStage<F, A, B> {
  private final Class<?> contextClass;
  private final Monad<F> monad;
  private final Kind<F, A> validKind;
  private final Kind<F, A> validKind2;
  private final Function<A, B> mapper;
  private final Function<A, Kind<F, B>> flatMapper;
  private final Kind<F, Function<A, B>> functionKind;
  private final BiFunction<A, A, B> combiningFunction;

  MonadOperationsStage(
      Class<?> contextClass,
      Monad<F> monad,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind,
      BiFunction<A, A, B> combiningFunction) {

    this.contextClass = contextClass;
    this.monad = monad;
    this.validKind = validKind;
    this.validKind2 = validKind2;
    this.mapper = mapper;
    this.flatMapper = flatMapper;
    this.functionKind = functionKind;
    this.combiningFunction = combiningFunction;
  }

  public MonadValidationStage<F, A, B> configureValidation() {
    return new MonadValidationStage<>(this, null);
  }

  public MonadLawsStage<F, A, B> withLawsTesting(
      A testValue,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    return new MonadLawsStage<>(this, testValue, testFunction, chainFunction, equalityChecker);
  }

  public MonadTestSelectionStage<F, A, B> selectTests() {
    return new MonadTestSelectionStage<>(this, null, null);
  }

  public void testOperationsAndValidations() {
    build(null).executeOperationsAndValidations();
  }

  public void testOperations() {
    build(null).executeOperations();
  }

  public void testValidations() {
    build(null).executeValidations();
  }

  public void testExceptions() {
    build(null).executeExceptions();
  }

  public void testAll() {
    throw new IllegalStateException(
        "Cannot test laws without law configuration. "
            + "Use .withLawsTesting() or .testOperationsAndValidations()");
  }

  MonadTestExecutor<F, A, B> build(MonadLawsStage<F, A, B> lawsStage) {
    return new MonadTestExecutor<>(
        contextClass,
        monad,
        validKind,
        validKind2,
        mapper,
        flatMapper,
        functionKind,
        combiningFunction,
        lawsStage,
        null);
  }

  MonadTestExecutor<F, A, B> build(
      MonadLawsStage<F, A, B> lawsStage, MonadValidationStage<F, A, B> validationStage) {
    return new MonadTestExecutor<>(
        contextClass,
        monad,
        validKind,
        validKind2,
        mapper,
        flatMapper,
        functionKind,
        combiningFunction,
        lawsStage,
        validationStage);
  }

  Class<?> getContextClass() {
    return contextClass;
  }

  Monad<F> getMonad() {
    return monad;
  }

  Kind<F, A> getValidKind() {
    return validKind;
  }

  Kind<F, A> getValidKind2() {
    return validKind2;
  }

  Function<A, B> getMapper() {
    return mapper;
  }

  Function<A, Kind<F, B>> getFlatMapper() {
    return flatMapper;
  }

  Kind<F, Function<A, B>> getFunctionKind() {
    return functionKind;
  }

  BiFunction<A, A, B> getCombiningFunction() {
    return combiningFunction;
  }
}
