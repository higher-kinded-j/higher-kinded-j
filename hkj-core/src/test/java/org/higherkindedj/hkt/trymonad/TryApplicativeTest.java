// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryApplicative Complete Test Suite")
class TryApplicativeTest
        extends TypeClassTestBase<TryKind.Witness, String, Integer> {

    private TryApplicative applicative;

    // ============================================================================
    // TypeClassTestBase Implementation
    // ============================================================================

    @Override
    protected Kind<TryKind.Witness, String> createValidKind() {
        return TryKindHelper.TRY.widen(Try.success("test value"));
    }

    @Override
    protected Kind<TryKind.Witness, String> createValidKind2() {
        return TryKindHelper.TRY.widen(Try.success("second value"));
    }

    @Override
    protected Function<String, Integer> createValidMapper() {
        return String::length;
    }

    @Override
    protected BiPredicate<Kind<TryKind.Witness, ?>, Kind<TryKind.Witness, ?>>
    createEqualityChecker() {
        return (k1, k2) -> {
            Try<?> t1 = TryKindHelper.TRY.narrow(k1);
            Try<?> t2 = TryKindHelper.TRY.narrow(k2);
            return t1.equals(t2);
        };
    }

    @Override
    protected Function<Integer, String> createSecondMapper() {
        return Object::toString;
    }

    @Override
    protected Kind<TryKind.Witness, Function<String, Integer>> createValidFunctionKind() {
        Function<String, Integer> func = String::length;
        return TryKindHelper.TRY.widen(Try.success(func));
    }

    @Override
    protected BiFunction<String, String, Integer> createValidCombiningFunction() {
        return (s1, s2) -> s1.length() + s2.length();
    }

    @Override
    protected String createTestValue() {
        return "test";
    }

    @Override
    protected Function<String, Kind<TryKind.Witness, Integer>> createTestFunction() {
        return s -> TryKindHelper.TRY.widen(Try.success(s.length()));
    }

    @BeforeEach
    void setUpApplicative() {
        applicative = new TryApplicative();
    }

    // ============================================================================
    // Complete Test Suite Using Framework
    // ============================================================================

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete Applicative test pattern")
        void runCompleteApplicativePattern() {
            TypeClassTest.applicative(TryApplicative.class)
                    .instance(applicative)
                    .withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .withLawsTesting(testValue, validMapper, equalityChecker)
                    .testAll();
        }

        @Test
        @DisplayName("Verify Applicative operations")
        void verifyApplicativeOperations() {
            TypeClassTest.applicative(TryApplicative.class)
                    .instance(applicative)
                    .withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .testOperations();
        }

        @Test
        @DisplayName("Verify Applicative validations")
        void verifyApplicativeValidations() {
            TypeClassTest.applicative(TryApplicative.class)
                    .instance(applicative)
                    .withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .testValidations();
        }
    }

    // ============================================================================
    // Individual Component Tests
    // ============================================================================

    @Nested
    @DisplayName("Individual Components")
    class IndividualComponents {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTest.applicative(TryApplicative.class)
                    .instance(applicative)
                    .withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .selectTests()
                    .onlyOperations()
                    .test();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTest.applicative(TryApplicative.class)
                    .instance(applicative)
                    .withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .selectTests()
                    .onlyValidations()
                    .test();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.applicative(TryApplicative.class)
                    .instance(applicative)
                    .withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .selectTests()
                    .onlyExceptions()
                    .test();
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTest.applicative(TryApplicative.class)
                    .instance(applicative)
                    .withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .withLawsTesting(testValue, validMapper, equalityChecker)
                    .selectTests()
                    .onlyLaws()
                    .test();
        }
    }

    // ============================================================================
    // Operation Tests - of()
    // ============================================================================

    @Nested
    @DisplayName("of() Operation Tests")
    class OfOperationTests {

        @Test
        @DisplayName("of() should wrap value in Success")
        void of_shouldWrapValueInSuccess() {
            Kind<TryKind.Witness, String> result = applicative.of("test");
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo("test"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("of() should allow null value")
        void of_shouldAllowNullValue() {
            Kind<TryKind.Witness, String> result = applicative.of(null);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isNull())
                    .doesNotThrowAnyException();
        }
    }

    // ============================================================================
    // Operation Tests - ap()
    // ============================================================================

    @Nested
    @DisplayName("ap() Operation Tests")
    class ApOperationTests {

        @Test
        @DisplayName("ap() with Success function and Success value should apply function")
        void ap_withSuccessAndSuccess_shouldApplyFunction() {
            Function<String, Integer> func = String::length;
            Kind<TryKind.Witness, Function<String, Integer>> funcKind =
                    TryKindHelper.TRY.widen(Try.success(func));
            Kind<TryKind.Witness, String> valueKind =
                    TryKindHelper.TRY.widen(Try.success("hello"));

            Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(5))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ap() with Failure function should return Failure")
        void ap_withFailureFunction_shouldReturnFailure() {
            RuntimeException exception = new RuntimeException("Function failure");
            Kind<TryKind.Witness, Function<String, Integer>> funcKind =
                    TryKindHelper.TRY.widen(Try.failure(exception));
            Kind<TryKind.Witness, String> valueKind =
                    TryKindHelper.TRY.widen(Try.success("hello"));

            Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }

        @Test
        @DisplayName("ap() with Success function and Failure value should return Failure")
        void ap_withSuccessFunctionAndFailureValue_shouldReturnFailure() {
            Function<String, Integer> func = String::length;
            Kind<TryKind.Witness, Function<String, Integer>> funcKind =
                    TryKindHelper.TRY.widen(Try.success(func));
            RuntimeException exception = new RuntimeException("Value failure");
            Kind<TryKind.Witness, String> valueKind =
                    TryKindHelper.TRY.widen(Try.failure(exception));

            Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }

        @Test
        @DisplayName("ap() with both Failure should return first Failure")
        void ap_withBothFailure_shouldReturnFirstFailure() {
            RuntimeException funcException = new RuntimeException("Function failure");
            Kind<TryKind.Witness, Function<String, Integer>> funcKind =
                    TryKindHelper.TRY.widen(Try.failure(funcException));
            RuntimeException valueException = new RuntimeException("Value failure");
            Kind<TryKind.Witness, String> valueKind =
                    TryKindHelper.TRY.widen(Try.failure(valueException));

            Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(funcException);
        }

        @Test
        @DisplayName("ap() should capture exception thrown by function")
        void ap_shouldCaptureExceptionThrownByFunction() {
            RuntimeException funcException = new RuntimeException("Function threw");
            Function<String, Integer> throwingFunc =
                    s -> {
                        throw funcException;
                    };
            Kind<TryKind.Witness, Function<String, Integer>> funcKind =
                    TryKindHelper.TRY.widen(Try.success(throwingFunc));
            Kind<TryKind.Witness, String> valueKind =
                    TryKindHelper.TRY.widen(Try.success("test"));

            Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(funcException);
        }
    }

    // ============================================================================
    // Validation Tests
    // ============================================================================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("ap() should throw NPE if function Kind is null")
        void ap_shouldThrowNPEIfFunctionKindIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> applicative.ap(null, validKind))
                    .withMessageContaining("Kind for TryApplicative.ap (function) cannot be null");
        }

        @Test
        @DisplayName("ap() should throw NPE if argument Kind is null")
        void ap_shouldThrowNPEIfArgumentKindIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> applicative.ap(validFunctionKind, null))
                    .withMessageContaining("Kind for TryApplicative.ap (argument) cannot be null");
        }
    }

    // ============================================================================
    // Exception Propagation Tests
    // ============================================================================

    @Nested
    @DisplayName("Exception Propagation Tests")
    class ExceptionPropagationTests {

        @Test
        @DisplayName("ap() should propagate RuntimeException from function application")
        void ap_shouldPropagateRuntimeExceptionFromFunctionApplication() {
            RuntimeException testException = new RuntimeException("Test exception");
            Function<String, Integer> throwingFunc =
                    s -> {
                        throw testException;
                    };
            Kind<TryKind.Witness, Function<String, Integer>> funcKind =
                    TryKindHelper.TRY.widen(Try.success(throwingFunc));

            Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, validKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(testException);
        }

        @Test
        @DisplayName("ap() should propagate Error from function application")
        void ap_shouldPropagateErrorFromFunctionApplication() {
            StackOverflowError testError = new StackOverflowError("Stack overflow");
            Function<String, Integer> throwingFunc =
                    s -> {
                        throw testError;
                    };
            Kind<TryKind.Witness, Function<String, Integer>> funcKind =
                    TryKindHelper.TRY.widen(Try.success(throwingFunc));

            Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, validKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(testError);
        }
    }

    // ============================================================================
    // Applicative Law Tests
    // ============================================================================

    @Nested
    @DisplayName("Applicative Law Tests")
    class ApplicativeLawTests {

        @Test
        @DisplayName("Identity law: ap(of(id), v) == v")
        void identityLaw() {
            Function<String, String> identity = s -> s;
            Kind<TryKind.Witness, Function<String, String>> idFunc = applicative.of(identity);
            Kind<TryKind.Witness, String> result = applicative.ap(idFunc, validKind);

            assertThat(equalityChecker.test(result, validKind))
                    .as("Applicative Identity Law: ap(of(id), v) == v")
                    .isTrue();
        }

        @Test
        @DisplayName("Homomorphism law: ap(of(f), of(x)) == of(f(x))")
        void homomorphismLaw() {
            String testVal = "test";
            Function<String, Integer> func = String::length;

            Kind<TryKind.Witness, Function<String, Integer>> funcKind = applicative.of(func);
            Kind<TryKind.Witness, String> valueKind = applicative.of(testVal);

            // Left side: ap(of(f), of(x))
            Kind<TryKind.Witness, Integer> leftSide = applicative.ap(funcKind, valueKind);

            // Right side: of(f(x))
            Kind<TryKind.Witness, Integer> rightSide = applicative.of(func.apply(testVal));

            assertThat(equalityChecker.test(leftSide, rightSide))
                    .as("Applicative Homomorphism Law: ap(of(f), of(x)) == of(f(x))")
                    .isTrue();
        }

        @Test
        @DisplayName("Interchange law: ap(u, of(y)) == ap(of(f -> f(y)), u)")
        void interchangeLaw() {
            String testVal = "test";
            Function<String, Integer> func = String::length;
            Kind<TryKind.Witness, Function<String, Integer>> funcKind = applicative.of(func);
            Kind<TryKind.Witness, String> valueKind = applicative.of(testVal);

            // Left side: ap(u, of(y))
            Kind<TryKind.Witness, Integer> leftSide = applicative.ap(funcKind, valueKind);

            // Right side: ap(of(f -> f(y)), u)
            Function<Function<String, Integer>, Integer> applyToValue = f -> f.apply(testVal);
            Kind<TryKind.Witness, Function<Function<String, Integer>, Integer>> applyFunc =
                    applicative.of(applyToValue);
            Kind<TryKind.Witness, Integer> rightSide = applicative.ap(applyFunc, funcKind);

            assertThat(equalityChecker.test(leftSide, rightSide))
                    .as("Applicative Interchange Law: ap(u, of(y)) == ap(of(f -> f(y)), u)")
                    .isTrue();
        }
    }

    // ============================================================================
    // Edge Case Tests
    // ============================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("ap() should handle function returning null")
        void ap_shouldHandleFunctionReturningNull() {
            Function<String, Integer> nullFunc = s -> null;
            Kind<TryKind.Witness, Function<String, Integer>> funcKind =
                    TryKindHelper.TRY.widen(Try.success(nullFunc));
            Kind<TryKind.Witness, String> valueKind =
                    TryKindHelper.TRY.widen(Try.success("test"));

            Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isNull())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ap() should handle null value")
        void ap_shouldHandleNullValue() {
            Function<String, Integer> safeFunc = s -> s == null ? -1 : s.length();
            Kind<TryKind.Witness, Function<String, Integer>> funcKind =
                    TryKindHelper.TRY.widen(Try.success(safeFunc));
            Kind<TryKind.Witness, String> valueKind = TryKindHelper.TRY.widen(Try.success(null));

            Kind<TryKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(-1))
                    .doesNotThrowAnyException();
        }
    }
}