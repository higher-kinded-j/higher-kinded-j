// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;

import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;

import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryFoldable Complete Test Suite")
class TryFoldableTest extends TypeClassTestBase<TryKind.Witness, Integer, String> {

    private static final Monoid<String> STRING_MONOID = Monoids.string();
    private static final Monoid<Integer> SUM_MONOID = Monoids.integerAddition();

    private Foldable<TryKind.Witness> foldable;

    @BeforeEach
    void setUpFoldable() {
        foldable = TryTraverse.INSTANCE;
    }

    @Override
    protected Kind<TryKind.Witness, Integer> createValidKind() {
        return TRY.widen(Try.success(42));
    }

    @Override
    protected Kind<TryKind.Witness, Integer> createValidKind2() {
        return TRY.widen(Try.success(24));
    }

    @Override
    protected Function<Integer, String> createValidMapper() {
        return Object::toString;
    }

    @Override
    protected java.util.function.BiPredicate<
            Kind<TryKind.Witness, ?>, Kind<TryKind.Witness, ?>>
    createEqualityChecker() {
        return (k1, k2) -> {
            Try<?> t1 = TRY.narrow(k1);
            Try<?> t2 = TRY.narrow(k2);
            return t1.equals(t2);
        };
    }

    @Nested
    @DisplayName("Complete Foldable Test Suite")
    class CompleteFoldableTestSuite {

        @Test
        @DisplayName("Run complete Foldable test pattern")
        void runCompleteFoldableTestPattern() {
            TypeClassTest.<TryKind.Witness>foldable(TryTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(validKind)
                    .withOperations(STRING_MONOID, validMapper)
                    .testAll();
        }
    }

    @Nested
    @DisplayName("Foldable Operation Tests")
    class FoldableOperationTests {

        @Test
        @DisplayName("foldMap on Success should apply function and return result")
        void foldMapOnSuccessShouldApplyFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));

            String result = foldable.foldMap(STRING_MONOID, Object::toString, successKind);

            assertThat(result).isEqualTo("42");
        }

        @Test
        @DisplayName("foldMap on Failure should return monoid identity")
        void foldMapOnFailureShouldReturnIdentity() {
            Kind<TryKind.Witness, Integer> failureKind =
                    TRY.widen(Try.failure(new RuntimeException("Test failure")));

            String result = foldable.foldMap(STRING_MONOID, Object::toString, failureKind);

            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("foldMap with null value in Success should work correctly")
        void foldMapWithNullValueShouldWork() {
            Kind<TryKind.Witness, Integer> successNullKind = TRY.widen(Try.success(null));

            String result = foldable.foldMap(STRING_MONOID, i -> "null", successNullKind);

            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("foldMap with custom monoid should use monoid operations")
        void foldMapWithCustomMonoidShouldUseMonoidOperations() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(5));

            Integer result = foldable.foldMap(SUM_MONOID, i -> i * 2, successKind);

            assertThat(result).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Individual Foldable Components")
    class IndividualFoldableComponents {

        @Test
        @DisplayName("Test foldMap operations only")
        void testOperationsOnly() {
            TypeClassTest.<TryKind.Witness>foldable(TryTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(validKind)
                    .withOperations(STRING_MONOID, validMapper)
                    .testOperations();
        }

        @Test
        @DisplayName("Test foldMap validations only")
        void testValidationsOnly() {
            TypeClassTest.<TryKind.Witness>foldable(TryTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(validKind)
                    .withOperations(STRING_MONOID, validMapper)
                    .testValidations();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.<TryKind.Witness>foldable(TryTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(validKind)
                    .withOperations(STRING_MONOID, validMapper)
                    .testExceptions();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("foldMap preserves Success/Failure distinction")
        void foldMapPreservesDistinction() {
            Kind<TryKind.Witness, Integer> success = TRY.widen(Try.success(42));
            Kind<TryKind.Witness, Integer> failure =
                    TRY.widen(Try.failure(new RuntimeException("Error")));

            String successResult = foldable.foldMap(STRING_MONOID, Object::toString, success);
            String failureResult = foldable.foldMap(STRING_MONOID, Object::toString, failure);

            assertThat(successResult).isNotEqualTo(failureResult);
            assertThat(successResult).isEqualTo("42");
            assertThat(failureResult).isEqualTo("");
        }

        @Test
        @DisplayName("foldMap with function returning empty should work")
        void foldMapWithEmptyReturningFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));

            String result = foldable.foldMap(STRING_MONOID, i -> "", successKind);

            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("Multiple foldMap operations should be independent")
        void multipleFoldMapOperationsAreIndependent() {
            Kind<TryKind.Witness, Integer> kind = TRY.widen(Try.success(42));

            String result1 = foldable.foldMap(STRING_MONOID, Object::toString, kind);
            String result2 = foldable.foldMap(STRING_MONOID, i -> "different", kind);

            assertThat(result1).isEqualTo("42");
            assertThat(result2).isEqualTo("different");
        }
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("foldMap should propagate function exceptions on Success")
        void foldMapShouldPropagateExceptionsFromFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            RuntimeException testException = new RuntimeException("Test exception in foldMap");

            Function<Integer, String> throwingFunction =
                    i -> {
                        throw testException;
                    };

            assertThatThrownBy(() -> foldable.foldMap(STRING_MONOID, throwingFunction, successKind))
                    .isSameAs(testException);
        }

        @Test
        @DisplayName("foldMap should not call function on Failure")
        void foldMapShouldNotCallFunctionOnFailure() {
            Kind<TryKind.Witness, Integer> failureKind =
                    TRY.widen(Try.failure(new RuntimeException("Original failure")));
            RuntimeException testException = new RuntimeException("Function should not be called");

            Function<Integer, String> throwingFunction =
                    i -> {
                        throw testException;
                    };

            // Should not throw because function should not be called
            assertThatCode(() -> foldable.foldMap(STRING_MONOID, throwingFunction, failureKind))
                    .doesNotThrowAnyException();

            String result = foldable.foldMap(STRING_MONOID, throwingFunction, failureKind);
            assertThat(result).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Monoid Behaviour Tests")
    class MonoidBehaviourTests {

        @Test
        @DisplayName("foldMap with identity should behave correctly")
        void foldMapWithIdentity() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            Kind<TryKind.Witness, Integer> failureKind =
                    TRY.widen(Try.failure(new RuntimeException("Error")));

            String successResult = foldable.foldMap(STRING_MONOID, i -> STRING_MONOID.empty(), successKind);
            String failureResult = foldable.foldMap(STRING_MONOID, i -> STRING_MONOID.empty(), failureKind);

            assertThat(successResult).isEqualTo(STRING_MONOID.empty());
            assertThat(failureResult).isEqualTo(STRING_MONOID.empty());
        }

        @Test
        @DisplayName("foldMap respects monoid combine operation")
        void foldMapRespectsMonoidCombine() {
            // For Success, result should be the mapped value
            // For Failure, result should be identity
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(5));

            // Using SUM_MONOID: empty() = 0, combine(a,b) = a+b
            Integer result = foldable.foldMap(SUM_MONOID, i -> i * 3, successKind);

            assertThat(result).isEqualTo(15);
        }
    }
}