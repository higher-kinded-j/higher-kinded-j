// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.lazy;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Internal executor for Lazy core type tests.
 *
 * <p>This class coordinates test execution by delegating to appropriate test methods.
 *
 * @param <A> The value type
 * @param <B> The mapped type
 */
final class LazyTestExecutor<A, B> {
    private final Class<?> contextClass;
    private final Lazy<A> deferredInstance;
    private final Lazy<A> nowInstance;
    private final Function<A, B> mapper;

    private final boolean includeFactoryMethods;
    private final boolean includeForce;
    private final boolean includeMap;
    private final boolean includeFlatMap;
    private final boolean includeValidations;
    private final boolean includeEdgeCases;
    private final boolean includeMemoisation;
    private final boolean includeConcurrency;

    private final LazyValidationStage<A, B> validationStage;

    LazyTestExecutor(
            Class<?> contextClass,
            Lazy<A> deferredInstance,
            Lazy<A> nowInstance,
            Function<A, B> mapper,
            boolean includeFactoryMethods,
            boolean includeForce,
            boolean includeMap,
            boolean includeFlatMap,
            boolean includeValidations,
            boolean includeEdgeCases,
            boolean includeMemoisation,
            boolean includeConcurrency) {
        this(contextClass, deferredInstance, nowInstance, mapper,
                includeFactoryMethods, includeForce, includeMap, includeFlatMap,
                includeValidations, includeEdgeCases, includeMemoisation, includeConcurrency, null);
    }

    LazyTestExecutor(
            Class<?> contextClass,
            Lazy<A> deferredInstance,
            Lazy<A> nowInstance,
            Function<A, B> mapper,
            boolean includeFactoryMethods,
            boolean includeForce,
            boolean includeMap,
            boolean includeFlatMap,
            boolean includeValidations,
            boolean includeEdgeCases,
            boolean includeMemoisation,
            boolean includeConcurrency,
            LazyValidationStage<A, B> validationStage) {

        this.contextClass = contextClass;
        this.deferredInstance = deferredInstance;
        this.nowInstance = nowInstance;
        this.mapper = mapper;
        this.includeFactoryMethods = includeFactoryMethods;
        this.includeForce = includeForce;
        this.includeMap = includeMap;
        this.includeFlatMap = includeFlatMap;
        this.includeValidations = includeValidations;
        this.includeEdgeCases = includeEdgeCases;
        this.includeMemoisation = includeMemoisation;
        this.includeConcurrency = includeConcurrency;
        this.validationStage = validationStage;
    }

    void executeAll() {
        if (includeFactoryMethods) testFactoryMethods();
        if (includeForce) testForce();
        if (includeMap && mapper != null) testMap();
        if (includeFlatMap && mapper != null) testFlatMap();
        if (includeValidations) testValidations();
        if (includeEdgeCases) testEdgeCases();
        if (includeMemoisation) testMemoisation();
        if (includeConcurrency) testConcurrency();
    }

    private void testFactoryMethods() {
        // Test defer creates correct instance
        assertThat(deferredInstance).isNotNull();
        assertThat(deferredInstance.toString()).isEqualTo("Lazy[unevaluated...]");

        // Test now creates evaluated instance
        assertThat(nowInstance).isNotNull();
        assertThat(nowInstance.toString()).contains("Lazy[");
    }

    private void testForce() {
        // Test force on now instance (should return immediately)
        assertThatCode(() -> nowInstance.force()).doesNotThrowAnyException();

        // Test force on deferred instance evaluates and returns value
        assertThatCode(() -> deferredInstance.force()).doesNotThrowAnyException();
    }

    private void testMap() {
        // Test map on deferred instance
        Lazy<B> mappedDeferred = deferredInstance.map(mapper);
        assertThat(mappedDeferred).isNotNull();

        // Test map on now instance
        Lazy<B> mappedNow = nowInstance.map(mapper);
        assertThat(mappedNow).isNotNull();

        // Test exception propagation in mapper
        RuntimeException testException = new RuntimeException("Test exception: map test");
        Function<A, B> throwingMapper = a -> {
            throw testException;
        };

        Lazy<B> throwingLazy = deferredInstance.map(throwingMapper);
        assertThatThrownBy(() -> throwingLazy.force()).isSameAs(testException);
    }

