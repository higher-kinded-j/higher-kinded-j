// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import static org.assertj.core.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.test.api.coretype.common.BaseCoreTypeTestExecutor;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;
import org.higherkindedj.hkt.validated.*;

/**
 * Internal executor for Validated core type tests.
 *
 * @param <E> The error type
 * @param <A> The value type
 * @param <B> The mapped type
 */
final class ValidatedTestExecutor<E, A, B>
    extends BaseCoreTypeTestExecutor<A, B, ValidatedValidationStage<E, A, B>> {

  private final Validated<E, A> invalidInstance;
  private final Validated<E, A> validInstance;

  private final boolean includeFactoryMethods;
  private final boolean includeGetters;
  private final boolean includeFold;
  private final boolean includeSideEffects;
  private final boolean includeMap;
  private final boolean includeFlatMap;

  ValidatedTestExecutor(
      Class<?> contextClass,
      Validated<E, A> invalidInstance,
      Validated<E, A> validInstance,
      Function<A, B> mapper,
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
        invalidInstance,
        validInstance,
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

  ValidatedTestExecutor(
      Class<?> contextClass,
      Validated<E, A> invalidInstance,
      Validated<E, A> validInstance,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeGetters,
      boolean includeFold,
      boolean includeSideEffects,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases,
      ValidatedValidationStage<E, A, B> validationStage) {

    super(contextClass, mapper, includeValidations, includeEdgeCases, validationStage);

    this.invalidInstance = invalidInstance;
    this.validInstance = validInstance;
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
    Function<E, String> errorMapper = e -> "Invalid: " + e;
    Function<A, String> valueMapper = a -> "Valid: " + a;
    Consumer<E> errorAction = e -> {};
    Consumer<A> valueAction = a -> {};

    ValidationTestBuilder builder = ValidationTestBuilder.create();

    // Fold validations (uses Validated interface, so always use contextClass)
    builder.assertFunctionNull(
        () -> invalidInstance.fold(null, valueMapper),
        "invalidMapper",
        contextClass,
        Operation.FOLD);
    builder.assertFunctionNull(
        () -> invalidInstance.fold(errorMapper, null), "validMapper", contextClass, Operation.FOLD);

    // Determine contexts for concrete implementations
    Class<?> validMapContext =
        (validationStage != null && validationStage.getMapContext() != null)
            ? validationStage.getMapContext()
            : Invalid.class; // Default to Invalid.class since we're testing Invalid

    Class<?> validFlatMapContext =
        (validationStage != null && validationStage.getFlatMapContext() != null)
            ? validationStage.getFlatMapContext()
            : Invalid.class; // Default to Invalid.class

    Class<?> validIfValidContext =
        (validationStage != null && validationStage.getIfValidContext() != null)
            ? validationStage.getIfValidContext()
            : Invalid.class; // Default to Invalid.class

    Class<?> validIfInvalidContext =
        (validationStage != null && validationStage.getIfInvalidContext() != null)
            ? validationStage.getIfInvalidContext()
            : Invalid.class; // Default to Invalid.class

    // Map validations
    builder.assertMapperNull(() -> invalidInstance.map(null), "fn", validMapContext, Operation.MAP);

    // FlatMap validations
    builder.assertFlatMapperNull(
        () -> invalidInstance.flatMap(null), "fn", validFlatMapContext, Operation.FLAT_MAP);

    // Side effect validations
    builder.assertFunctionNull(
        () -> invalidInstance.ifInvalid(null),
        "consumer",
        validIfInvalidContext,
        Operation.IF_INVALID);

    builder.assertFunctionNull(
        () -> invalidInstance.ifValid(null), "consumer", validIfValidContext, Operation.IF_VALID);

    builder.execute();
  }

  @Override
  protected void executeEdgeCaseTests() {
    // Test with null values (if instances allow them)
    Validated<E, A> invalidNull = Validated.invalid(null);
    assertThat(invalidNull.isInvalid()).isTrue();
    assertThat(invalidNull.getError()).isNull();

    Validated<E, A> validNull = Validated.valid(null);
    assertThat(validNull.isValid()).isTrue();
    assertThat(validNull.get()).isNull();

    // Test toString
    assertThat(invalidInstance.toString()).contains("Invalid(");
    assertThat(validInstance.toString()).contains("Valid(");

    // Test equals and hashCode
    Validated<E, A> anotherInvalid = Validated.invalid(invalidInstance.getError());
    assertThat(invalidInstance).isEqualTo(anotherInvalid);
    assertThat(invalidInstance.hashCode()).isEqualTo(anotherInvalid.hashCode());

    Validated<E, A> anotherValid = Validated.valid(validInstance.get());
    assertThat(validInstance).isEqualTo(anotherValid);
    assertThat(validInstance.hashCode()).isEqualTo(anotherValid.hashCode());
  }

  private void testFactoryMethods() {
    // Test that invalid() creates correct instances
    assertThat(invalidInstance).isInstanceOf(Invalid.class);
    assertThat(invalidInstance.isInvalid()).isTrue();
    assertThat(invalidInstance.isValid()).isFalse();

    // Test that valid() creates correct instances
    assertThat(validInstance).isInstanceOf(Valid.class);
    assertThat(validInstance.isValid()).isTrue();
    assertThat(validInstance.isInvalid()).isFalse();
  }

  private void testGetters() {
    // Test getError() on Invalid instance
    assertThatCode(() -> invalidInstance.getError()).doesNotThrowAnyException();

    // Test getError() on Valid instance throws
    assertThatThrownBy(() -> validInstance.getError())
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining("Cannot getError() from a Valid instance");

    // Test get() on Valid instance
    assertThatCode(() -> validInstance.get()).doesNotThrowAnyException();

    // Test get() on Invalid instance throws
    assertThatThrownBy(() -> invalidInstance.get())
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining("Cannot get() from an Invalid instance");
  }

  private void testFold() {
    Function<E, String> errorMapper = e -> "Invalid: " + e;
    Function<A, String> valueMapper = a -> "Valid: " + a;

    // Test fold on Invalid
    String invalidResult = invalidInstance.fold(errorMapper, valueMapper);
    assertThat(invalidResult).isNotNull().startsWith("Invalid:");

    // Test fold on Valid
    String validResult = validInstance.fold(errorMapper, valueMapper);
    assertThat(validResult).isNotNull().startsWith("Valid:");
  }

  private void testSideEffects() {
    AtomicBoolean invalidExecuted = new AtomicBoolean(false);
    AtomicBoolean validExecuted = new AtomicBoolean(false);

    Consumer<E> errorAction = e -> invalidExecuted.set(true);
    Consumer<A> valueAction = a -> validExecuted.set(true);

    // Test ifInvalid on Invalid instance
    invalidInstance.ifInvalid(errorAction);
    assertThat(invalidExecuted).isTrue();

    // Test ifValid on Invalid instance (should not execute)
    invalidInstance.ifValid(valueAction);
    assertThat(validExecuted).isFalse();

    // Reset and test Valid instance
    invalidExecuted.set(false);
    validExecuted.set(false);

    // Test ifValid on Valid instance
    validInstance.ifValid(valueAction);
    assertThat(validExecuted).isTrue();

    // Test ifInvalid on Valid instance (should not execute)
    validInstance.ifInvalid(errorAction);
    assertThat(invalidExecuted).isFalse();
  }

  private void testMap() {
    // Test map on Valid
    Validated<E, B> mappedValid = validInstance.map(mapper);
    assertThat(mappedValid.isValid()).isTrue();

    // Test map on Invalid (should preserve Invalid)
    Validated<E, B> mappedInvalid = invalidInstance.map(mapper);
    assertThat(mappedInvalid).isSameAs(invalidInstance);
    assertThat(mappedInvalid.isInvalid()).isTrue();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: map test");
    Function<A, B> throwingMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> validInstance.map(throwingMapper)).isSameAs(testException);

    // Invalid should not call mapper
    assertThatCode(() -> invalidInstance.map(throwingMapper)).doesNotThrowAnyException();
  }

  private void testFlatMap() {
    Function<A, Validated<E, B>> flatMapper = a -> Validated.valid(mapper.apply(a));

    // Test flatMap on Valid
    Validated<E, B> flatMappedValid = validInstance.flatMap(flatMapper);
    assertThat(flatMappedValid.isValid()).isTrue();

    // Test flatMap on Invalid (should preserve Invalid)
    Validated<E, B> flatMappedInvalid = invalidInstance.flatMap(flatMapper);
    assertThat(flatMappedInvalid).isSameAs(invalidInstance);
    assertThat(flatMappedInvalid.isInvalid()).isTrue();

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: flatMap test");
    Function<A, Validated<E, B>> throwingFlatMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> validInstance.flatMap(throwingFlatMapper)).isSameAs(testException);

    // Invalid should not call flatMapper
    assertThatCode(() -> invalidInstance.flatMap(throwingFlatMapper)).doesNotThrowAnyException();
  }
}
