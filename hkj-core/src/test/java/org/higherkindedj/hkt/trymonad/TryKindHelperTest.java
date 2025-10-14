// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.io.IOException;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryKindHelper Complete Test Suite")
class TryKindHelperTest extends TypeClassTestBase<TryKind.Witness, String, Integer> {

    private final String successValue = "Test Value";
    private final RuntimeException testException = new RuntimeException("Test failure");
    private final Try<String> successInstance = Try.success(successValue);
    private final Try<String> failureInstance = Try.failure(testException);


    @Override
    protected Kind<TryKind.Witness, String> createValidKind() {
        return TRY.widen(successInstance);
    }

    @Override
    protected Kind<TryKind.Witness, String> createValidKind2() {
        return TRY.widen(Try.success("Second Value"));
    }

    @Override
    protected Function<String, Integer> createValidMapper() {
        return String::length;
    }

    @Override
    protected BiPredicate<Kind<TryKind.Witness, ?>, Kind<TryKind.Witness, ?>>
    createEqualityChecker() {
        return (k1, k2) -> {
            Try<?> t1 = TRY.narrow(k1);
            Try<?> t2 = TRY.narrow(k2);
            return t1.equals(t2);
        };
    }


    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete KindHelper tests")
        void runCompleteKindHelperTests() {
            CoreTypeTest.tryKindHelper(successInstance).test();
        }

        @Test
        @DisplayName("Run KindHelper tests with performance validation")
        void runWithPerformanceTests() {
            CoreTypeTest.tryKindHelper(successInstance)
                    .withPerformanceTests()
                    .test();
        }