    private void testFlatMap() {
        Function<A, Lazy<B>> flatMapper = a -> Lazy.now(mapper.apply(a));

        // Test flatMap on deferred instance
        Lazy<B> flatMappedDeferred = deferredInstance.flatMap(flatMapper);
        assertThat(flatMappedDeferred).isNotNull();

        // Test flatMap on now instance
        Lazy<B> flatMappedNow = nowInstance.flatMap(flatMapper);
        assertThat(flatMappedNow).isNotNull();

        // Test exception propagation in flatMapper
        RuntimeException testException = new RuntimeException("Test exception: flatMap test");
        Function<A, Lazy<B>> throwingFlatMapper = a -> {
            throw testException;
        };

        Lazy<B> throwingLazy = deferredInstance.flatMap(throwingFlatMapper);
        assertThatThrownBy(() -> throwingLazy.force()).isSameAs(testException);

        // Test null return from flatMapper
        // Note: This throws KindUnwrapException (not NPE) because validation happens
        // inside the deferred computation when force() is called
        Function<A, Lazy<B>> nullReturningMapper = a -> null;
        Lazy<B> nullLazy = deferredInstance.flatMap(nullReturningMapper);
        assertThatThrownBy(() -> nullLazy.force())
                .isInstanceOf(KindUnwrapException.class)
                .hasMessageContaining("Function in flatMap returned null");
    }

    void testValidations() {
        // Determine which class context to use for map
        Class<?> mapContext = (validationStage != null && validationStage.getMapContext() != null)
                ? validationStage.getMapContext()
                : contextClass;

        // Determine which class context to use for flatMap
        Class<?> flatMapContext = (validationStage != null && validationStage.getFlatMapContext() != null)
                ? validationStage.getFlatMapContext()
                : contextClass;

        ValidationTestBuilder builder = ValidationTestBuilder.create();

        // Map validations
        builder.assertMapperNull(
                () -> deferredInstance.map(null), mapContext, Operation.MAP);

        // FlatMap validations
        builder.assertFlatMapperNull(
                () -> deferredInstance.flatMap(null), flatMapContext, Operation.FLAT_MAP);

        builder.execute();
    }

    private void testEdgeCases() {
        // Test with null values
        Lazy<A> lazyNull = Lazy.now(null);
        assertThatCode(() -> lazyNull.force()).doesNotThrowAnyException();
        assertThat(lazyNull.toString()).isEqualTo("Lazy[null]");

        // Test toString for unevaluated
        assertThat(Lazy.defer(() -> null).toString()).isEqualTo("Lazy[unevaluated...]");

        // Test toString for evaluated
        try {
            nowInstance.force();
            assertThat(nowInstance.toString()).contains("Lazy[");
        } catch (Throwable ignored) {
            // If force throws, that's fine for this test
        }

        // Test equals and hashCode (reference equality)
        Lazy<A> lazy1 = Lazy.defer(() -> null);
        Lazy<A> lazy2 = Lazy.defer(() -> null);
        assertThat(lazy1).isNotEqualTo(lazy2);
        assertThat(lazy1).isEqualTo(lazy1);
    }

    private void testMemoisation() {
        AtomicInteger counter = new AtomicInteger(0);
        Lazy<String> lazy = Lazy.defer(() -> {
            counter.incrementAndGet();
            return "value";
        });

        // Force multiple times
        assertThatCode(() -> {
            lazy.force();
            lazy.force();
            lazy.force();
        }).doesNotThrowAnyException();

        // Verify computation ran only once
        assertThat(counter.get()).isEqualTo(1);
    }

    private void testConcurrency() {
        AtomicInteger counter = new AtomicInteger(0);
        Lazy<String> lazy = Lazy.defer(() -> {
            counter.incrementAndGet();
            Thread.sleep(10); // Small delay to encourage contention
            return "concurrent";
        });

        // Force from multiple threads
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    lazy.force();
                } catch (Throwable ignored) {
                    // Ignore for this test
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Verify computation ran only once
        assertThat(counter.get()).isEqualTo(1);
    }
}