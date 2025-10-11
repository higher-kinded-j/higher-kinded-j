// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LazyKindHelper Complete Test Suite")
class LazyKindHelperTest extends TypeClassTestBase<LazyKind.Witness, String, Integer> {

    // ============================================================================
    // Test Fixtures
    // ============================================================================

    private static final ThrowableSupplier<String> BASE_COMPUTATION = () -> "Result";
    private static final Lazy<String> BASE_LAZY = Lazy.defer(BASE_COMPUTATION);

    private static final RuntimeException TEST_EXCEPTION = new RuntimeException("Lazy Failure");
    private static final ThrowableSupplier<String> FAILING_COMPUTATION = () -> {
        throw TEST_EXCEPTION;
    };
    private static final Lazy<String> FAILING_LAZY = Lazy.defer(FAILING_COMPUTATION);

    private static final ThrowableSupplier<String> CHECKED_EXCEPTION_COMPUTATION = () -> {
        throw new IOException("Checked IO Failure");
    };
    private static final Lazy<String> CHECKED_FAILING_LAZY = Lazy.defer(CHECKED_EXCEPTION_COMPUTATION);

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

    // ============================================================================
    // Complete Test Suite
    // ============================================================================

    @Nested
    @DisplayName("Complete LazyKindHelper Test Suite")
    class CompleteLazyKindHelperTestSuite {

        @Test
        @DisplayName("Run complete LazyKindHelper tests using base fixtures")
        void runCompleteLazyKindHelperTestsUsingBaseFixtures() {
            validateRequiredFixtures();

            Lazy<String> instance = LAZY.narrow(validKind);
            CoreTypeTest.lazyKindHelper(instance)
                    .test();
        }

        @Test
        @DisplayName("Run complete LazyKindHelper tests with custom instance")
        void runCompleteLazyKindHelperTestsWithCustomInstance() {
            CoreTypeTest.lazyKindHelper(BASE_LAZY)
                    .test();
        }

        @Test
        @DisplayName("Run complete LazyKindHelper tests with performance")
        void runCompleteLazyKindHelperTestsWithPerformance() {
            CoreTypeTest.lazyKindHelper(BASE_LAZY)
                    .withPerformanceTests()
                    .test();
        }

        @Test
        @DisplayName("Run complete LazyKindHelper tests with concurrency")
        void runCompleteLazyKindHelperTestsWithConcurrency() {
            CoreTypeTest.lazyKindHelper(BASE_LAZY)
                    .withConcurrencyTests()
                    .test();
        }
    }

    // ============================================================================
    // Widen Operations
    // ============================================================================

    @Nested
    @DisplayName("widen() Operations")
    class WidenTests {

        @Test
        @DisplayName("widen should return LazyHolder for Lazy")
        void widenShouldReturnHolderForLazy() {
            Kind<LazyKind.Witness, String> kind = LAZY.widen(BASE_LAZY);

            assertThat(kind).isInstanceOf(LazyKindHelper.LazyHolder.class);
            assertThat(LAZY.narrow(kind)).isSameAs(BASE_LAZY);
        }

        @Test
        @DisplayName("widen should throw NPE for null input")
        void widenShouldThrowNPEForNullInput() {
            assertThatNullPointerException()
                    .isThrownBy(() -> LAZY.widen(null))
                    .withMessageContaining(NULL_WIDEN_INPUT_TEMPLATE.formatted("Lazy"));
        }

        @Test
        @DisplayName("widen should preserve lazy semantics")
        void widenShouldPreserveLazySemantics() throws Throwable {
            AtomicInteger counter = new AtomicInteger(0);
            Lazy<String> lazy = Lazy.defer(() -> {
                counter.incrementAndGet();
                return "test";
            });

            Kind<LazyKind.Witness, String> widened = LAZY.widen(lazy);

            assertThat(counter.get()).isZero();

            String result = LAZY.force(widened);
            assertThat(result).isEqualTo("test");
            assertThat(counter.get()).isEqualTo(1);
        }
    }

    // ============================================================================
    // Narrow Operations
    // ============================================================================

    @Nested
    @DisplayName("narrow() Operations")
    class NarrowTests {

        @Test
        @DisplayName("narrow should return original Lazy")
        void narrowShouldReturnOriginalLazy() {
            Kind<LazyKind.Witness, String> kind = LAZY.widen(BASE_LAZY);

            assertThat(LAZY.narrow(kind)).isSameAs(BASE_LAZY);
        }

