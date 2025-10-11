// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import static org.assertj.core.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Maybe core type tests.
 *
 * <p>This class coordinates test execution by delegating to appropriate test methods.
 *
 * @param <T> The value type
 * @param <S> The mapped type
 */
final class MaybeTestExecutor<T, S> {
    private final Class<?> contextClass;
    private final Maybe<T> justInstance;
    private final Maybe<T> nothingInstance;
    private final Function<T, S> mapper;

    private final boolean includeFactoryMethods;
    private final boolean includeGetters;
    private final boolean includeOrElse;
    private final boolean includeMap;
    private final boolean includeFlatMap;
    private final boolean includeValidations;
    private final boolean includeEdgeCases;

    private final MaybeValidationStage<T, S> validationStage;

    MaybeTestExecutor(
            Class<?> contextClass,
            Maybe<T> justInstance,
            Maybe<T> nothingInstance,
            Function<T, S> mapper,
            boolean includeFactoryMethods,
            boolean includeGetters,
            boolean includeOrElse,
            boolean includeMap,
            boolean includeFlatMap,
            boolean includeValidations,
            boolean includeEdgeCases,
            MaybeValidationStage<T, S> validationStage) {

        this.contextClass = contextClass;
        this.justInstance = justInstance;
        this.nothingInstance = nothingInstance;
        this.mapper = mapper;
        this.includeFactoryMethods = includeFactoryMethods;
        this.includeGetters = includeGetters;
        this.includeOrElse = includeOrElse;
        this.includeMap = includeMap;
        this.includeFlatMap = includeFlatMap;
        this.includeValidations = includeValidations;
        this.includeEdgeCases = includeEdgeCases;
        this.validationStage = validationStage;
    }

    void executeAll() {
        if (includeFactoryMethods) testFactoryMethods();
        if (includeGetters) testGetters();
        if (includeOrElse) testOrElse();
        if (includeMap && mapper != null) testMap();
        if (includeFlatMap && mapper != null) testFlatMap();
        if (includeValidations) testValidations();
        if (includeEdgeCases) testEdgeCases();
    }

    void testValidations() {
        T defaultValue = justInstance.get();

        // Determine which class context to use for map
        Class<?> mapContext = (validationStage != null && validationStage.getMapContext() != null)
                ? validationStage.getMapContext()
                : contextClass;

        // Determine which class context to use for flatMap
        Class<?> flatMapContext = (validationStage != null && validationStage.getFlatMapContext() != null)
                ? validationStage.getFlatMapContext()
                : contextClass;

        ValidationTestBuilder builder = ValidationTestBuilder.create();

        // Map validations - test through the Functor interface if custom context provided
        if (validationStage != null && validationStage.getMapContext() != null) {
            // Use the type class interface validation
            MaybeFunctor functor = MaybeFunctor.INSTANCE;
            Kind<MaybeKind.Witness, T> kind = MaybeKindHelper.MAYBE.widen(justInstance);
            builder.assertMapperNull(() -> functor.map(null, kind), mapContext, Operation.MAP);
        } else {
            // Use the instance method
            builder.assertMapperNull(() -> justInstance.map(null), mapContext, Operation.MAP);
        }

        // FlatMap validations - test through the Monad interface if custom context provided
        if (validationStage != null && validationStage.getFlatMapContext() != null) {
            // Use the type class interface validation
            MaybeMonad monad = MaybeMonad.INSTANCE;
            Kind<MaybeKind.Witness, T> kind = MaybeKindHelper.MAYBE.widen(justInstance);
            builder.assertFlatMapperNull(() -> monad.flatMap(null, kind), flatMapContext, Operation.FLAT_MAP);
        } else {
            // Use the instance method
            builder.assertFlatMapperNull(() -> justInstance.flatMap(null), flatMapContext, Operation.FLAT_MAP);
        }

        // OrElseGet validation (only on Nothing, as Just doesn't call supplier)
        builder.assertFunctionNull(
                () -> nothingInstance.orElseGet(null), "otherSupplier", contextClass, Operation.OR_ELSE_GET);

        builder.execute();
    }


