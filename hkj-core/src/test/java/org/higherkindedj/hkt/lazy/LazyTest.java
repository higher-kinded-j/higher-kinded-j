// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Lazy<A> Complete Test Suite")
class LazyTest extends TypeClassTestBase<LazyKind.Witness, String, Integer> {

    // ============================================================================
    // Test Fixtures
    // ============================================================================

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    // ============================================================================
    // TypeClassTestBase Implementation
    // ============================================================================

    @Override
    protected Kind<LazyKind.Witness, String> createValidKind() {
        return LAZY.widen(Lazy.defer(() -> "TestValue"));
    }

    @Override
    protected Kind<LazyKind.Witness, String> createValidKind2() {
        return LAZY.widen(Lazy.defer(() -> "TestValue2"));
    }

    @Override
    protected Function<String, Integer> createValidMapper() {
        return String::length;
    }

    @Override
    protected BiPredicate<Kind<LazyKind.Witness, ?>, Kind<LazyKind.Witness, ?>> createEqualityChecker() {
        return (k1, k2) -> {
            try {
                Lazy<?> lazy1 = LAZY.narrow((Kind<LazyKind.Witness, Object>) k1);
                Lazy<?> lazy2 = LAZY.narrow((Kind<LazyKind.Witness, Object>) k2);
                Object v1 = lazy1.force();
                Object v2 = lazy2.force();
                return v1 != null ? v1.equals(v2) : v2 == null;
            } catch (Throwable e) {
                return false;
            }
        };
    }

    @Override
    protected Function<Integer, String> createSecondMapper() {
        return Object::toString;
    }

    @Override
    protected Function<String, Kind<LazyKind.Witness, Integer>> createValidFlatMapper() {
        return s -> LAZY.widen(Lazy.defer(() -> s.length()));
    }

    private static ThrowableSupplier<String> successSupplier() {
        return () -> {
            COUNTER.incrementAndGet();
            Thread.sleep(5); // Small delay to test memoisation
            return "SuccessValue";
        };
    }

    private static ThrowableSupplier<String> nullSupplier() {
        return () -> {
            COUNTER.incrementAndGet();
            return null;
        };
    }

    private static ThrowableSupplier<String> runtimeFailSupplier() {
        return () -> {
            COUNTER.incrementAndGet();
            throw new IllegalStateException("Runtime Failure");
        };
    }

    private static ThrowableSupplier<String> checkedFailSupplier() {
        return () -> {
            COUNTER.incrementAndGet();
            throw new IOException("Checked Failure");
        };
    }

    // ============================================================================
    // Complete Test Suite
    // ============================================================================

    @Nested
    @DisplayName("Complete Lazy Test Suite")
    class CompleteLazyTestSuite {

        @Test
        @DisplayName("Run complete Lazy core type tests using base fixtures")
        void runCompleteLazyCoreTypeTestsUsingBaseFixtures() {
            validateRequiredFixtures();

            // Extract Lazy instances from base fixtures with explicit type casting
            Lazy<String> deferredLazy = LAZY.narrow(validKind);
            Lazy<String> nowLazy = LAZY.narrow(validKind2);

            CoreTypeTest.<String>lazy(Lazy.class)
                    .withDeferred(deferredLazy)
                    .withNow(nowLazy)
                    .withMappers(validMapper)
                    .testAll();
        }

        @Test
        @DisplayName("Run complete Lazy core type tests with custom instances")
        void runCompleteLazyCoreTypeTestsWithCustomInstances() {
            COUNTER.set(0);
            Lazy<String> deferred = Lazy.defer(() -> {
                COUNTER.incrementAndGet();
                Thread.sleep(5);
                return "SuccessValue";
            });
            Lazy<String> now = Lazy.now("PrecomputedValue");

            CoreTypeTest.<String>lazy(Lazy.class)
                    .withDeferred(deferred)
                    .withNow(now)
                    .withMappers(String::length)
                    .testAll();
        }