        @Test
        @DisplayName("narrow should throw for null input")
        void narrowShouldThrowForNullInput() {
            assertThatThrownBy(() -> LAZY.narrow(null))
                    .isInstanceOf(KindUnwrapException.class)
                    .hasMessageContaining(NULL_KIND_TEMPLATE.formatted("Lazy"));
        }

        @Test
        @DisplayName("narrow should throw for unknown Kind type")
        void narrowShouldThrowForUnknownKindType() {
            record DummyOtherKind<A>() implements Kind<LazyKind.Witness, A> {}
            Kind<LazyKind.Witness, String> unknownKind = new DummyOtherKind<>();

            assertThatThrownBy(() -> LAZY.narrow(unknownKind))
                    .isInstanceOf(KindUnwrapException.class)
                    .hasMessageContaining(
                            INVALID_KIND_TYPE_TEMPLATE.formatted("Lazy", DummyOtherKind.class.getName()));
        }

        @Test
        @DisplayName("narrow should preserve computation state")
        void narrowShouldPreserveComputationState() throws Throwable {
            Lazy<String> original = Lazy.now("PreComputed");
            Kind<LazyKind.Witness, String> widened = LAZY.widen(original);
            Lazy<String> narrowed = LAZY.narrow(widened);

            assertThat(narrowed.toString()).contains("PreComputed");
            assertThat(narrowed.force()).isEqualTo("PreComputed");
        }
    }

    // ============================================================================
    // Helper Factory Methods
    // ============================================================================

    @Nested
    @DisplayName("Helper Factory Methods")
    class HelperFactoryTests {

        @Test
        @DisplayName("defer should wrap supplier lazily")
        void deferShouldWrapSupplierLazily() throws Throwable {
            AtomicInteger counter = new AtomicInteger(0);
            ThrowableSupplier<Integer> supplier = () -> {
                counter.incrementAndGet();
                return 42;
            };

            Kind<LazyKind.Witness, Integer> kind = LAZY.defer(supplier);

            assertThat(counter.get()).isZero();

            Lazy<Integer> lazy = LAZY.narrow(kind);
            assertThat(lazy.force()).isEqualTo(42);
            assertThat(counter.get()).isEqualTo(1);

            // Second force should use memoised value
            assertThat(lazy.force()).isEqualTo(42);
            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("defer should throw NPE for null supplier")
        void deferShouldThrowNPEForNullSupplier() {
            assertThatNullPointerException()
                    .isThrownBy(() -> LAZY.defer(null))
                    .withMessageContaining("computation");
        }

        @Test
        @DisplayName("now should wrap already evaluated value")
        void nowShouldWrapAlreadyEvaluatedValue() throws Throwable {
            AtomicInteger counter = new AtomicInteger(0);
            Kind<LazyKind.Witness, String> kind = LAZY.now("Precomputed");

            assertThat(counter.get()).isZero();

            Lazy<String> lazy = LAZY.narrow(kind);
            assertThat(lazy.force()).isEqualTo("Precomputed");
            assertThat(counter.get()).isZero();
        }

        @Test
        @DisplayName("now should wrap null value")
        void nowShouldWrapNullValue() throws Throwable {
            Kind<LazyKind.Witness, String> kind = LAZY.now(null);
            Lazy<String> lazy = LAZY.narrow(kind);

            assertThat(lazy.force()).isNull();
        }
    }

    // ============================================================================
    // Force Operations
    // ============================================================================

    @Nested
    @DisplayName("force() Operations")
    class ForceTests {

        @Test
        @DisplayName("force should execute wrapped Lazy")
        void forceShouldExecuteWrappedLazy() throws Throwable {
            AtomicInteger counter = new AtomicInteger(0);
            Kind<LazyKind.Witness, String> kind = LAZY.defer(() -> {
                counter.incrementAndGet();
                return "Executed";
            });

            assertThat(counter.get()).isZero();
            assertThat(LAZY.force(kind)).isEqualTo("Executed");
            assertThat(counter.get()).isEqualTo(1);

            // Second force should use memoised value
            assertThat(LAZY.force(kind)).isEqualTo("Executed");
            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("force should propagate runtime exception from Lazy")
        void forceShouldPropagateRuntimeExceptionFromLazy() {
            Kind<LazyKind.Witness, String> failingKind = LAZY.widen(FAILING_LAZY);

            assertThatThrownBy(() -> LAZY.force(failingKind))
                    .isInstanceOf(RuntimeException.class)
                    .isSameAs(TEST_EXCEPTION);
        }

        @Test
        @DisplayName("force should propagate checked exception from Lazy")
        void forceShouldPropagateCheckedExceptionFromLazy() {
            Kind<LazyKind.Witness, String> checkedFailingKind = LAZY.widen(CHECKED_FAILING_LAZY);

            Throwable thrown = catchThrowable(() -> LAZY.force(checkedFailingKind));
            assertThat(thrown)
                    .isInstanceOf(IOException.class)
                    .hasMessage("Checked IO Failure");
        }

        @Test
        @DisplayName("force should throw KindUnwrapException if Kind is invalid")
        void forceShouldThrowKindUnwrapExceptionIfKindIsInvalid() {
            assertThatThrownBy(() -> LAZY.force(null))
                    .isInstanceOf(KindUnwrapException.class);
        }

        @Test
        @DisplayName("force should handle null results correctly")
        void forceShouldHandleNullResultsCorrectly() throws Throwable {
            Kind<LazyKind.Witness, String> kind = LAZY.defer(() -> null);

            assertThat(LAZY.force(kind)).isNull();
        }
    }