        @Test
        @DisplayName("Run KindHelper tests with concurrency validation")
        void runWithConcurrencyTests() {
            CoreTypeTest.tryKindHelper(successInstance)
                    .withConcurrencyTests()
                    .test();
        }
    }

    @Nested
    @DisplayName("Individual Components")
    class IndividualComponents {

        @Test
        @DisplayName("Test round-trip only")
        void testRoundTripOnly() {
            CoreTypeTest.tryKindHelper(successInstance)
                    .skipValidations()
                    .skipInvalidType()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            CoreTypeTest.tryKindHelper(successInstance)
                    .skipRoundTrip()
                    .skipInvalidType()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test invalid type handling only")
        void testInvalidTypeOnly() {
            CoreTypeTest.tryKindHelper(successInstance)
                    .skipRoundTrip()
                    .skipValidations()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test idempotency only")
        void testIdempotencyOnly() {
            CoreTypeTest.tryKindHelper(successInstance)
                    .skipRoundTrip()
                    .skipValidations()
                    .skipInvalidType()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test edge cases only")
        void testEdgeCasesOnly() {
            CoreTypeTest.tryKindHelper(successInstance)
                    .skipRoundTrip()
                    .skipValidations()
                    .skipInvalidType()
                    .skipIdempotency()
                    .test();
        }
    }

    // ============================================================================
    // Widen Operation Tests
    // ============================================================================

    @Nested
    @DisplayName("Widen Operations")
    class WidenOperations {

        @Test
        @DisplayName("widen() should create holder for Success")
        void widen_shouldCreateHolderForSuccess() {
            Kind<TryKind.Witness, String> kind = TRY.widen(successInstance);

            assertThat(kind)
                    .as("widen should return non-null Kind")
                    .isNotNull();

            assertThat(TRY.narrow(kind))
                    .as("narrowed result should be same instance")
                    .isSameAs(successInstance);
        }

        @Test
        @DisplayName("widen() should create holder for Failure")
        void widen_shouldCreateHolderForFailure() {
            Kind<TryKind.Witness, String> kind = TRY.widen(failureInstance);

            assertThat(kind)
                    .as("widen should return non-null Kind")
                    .isNotNull();

            assertThat(TRY.narrow(kind))
                    .as("narrowed result should be same instance")
                    .isSameAs(failureInstance);
        }

        @Test
        @DisplayName("widen() should handle Success with null value")
        void widen_shouldHandleSuccessWithNullValue() {
            Try<String> successNull = Try.success(null);
            Kind<TryKind.Witness, String> kind = TRY.widen(successNull);

            assertThat(TRY.narrow(kind))
                    .as("should preserve Success with null value")
                    .isSameAs(successNull);
        }

        @Test
        @DisplayName("widen() should throw NPE for null Try")
        void widen_shouldThrowNPEForNullTry() {
            assertThatNullPointerException()
                    .isThrownBy(() -> TRY.widen(null))
                    .withMessageContaining("Input Try cannot be null");
        }
    }

    // ============================================================================
    // Narrow Operation Tests
    // ============================================================================

    @Nested
    @DisplayName("Narrow Operations")
    class NarrowOperations {

        @Test
        @DisplayName("narrow() should return original Success")
        void narrow_shouldReturnOriginalSuccess() {
            Kind<TryKind.Witness, String> kind = TRY.widen(successInstance);
            Try<String> result = TRY.narrow(kind);

            assertThat(result)
                    .as("narrow should return same instance")
                    .isSameAs(successInstance);
        }

        @Test
        @DisplayName("narrow() should return original Failure")
        void narrow_shouldReturnOriginalFailure() {
            Kind<TryKind.Witness, String> kind = TRY.widen(failureInstance);
            Try<String> result = TRY.narrow(kind);

            assertThat(result)
                    .as("narrow should return same instance")
                    .isSameAs(failureInstance);
        }

        @Test
        @DisplayName("narrow() should throw KindUnwrapException for null Kind")
        void narrow_shouldThrowKindUnwrapExceptionForNullKind() {
            assertThatExceptionOfType(org.higherkindedj.hkt.exception.KindUnwrapException.class)
                    .isThrownBy(() -> TRY.narrow(null))
                    .withMessageContaining("Cannot narrow null Kind for Try");
        }

        @Test
        @DisplayName("narrow() should throw KindUnwrapException for invalid Kind type")
        void narrow_shouldThrowKindUnwrapExceptionForInvalidKindType() {
            Kind<TryKind.Witness, String> invalidKind = new InvalidKind<>();

            assertThatExceptionOfType(org.higherkindedj.hkt.exception.KindUnwrapException.class)
                    .isThrownBy(() -> TRY.narrow(invalidKind))
                    .withMessageContaining("Kind instance is not a Try:");
        }

        // Dummy Kind implementation for testing invalid types
        private record InvalidKind<A>() implements Kind<TryKind.Witness, A> {}
    }


    @Nested
    @DisplayName("Helper Factory Methods")
    class HelperFactoryMethods {

        @Test
        @DisplayName("success() should create Success Kind")
        void success_shouldCreateSuccessKind() {
            Kind<TryKind.Witness, String> kind = TRY.success(successValue);
            Try<String> result = TRY.narrow(kind);

            assertThat(result.isSuccess())
                    .as("should be Success")
                    .isTrue();

            assertThatCode(() -> assertThat(result.get()).isEqualTo(successValue))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("success() should allow null value")
        void success_shouldAllowNullValue() {
            Kind<TryKind.Witness, String> kind = TRY.success(null);
            Try<String> result = TRY.narrow(kind);

            assertThat(result.isSuccess())
                    .as("should be Success")
                    .isTrue();

            assertThatCode(() -> assertThat(result.get()).isNull())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("failure() should create Failure Kind")
        void failure_shouldCreateFailureKind() {
            IOException ioException = new IOException("I/O error");
            Kind<TryKind.Witness, String> kind = TRY.failure(ioException);
            Try<String> result = TRY.narrow(kind);

            assertThat(result.isFailure())
                    .as("should be Failure")
                    .isTrue();

            assertThatThrownBy(result::get)
                    .isSameAs(ioException);
        }

        @Test
        @DisplayName("failure() should throw NPE for null Throwable")
        void failure_shouldThrowNPEForNullThrowable() {
            assertThatNullPointerException()
                    .isThrownBy(() -> TRY.failure(null))
                    .withMessageContaining("Throwable for Failure cannot be null");
        }

        @Test
        @DisplayName("tryOf() should create Success Kind for normal execution")
        void tryOf_shouldCreateSuccessKindForNormalExecution() {
            Kind<TryKind.Witness, Integer> kind = TRY.tryOf(() -> 10 / 2);
            Try<Integer> result = TRY.narrow(kind);

            assertThat(result.isSuccess())
                    .as("should be Success")
                    .isTrue();

            assertThatCode(() -> assertThat(result.get()).isEqualTo(5))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("tryOf() should create Failure Kind when supplier throws")
        void tryOf_shouldCreateFailureKindWhenSupplierThrows() {
            ArithmeticException arithmeticException = new ArithmeticException("Division by zero");
            Kind<TryKind.Witness, Integer> kind = TRY.tryOf(() -> {
                throw arithmeticException;
            });
            Try<Integer> result = TRY.narrow(kind);

            assertThat(result.isFailure())
                    .as("should be Failure")
                    .isTrue();

            assertThatThrownBy(result::get)
                    .isSameAs(arithmeticException);
        }

        @Test
        @DisplayName("tryOf() should throw NPE for null supplier")
        void tryOf_shouldThrowNPEForNullSupplier() {
            assertThatNullPointerException()
                    .isThrownBy(() -> TRY.tryOf(null))
                    .withMessageContaining("Function supplier for Try.of cannot be null");
        }
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Multiple widen operations should be idempotent")
        void multipleWidenOperationsShouldBeIdempotent() {
            Kind<TryKind.Witness, String> kind1 = TRY.widen(successInstance);
            Kind<TryKind.Witness, String> kind2 = TRY.widen(successInstance);

            assertThat(TRY.narrow(kind1))
                    .as("both should narrow to same instance")
                    .isSameAs(TRY.narrow(kind2));
        }

        @Test
        @DisplayName("Multiple round-trips should preserve identity")
        void multipleRoundTripsShouldPreserveIdentity() {
            Try<String> current = successInstance;

            for (int i = 0; i < 5; i++) {
                Kind<TryKind.Witness, String> kind = TRY.widen(current);
                current = TRY.narrow(kind);
            }

            assertThat(current)
                    .as("should be same instance after multiple round-trips")
                    .isSameAs(successInstance);
        }

        @Test
        @DisplayName("Round-trip with Failure should preserve exception")
        void roundTripWithFailureShouldPreserveException() {
            Kind<TryKind.Witness, String> kind = TRY.widen(failureInstance);
            Try<String> result = TRY.narrow(kind);

            assertThatThrownBy(result::get)
                    .as("should throw same exception")
                    .isSameAs(testException);
        }

        @Test
        @DisplayName("Widen and narrow should work with different exception types")
        void widenAndNarrowShouldWorkWithDifferentExceptionTypes() {
            IOException ioException = new IOException("I/O error");
            Error stackOverflow = new StackOverflowError("Stack overflow");

            Try<String> ioFailure = Try.failure(ioException);
            Try<String> errorFailure = Try.failure(stackOverflow);

            assertThat(TRY.narrow(TRY.widen(ioFailure)))
                    .as("should preserve IOException Failure")
                    .isSameAs(ioFailure);

            assertThat(TRY.narrow(TRY.widen(errorFailure)))
                    .as("should preserve Error Failure")
                    .isSameAs(errorFailure);
        }
    }
}