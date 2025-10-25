// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either;

import static org.assertj.core.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherFunctor;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Either core type tests.
 *
 * <p>This class coordinates test execution by delegating to appropriate test methods.
 *
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The mapped type
 */
final class EitherTestExecutor<L, R, S>
    extends BaseCoreTypeTestExecutor<R, S, EitherValidationStage<L, R, S>> {

  private final Either<L, R> leftInstance;
  private final Either<L, R> rightInstance;

  private final boolean includeFactoryMethods;
  private final boolean includeGetters;
  private final boolean includeFold;
  private final boolean includeSideEffects;
  private final boolean includeMap;
  private final boolean includeFlatMap;

  EitherTestExecutor(
      Class<?> contextClass,
      Either<L, R> leftInstance,
      Either<L, R> rightInstance,
      Function<R, S> mapper,
      boolean includeFactoryMethods,
      boolean includeGetters,
      boolean includeFold,
      boolean includeSideEffects,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        leftInstance,
        rightInstance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeFold,
        includeSideEffects,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        null);
  }

  EitherTestExecutor(
      Class<?> contextClass,
      Either<L, R> leftInstance,
      Either<L, R> rightInstance,
      Function<R, S> mapper,
      boolean includeFactoryMethods,
      boolean includeGetters,
      boolean includeFold,
      boolean includeSideEffects,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases,
      EitherValidationStage<L, R, S> validationStage) {

    super(contextClass, mapper, includeValidations, includeEdgeCases, validationStage);

    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
    this.includeFactoryMethods = includeFactoryMethods;
    this.includeGetters = includeGetters;
    this.includeFold = includeFold;
    this.includeSideEffects = includeSideEffects;
    this.includeMap = includeMap;
    this.includeFlatMap = includeFlatMap;
  }

  @Override
  protected void executeOperationTests() {
    if (includeFactoryMethods) testFactoryMethods();
    if (includeGetters) testGetters();
    if (includeFold) testFold();
    if (includeSideEffects) testSideEffects();
    if (includeMap && hasMapper()) testMap();
    if (includeFlatMap && hasMapper()) testFlatMap();
  }

  @Override
  protected void executeValidationTests() {
    Function<L, String> leftMapper = l -> "Left: " + l;
    Function<R, String> rightMapper = r -> "Right: " + r;
    Consumer<L> leftAction = l -> {};
    Consumer<R> rightAction = r -> {};

    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Fold validations
    builder.assertFunctionNull(
        () -> leftInstance.fold(null, rightMapper), "leftMapper", contextClass, Operation.FOLD);
    builder.assertFunctionNull(
        () -> leftInstance.fold(leftMapper, null), "rightMapper", contextClass, Operation.FOLD);

    // Map validations - test through the Functor interface if custom context provided
    if (validationStage != null && validationStage.getMapContext() != null) {
      EitherFunctor<L> functor = EitherFunctor.instance();
      Kind<EitherKind.Witness<L>, R> kind = EitherKindHelper.EITHER.widen(rightInstance);
      builder.assertMapperNull(() -> functor.map(null, kind), "f", getMapContext(), Operation.MAP);
    } else {
      builder.assertMapperNull(
          () -> rightInstance.map(null), "mapper", getMapContext(), Operation.MAP);
    }

    // FlatMap validations - test through the Monad interface if custom context provided
    if (validationStage != null && validationStage.getFlatMapContext() != null) {
      EitherMonad<L> monad = EitherMonad.instance();
      Kind<EitherKind.Witness<L>, R> kind = EitherKindHelper.EITHER.widen(rightInstance);
      builder.assertFlatMapperNull(
          () -> monad.flatMap(null, kind), "f", getFlatMapContext(), Operation.FLAT_MAP);
    } else {
      builder.assertFlatMapperNull(
          () -> rightInstance.flatMap(null), "mapper", getFlatMapContext(), Operation.FLAT_MAP);
    }

    // Side effect validations
    builder.assertFunctionNull(
        () -> leftInstance.ifLeft(null), "action", contextClass, Operation.IF_LEFT);
    builder.assertFunctionNull(
        () -> rightInstance.ifRight(null), "action", contextClass, Operation.IF_RIGHT);

    builder.execute();
  }

  @Override
  protected void executeEdgeCaseTests() {
    // Test with null values (if instances allow them)
    Either<L, R> leftNull = Either.left(null);
    assertThat(leftNull.isLeft()).isTrue();
    assertThat(leftNull.getLeft()).isNull();

    Either<L, R> rightNull = Either.right(null);
    assertThat(rightNull.isRight()).isTrue();
    assertThat(rightNull.getRight()).isNull();

    // Test toString
    assertThat(leftInstance.toString()).contains("Left(");
    assertThat(rightInstance.toString()).contains("Right(");

    // Test equals and hashCode
    Either<L, R> anotherLeft = Either.left(leftInstance.getLeft());
    assertThat(leftInstance).isEqualTo(anotherLeft);
    assertThat(leftInstance.hashCode()).isEqualTo(anotherLeft.hashCode());

    Either<L, R> anotherRight = Either.right(rightInstance.getRight());
    assertThat(rightInstance).isEqualTo(anotherRight);
    assertThat(rightInstance.hashCode()).isEqualTo(anotherRight.hashCode());
  }

  private void testFactoryMethods() {
    // Test that left() creates correct instances
    assertThat(leftInstance).isInstanceOf(Either.Left.class);
    assertThat(leftInstance.isLeft()).isTrue();
    assertThat(leftInstance.isRight()).isFalse();

    // Test that right() creates correct instances
    assertThat(rightInstance).isInstanceOf(Either.Right.class);
    assertThat(rightInstance.isRight()).isTrue();
    assertThat(rightInstance.isLeft()).isFalse();
  }

  private void testGetters() {
    // Test getLeft() on Left instance
    assertThatCode(() -> leftInstance.getLeft()).doesNotThrowAnyException();

    // Test getLeft() on Right instance throws
    assertThatThrownBy(() -> rightInstance.getLeft())
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining("Cannot invoke getLeft() on a Right instance");

    // Test getRight() on Right instance
    assertThatCode(() -> rightInstance.getRight()).doesNotThrowAnyException();

    // Test getRight() on Left instance throws
    assertThatThrownBy(() -> leftInstance.getRight())
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining("Cannot invoke getRight() on a Left instance");
  }

  private void testFold() {
    Function<L, String> leftMapper = l -> "Left: " + l;
    Function<R, String> rightMapper = r -> "Right: " + r;

    // Test fold on Left
    String leftResult = leftInstance.fold(leftMapper, rightMapper);
    assertThat(leftResult).isNotNull().startsWith("Left:");

    // Test fold on Right
    String rightResult = rightInstance.fold(leftMapper, rightMapper);
    assertThat(rightResult).isNotNull().startsWith("Right:");
  }

  private void testSideEffects() {
    AtomicBoolean leftExecuted = new AtomicBoolean(false);
    AtomicBoolean rightExecuted = new AtomicBoolean(false);

    Consumer<L> leftAction = l -> leftExecuted.set(true);
    Consumer<R> rightAction = r -> rightExecuted.set(true);

    // Test ifLeft on Left instance
    leftInstance.ifLeft(leftAction);
    assertThat(leftExecuted).isTrue();

    // Test ifRight on Left instance (should not execute)
    leftInstance.ifRight(rightAction);
    assertThat(rightExecuted).isFalse();

    // Reset and test Right instance
    leftExecuted.set(false);
    rightExecuted.set(false);

    // Test ifRight on Right instance
    rightInstance.ifRight(rightAction);
    assertThat(rightExecuted).isTrue();

    // Test ifLeft on Right instance (should not execute)
    rightInstance.ifLeft(leftAction);
    assertThat(leftExecuted).isFalse();
  }

  private void testMap() {
    // Test map on Right
    Either<L, S> mappedRight = rightInstance.map(mapper);
    assertThat(mappedRight.isRight()).isTrue();

    // Test map on Left (should preserve Left)
    Either<L, S> mappedLeft = leftInstance.map(mapper);
    assertThat(mappedLeft).isSameAs(leftInstance);
    assertThat(mappedLeft.isLeft()).isTrue();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: map test");
    Function<R, S> throwingMapper =
        r -> {
          throw testException;
        };
    assertThatThrownBy(() -> rightInstance.map(throwingMapper)).isSameAs(testException);

    // Left should not call mapper
    assertThatCode(() -> leftInstance.map(throwingMapper)).doesNotThrowAnyException();
  }

  private void testFlatMap() {
    Function<R, Either<L, S>> flatMapper = r -> Either.right(mapper.apply(r));

    // Test flatMap on Right
    Either<L, S> flatMappedRight = rightInstance.flatMap(flatMapper);
    assertThat(flatMappedRight.isRight()).isTrue();

    // Test flatMap on Left (should preserve Left)
    Either<L, S> flatMappedLeft = leftInstance.flatMap(flatMapper);
    assertThat(flatMappedLeft).isSameAs(leftInstance);
    assertThat(flatMappedLeft.isLeft()).isTrue();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: flatMap test");
    Function<R, Either<L, S>> throwingFlatMapper =
        r -> {
          throw testException;
        };
    assertThatThrownBy(() -> rightInstance.flatMap(throwingFlatMapper)).isSameAs(testException);

    // Left should not call flatMapper
    assertThatCode(() -> leftInstance.flatMap(throwingFlatMapper)).doesNotThrowAnyException();
  }
}
