// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe_t;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for MaybeT core type tests.
 *
 * <p>This class coordinates test execution by delegating to appropriate test methods.
 *
 * @param <F> The outer monad witness type
 * @param <A> The type of the value potentially held by the inner Maybe
 * @param <B> The mapped type
 */
final class MaybeTTestExecutor<F extends WitnessArity<TypeArity.Unary>, A, B> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final MaybeT<F, A> justInstance;
  private final MaybeT<F, A> nothingInstance;
  private final Function<A, B> mapper;

  private final boolean includeFactoryMethods;
  private final boolean includeValueAccessor;
  private final boolean includeValidations;
  private final boolean includeEdgeCases;

  private final MaybeTValidationStage<F, A, B> validationStage;

  MaybeTTestExecutor(
      Class<?> contextClass,
      Monad<F> outerMonad,
      MaybeT<F, A> justInstance,
      MaybeT<F, A> nothingInstance,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeValueAccessor,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        outerMonad,
        justInstance,
        nothingInstance,
        mapper,
        includeFactoryMethods,
        includeValueAccessor,
        includeValidations,
        includeEdgeCases,
        null);
  }

  MaybeTTestExecutor(
      Class<?> contextClass,
      Monad<F> outerMonad,
      MaybeT<F, A> justInstance,
      MaybeT<F, A> nothingInstance,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeValueAccessor,
      boolean includeValidations,
      boolean includeEdgeCases,
      MaybeTValidationStage<F, A, B> validationStage) {

    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.justInstance = justInstance;
    this.nothingInstance = nothingInstance;
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
    // Test just() factory method
    assertThat(justInstance).isNotNull();
    assertThat(justInstance.value()).isNotNull();

    // Test nothing() factory method
    assertThat(nothingInstance).isNotNull();
    assertThat(nothingInstance.value()).isNotNull();

    // Test fromKind() factory method
    Kind<F, Maybe<A>> kindValue = justInstance.value();
    MaybeT<F, A> fromKind = MaybeT.fromKind(kindValue);
    assertThat(fromKind).isNotNull();
    assertThat(fromKind.value()).isNotNull();
  }

  private void testValueAccessor() {
    // Test value() accessor returns non-null
    Kind<F, Maybe<A>> justValue = justInstance.value();
    assertThat(justValue).as("Just instance value() should return non-null").isNotNull();

    Kind<F, Maybe<A>> nothingValue = nothingInstance.value();
    assertThat(nothingValue).as("Nothing instance value() should return non-null").isNotNull();
  }

  void testValidations() {
    // Determine which class context to use
    Class<?> validationContext =
        (validationStage != null && validationStage.getValidationContext() != null)
            ? validationStage.getValidationContext()
            : contextClass;

    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Test fromKind() null validation - uses Validation.coreTypeValidator().requireValue
    builder.assertValueNull(
        () -> MaybeT.fromKind(null), "value", validationContext, Operation.CONSTRUCTION);

    // Test just() null monad validation - uses DomainValidator.requireOuterMonad
    // Pass a simple non-null value, not outerMonad.of() which requires the monad
    builder.assertTransformerOuterMonadNull(
        () -> MaybeT.just(null, "test"), validationContext, Operation.JUST);

    // Test nothing() null monad validation - uses DomainValidator.requireOuterMonad
    builder.assertTransformerOuterMonadNull(
        () -> MaybeT.nothing(null), validationContext, Operation.NONE);

    // Test fromMaybe() null monad validation - uses DomainValidator.requireOuterMonad
    builder.assertTransformerOuterMonadNull(
        () -> MaybeT.fromMaybe(null, Maybe.fromNullable("test")),
        validationContext,
        Operation.FROM_MAYBE);

    // Test fromMaybe() null maybe validation - uses DomainValidator.requireTransformerComponent
    builder.assertTransformerComponentNull(
        () -> MaybeT.fromMaybe(outerMonad, null),
        "inner Maybe",
        validationContext,
        Operation.FROM_MAYBE);

    // Test liftF() null monad validation - uses DomainValidator.requireOuterMonad
    builder.assertTransformerOuterMonadNull(
        () -> MaybeT.liftF(null, outerMonad.of("test")), validationContext, Operation.LIFT_F);

    // Test liftF() null Kind validation
    builder.assertKindNull(
        () -> MaybeT.liftF(outerMonad, null), validationContext, Operation.LIFT_F, "source Kind");

    builder.execute();
  }

  private void testEdgeCases() {
    // Test with null values using fromNullable instead of just
    MaybeT<F, A> justNull = MaybeT.fromMaybe(outerMonad, Maybe.fromNullable(null));
    assertThat(justNull).isNotNull();
    assertThat(justNull.value()).isNotNull();

    MaybeT<F, A> nothingTest = MaybeT.nothing(outerMonad);
    assertThat(nothingTest).isNotNull();
    assertThat(nothingTest.value()).isNotNull();

    // Test fromMaybe with Just - use fromNullable for null values
    Maybe<A> maybeJust = Maybe.fromNullable(null);
    MaybeT<F, A> fromMaybeJust = MaybeT.fromMaybe(outerMonad, maybeJust);
    assertThat(fromMaybeJust).isNotNull();
    assertThat(fromMaybeJust.value()).isNotNull();

    // Test fromMaybe with Nothing
    Maybe<A> maybeNothing = Maybe.nothing();
    MaybeT<F, A> fromMaybeNothing = MaybeT.fromMaybe(outerMonad, maybeNothing);
    assertThat(fromMaybeNothing).isNotNull();
    assertThat(fromMaybeNothing.value()).isNotNull();

    // Test liftF
    Kind<F, A> liftedValue = outerMonad.of(null);
    MaybeT<F, A> lifted = MaybeT.liftF(outerMonad, liftedValue);
    assertThat(lifted).isNotNull();
    assertThat(lifted.value()).isNotNull();

    // Test toString
    assertThat(justInstance.toString()).isNotNull();
    assertThat(nothingInstance.toString()).isNotNull();

    // Test equals and hashCode
    MaybeT<F, A> anotherJust = MaybeT.fromKind(justInstance.value());
    assertThat(justInstance).isEqualTo(anotherJust);
    assertThat(justInstance.hashCode()).isEqualTo(anotherJust.hashCode());
  }
}
