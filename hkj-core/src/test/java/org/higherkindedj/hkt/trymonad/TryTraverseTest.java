// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryTraverse Complete Test Suite")
class TryTraverseTest extends TypeClassTestBase<TryKind.Witness, Integer, String> {

    private static final MaybeKindHelper MAYBE = MaybeKindHelper.MAYBE;

    private static final Monoid<String> STRING_MONOID = Monoids.string();
    private static final Monoid<Integer> SUM_MONOID = Monoids.integerAddition();

    private TryTraverse traverse;
    private Applicative<MaybeKind.Witness> maybeApplicative;

    @BeforeEach
    void setUpTraverse() {
        traverse = TryTraverse.INSTANCE;
        maybeApplicative = MaybeMonad.INSTANCE;
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
    @DisplayName("Complete Traverse Test Suite")
    class CompleteTraverseTestSuite {


        @Test
        @DisplayName("Run complete Traverse test pattern")
        void runCompleteTraverseTestPattern() {
            Function<Integer, Kind<MaybeKind.Witness, String>> traverseFunc =
                    i -> MAYBE.widen(Maybe.just(i.toString()));

            // Note: We test operations and validations separately because Try's map
            // catches exceptions (converting them to Failure) rather than propagating them,
            // which differs from the standard Traverse exception propagation tests
            TypeClassTest.<TryKind.Witness>traverse(TryTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(validKind)
                    .withOperations(validMapper)
                    .withApplicative(maybeApplicative, traverseFunc)
                    .withFoldableOperations(STRING_MONOID, validMapper)
                    .testOperations();

            TypeClassTest.<TryKind.Witness>traverse(TryTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(validKind)
                    .withOperations(validMapper)
                    .withApplicative(maybeApplicative, traverseFunc)
                    .withFoldableOperations(STRING_MONOID, validMapper)
                    .testValidations();

            // Exception tests are handled separately in ExceptionHandlingTests
            // because Try has special exception handling semantics
        }
    }

    @Nested
    @DisplayName("Traverse Operation Tests")
    class TraverseOperationTests {

        @Test
        @DisplayName("traverse on Success should apply function and wrap result")
        void traverseOnSuccessShouldApplyFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            Function<Integer, Kind<MaybeKind.Witness, String>> func =
                    i -> MAYBE.widen(Maybe.just(i.toString()));

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
                    traverse.traverse(maybeApplicative, func, successKind);

            Maybe<Kind<TryKind.Witness, String>> narrowedResult = MAYBE.narrow(result);
            assertThat(narrowedResult.isJust()).isTrue();

            Try<String> innerTry = TRY.narrow(narrowedResult.get());
            assertThat(innerTry.isSuccess()).isTrue();
            assertThat(innerTry.orElse(null)).isEqualTo("42");
        }

        @Test
        @DisplayName("traverse on Failure should wrap Failure in applicative")
        void traverseOnFailureShouldWrapFailure() {
            RuntimeException originalException = new RuntimeException("Test failure");
            Kind<TryKind.Witness, Integer> failureKind = TRY.widen(Try.failure(originalException));
            Function<Integer, Kind<MaybeKind.Witness, String>> func =
                    i -> MAYBE.widen(Maybe.just(i.toString()));

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
                    traverse.traverse(maybeApplicative, func, failureKind);

            Maybe<Kind<TryKind.Witness, String>> narrowedResult = MAYBE.narrow(result);
            assertThat(narrowedResult.isJust()).isTrue();

            Try<String> innerTry = TRY.narrow(narrowedResult.get());
            assertThat(innerTry.isFailure()).isTrue();
        }

        @Test
        @DisplayName("traverse with null value in Success should work correctly")
        void traverseWithNullValueShouldWork() {
            Kind<TryKind.Witness, Integer> successNullKind = TRY.widen(Try.success(null));
            Function<Integer, Kind<MaybeKind.Witness, String>> func =
                    i -> MAYBE.widen(Maybe.just("null"));

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
                    traverse.traverse(maybeApplicative, func, successNullKind);

            Maybe<Kind<TryKind.Witness, String>> narrowedResult = MAYBE.narrow(result);
            assertThat(narrowedResult.isJust()).isTrue();

            Try<String> innerTry = TRY.narrow(narrowedResult.get());
            assertThat(innerTry.isSuccess()).isTrue();
            assertThat(innerTry.orElse(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("traverse should preserve structure")
        void traverseShouldPreserveStructure() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            Function<Integer, Kind<MaybeKind.Witness, String>> func =
                    i -> MAYBE.widen(Maybe.just(i.toString()));

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
                    traverse.traverse(maybeApplicative, func, successKind);

            assertThat(result).isNotNull();
            Maybe<Kind<TryKind.Witness, String>> narrowedResult = MAYBE.narrow(result);
            assertThat(narrowedResult.isJust()).isTrue();
        }
    }

    @Nested
    @DisplayName("Functor Operations Tests (Inherited)")
    class FunctorOperationsTests {

        @Test
        @DisplayName("map on Success should apply function")
        void mapOnSuccessShouldApplyFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));

            Kind<TryKind.Witness, String> result = traverse.map(Object::toString, successKind);

            Try<String> narrowedResult = TRY.narrow(result);
            assertThat(narrowedResult.isSuccess()).isTrue();
            assertThat(narrowedResult.orElse(null)).isEqualTo("42");
        }

        @Test
        @DisplayName("map on Failure should preserve Failure")
        void mapOnFailureShouldPreserveFailure() {
            RuntimeException originalException = new RuntimeException("Test failure");
            Kind<TryKind.Witness, Integer> failureKind = TRY.widen(Try.failure(originalException));

            Kind<TryKind.Witness, String> result = traverse.map(Object::toString, failureKind);

            Try<String> narrowedResult = TRY.narrow(result);
            assertThat(narrowedResult.isFailure()).isTrue();
        }
    }

    @Nested
    @DisplayName("Foldable Operations Tests (Inherited)")
    class FoldableOperationsTests {

        @Test
        @DisplayName("foldMap on Success should apply function")
        void foldMapOnSuccessShouldApplyFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));

