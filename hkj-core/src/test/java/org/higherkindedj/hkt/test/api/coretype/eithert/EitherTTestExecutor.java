// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.eithert;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for EitherT core type tests.
 *
 * <p>This class coordinates test execution by delegating to appropriate test methods.
 *
 * @param <F> The outer monad witness type
 * @param <L> The Left type
 * @param <R> The Right type
 * @param <S> The mapped type
 */
final class EitherTTestExecutor<F, L, R, S> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final EitherT<F, L, R> leftInstance;
  private final EitherT<F, L, R> rightInstance;
  private final Function<R, S> mapper;

  private final boolean includeFactoryMethods;
  private final boolean includeValueAccessor;
  private final boolean includeValidations;
  private final boolean includeEdgeCases;

  private final EitherTValidationStage<F, L, R, S> validationStage;

  EitherTTestExecutor(
      Class<?> contextClass,
      Monad<F> outerMonad,
      EitherT<F, L, R> leftInstance,
      EitherT<F, L, R> rightInstance,
      Function<R, S> mapper,
      boolean includeFactoryMethods,
      boolean includeValueAccessor,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        outerMonad,
        leftInstance,
        rightInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases,
        null);
  }

  EitherTTestExecutor(
      Class<?> contextClass,
      Monad<F> outerMonad,
      EitherT<F, L, R> leftInstance,
      EitherT<F, L, R> rightInstance,
      Function<R, S> mapper,
      boolean includeFactoryMethods,
      boolean includeValueAccessor,
      boolean includeValidations,
      boolean includeEdgeCases,
      EitherTValidationStage<F, L, R, S> validationStage) {

    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
    this.mapper = mapper;
    this.includeFactoryMethods = includeFactoryMethods;
    this.includeValueAccessor = includeValueAccessor;
    this.includeValidations = includeValidations;
    this.includeEdgeCases = includeEdgeCases;
    this.validationStage = validationStage;
  }

  void executeAll() {
    if (includeFactoryMethods) testFactoryMethods();
    if (includeValueAccessor) testValueAccessor();
    if (includeValidations) testValidations();
    if (includeEdgeCases) testEdgeCases();
  }

  private void testFactoryMethods() {
    // Test right() factory method
    assertThat(rightInstance).isNotNull();
    assertThat(rightInstance.value()).isNotNull();

    // Test left() factory method
    assertThat(leftInstance).isNotNull();
    assertThat(leftInstance.value()).isNotNull();

    // Test fromKind() factory method
    Kind<F, Either<L, R>> kindValue = rightInstance.value();
    EitherT<F, L, R> fromKind = EitherT.fromKind(kindValue);
    assertThat(fromKind).isNotNull();
    assertThat(fromKind.value()).isNotNull();
  }

  private void testValueAccessor() {
    // Test value() accessor returns non-null
    Kind<F, Either<L, R>> rightValue = rightInstance.value();
    assertThat(rightValue).as("Right instance value() should return non-null").isNotNull();

    Kind<F, Either<L, R>> leftValue = leftInstance.value();
    assertThat(leftValue).as("Left instance value() should return non-null").isNotNull();
  }

    void testValidations() {
        // Determine which class context to use
        Class<?> validationContext =
                (validationStage != null && validationStage.getValidationContext() != null)
                        ? validationStage.getValidationContext()
                        : contextClass;

        ValidationTestBuilder builder = ValidationTestBuilder.create();

        // Test fromKind() null validation - uses KindValidator
        builder.assertKindNull(
                () -> EitherT.fromKind(null),
                validationContext,
                Operation.CONSTRUCTION);

        // Test right() null monad validation - uses DomainValidator.requireOuterMonad
        builder.assertTransformerOuterMonadNull(
                () -> EitherT.right(null, rightInstance.value()),
                validationContext,
                Operation.RIGHT);

        // Test left() null monad validation - uses DomainValidator.requireOuterMonad
        builder.assertTransformerOuterMonadNull(
                () -> EitherT.left(null, leftInstance.value()),
                validationContext,
                Operation.LEFT);

        // Test fromEither() null monad validation - uses DomainValidator.requireOuterMonad
        builder.assertTransformerOuterMonadNull(
                () -> EitherT.fromEither(null, Either.right(null)),
                validationContext,
                Operation.FROM_EITHER);

        // Test fromEither() null either validation - uses DomainValidator.requireTransformerComponent
        builder.assertTransformerComponentNull(
                () -> EitherT.fromEither(outerMonad, null),
                "inner Either",
                validationContext,
                Operation.FROM_EITHER);

        // Test liftF() null monad validation - uses DomainValidator.requireOuterMonad
        builder.assertTransformerOuterMonadNull(
                () -> EitherT.liftF(null, outerMonad.of(null)),
                validationContext,
                Operation.LIFT_F);

        // Test liftF() null Kind validation
        builder.assertKindNull(
                () -> EitherT.liftF(outerMonad, null),
                validationContext,
                Operation.LIFT_F,
                "source Kind");

        builder.execute();
    }

  private void testEdgeCases() {
    // Test with null values (if instances allow them)
    EitherT<F, L, R> leftNull = EitherT.left(outerMonad, null);
    assertThat(leftNull).isNotNull();
    assertThat(leftNull.value()).isNotNull();

    EitherT<F, L, R> rightNull = EitherT.right(outerMonad, null);
    assertThat(rightNull).isNotNull();
    assertThat(rightNull.value()).isNotNull();

    // Test fromEither with Left
    Either<L, R> eitherLeft = Either.left(null);
    EitherT<F, L, R> fromEitherLeft = EitherT.fromEither(outerMonad, eitherLeft);
    assertThat(fromEitherLeft).isNotNull();
    assertThat(fromEitherLeft.value()).isNotNull();

    // Test fromEither with Right
    Either<L, R> eitherRight = Either.right(null);
    EitherT<F, L, R> fromEitherRight = EitherT.fromEither(outerMonad, eitherRight);
    assertThat(fromEitherRight).isNotNull();
    assertThat(fromEitherRight.value()).isNotNull();

    // Test liftF
    Kind<F, R> liftedValue = outerMonad.of(null);
    EitherT<F, L, R> lifted = EitherT.liftF(outerMonad, liftedValue);
    assertThat(lifted).isNotNull();
    assertThat(lifted.value()).isNotNull();

    // Test toString
    assertThat(leftInstance.toString()).isNotNull();
    assertThat(rightInstance.toString()).isNotNull();

    // Test equals and hashCode
    EitherT<F, L, R> anotherRight = EitherT.fromKind(rightInstance.value());
    assertThat(rightInstance).isEqualTo(anotherRight);
    assertThat(rightInstance.hashCode()).isEqualTo(anotherRight.hashCode());
  }
}
