// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Id core type tests.
 *
 * <p>This class coordinates test execution by delegating to appropriate test methods.
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
final class IdTestExecutor<A, B> {
  private final Class<?> contextClass;
  private final Id<A> instance;
  private final Function<A, B> mapper;

  private final boolean includeFactoryMethods;
  private final boolean includeGetters;
  private final boolean includeMap;
  private final boolean includeFlatMap;
  private final boolean includeValidations;
  private final boolean includeEdgeCases;

  private final IdValidationStage<A, B> validationStage;

  IdTestExecutor(
      Class<?> contextClass,
      Id<A> instance,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeGetters,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases) {
    this(
        contextClass,
        instance,
        mapper,
        includeFactoryMethods,
        includeGetters,
        includeMap,
        includeFlatMap,
        includeValidations,
        includeEdgeCases,
        null);
  }

  IdTestExecutor(
      Class<?> contextClass,
      Id<A> instance,
      Function<A, B> mapper,
      boolean includeFactoryMethods,
      boolean includeGetters,
      boolean includeMap,
      boolean includeFlatMap,
      boolean includeValidations,
      boolean includeEdgeCases,
      IdValidationStage<A, B> validationStage) {

    this.contextClass = contextClass;
    this.instance = instance;
    this.mapper = mapper;
    this.includeFactoryMethods = includeFactoryMethods;
    this.includeGetters = includeGetters;
    this.includeMap = includeMap;
    this.includeFlatMap = includeFlatMap;
    this.includeValidations = includeValidations;
    this.includeEdgeCases = includeEdgeCases;
    this.validationStage = validationStage;
  }

  void executeAll() {
    if (includeFactoryMethods) testFactoryMethods();
    if (includeGetters) testGetters();
    if (includeMap && mapper != null) testMap();
    if (includeFlatMap && mapper != null) testFlatMap();
    if (includeValidations) testValidations();
    if (includeEdgeCases) testEdgeCases();
  }

  private void testFactoryMethods() {
    // Test that of() creates correct instances
    assertThat(instance).isNotNull();
    assertThat(instance).isInstanceOf(Id.class);

    // Test creating Id with null value
    Id<A> nullId = Id.of(null);
    assertThat(nullId).isNotNull();
    assertThat(nullId.value()).isNull();
  }

  private void testGetters() {
    // Test value() accessor
    assertThatCode(() -> instance.value()).doesNotThrowAnyException();

    // Test that value() returns the wrapped value
    A value = instance.value();
    Id<A> recreated = Id.of(value);
    assertThat(recreated.value()).isEqualTo(value);
  }

  private void testMap() {
    // Test map on instance
    Id<B> mapped = instance.map(mapper);
    assertThat(mapped).isNotNull();

    // Test that map preserves the monad structure
    A value = instance.value();
    B expectedValue = mapper.apply(value);
    assertThat(mapped.value()).isEqualTo(expectedValue);

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: map test");
    Function<A, B> throwingMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> instance.map(throwingMapper)).isSameAs(testException);
  }

  private void testFlatMap() {
    Function<A, Id<B>> flatMapper = a -> Id.of(mapper.apply(a));

    // Test flatMap on instance
    Id<B> flatMapped = instance.flatMap(flatMapper);
    assertThat(flatMapped).isNotNull();

    // Test that flatMap preserves the monad structure
    A value = instance.value();
    B expectedValue = mapper.apply(value);
    assertThat(flatMapped.value()).isEqualTo(expectedValue);

    // Test exception propagation
    RuntimeException testException = new RuntimeException("Test exception: flatMap test");
    Function<A, Id<B>> throwingFlatMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> instance.flatMap(throwingFlatMapper)).isSameAs(testException);
  }

  void testValidations() {
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

    // Map validations - test through the Monad interface if custom context provided
    if (validationStage != null && validationStage.getMapContext() != null) {
      // Use the type class interface validation
      IdMonad monad = IdMonad.instance();
      Kind<Id.Witness, A> kind = IdKindHelper.ID.widen(instance);
      builder.assertMapperNull(() -> monad.map(null, kind), "f", mapContext, Operation.MAP);
    } else {
      // Use the instance method
      builder.assertMapperNull(() -> instance.map(null), "fn", mapContext, Operation.MAP);
    }

    // FlatMap validations - test through the Monad interface if custom context provided
    if (validationStage != null && validationStage.getFlatMapContext() != null) {
      // Use the type class interface validation
      IdMonad monad = IdMonad.instance();
      Kind<Id.Witness, A> kind = IdKindHelper.ID.widen(instance);
      builder.assertFlatMapperNull(
          () -> monad.flatMap(null, kind), "f", flatMapContext, Operation.FLAT_MAP);
    } else {
      // Use the instance method
      builder.assertFlatMapperNull(
          () -> instance.flatMap(null), "fn", flatMapContext, Operation.FLAT_MAP);
    }

    builder.execute();
  }

  private void testEdgeCases() {
    // Test with null value
    Id<A> nullId = Id.of(null);
    assertThat(nullId).isNotNull();
    assertThat(nullId.value()).isNull();

    // Test toString
    assertThat(instance.toString()).contains("Id");

    // Test equals and hashCode
    A value = instance.value();
    Id<A> another = Id.of(value);
    assertThat(instance).isEqualTo(another);
    assertThat(instance.hashCode()).isEqualTo(another.hashCode());

    // Test that different values are not equal
    if (value != null) {
      Id<A> nullInstance = Id.of(null);
      assertThat(instance).isNotEqualTo(nullInstance);
    }
  }
}