            String result = traverse.foldMap(STRING_MONOID, Object::toString, successKind);

            assertThat(result).isEqualTo("42");
        }

        @Test
        @DisplayName("foldMap on Failure should return identity")
        void foldMapOnFailureShouldReturnIdentity() {
            Kind<TryKind.Witness, Integer> failureKind =
                    TRY.widen(Try.failure(new RuntimeException("Test failure")));

            String result = traverse.foldMap(STRING_MONOID, Object::toString, failureKind);

            assertThat(result).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Individual Traverse Components")
    class IndividualTraverseComponents {

        @Test
        @DisplayName("Test traverse operations only")
        void testOperationsOnly() {
            Function<Integer, Kind<MaybeKind.Witness, String>> traverseFunc =
                    i -> MAYBE.widen(Maybe.just(i.toString()));
            TypeClassTest.<TryKind.Witness>traverse(TryTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(validKind)
                    .withOperations(validMapper)
                    .withApplicative(maybeApplicative, traverseFunc)
                    .withFoldableOperations(STRING_MONOID, validMapper)
                    .testOperations();
        }

        @Test
        @DisplayName("Test traverse validations only")
        void testValidationsOnly() {
            Function<Integer, Kind<MaybeKind.Witness, String>> traverseFunc =
                    i -> MAYBE.widen(Maybe.just(i.toString()));

            TypeClassTest.<TryKind.Witness>traverse(TryTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(validKind)
                    .withOperations(validMapper)
                    .withApplicative(maybeApplicative, traverseFunc)
                    .withFoldableOperations(STRING_MONOID, validMapper)
                    .testValidations();
        }


        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            // Note: Try has special exception handling semantics where map() catches
            // exceptions and wraps them in Failure rather than propagating them.
            // Therefore, we cannot use the standard testExceptions() pattern.
            // Exception handling is thoroughly tested in the ExceptionHandlingTests nested class instead.

            // We verify here that traverse and foldMap do propagate exceptions correctly:
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            RuntimeException testException = new RuntimeException("Test exception");

            // traverse should propagate exceptions
            Function<Integer, Kind<MaybeKind.Witness, String>> throwingTraverseFunc =
                    i -> {
                        throw testException;
                    };
            assertThatThrownBy(() -> traverse.traverse(maybeApplicative, throwingTraverseFunc, successKind))
                    .isSameAs(testException);

            // foldMap should propagate exceptions
            Function<Integer, String> throwingFoldMapFunc =
                    i -> {
                        throw testException;
                    };
            assertThatThrownBy(() -> traverse.foldMap(STRING_MONOID, throwingFoldMapFunc, successKind))
                    .isSameAs(testException);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("traverse preserves Success/Failure distinction")
        void traversePreservesDistinction() {
            Kind<TryKind.Witness, Integer> success = TRY.widen(Try.success(42));
            Kind<TryKind.Witness, Integer> failure =
                    TRY.widen(Try.failure(new RuntimeException("Error")));
            Function<Integer, Kind<MaybeKind.Witness, String>> func =
                    i -> MAYBE.widen(Maybe.just(i.toString()));

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> successResult =
                    traverse.traverse(maybeApplicative, func, success);
            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> failureResult =
                    traverse.traverse(maybeApplicative, func, failure);

            Try<String> successTry = TRY.narrow(MAYBE.narrow(successResult).get());
            Try<String> failureTry = TRY.narrow(MAYBE.narrow(failureResult).get());

            assertThat(successTry.isSuccess()).isTrue();
            assertThat(failureTry.isFailure()).isTrue();
        }

        @Test
        @DisplayName("traverse with function returning Nothing should preserve structure")
        void traverseWithNothingReturningFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            Function<Integer, Kind<MaybeKind.Witness, String>> func =
                    i -> MAYBE.widen(Maybe.nothing());

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
                    traverse.traverse(maybeApplicative, func, successKind);

            Maybe<Kind<TryKind.Witness, String>> narrowedResult = MAYBE.narrow(result);
            assertThat(narrowedResult.isNothing()).isTrue();
        }

        @Test
        @DisplayName("Multiple traverse operations should be independent")
        void multipleTraverseOperationsAreIndependent() {
            Kind<TryKind.Witness, Integer> kind = TRY.widen(Try.success(42));
            Function<Integer, Kind<MaybeKind.Witness, String>> func1 =
                    i -> MAYBE.widen(Maybe.just(i.toString()));
            Function<Integer, Kind<MaybeKind.Witness, String>> func2 =
                    i -> MAYBE.widen(Maybe.just("different"));

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result1 =
                    traverse.traverse(maybeApplicative, func1, kind);
            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result2 =
                    traverse.traverse(maybeApplicative, func2, kind);

            Try<String> try1 = TRY.narrow(MAYBE.narrow(result1).get());
            Try<String> try2 = TRY.narrow(MAYBE.narrow(result2).get());

            assertThat(try1.orElse(null)).isEqualTo("42");
            assertThat(try2.orElse(null)).isEqualTo("different");
        }
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("traverse should propagate function exceptions on Success")
        void traverseShouldPropagateExceptionsFromFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            RuntimeException testException = new RuntimeException("Test exception in traverse");

            Function<Integer, Kind<MaybeKind.Witness, String>> throwingFunction =
                    i -> {
                        throw testException;
                    };

            assertThatThrownBy(() -> traverse.traverse(maybeApplicative, throwingFunction, successKind))
                    .isSameAs(testException);
        }

        @Test
        @DisplayName("traverse should not call function on Failure")
        void traverseShouldNotCallFunctionOnFailure() {
            Kind<TryKind.Witness, Integer> failureKind =
                    TRY.widen(Try.failure(new RuntimeException("Original failure")));
            RuntimeException testException = new RuntimeException("Function should not be called");

            Function<Integer, Kind<MaybeKind.Witness, String>> throwingFunction =
                    i -> {
                        throw testException;
                    };

            // Should not throw because function should not be called
            assertThatCode(
                    () -> traverse.traverse(maybeApplicative, throwingFunction, failureKind))
                    .doesNotThrowAnyException();

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
                    traverse.traverse(maybeApplicative, throwingFunction, failureKind);

            Maybe<Kind<TryKind.Witness, String>> narrowedResult = MAYBE.narrow(result);
            assertThat(narrowedResult.isJust()).isTrue();

            Try<String> innerTry = TRY.narrow(narrowedResult.get());
            assertThat(innerTry.isFailure()).isTrue();
        }

        @Test
        @DisplayName("map should capture function exceptions in Failure on Success")
        void mapShouldCaptureExceptionsFromFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            RuntimeException testException = new RuntimeException("Test exception in map");

            Function<Integer, String> throwingFunction =
                    i -> {
                        throw testException;
                    };

            Kind<TryKind.Witness, String> result = traverse.map(throwingFunction, successKind);
            Try<String> narrowedResult = TRY.narrow(result);

            assertThat(narrowedResult.isFailure()).isTrue();
            assertThatThrownBy(() -> narrowedResult.get())
                    .isSameAs(testException);
        }

        @Test
        @DisplayName("foldMap should capture function exceptions in identity on Success")
        void foldMapShouldCaptureExceptionsFromFunction() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            RuntimeException testException = new RuntimeException("Test exception in foldMap");

            Function<Integer, String> throwingFunction =
                    i -> {
                        throw testException;
                    };

            // foldMap will propagate the exception since it's not caught by Try
            assertThatThrownBy(() -> traverse.foldMap(STRING_MONOID, throwingFunction, successKind))
                    .isSameAs(testException);
        }
    }


    @Nested
    @DisplayName("Applicative Effect Tests")
    class ApplicativeEffectTests {

        @Test
        @DisplayName("traverse respects applicative structure")
        void traverseRespectsApplicativeStructure() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            Function<Integer, Kind<MaybeKind.Witness, String>> func =
                    i -> MAYBE.widen(Maybe.just(i.toString()));

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
                    traverse.traverse(maybeApplicative, func, successKind);

            // Result should be wrapped in Maybe applicative context
            assertThat(result).isNotNull();
            Maybe<Kind<TryKind.Witness, String>> narrowedResult = MAYBE.narrow(result);
            assertThat(narrowedResult).isNotNull();
            assertThat(narrowedResult.isJust()).isTrue();
        }

        @Test
        @DisplayName("traverse with failing applicative function should preserve failure")
        void traverseWithFailingApplicativeFunctionShouldPreserveFailure() {
            Kind<TryKind.Witness, Integer> successKind = TRY.widen(Try.success(42));
            Function<Integer, Kind<MaybeKind.Witness, String>> func =
                    i -> MAYBE.widen(Maybe.nothing());

            Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
                    traverse.traverse(maybeApplicative, func, successKind);

            Maybe<Kind<TryKind.Witness, String>> narrowedResult = MAYBE.narrow(result);
            // When the applicative function returns Nothing, the whole result is Nothing
            assertThat(narrowedResult.isNothing()).isTrue();
        }
    }
}