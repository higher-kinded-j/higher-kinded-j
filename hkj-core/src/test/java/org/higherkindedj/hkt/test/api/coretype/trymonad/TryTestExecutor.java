// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.trymonad;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.trymonad.*;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Try core type tests.
 *
 * <p>This class coordinates test execution by delegating to appropriate test methods.
 *
 * @param <T> The value type
 * @param <S> The mapped type
 */
final class TryTestExecutor<T, S> {
  private final Class<?> contextClass;
  private final Try<T> successInstance;
  private final Try<T> failureInstance;
  private final Function<T, S> mapper;

  private final boolean includeFactoryMethods;
  private final boolean includeGetters;
  private final boolean includeFold;
  private final boolean includeOrElse;
  private final boolean includeMap;
  private final boolean includeFlatMap;
  private final boolean includeRecover;
  private final boolean includeToEither;
  private final boolean includeValidations;
  private final boolean includeEdgeCases;

  private final TryValidationStage<T, S> validationStage;

  TryTestExecutor(
      Class<?> contextClass,
      Try<T> successInstance,
      Try<T> failureInstance,
      Function<T, S> mapper,
      boolean includeFactoryMethods,
      boolean includeGetters,
      boolean includeFold,
      boolean includeOrElse,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeRecover,
      boolean includeToEither,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        successInstance,
        failureInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeOrElse,
        includeMap,
        includeFlatMap,
        includeRecover,
        includeToEither,
        includeValidations,
        includeEdgeCases,
        null);
  }

  TryTestExecutor(
      Class<?> contextClass,
      Try<T> successInstance,
      Try<T> failureInstance,
      Function<T, S> mapper,
      boolean includeFactoryMethods,
      boolean includeGetters,
      boolean includeFold,
      boolean includeOrElse,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeRecover,
      boolean includeToEither,
      boolean includeValidations,
      boolean includeEdgeCases,
      TryValidationStage<T, S> validationStage) {

    this.contextClass = contextClass;
    this.successInstance = successInstance;
    this.failureInstance = failureInstance;
    this.mapper = mapper;
    this.includeFactoryMethods = includeFactoryMethods;
    this.includeGetters = includeGetters;
    this.includeFold = includeFold;
    this.includeOrElse = includeOrElse;
    this.includeMap = includeMap;
    this.includeFlatMap = includeFlatMap;
    this.includeRecover = includeRecover;
    this.includeToEither = includeToEither;
    this.includeValidations = includeValidations;
    this.includeEdgeCases = includeEdgeCases;
    this.validationStage = validationStage;
  }

  void executeAll() {
    if (includeFactoryMethods) testFactoryMethods();
    if (includeGetters) testGetters();
    if (includeFold) testFold();
    if (includeOrElse) testOrElse();
    if (includeMap && mapper != null) testMap();
    if (includeFlatMap && mapper != null) testFlatMap();
    if (includeRecover) testRecover();
    if (includeToEither) testToEither();
    if (includeValidations) testValidations();
    if (includeEdgeCases) testEdgeCases();
  }

  private void testFactoryMethods() {
    // Test that success() creates correct instances
    assertThat(successInstance).isInstanceOf(Try.Success.class);
    assertThat(successInstance.isSuccess()).isTrue();
    assertThat(successInstance.isFailure()).isFalse();

    // Test that failure() creates correct instances
    assertThat(failureInstance).isInstanceOf(Try.Failure.class);
    assertThat(failureInstance.isFailure()).isTrue();
    assertThat(failureInstance.isSuccess()).isFalse();
  }

  private void testGetters() {
    // Test get() on Success instance
    assertThatCode(() -> successInstance.get()).doesNotThrowAnyException();

    // Test get() on Failure instance throws
    assertThatThrownBy(() -> failureInstance.get())
        .isInstanceOf(Throwable.class)
        .hasMessageContaining("Test exception");
  }

  private void testFold() {
    Function<T, String> successMapper = t -> "Success: " + t;
    Function<Throwable, String> failureMapper = t -> "Failure: " + t.getMessage();

    // Test fold on Success
    String successResult = successInstance.fold(successMapper, failureMapper);
    assertThat(successResult).isNotNull().startsWith("Success:");

    // Test fold on Failure
    String failureResult = failureInstance.fold(successMapper, failureMapper);
    assertThat(failureResult).isNotNull().startsWith("Failure:");
  }

  private void testOrElse() {
    // Test orElse on Success
    T successValue = successInstance.orElse(null);
    assertThat(successValue).isNotNull();

    // Test orElse on Failure
    T failureValue = failureInstance.orElse(null);
    assertThat(failureValue).isNull();

    // Test orElseGet on Success
    T successGetValue = successInstance.orElseGet(() -> null);
    assertThat(successGetValue).isNotNull();

    // Test orElseGet on Failure
    T failureGetValue = failureInstance.orElseGet(() -> null);
    assertThat(failureGetValue).isNull();
  }

  private void testMap() {
    // Test map on Success
    Try<S> mappedSuccess = successInstance.map(mapper);
    assertThat(mappedSuccess.isSuccess()).isTrue();

    // Test map on Failure (should preserve Failure)
    Try<S> mappedFailure = failureInstance.map(mapper);
    assertThat(mappedFailure).isSameAs(failureInstance);
    assertThat(mappedFailure.isFailure()).isTrue();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: map test");
    Function<T, S> throwingMapper =
        t -> {
          throw testException;
        };
    Try<S> thrownResult = successInstance.map(throwingMapper);
    assertThat(thrownResult.isFailure()).isTrue();

    // Failure should not call mapper
    assertThatCode(() -> failureInstance.map(throwingMapper)).doesNotThrowAnyException();
  }

  private void testFlatMap() {
    Function<T, Try<S>> flatMapper = t -> Try.success(mapper.apply(t));

    // Test flatMap on Success
    Try<S> flatMappedSuccess = successInstance.flatMap(flatMapper);
    assertThat(flatMappedSuccess.isSuccess()).isTrue();

    // Test flatMap on Failure (should preserve Failure)
    Try<S> flatMappedFailure = failureInstance.flatMap(flatMapper);
    assertThat(flatMappedFailure).isSameAs(failureInstance);
    assertThat(flatMappedFailure.isFailure()).isTrue();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: flatMap test");
    Function<T, Try<S>> throwingFlatMapper =
        t -> {
          throw testException;
        };
    Try<S> thrownResult = successInstance.flatMap(throwingFlatMapper);
    assertThat(thrownResult.isFailure()).isTrue();

    // Failure should not call flatMapper
    assertThatCode(() -> failureInstance.flatMap(throwingFlatMapper)).doesNotThrowAnyException();
  }

  private void testRecover() {
    Function<Throwable, T> recoveryFunction = t -> null;

    // Test recover on Failure
    Try<T> recoveredFailure = failureInstance.recover(recoveryFunction);
    assertThat(recoveredFailure.isSuccess()).isTrue();

    // Test recover on Success (should preserve Success)
    Try<T> recoveredSuccess = successInstance.recover(recoveryFunction);
    assertThat(recoveredSuccess).isSameAs(successInstance);
    assertThat(recoveredSuccess.isSuccess()).isTrue();

    // Test recoverWith on Failure
    Function<Throwable, Try<T>> recoverWithFunction = t -> Try.success(null);
    Try<T> recoveredWithFailure = failureInstance.recoverWith(recoverWithFunction);
    assertThat(recoveredWithFailure.isSuccess()).isTrue();

    // Test recoverWith on Success (should preserve Success)
    Try<T> recoveredWithSuccess = successInstance.recoverWith(recoverWithFunction);
    assertThat(recoveredWithSuccess).isSameAs(successInstance);
    assertThat(recoveredWithSuccess.isSuccess()).isTrue();
  }

  private void testToEither() {
    Function<Throwable, String> failureToLeftMapper = Throwable::getMessage;

    // Test toEither on Success
    Either<String, T> successEither = successInstance.toEither(failureToLeftMapper);
    assertThat(successEither.isRight()).isTrue();

    // Test toEither on Failure
    Either<String, T> failureEither = failureInstance.toEither(failureToLeftMapper);
    assertThat(failureEither.isLeft()).isTrue();
    assertThat(failureEither.getLeft()).contains("Test exception");
  }

  void testValidations() {
    Function<T, String> successMapper = t -> "Success: " + t;
    Function<Throwable, String> failureMapper = t -> "Failure: " + t.getMessage();
    Function<Throwable, T> recoveryFunction = t -> null;

    // Determine which class context to use for map
    Class<?> mapContext =
        (validationStage != null && validationStage.getMapContext() != null)
            ? validationStage.getMapContext()
            : contextClass;

    // Determine which class context to use for flatMap
    Class<?> flatMapContext =
        (validationStage != null && validationStage.getFlatMapContext() != null)
            ? validationStage.getFlatMapContext()
            : contextClass;

    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Fold validations
    builder.assertFunctionNull(
        () -> successInstance.fold(null, failureMapper),
        "successMapper",
        contextClass,
        Operation.FOLD);
    builder.assertFunctionNull(
        () -> successInstance.fold(successMapper, null),
        "failureMapper",
        contextClass,
        Operation.FOLD);

    // Map validations - test through the Functor interface if custom context provided
    if (validationStage != null && validationStage.getMapContext() != null) {
      // Use the type class interface validation
      TryFunctor functor = new TryFunctor();
      Kind<TryKind.Witness, T> kind = TryKindHelper.TRY.widen(successInstance);
      builder.assertMapperNull(() -> functor.map(null, kind), mapContext, Operation.MAP);
    } else {
      // Use the instance method
      builder.assertMapperNull(() -> successInstance.map(null), mapContext, Operation.MAP);
    }

    // FlatMap validations - test through the Monad interface if custom context provided
    if (validationStage != null && validationStage.getFlatMapContext() != null) {
      // Use the type class interface validation
      TryMonad monad = TryMonad.INSTANCE;
      Kind<TryKind.Witness, T> kind = TryKindHelper.TRY.widen(successInstance);
      builder.assertFlatMapperNull(
          () -> monad.flatMap(null, kind), flatMapContext, Operation.FLAT_MAP);
    } else {
      // Use the instance method
      builder.assertFlatMapperNull(
          () -> successInstance.flatMap(null), flatMapContext, Operation.FLAT_MAP);
    }

    // Recover validations
    builder.assertFunctionNull(
        () -> failureInstance.recover(null), "recoveryFunction", contextClass, Operation.RECOVER);
    builder.assertFunctionNull(
        () -> failureInstance.recoverWith(null),
        "recoveryFunction",
        contextClass,
        Operation.RECOVER_WITH);

    // OrElseGet validations
    builder.assertFunctionNull(
        () -> failureInstance.orElseGet(null), "supplier", contextClass, Operation.OR_ELSE_GET);

    // ToEither validations
    builder.assertFunctionNull(
        () -> successInstance.toEither(null),
        "failureToLeftMapper",
        contextClass,
        Operation.TO_EITHER);

    builder.execute();
  }

  private void testEdgeCases() {
    // Test with null values (if instances allow them)
    Try<T> successNull = Try.success(null);
    assertThat(successNull.isSuccess()).isTrue();
    assertThatCode(() -> successNull.get()).doesNotThrowAnyException();

    try {
      assertThat(successNull.get()).isNull();
    } catch (Throwable t) {
      fail("Should not throw for Success", t);
    }

    // Test toString
    assertThat(successInstance.toString()).contains("Success(");
    assertThat(failureInstance.toString()).contains("Failure(");

    // Test equals and hashCode
    try {
      T value = successInstance.get();
      Try<T> anotherSuccess = Try.success(value);
      assertThat(successInstance).isEqualTo(anotherSuccess);
      assertThat(successInstance.hashCode()).isEqualTo(anotherSuccess.hashCode());
    } catch (Throwable t) {
      fail("Should not throw for Success", t);
    }

    // Test match for side effects
    AtomicBoolean successExecuted = new AtomicBoolean(false);
    AtomicBoolean failureExecuted = new AtomicBoolean(false);

    Consumer<T> successAction = t -> successExecuted.set(true);
    Consumer<Throwable> failureAction = t -> failureExecuted.set(true);

    // Test match on Success
    successInstance.match(successAction, failureAction);
    assertThat(successExecuted).isTrue();
    assertThat(failureExecuted).isFalse();

    // Reset and test Failure
    successExecuted.set(false);
    failureExecuted.set(false);

    // Test match on Failure
    failureInstance.match(successAction, failureAction);
    assertThat(successExecuted).isFalse();
    assertThat(failureExecuted).isTrue();
  }
}
