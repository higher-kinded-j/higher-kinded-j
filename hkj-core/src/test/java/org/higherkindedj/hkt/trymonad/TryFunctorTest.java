// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryFunctor Complete Test Suite")
class TryFunctorTest extends TypeClassTestBase<TryKind.Witness, String, Integer> {

    private TryFunctor functor;

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

    @BeforeEach
    void setUpFunctor() {
        functor = new TryFunctor();
    }

    // ============================================================================
    // Complete Test Suite Using Framework
    // ============================================================================

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete Functor test pattern")
        void runCompleteFunctorPattern() {
            TypeClassTest.functor(TryFunctor.class)
                    .instance(functor)
                    .withKind(validKind)
                    .withMapper(validMapper)
                    .withSecondMapper(secondMapper)
                    .withEqualityChecker(equalityChecker)
                    .testAll();
        }

        @Test
        @DisplayName("Verify Functor operations")
        void verifyFunctorOperations() {
            TypeClassTest.functor(TryFunctor.class)
                    .instance(functor)
                    .withKind(validKind)
                    .withMapper(validMapper)
                    .testOperations();
        }

        @Test
        @DisplayName("Verify Functor validations")
        void verifyFunctorValidations() {
            TypeClassTest.functor(TryFunctor.class)
                    .instance(functor)
                    .withKind(validKind)
                    .withMapper(validMapper)
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
            TypeClassTest.functor(TryFunctor.class)
                    .instance(functor)
                    .withKind(validKind)
                    .withMapper(validMapper)
                    .selectTests()
                    .onlyOperations()
                    .test();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTest.functor(TryFunctor.class)
                    .instance(functor)
                    .withKind(validKind)
                    .withMapper(validMapper)
                    .selectTests()
                    .onlyValidations()
                    .test();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.functor(TryFunctor.class)
                    .instance(functor)
                    .withKind(validKind)
                    .withMapper(validMapper)
                    .selectTests()
                    .onlyExceptions()
                    .test();
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTest.functor(TryFunctor.class)
                    .instance(functor)
                    .withKind(validKind)
                    .withMapper(validMapper)
                    .withSecondMapper(secondMapper)
                    .withEqualityChecker(equalityChecker)
                    .selectTests()
                    .onlyLaws()
                    .test();
        }
    }

    // ============================================================================
    // Operation Tests
    // ============================================================================

    @Nested
    @DisplayName("Operation Tests")
    class OperationTests {

        @Test
        @DisplayName("map() on Success should transform value")
        void map_onSuccess_shouldTransformValue() {
            Kind<TryKind.Witness, String> success = TryKindHelper.TRY.widen(Try.success("hello"));
            Kind<TryKind.Witness, Integer> result = functor.map(String::length, success);

            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);
            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(5))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("map() on Failure should preserve Failure")
        void map_onFailure_shouldPreserveFailure() {
            RuntimeException exception = new RuntimeException("Test failure");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));
            Kind<TryKind.Witness, Integer> result = functor.map(String::length, failure);

            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);
            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }

        @Test
        @DisplayName("map() should return Success with null if mapper returns null")
        void map_shouldReturnSuccessWithNullIfMapperReturnsNull() {
            Kind<TryKind.Witness, String> success = TryKindHelper.TRY.widen(Try.success("test"));
            Function<String, Integer> nullMapper = s -> null;
            Kind<TryKind.Witness, Integer> result = functor.map(nullMapper, success);

            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);
            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isNull())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("map() should capture exception thrown by mapper")
        void map_shouldCaptureExceptionThrownByMapper() {
            Kind<TryKind.Witness, String> success = TryKindHelper.TRY.widen(Try.success("test"));
            RuntimeException mapperException = new RuntimeException("Mapper failed");
            Function<String, Integer> throwingMapper =
                    s -> {
                        throw mapperException;
                    };

            Kind<TryKind.Witness, Integer> result = functor.map(throwingMapper, success);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(mapperException);
        }
    }

    // ============================================================================
    // Validation Tests
    // ============================================================================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("map() should throw NPE if mapper is null")
        void map_shouldThrowNPEIfMapperIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> functor.map(null, validKind))
                    .withMessageContaining("function f for TryFunctor.map cannot be null");
        }

        @Test
        @DisplayName("map() should throw NPE if Kind is null")
        void map_shouldThrowNPEIfKindIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> functor.map(validMapper, null))
                    .withMessageContaining("Kind for TryFunctor.map cannot be null");
        }
    }

    // ============================================================================
    // Exception Propagation Tests
    // ============================================================================

    @Nested
    @DisplayName("Exception Propagation Tests")
    class ExceptionPropagationTests {

        @Test
        @DisplayName("map() should propagate RuntimeException from mapper")
        void map_shouldPropagateRuntimeExceptionFromMapper() {
            RuntimeException testException = new RuntimeException("Test exception");
            Function<String, Integer> throwingMapper =
                    s -> {
                        throw testException;
                    };

            Kind<TryKind.Witness, Integer> result = functor.map(throwingMapper, validKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(testException);
        }

        @Test
        @DisplayName("map() should propagate Error from mapper")
        void map_shouldPropagateErrorFromMapper() {
            StackOverflowError testError = new StackOverflowError("Stack overflow");
            Function<String, Integer> throwingMapper =
                    s -> {
                        throw testError;
                    };

            Kind<TryKind.Witness, Integer> result = functor.map(throwingMapper, validKind);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(testError);
        }

        @Test
        @DisplayName("map() on Failure should not invoke mapper")
        void map_onFailure_shouldNotInvokeMapper() {
            RuntimeException exception = new RuntimeException("Original failure");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));

            RuntimeException mapperException = new RuntimeException("Mapper should not be called");
            Function<String, Integer> throwingMapper =
                    s -> {
                        throw mapperException;
                    };

            Kind<TryKind.Witness, Integer> result = functor.map(throwingMapper, failure);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }
    }

    // ============================================================================
    // Law Tests
    // ============================================================================

    @Nested
    @DisplayName("Functor Law Tests")
    class FunctorLawTests {

        @Test
        @DisplayName("Identity law: map(id) == id")
        void identityLaw() {
            Function<String, String> identity = s -> s;
            Kind<TryKind.Witness, String> mapped = functor.map(identity, validKind);

            assertThat(equalityChecker.test(mapped, validKind))
                    .as("Functor Identity Law: map(id, fa) == fa")
                    .isTrue();
        }

        @Test
        @DisplayName("Composition law: map(g . f) == map(g) . map(f)")
        void compositionLaw() {
            Function<String, Integer> f = String::length;
            Function<Integer, String> g = Object::toString;

            // Left side: map(g . f)
            Function<String, String> composed = s -> g.apply(f.apply(s));
            Kind<TryKind.Witness, String> leftSide = functor.map(composed, validKind);

            // Right side: map(g, map(f, fa))
            Kind<TryKind.Witness, Integer> intermediate = functor.map(f, validKind);
            Kind<TryKind.Witness, String> rightSide = functor.map(g, intermediate);

            assertThat(equalityChecker.test(leftSide, rightSide))
                    .as("Functor Composition Law: map(g ∘ f, fa) == map(g, map(f, fa))")
                    .isTrue();
        }

        @Test
        @DisplayName("Identity law holds for Failure")
        void identityLaw_onFailure() {
            RuntimeException exception = new RuntimeException("Test failure");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));

            Function<String, String> identity = s -> s;
            Kind<TryKind.Witness, String> mapped = functor.map(identity, failure);

            assertThat(equalityChecker.test(mapped, failure))
                    .as("Identity law holds for Failure")
                    .isTrue();
        }

        @Test
        @DisplayName("Composition law holds for Failure")
        void compositionLaw_onFailure() {
            RuntimeException exception = new RuntimeException("Test failure");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));

            Function<String, Integer> f = String::length;
            Function<Integer, String> g = Object::toString;

            Function<String, String> composed = s -> g.apply(f.apply(s));
            Kind<TryKind.Witness, String> leftSide = functor.map(composed, failure);

            Kind<TryKind.Witness, Integer> intermediate = functor.map(f, failure);
            Kind<TryKind.Witness, String> rightSide = functor.map(g, intermediate);

            assertThat(equalityChecker.test(leftSide, rightSide))
                    .as("Composition law holds for Failure")
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
        @DisplayName("map() should handle Success with null value")
        void map_shouldHandleSuccessWithNullValue() {
            Kind<TryKind.Witness, String> successNull =
                    TryKindHelper.TRY.widen(Try.success(null));
            Function<String, Integer> safeMapper = s -> s == null ? -1 : s.length();

            Kind<TryKind.Witness, Integer> result = functor.map(safeMapper, successNull);
            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(-1))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("map() should handle mapper that accepts null")
        void map_shouldHandleMapperThatAcceptsNull() {
            Kind<TryKind.Witness, String> successNull =
                    TryKindHelper.TRY.widen(Try.success(null));
            Function<String, String> nullSafeMapper = s -> "Length: " + (s == null ? 0 : s.length());

            Kind<TryKind.Witness, String> result = functor.map(nullSafeMapper, successNull);
            Try<String> tryResult = TryKindHelper.TRY.narrow(result);

            assertThat(tryResult.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(tryResult.get()).isEqualTo("Length: 0"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("map() should preserve Failure type through multiple mappings")
        void map_shouldPreserveFailureTypeThroughMultipleMappings() {
            RuntimeException exception = new RuntimeException("Original failure");
            Kind<TryKind.Witness, String> failure = TryKindHelper.TRY.widen(Try.failure(exception));

            Kind<TryKind.Witness, Integer> result1 = functor.map(String::length, failure);
            Kind<TryKind.Witness, String> result2 = functor.map(Object::toString, result1);
            Kind<TryKind.Witness, Integer> result3 = functor.map(String::length, result2);

            Try<Integer> tryResult = TryKindHelper.TRY.narrow(result3);
            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(exception);
        }
    }
}