        @Test
        @DisplayName("Run complete Lazy KindHelper tests")
        void runCompleteLazyKindHelperTests() {
            Lazy<String> instance = Lazy.now("TestValue");

            CoreTypeTest.lazyKindHelper(instance)
                    .test();
        }
    }

    // ============================================================================
    // Factory Methods
    // ============================================================================

    @Nested
    @DisplayName("Factory Methods (defer, now)")
    class FactoryTests {

        @Test
        @DisplayName("defer should not evaluate supplier immediately")
        void deferShouldNotEvaluateSupplierImmediately() {
            COUNTER.set(0);
            Lazy<String> lazy = Lazy.defer(successSupplier());

            assertThat(COUNTER.get()).isZero();
            assertThat(lazy.toString()).isEqualTo("Lazy[unevaluated...]");
        }

        @Test
        @DisplayName("defer should throw NPE for null supplier")
        void deferShouldThrowNPEForNullSupplier() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Lazy.defer(null))
                    .withMessageContaining("computation");
        }

        @Test
        @DisplayName("now should create evaluated Lazy with value")
        void nowShouldCreateEvaluatedLazyWithValue() throws Throwable {
            COUNTER.set(0);
            Lazy<String> lazy = Lazy.now("DirectValue");

            assertThat(COUNTER.get()).isZero();
            assertThat(lazy.force()).isEqualTo("DirectValue");
            assertThat(COUNTER.get()).isZero();
            assertThat(lazy.toString()).isEqualTo("Lazy[DirectValue]");
        }

        @Test
        @DisplayName("now should create evaluated Lazy with null")
        void nowShouldCreateEvaluatedLazyWithNull() throws Throwable {
            COUNTER.set(0);
            Lazy<String> lazy = Lazy.now(null);

            assertThat(COUNTER.get()).isZero();
            assertThat(lazy.force()).isNull();
            assertThat(COUNTER.get()).isZero();
            assertThat(lazy.toString()).isEqualTo("Lazy[null]");
        }
    }

    // ============================================================================
    // Force Evaluation
    // ============================================================================

    @Nested
    @DisplayName("force() Method")
    class ForceTests {

        @Test
        @DisplayName("force should evaluate deferred supplier only once")
        void forceShouldEvaluateDeferredSupplierOnlyOnce() throws Throwable {
            COUNTER.set(0);
            Lazy<String> lazy = Lazy.defer(successSupplier());

            assertThat(COUNTER.get()).isZero();

            // First force
            assertThat(lazy.force()).isEqualTo("SuccessValue");
            assertThat(COUNTER.get()).isEqualTo(1);

            // Second force - should use cached value
            assertThat(lazy.force()).isEqualTo("SuccessValue");
            assertThat(COUNTER.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("force should return cached value for now")
        void forceShouldReturnCachedValueForNow() throws Throwable {
            COUNTER.set(0);
            Lazy<String> lazy = Lazy.now("Preset");

            assertThat(lazy.force()).isEqualTo("Preset");
            assertThat(COUNTER.get()).isZero();
        }

        @Test
        @DisplayName("force should cache and return null value")
        void forceShouldCacheAndReturnNullValue() throws Throwable {
            COUNTER.set(0);
            Lazy<String> lazy = Lazy.defer(nullSupplier());

            // First force
            assertThat(lazy.force()).isNull();
            assertThat(COUNTER.get()).isEqualTo(1);

            // Second force - memoised null
            assertThat(lazy.force()).isNull();
            assertThat(COUNTER.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("force should cache and rethrow runtime exception")
        void forceShouldCacheAndRethrowRuntimeException() {
            COUNTER.set(0);
            Lazy<String> lazy = Lazy.defer(runtimeFailSupplier());

            // First force
            Throwable thrown1 = catchThrowable(lazy::force);
            assertThat(thrown1)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Runtime Failure");
            assertThat(COUNTER.get()).isEqualTo(1);

            // Second force - cached exception
            Throwable thrown2 = catchThrowable(lazy::force);
            assertThat(thrown2)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Runtime Failure");
            assertThat(COUNTER.get()).isEqualTo(1);
            assertThat(thrown2).isSameAs(thrown1);
        }

        @Test
        @DisplayName("force should cache and rethrow checked exception")
        void forceShouldCacheAndRethrowCheckedException() {
            COUNTER.set(0);
            Lazy<String> lazy = Lazy.defer(checkedFailSupplier());

            // First force
            Throwable thrown1 = catchThrowable(lazy::force);
            assertThat(thrown1)
                    .isInstanceOf(IOException.class)
                    .hasMessage("Checked Failure");
            assertThat(COUNTER.get()).isEqualTo(1);

            // Second force - cached exception
            Throwable thrown2 = catchThrowable(lazy::force);
            assertThat(thrown2)
                    .isInstanceOf(IOException.class)
                    .hasMessage("Checked Failure");
            assertThat(COUNTER.get()).isEqualTo(1);
            assertThat(thrown2).isSameAs(thrown1);
            assertThat(lazy.toString()).isEqualTo("Lazy[failed: IOException]");
        }
    }

    // ============================================================================
    // Map Operations
    // ============================================================================

    @Nested
    @DisplayName("map() Method")
    class MapTests {

        @Test
        @DisplayName("map should transform value lazily")
        void mapShouldTransformValueLazily() throws Throwable {
            COUNTER.set(0);
            AtomicInteger mapCounter = new AtomicInteger(0);

            Lazy<String> lazy = Lazy.defer(successSupplier());
            Lazy<Integer> mapped = lazy.map(s -> {
                mapCounter.incrementAndGet();
                return s.length();
            });

            assertThat(COUNTER.get()).isZero();
            assertThat(mapCounter.get()).isZero();

            // Force the mapped lazy
            assertThat(mapped.force()).isEqualTo("SuccessValue".length());
            assertThat(COUNTER.get()).isEqualTo(1);
            assertThat(mapCounter.get()).isEqualTo(1);

            // Force again - both should be memoised
            assertThat(mapped.force()).isEqualTo("SuccessValue".length());
            assertThat(COUNTER.get()).isEqualTo(1);
            assertThat(mapCounter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("map should propagate failure from original Lazy")
        void mapShouldPropagateFailureFromOriginalLazy() {
            COUNTER.set(0);
            AtomicInteger mapCounter = new AtomicInteger(0);

            Lazy<String> lazy = Lazy.defer(runtimeFailSupplier());
            Lazy<Integer> mapped = lazy.map(s -> {
                mapCounter.incrementAndGet();
                return s.length();
            });

            assertThat(COUNTER.get()).isZero();
            assertThat(mapCounter.get()).isZero();

            assertThatThrownBy(mapped::force)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Runtime Failure");

            assertThat(COUNTER.get()).isEqualTo(1);
            assertThat(mapCounter.get()).isZero(); // Mapper never ran
        }

        @Test
        @DisplayName("map should fail if mapper throws")
        void mapShouldFailIfMapperThrows() {
            COUNTER.set(0);
            RuntimeException mapperEx = new IllegalArgumentException("Mapper failed");

            Lazy<String> lazy = Lazy.defer(successSupplier());
            Lazy<Integer> mapped = lazy.map(s -> {
                throw mapperEx;
            });

            assertThatThrownBy(mapped::force)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Mapper failed");

            assertThat(COUNTER.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("flatMap should throw exception if mapper returns null")
        void flatMapShouldThrowExceptionIfMapperReturnsNull() {
            Lazy<String> lazy = Lazy.defer(successSupplier());
            Lazy<Integer> flatMapped = lazy.flatMap(s -> null);

            assertThatThrownBy(flatMapped::force)
                    .isInstanceOf(KindUnwrapException.class)
                    .hasMessageContaining("Function in flatMap returned null");
        }
    }

    // ============================================================================
    // FlatMap Operations
    // ============================================================================

    @Nested
    @DisplayName("flatMap() Method")
    class FlatMapTests {

        @Test
        @DisplayName("flatMap should sequence lazily")
        void flatMapShouldSequenceLazily() throws Throwable {
            COUNTER.set(0);
            AtomicInteger innerCounter = new AtomicInteger(0);

            Lazy<String> lazyA = Lazy.defer(successSupplier());
            Lazy<Integer> flatMapped = lazyA.flatMap(s ->
                    Lazy.defer(() -> {
                        innerCounter.incrementAndGet();
                        return s.length();
                    }));

            assertThat(COUNTER.get()).isZero();
            assertThat(innerCounter.get()).isZero();

            assertThat(flatMapped.force()).isEqualTo("SuccessValue".length());
            assertThat(COUNTER.get()).isEqualTo(1);
            assertThat(innerCounter.get()).isEqualTo(1);

            // Second force - both memoised
            assertThat(flatMapped.force()).isEqualTo("SuccessValue".length());
            assertThat(COUNTER.get()).isEqualTo(1);
            assertThat(innerCounter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("flatMap should propagate failure from initial Lazy")
        void flatMapShouldPropagateFailureFromInitialLazy() {
            COUNTER.set(0);
            AtomicInteger innerCounter = new AtomicInteger(0);

            Lazy<String> lazyA = Lazy.defer(runtimeFailSupplier());
            Lazy<Integer> flatMapped = lazyA.flatMap(s ->
                    Lazy.defer(() -> {
                        innerCounter.incrementAndGet();
                        return s.length();
                    }));

            assertThatThrownBy(flatMapped::force)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Runtime Failure");

            assertThat(COUNTER.get()).isEqualTo(1);
            assertThat(innerCounter.get()).isZero();
        }

        @Test
        @DisplayName("flatMap should propagate failure from mapper function")
        void flatMapShouldPropagateFailureFromMapperFunction() {
            COUNTER.set(0);
            RuntimeException mapperEx = new IllegalArgumentException("Mapper failed");

            Lazy<String> lazyA = Lazy.defer(successSupplier());
            Lazy<Integer> flatMapped = lazyA.flatMap(s -> {
                throw mapperEx;
            });

            assertThatThrownBy(flatMapped::force)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Mapper failed");

            assertThat(COUNTER.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("flatMap should propagate failure from resulting Lazy")
        void flatMapShouldPropagateFailureFromResultingLazy() {
            COUNTER.set(0);
            RuntimeException resultEx = new UnsupportedOperationException("Result Lazy failed");

            Lazy<String> lazyA = Lazy.defer(successSupplier());
            Lazy<Integer> flatMapped = lazyA.flatMap(s ->
                    Lazy.defer(() -> {
                        throw resultEx;
                    }));

            assertThatThrownBy(flatMapped::force)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Result Lazy failed");

            assertThat(COUNTER.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("flatMap should throw NPE for null mapper")
        void flatMapShouldThrowNPEForNullMapper() {
            Lazy<String> lazy = Lazy.defer(successSupplier());

            assertThatNullPointerException()
                    .isThrownBy(() -> lazy.flatMap(null))
                    .withMessageContaining("function f for Lazy.flatMap cannot be null");
        }

        @Test
        @DisplayName("flatMap should throw exception if mapper returns null")
        void flatMapShouldThrowExceptionIfMapperReturnsNull() {
            Lazy<String> lazy = Lazy.defer(successSupplier());
            Lazy<Integer> flatMapped = lazy.flatMap(s -> null);

            assertThatThrownBy(flatMapped::force)
                    .isInstanceOf(KindUnwrapException.class)
                    .hasMessageContaining("Function in flatMap returned null");
        }
    }

    // ============================================================================
    // Memoisation
    // ============================================================================

    @Nested
    @DisplayName("Memoisation Semantics")
    class MemoizationTests {

        @Test
        @DisplayName("Should memoise successful computation")
        void shouldMemoiseSuccessfulComputation() throws Throwable {
            AtomicInteger counter = new AtomicInteger(0);
            Lazy<String> lazy = Lazy.defer(() -> {
                counter.incrementAndGet();
                return "value";
            });

            lazy.force();
            lazy.force();
            lazy.force();

            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should memoise null value")
        void shouldMemoiseNullValue() throws Throwable {
            AtomicInteger counter = new AtomicInteger(0);
            Lazy<String> lazy = Lazy.defer(() -> {
                counter.incrementAndGet();
                return null;
            });

            lazy.force();
            lazy.force();

            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should memoise exception")
        void shouldMemoiseException() {
            AtomicInteger counter = new AtomicInteger(0);
            Lazy<String> lazy = Lazy.defer(() -> {
                counter.incrementAndGet();
                throw new RuntimeException("Failed");
            });

            catchThrowable(lazy::force);
            catchThrowable(lazy::force);

            assertThat(counter.get()).isEqualTo(1);
        }
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("toString should not force evaluation")
        void toStringShouldNotForceEvaluation() {
            COUNTER.set(0);
            Lazy<String> lazy = Lazy.defer(successSupplier());

            assertThat(lazy.toString()).isEqualTo("Lazy[unevaluated...]");
            assertThat(COUNTER.get()).isZero();
        }

        @Test
        @DisplayName("toString should show value after force")
        void toStringShouldShowValueAfterForce() throws Throwable {
            Lazy<String> lazy = Lazy.defer(() -> "Ready");
            lazy.force();

            assertThat(lazy.toString()).isEqualTo("Lazy[Ready]");
        }

        @Test
        @DisplayName("toString should show failure state")
        void toStringShouldShowFailureState() {
            Lazy<String> lazy = Lazy.defer(() -> {
                throw new IllegalStateException("Test");
            });

            catchThrowable(lazy::force);

            assertThat(lazy.toString()).isEqualTo("Lazy[failed: IllegalStateException]");
        }

        @Test
        @DisplayName("Lazy should use reference equality")
        void lazyShouldUseReferenceEquality() {
            Lazy<String> lazy1 = Lazy.defer(() -> "a");
            Lazy<String> lazy2 = Lazy.defer(() -> "a");
            Lazy<String> lazy1Ref = lazy1;

            assertThat(lazy1).isEqualTo(lazy1Ref);
            assertThat(lazy1).isNotEqualTo(lazy2);
            assertThat(lazy1).isNotEqualTo(null);
            assertThat(lazy1).isNotEqualTo("a");
        }

        @Test
        @DisplayName("hashCode should use reference hashCode")
        void hashCodeShouldUseReferenceHashCode() {
            Lazy<String> lazy1 = Lazy.defer(() -> "a");
            Lazy<String> lazy1Ref = lazy1;

            assertThat(lazy1.hashCode()).isEqualTo(lazy1Ref.hashCode());
        }
    }

    // ============================================================================
    // Individual Component Tests
    // ============================================================================

    @Nested
    @DisplayName("Individual Component Tests")
    class IndividualComponents {

        @Test
        @DisplayName("Test factory methods only")
        void testFactoryMethodsOnly() {
            CoreTypeTest.lazy(Lazy.class)
                    .withDeferred(Lazy.defer(() -> "test"))
                    .withNow(Lazy.now("test"))
                    .withoutMappers()
                    .onlyFactoryMethods()
                    .testAll();
        }

        @Test
        @DisplayName("Test force operation only")
        void testForceOnly() {
            CoreTypeTest.lazy(Lazy.class)
                    .withDeferred(Lazy.defer(() -> "test"))
                    .withNow(Lazy.now("test"))
                    .withoutMappers()
                    .onlyForce()
                    .testAll();
        }

        @Test
        @DisplayName("Test memoisation only")
        void testMemoizationOnly() {
            CoreTypeTest.lazy(Lazy.class)
                    .withDeferred(Lazy.defer(() -> "test"))
                    .withNow(Lazy.now("test"))
                    .withoutMappers()
                    .onlyMemoisation()
                    .testAll();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            CoreTypeTest.lazy(Lazy.class)
                    .withDeferred(Lazy.defer(() -> "test"))
                    .withNow(Lazy.now("test"))
                    .withMappers(Object::toString)
                    .onlyValidations()
                    .testAll();
        }
    }
}