    private void testFactoryMethods() {
        // Test that just() creates correct instances
        assertThat(justInstance.isJust()).isTrue();
        assertThat(justInstance.isNothing()).isFalse();

        // Test that nothing() creates correct instances
        assertThat(nothingInstance.isNothing()).isTrue();
        assertThat(nothingInstance.isJust()).isFalse();

        // Test just() rejects null
        assertThatThrownBy(() -> Maybe.just(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Maybe.just value cannot be null");

        // Test fromNullable
        T value = justInstance.get();
        Maybe<T> fromNonNull = Maybe.fromNullable(value);
        assertThat(fromNonNull.isJust()).isTrue();

        Maybe<T> fromNull = Maybe.fromNullable(null);
        assertThat(fromNull.isNothing()).isTrue();
    }

    private void testGetters() {
        // Test get() on Just instance
        assertThatCode(() -> justInstance.get()).doesNotThrowAnyException();
        T value = justInstance.get();
        assertThat(value).isNotNull();

        // Test get() on Nothing instance throws
        assertThatThrownBy(() -> nothingInstance.get())
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Cannot call get() on Nothing");

        // Test isJust() and isNothing()
        assertThat(justInstance.isJust()).isTrue();
        assertThat(justInstance.isNothing()).isFalse();
        assertThat(nothingInstance.isJust()).isFalse();
        assertThat(nothingInstance.isNothing()).isTrue();
    }

    private void testOrElse() {
        T defaultValue = justInstance.get(); // Use the Just value as default

        // Test orElse on Just (should return value)
        T justResult = justInstance.orElse(defaultValue);
        assertThat(justResult).isEqualTo(justInstance.get());

        // Test orElse on Nothing (should return default)
        T nothingResult = nothingInstance.orElse(defaultValue);
        assertThat(nothingResult).isEqualTo(defaultValue);

        // Test orElseGet on Just (should not call supplier)
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        Supplier<T> supplier = () -> {
            supplierCalled.set(true);
            return defaultValue;
        };

        T justGetResult = justInstance.orElseGet(supplier);
        assertThat(justGetResult).isEqualTo(justInstance.get());
        assertThat(supplierCalled).isFalse();

        // Test orElseGet on Nothing (should call supplier)
        supplierCalled.set(false);
        T nothingGetResult = nothingInstance.orElseGet(supplier);
        assertThat(nothingGetResult).isEqualTo(defaultValue);
        assertThat(supplierCalled).isTrue();
    }

    private void testMap() {
        // Test map on Just
        Maybe<S> mappedJust = justInstance.map(mapper);
        assertThat(mappedJust.isJust()).isTrue();

        // Test map on Nothing (should preserve Nothing)
        Maybe<S> mappedNothing = nothingInstance.map(mapper);
        assertThat(mappedNothing).isSameAs(nothingInstance);
        assertThat(mappedNothing.isNothing()).isTrue();

        // Test map with null-returning function creates Nothing
        Function<T, S> nullReturningMapper = t -> null;
        Maybe<S> nullResult = justInstance.map(nullReturningMapper);
        assertThat(nullResult.isNothing()).isTrue();

        // Test exception propagation
        RuntimeException testException = new RuntimeException("Test exception: map test");
        Function<T, S> throwingMapper = t -> {
            throw testException;
        };
        assertThatThrownBy(() -> justInstance.map(throwingMapper)).isSameAs(testException);

        // Nothing should not call mapper
        assertThatCode(() -> nothingInstance.map(throwingMapper)).doesNotThrowAnyException();
    }

    private void testFlatMap() {
        Function<T, Maybe<S>> flatMapper = t -> Maybe.just(mapper.apply(t));

        // Test flatMap on Just
        Maybe<S> flatMappedJust = justInstance.flatMap(flatMapper);
        assertThat(flatMappedJust.isJust()).isTrue();

        // Test flatMap on Nothing (should preserve Nothing)
        Maybe<S> flatMappedNothing = nothingInstance.flatMap(flatMapper);
        assertThat(flatMappedNothing).isSameAs(nothingInstance);
        assertThat(flatMappedNothing.isNothing()).isTrue();

        // Test flatMap returning Nothing
        Function<T, Maybe<S>> nothingMapper = t -> Maybe.nothing();
        Maybe<S> nothingResult = justInstance.flatMap(nothingMapper);
        assertThat(nothingResult.isNothing()).isTrue();

        // Test exception propagation
        RuntimeException testException = new RuntimeException("Test exception: flatMap test");
        Function<T, Maybe<S>> throwingFlatMapper = t -> {
            throw testException;
        };
        assertThatThrownBy(() -> justInstance.flatMap(throwingFlatMapper)).isSameAs(testException);

        // Nothing should not call flatMapper
        assertThatCode(() -> nothingInstance.flatMap(throwingFlatMapper)).doesNotThrowAnyException();
    }

    private void testEdgeCases() {
        // Test toString
        assertThat(justInstance.toString()).contains("Just(");
        assertThat(nothingInstance.toString()).isEqualTo("Nothing");

        // Test equals and hashCode
        Maybe<T> anotherJust = Maybe.just(justInstance.get());
        assertThat(justInstance).isEqualTo(anotherJust);
        assertThat(justInstance.hashCode()).isEqualTo(anotherJust.hashCode());

        Maybe<T> anotherNothing = Maybe.nothing();
        assertThat(nothingInstance).isEqualTo(anotherNothing);
        assertThat(nothingInstance.hashCode()).isEqualTo(anotherNothing.hashCode());

        // Nothing should be a singleton
        assertThat(nothingInstance).isSameAs(anotherNothing);

        // Test that Just and Nothing are not equal
        assertThat(justInstance).isNotEqualTo(nothingInstance);
    }
}