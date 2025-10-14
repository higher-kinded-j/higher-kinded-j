// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
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

@DisplayName("TryMonad Complete Test Suite")
class TryMonadTest extends TypeClassTestBase<TryKind.Witness, String, Integer> {

    private TryMonad monad;

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
    protected Function<String, Kind<TryKind.Witness, Integer>> createValidFlatMapper() {
        return s -> TryKindHelper.TRY.widen(Try.success(s.length()));
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

    @Override
    protected Function<Integer, Kind<TryKind.Witness, Integer>> createChainFunction() {
        return i -> TryKindHelper.TRY.widen(Try.success(i * 2));
    }

    @BeforeEach
    void setUpMonad() {
        monad = TryMonad.INSTANCE;
    }

    // ============================================================================
    // Complete Test Suite Using Framework
    // ============================================================================

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete Monad test pattern")
        void runCompleteMonadPattern() {
            TypeClassTest.monad(TryMonad.class)
                    .instance(monad)
                    .withKind(validKind)
                    .withMonadOperations(
                            validKind2,
                            validMapper,
                            validFlatMapper,
                            validFunctionKind,
                            validCombiningFunction)
                    .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
                    .testAll();
        }

        @Test
        @DisplayName("Verify Monad operations")
        void verifyMonadOperations() {
            TypeClassTest.monad(TryMonad.class)
                    .instance(monad)
                    .withKind(validKind)
                    .withMonadOperations(
                            validKind2,
                            validMapper,
                            validFlatMapper,
                            validFunctionKind,
                            validCombiningFunction)
                    .testOperations();
        }

        @Test
        @DisplayName("Verify Monad validations")
        void verifyMonadValidations() {
            TypeClassTest.monad(TryMonad.class)
                    .instance(monad)
                    .withKind(validKind)
                    .withMonadOperations(
                            validKind2,
                            validMapper,
                            validFlatMapper,
                            validFunctionKind,
                            validCombiningFunction)
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
            TypeClassTest.monad(TryMonad.class)
                    .instance(monad)
                    .withKind(validKind)
                    .withMonadOperations(
                            validKind2,
                            validMapper,
                            validFlatMapper,
                            validFunctionKind,
                            validCombiningFunction)
                    .selectTests()
                    .onlyOperations()
                    .test();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTest.monad(TryMonad.class)
                    .instance(monad)
                    .withKind(validKind)
                    .withMonadOperations(
                            validKind2,
                            validMapper,
                            validFlatMapper,
                            validFunctionKind,
                            validCombiningFunction)
                    .selectTests()
                    .onlyValidations()
                    .test();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.monad(TryMonad.class)
                    .instance(monad)
                    .withKind(validKind)
                    .withMonadOperations(
                            validKind2,
                            validMapper,
                            validFlatMapper,
                            validFunctionKind,
                            validCombiningFunction)
                    .selectTests()
                    .onlyExceptions()
                    .test();
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTest.monad(TryMonad.class)
                    .instance(monad)
                    .withKind(validKind)
                    .withMonadOperations(
                            validKind2,
                            validMapper,
                            validFlatMapper,
                            validFunctionKind,
                            validCombiningFunction)
                    .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
                    .selectTests()
                    .onlyLaws()
                    .test();
        }
    }

    // ============================================================================
    // Operation Tests - flatMap()
    // ============================================================================

    @Nested
    @DisplayName("flatMap() Operation Tests")
    class FlatMapOperationTests {

        @Test
        @DisplayName("flatMap() with Success and returning Success should succeed")
        void flatMap_withSuccessReturningSuccess_shouldSucceed() {
            Kind<TryKind.Witness, String> success =
                    TryKindHelper.TRY.widen(Try.success("hello"));
            Function<String, Kind<TryKind.Witness, Integer>> func =
                    s -> TryKindHelper.TRY.widen(Try.success(s.length()));

            Kind<TryKind.Witness, Integer> result = monad.flatMap(func, success);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(5))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("flatMap() with Success and returning Failure should return Failure")
        void flatMap_withSuccessReturningFailure_shouldReturnFailure() {
            Kind<TryKind.Witness, String> success =
                    TryKindHelper.TRY.widen(Try.success("hello"));
            RuntimeException exception = new RuntimeException("Inner failure");
            Function<String, Kind<TryKind.Witness, Integer>> func =
                    s -> TryKindHelper.TRY.widen(Try.failure(exception));

            Kind<TryKind.Witness, Integer> result = monad.flatMap(func, success);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }

        @Test
        @DisplayName("flatMap() with Failure should return Failure")
        void flatMap_withFailure_shouldReturnFailure() {
            RuntimeException exception = new RuntimeException("Original failure");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));
            Function<String, Kind<TryKind.Witness, Integer>> func =
                    s -> TryKindHelper.TRY.widen(Try.success(s.length()));

            Kind<TryKind.Witness, Integer> result = monad.flatMap(func, failure);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }

        @Test
        @DisplayName("flatMap() should capture exception thrown by function")
        void flatMap_shouldCaptureExceptionThrownByFunction() {
            Kind<TryKind.Witness, String> success =
                    TryKindHelper.TRY.widen(Try.success("test"));
            RuntimeException funcException = new RuntimeException("Function threw");
            Function<String, Kind<TryKind.Witness, Integer>> throwingFunc =
                    s -> {
                        throw funcException;
                    };

            Kind<TryKind.Witness, Integer> result = monad.flatMap(throwingFunc, success);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(funcException);
        }

        @Test
        @DisplayName("flatMap() should reject null result from function")
        void flatMap_shouldRejectNullResultFromFunction() {
            Kind<TryKind.Witness, String> success =
                    TryKindHelper.TRY.widen(Try.success("test"));
            Function<String, Kind<TryKind.Witness, Integer>> nullFunc = s -> null;

            Kind<TryKind.Witness, Integer> result = monad.flatMap(nullFunc, success);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get)
                    .hasMessageContaining("Function f returned null in TryMonad.flatMap");
        }
    }

    // ============================================================================
    // Operation Tests - raiseError()
    // ============================================================================

    @Nested
    @DisplayName("raiseError() Operation Tests")
    class RaiseErrorOperationTests {

        @Test
        @DisplayName("raiseError() should create Failure with RuntimeException")
        void raiseError_shouldCreateFailureWithRuntimeException() {
            RuntimeException exception = new RuntimeException("Test error");
            Kind<TryKind.Witness, String> result = monad.raiseError(exception);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }

        @Test
        @DisplayName("raiseError() should create Failure with checked exception")
        void raiseError_shouldCreateFailureWithCheckedException() {
            IOException exception = new IOException("IO error");
            Kind<TryKind.Witness, String> result = monad.raiseError(exception);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }

        @Test
        @DisplayName("raiseError() should create Failure with Error")
        void raiseError_shouldCreateFailureWithError() {
            StackOverflowError error = new StackOverflowError("Stack overflow");
            Kind<TryKind.Witness, String> result = monad.raiseError(error);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(error);
        }

        @Test
        @DisplayName("raiseError() should throw NPE for null Throwable")
        void raiseError_shouldThrowNPEForNullThrowable() {
            assertThatNullPointerException()
                    .isThrownBy(() -> monad.raiseError(null))
                    .withMessageContaining("Function error for TryMonad.raiseError cannot be null");
        }
    }

    // ============================================================================
    // Operation Tests - handleErrorWith()
    // ============================================================================

    @Nested
    @DisplayName("handleErrorWith() Operation Tests")
    class HandleErrorWithOperationTests {

        @Test
        @DisplayName("handleErrorWith() on Success should return original Success")
        void handleErrorWith_onSuccess_shouldReturnOriginalSuccess() {
            Kind<TryKind.Witness, String> success =
                    TryKindHelper.TRY.widen(Try.success("test"));
            Function<Throwable, Kind<TryKind.Witness, String>> handler =
                    t -> TryKindHelper.TRY.widen(Try.success("recovered"));

            Kind<TryKind.Witness, String> result = monad.handleErrorWith(success, handler);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo("test"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handleErrorWith() on Failure should apply handler")
        void handleErrorWith_onFailure_shouldApplyHandler() {
            RuntimeException exception = new RuntimeException("Original error");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));
            Function<Throwable, Kind<TryKind.Witness, String>> handler =
                    t -> TryKindHelper.TRY.widen(Try.success("recovered"));

            Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, handler);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo("recovered"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handleErrorWith() should capture exception from handler")
        void handleErrorWith_shouldCaptureExceptionFromHandler() {
            RuntimeException originalException = new RuntimeException("Original error");
            Kind<TryKind.Witness, String> failure =
                    TryKindHelper.TRY.widen(Try.failure(originalException));
            RuntimeException handlerException = new RuntimeException("Handler error");
            Function<Throwable, Kind<TryKind.Witness, String>> throwingHandler =
                    t -> {
                        throw handlerException;
                    };

            Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, throwingHandler);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(handlerException);
        }

        @Test
        @DisplayName("handleErrorWith() should reject null result from handler")
        void handleErrorWith_shouldRejectNullResultFromHandler() {
            RuntimeException exception = new RuntimeException("Original error");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));
            Function<Throwable, Kind<TryKind.Witness, String>> nullHandler = t -> null;

            Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, nullHandler);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get)
                    .hasMessageContaining("Function handler returned null in TryMonad.handleErrorWith");
        }
    }

    // ============================================================================
    // Validation Tests
    // ============================================================================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("flatMap() should throw NPE if function is null")
        void flatMap_shouldThrowNPEIfFunctionIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> monad.flatMap(null, validKind))
                    .withMessageContaining("Function f for TryMonad.flatMap cannot be null");
        }

        @Test
        @DisplayName("flatMap() should throw NPE if Kind is null")
        void flatMap_shouldThrowNPEIfKindIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> monad.flatMap(validFlatMapper, null))
                    .withMessageContaining("Kind for TryMonad.flatMap cannot be null");
        }

        @Test
        @DisplayName("handleErrorWith() should throw NPE if Kind is null")
        void handleErrorWith_shouldThrowNPEIfKindIsNull() {
            Function<Throwable, Kind<TryKind.Witness, String>> handler =
                    t -> TryKindHelper.TRY.widen(Try.success("recovered"));
            assertThatNullPointerException()
                    .isThrownBy(() -> monad.handleErrorWith(null, handler))
                    .withMessageContaining(
                            "Kind for TryMonad.handleErrorWith (source) cannot be null");
        }

        @Test
        @DisplayName("handleErrorWith() should throw NPE if handler is null")
        void handleErrorWith_shouldThrowNPEIfHandlerIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> monad.handleErrorWith(validKind, null))
                    .withMessageContaining("Function handler for TryMonad.handleErrorWith cannot be null");
        }
    }

    // ============================================================================
    // Exception Propagation Tests
    // ============================================================================

    @Nested
    @DisplayName("Exception Propagation Tests")
    class ExceptionPropagationTests {

        @Test
        @DisplayName("flatMap() should propagate RuntimeException from function")
        void flatMap_shouldPropagateRuntimeExceptionFromFunction() {
            RuntimeException testException = new RuntimeException("Test exception");
            Function<String, Kind<TryKind.Witness, Integer>> throwingFunc =
                    s -> {
                        throw testException;
                    };

            Kind<TryKind.Witness, Integer> result = monad.flatMap(throwingFunc, validKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(testException);
        }

        @Test
        @DisplayName("flatMap() should propagate Error from function")
        void flatMap_shouldPropagateErrorFromFunction() {
            StackOverflowError testError = new StackOverflowError("Stack overflow");
            Function<String, Kind<TryKind.Witness, Integer>> throwingFunc =
                    s -> {
                        throw testError;
                    };

            Kind<TryKind.Witness, Integer> result = monad.flatMap(throwingFunc, validKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(testError);
        }

        @Test
        @DisplayName("handleErrorWith() should propagate exception from handler")
        void handleErrorWith_shouldPropagateExceptionFromHandler() {
            RuntimeException originalException = new RuntimeException("Original");
            Kind<TryKind.Witness, String> failure =
                    TryKindHelper.TRY.widen(Try.failure(originalException));
            RuntimeException handlerException = new RuntimeException("Handler failed");
            Function<Throwable, Kind<TryKind.Witness, String>> throwingHandler =
                    t -> {
                        throw handlerException;
                    };

            Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, throwingHandler);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(handlerException);
        }
    }

    // ============================================================================
    // Monad Law Tests
    // ============================================================================

    @Nested
    @DisplayName("Monad Law Tests")
    class MonadLawTests {

        @Test
        @DisplayName("Left identity: flatMap(of(a), f) == f(a)")
        void leftIdentityLaw() {
            String testVal = "test";
            Function<String, Kind<TryKind.Witness, Integer>> func =
                    s -> TryKindHelper.TRY.widen(Try.success(s.length()));

            Kind<TryKind.Witness, String> ofValue = monad.of(testVal);
            Kind<TryKind.Witness, Integer> leftSide = monad.flatMap(func, ofValue);
            Kind<TryKind.Witness, Integer> rightSide = func.apply(testVal);

            assertThat(equalityChecker.test(leftSide, rightSide))
                    .as("Monad Left Identity Law: flatMap(of(a), f) == f(a)")
                    .isTrue();
        }

        @Test
        @DisplayName("Right identity: flatMap(m, of) == m")
        void rightIdentityLaw() {
            Function<String, Kind<TryKind.Witness, String>> ofFunc = monad::of;
            Kind<TryKind.Witness, String> leftSide = monad.flatMap(ofFunc, validKind);

            assertThat(equalityChecker.test(leftSide, validKind))
                    .as("Monad Right Identity Law: flatMap(m, of) == m")
                    .isTrue();
        }

        @Test
        @DisplayName("Associativity: flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))")
        void associativityLaw() {
            Function<String, Kind<TryKind.Witness, Integer>> f =
                    s -> TryKindHelper.TRY.widen(Try.success(s.length()));
            Function<Integer, Kind<TryKind.Witness, Integer>> g =
                    i -> TryKindHelper.TRY.widen(Try.success(i * 2));

            // Left side: flatMap(flatMap(m, f), g)
            Kind<TryKind.Witness, Integer> innerFlatMap = monad.flatMap(f, validKind);
            Kind<TryKind.Witness, Integer> leftSide = monad.flatMap(g, innerFlatMap);

            // Right side: flatMap(m, a -> flatMap(f(a), g))
            Function<String, Kind<TryKind.Witness, Integer>> rightSideFunc =
                    a -> monad.flatMap(g, f.apply(a));
            Kind<TryKind.Witness, Integer> rightSide = monad.flatMap(rightSideFunc, validKind);

            assertThat(equalityChecker.test(leftSide, rightSide))
                    .as(
                            "Monad Associativity Law: flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))")
                    .isTrue();
        }

        @Test
        @DisplayName("Laws hold for Failure instances")
        void lawsHoldForFailure() {
            RuntimeException exception = new RuntimeException("Test failure");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));

            // Right identity with Failure
            Function<String, Kind<TryKind.Witness, String>> ofFunc = monad::of;
            Kind<TryKind.Witness, String> result = monad.flatMap(ofFunc, failure);

            assertThat(equalityChecker.test(result, failure))
                    .as("Right identity holds for Failure")
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
        @DisplayName("flatMap() should handle null value in Success")
        void flatMap_shouldHandleNullValueInSuccess() {
            Kind<TryKind.Witness, String> successNull =
                    TryKindHelper.TRY.widen(Try.success(null));
            Function<String, Kind<TryKind.Witness, Integer>> safeFunc =
                    s -> TryKindHelper.TRY.widen(Try.success(s == null ? -1 : s.length()));

            Kind<TryKind.Witness, Integer> result = monad.flatMap(safeFunc, successNull);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(-1))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handleErrorWith() should handle different exception types")
        void handleErrorWith_shouldHandleDifferentExceptionTypes() {
            // Test with RuntimeException
            RuntimeException runtimeEx = new RuntimeException("Runtime error");
            Kind<TryKind.Witness, String> runtimeFailure =
                    TryKindHelper.TRY.widen(Try.failure(runtimeEx));
            Function<Throwable, Kind<TryKind.Witness, String>> handler =
                    t ->
                            TryKindHelper.TRY.widen(
                                    Try.success("Recovered from: " + t.getClass().getSimpleName()));

            Kind<TryKind.Witness, String> result1 = monad.handleErrorWith(runtimeFailure, handler);
            Try<String> tryResult1 = TryKindHelper.TRY.narrow(result1);
            assertThat(tryResult1.isSuccess()).isTrue();
            assertThatCode(
                    () -> assertThat(tryResult1.get()).isEqualTo("Recovered from: RuntimeException"))
                    .doesNotThrowAnyException();

            // Test with checked exception
            IOException ioEx = new IOException("IO error");
            Kind<TryKind.Witness, String> ioFailure = TryKindHelper.TRY.widen(Try.failure(ioEx));

            Kind<TryKind.Witness, String> result2 = monad.handleErrorWith(ioFailure, handler);
            Try<String> tryResult2 = TryKindHelper.TRY.narrow(result2);
            assertThat(tryResult2.isSuccess()).isTrue();
            assertThatCode(
                    () -> assertThat(tryResult2.get()).isEqualTo("Recovered from: IOException"))
                    .doesNotThrowAnyException();

            // Test with Error
            StackOverflowError error = new StackOverflowError("Stack overflow");
            Kind<TryKind.Witness, String> errorFailure = TryKindHelper.TRY.widen(Try.failure(error));

            Kind<TryKind.Witness, String> result3 = monad.handleErrorWith(errorFailure, handler);
            Try<String> tryResult3 = TryKindHelper.TRY.narrow(result3);
            assertThat(tryResult3.isSuccess()).isTrue();
            assertThatCode(
                    () -> assertThat(tryResult3.get()).isEqualTo("Recovered from: StackOverflowError"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Chained operations should preserve error information")
        void chainedOperations_shouldPreserveErrorInformation() {
            RuntimeException exception = new RuntimeException("Original error");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));

            Function<String, Kind<TryKind.Witness, Integer>> func1 =
                    s -> TryKindHelper.TRY.widen(Try.success(s.length()));
            Function<Integer, Kind<TryKind.Witness, String>> func2 =
                    i -> TryKindHelper.TRY.widen(Try.success(i.toString()));

            Kind<TryKind.Witness, Integer> result1 = monad.flatMap(func1, failure);
            Kind<TryKind.Witness, String> result2 = monad.flatMap(func2, result1);

            Try<String> tryResult = TryKindHelper.TRY.narrow(result2);
            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }

        @Test
        @DisplayName("of() should wrap null value correctly")
        void of_shouldWrapNullValueCorrectly() {
            Kind<TryKind.Witness, String> result = monad.of(null);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isNull())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handleErrorWith() can convert Failure to another Failure")
        void handleErrorWith_canConvertFailureToAnotherFailure() {
            RuntimeException originalException = new RuntimeException("Original");
            Kind<TryKind.Witness, String> failure =
                    TryKindHelper.TRY.widen(Try.failure(originalException));
            IOException newException = new IOException("Converted error");
            Function<Throwable, Kind<TryKind.Witness, String>> handler =
                    t -> TryKindHelper.TRY.widen(Try.failure(newException));

            Kind<TryKind.Witness, String> result = monad.handleErrorWith(failure, handler);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(newException);
        }
    }

    // ============================================================================
    // Integration Tests
    // ============================================================================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Complex chain of flatMap operations")
        void complexChainOfFlatMapOperations() {
            Function<String, Kind<TryKind.Witness, Integer>> step1 =
                    s -> TryKindHelper.TRY.widen(Try.success(s.length()));
            Function<Integer, Kind<TryKind.Witness, Integer>> step2 =
                    i -> TryKindHelper.TRY.widen(Try.success(i * 2));
            Function<Integer, Kind<TryKind.Witness, String>> step3 =
                    i -> TryKindHelper.TRY.widen(Try.success("Result: " + i));

            Kind<TryKind.Witness, Integer> result1 = monad.flatMap(step1, validKind);
            Kind<TryKind.Witness, Integer> result2 = monad.flatMap(step2, result1);
            Kind<TryKind.Witness, String> result3 = monad.flatMap(step3, result2);

            Try<String> tryResult = TryKindHelper.TRY.narrow(result3);
            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).startsWith("Result:"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Combining flatMap and handleErrorWith")
        void combiningFlatMapAndHandleErrorWith() {
            Function<String, Kind<TryKind.Witness, Integer>> riskyOperation =
                    s -> {
                        if (s.isEmpty()) {
                            return TryKindHelper.TRY.widen(
                                    Try.failure(new IllegalArgumentException("Empty string")));
                        }
                        return TryKindHelper.TRY.widen(Try.success(s.length()));
                    };

            Function<Throwable, Kind<TryKind.Witness, Integer>> errorHandler =
                    t -> TryKindHelper.TRY.widen(Try.success(-1));

            // Test with valid input
            Kind<TryKind.Witness, String> validInput =
                    TryKindHelper.TRY.widen(Try.success("test"));
            Kind<TryKind.Witness, Integer> result1 = monad.flatMap(riskyOperation, validInput);
            Kind<TryKind.Witness, Integer> handled1 = monad.handleErrorWith(result1, errorHandler);

            Try<Integer> tryResult1 = TryKindHelper.TRY.narrow(handled1);
            assertThat(tryResult1.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult1.get()).isEqualTo(4))
                    .doesNotThrowAnyException();

            // Test with empty input
            Kind<TryKind.Witness, String> emptyInput = TryKindHelper.TRY.widen(Try.success(""));
            Kind<TryKind.Witness, Integer> result2 = monad.flatMap(riskyOperation, emptyInput);
            Kind<TryKind.Witness, Integer> handled2 = monad.handleErrorWith(result2, errorHandler);

            Try<Integer> tryResult2 = TryKindHelper.TRY.narrow(handled2);
            assertThat(tryResult2.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult2.get()).isEqualTo(-1))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MonadError operations preserve type class behaviour")
        void monadErrorOperations_preserveTypeClassBehaviour() {
            // Verify that monad operations work correctly
            Kind<TryKind.Witness, Integer> mapped = monad.map(validMapper, validKind);
            assertThat(TryKindHelper.TRY.narrow(mapped).isSuccess()).isTrue();

            Kind<TryKind.Witness, Integer> flatMapped = monad.flatMap(validFlatMapper, validKind);
            assertThat(TryKindHelper.TRY.narrow(flatMapped).isSuccess()).isTrue();

            Kind<TryKind.Witness, Integer> apped = monad.ap(validFunctionKind, validKind);
            assertThat(TryKindHelper.TRY.narrow(apped).isSuccess()).isTrue();

            // Verify error handling
            RuntimeException exception = new RuntimeException("Error");
            Kind<TryKind.Witness, String> failure = monad.raiseError(exception);
            assertThat(TryKindHelper.TRY.narrow(failure).isFailure()).isTrue();

            Function<Throwable, Kind<TryKind.Witness, String>> handler =
                    t -> monad.of("recovered");
            Kind<TryKind.Witness, String> recovered = monad.handleErrorWith(failure, handler);
            assertThat(TryKindHelper.TRY.narrow(recovered).isSuccess()).isTrue();
        }
    }
}