    // ============================================================================
    // Round-Trip Tests
    // ============================================================================

    @Nested
    @DisplayName("Round-Trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Round-trip should preserve identity")
        void roundTripShouldPreserveIdentity() {
            Kind<LazyKind.Witness, String> widened = LAZY.widen(BASE_LAZY);
            Lazy<String> narrowed = LAZY.narrow(widened);

            assertThat(narrowed).isSameAs(BASE_LAZY);
        }

        @Test
        @DisplayName("Multiple round-trips should preserve identity")
        void multipleRoundTripsShouldPreserveIdentity() {
            Lazy<String> current = BASE_LAZY;

            for (int i = 0; i < 5; i++) {
                Kind<LazyKind.Witness, String> widened = LAZY.widen(current);
                current = LAZY.narrow(widened);
            }

            assertThat(current).isSameAs(BASE_LAZY);
        }
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle Lazy with side effects")
        void shouldHandleLazyWithSideEffects() throws Throwable {
            AtomicInteger sideEffect = new AtomicInteger(0);
            Lazy<String> lazy = Lazy.defer(() -> {
                sideEffect.incrementAndGet();
                return "result";
            });

            Kind<LazyKind.Witness, String> kind = LAZY.widen(lazy);
            assertThat(sideEffect.get()).isZero();

            LAZY.force(kind);
            assertThat(sideEffect.get()).isEqualTo(1);

            LAZY.force(kind);
            assertThat(sideEffect.get()).isEqualTo(1); // Side effect only once
        }

        @Test
        @DisplayName("Should handle Lazy that throws on second force")
        void shouldHandleLazyThatThrowsOnSecondForce() throws Throwable {
            AtomicInteger callCount = new AtomicInteger(0);
            Lazy<String> lazy = Lazy.defer(() -> {
                if (callCount.incrementAndGet() > 1) {
                    throw new IllegalStateException("Should not call twice");
                }
                return "result";
            });

            Kind<LazyKind.Witness, String> kind = LAZY.widen(lazy);

            String first = LAZY.force(kind);
            String second = LAZY.force(kind);

            assertThat(first).isEqualTo("result");
            assertThat(second).isEqualTo("result");
            assertThat(callCount.get()).isEqualTo(1);
        }
    }

    // ============================================================================
    // Individual Component Tests
    // ============================================================================

    @Nested
    @DisplayName("Individual Component Tests")
    class IndividualComponents {

        @Test
        @DisplayName("Test round-trip only")
        void testRoundTripOnly() {
            CoreTypeTest.lazyKindHelper(BASE_LAZY)
                    .skipValidations()
                    .skipInvalidType()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            CoreTypeTest.lazyKindHelper(BASE_LAZY)
                    .skipRoundTrip()
                    .skipInvalidType()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test invalid type handling only")
        void testInvalidTypeHandlingOnly() {
            CoreTypeTest.lazyKindHelper(BASE_LAZY)
                    .skipRoundTrip()
                    .skipValidations()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test performance characteristics")
        void testPerformanceCharacteristics() {
            CoreTypeTest.lazyKindHelper(BASE_LAZY)
                    .skipRoundTrip()
                    .skipValidations()
                    .skipInvalidType()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .withPerformanceTests()
                    .test();
        }

        @Test
        @DisplayName("Test concurrency safety")
        void testConcurrencySafety() {
            CoreTypeTest.lazyKindHelper(BASE_LAZY)
                    .skipRoundTrip()
                    .skipValidations()
                    .skipInvalidType()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .withConcurrencyTests()
                    .test();
        }
    }
}