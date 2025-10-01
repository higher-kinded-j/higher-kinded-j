// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeFunctor Complete Test Suite")
class MaybeFunctorTest {
    private MaybeFunctor functor;
    private Functor<MaybeKind.Witness> functorTyped;
    private Kind<MaybeKind.Witness, Integer> validKind;
    private Kind<MaybeKind.Witness, Integer> validKind2;
    private Function<Integer, String> validMapper;
    private Function<String, String> secondMapper;
    private BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> equalityChecker;

    @BeforeEach
    void setUpFunctor() {
        functor = new MaybeFunctor();
        functorTyped = functor;
        validKind = MAYBE.widen(Maybe.just(42));
        validKind2 = MAYBE.widen(Maybe.just(24));
        validMapper = TestFunctions.INT_TO_STRING;
        secondMapper = Object::toString;
        equalityChecker = (k1, k2) -> MAYBE.narrow(k1).equals(MAYBE.narrow(k2));
    }

    @Nested
    @DisplayName("Complete Functor Test Suite")
    class CompleteFunctorTestSuite {
        @Test
        @DisplayName("Run complete Functor test pattern")
        void runCompleteFunctorTestPattern() {
            TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
                    .<Integer>instance(functorTyped)
                    .<String>withKind(validKind)
                    .withMapper(validMapper)
                    .withSecondMapper(secondMapper)
                    .withEqualityChecker(equalityChecker)
                    .testAll();
        }

        @Test
        @DisplayName("Validate test structure follows standards")
        void validateTestStructure() {
            TestPatternValidator.ValidationResult result =
                    TestPatternValidator.validateAndReport(MaybeFunctorTest.class);

            if (result.hasErrors()) {
                result.printReport();
                throw new AssertionError("Test structure validation failed");
            }
        }
    }

    @Nested
    @DisplayName("Operation Tests")
    class OperationTests {
        @Test
        @DisplayName("map() on Just applies function")
        void mapOnJustAppliesFunction() {
            Kind<MaybeKind.Witness, String> result = functor.map(validMapper, validKind);

            Maybe<String> maybe = MAYBE.narrow(result);
            assertThat(maybe.isJust()).isTrue();
            assertThat(maybe.get()).isEqualTo("42");
        }

        @Test
        @DisplayName("map() on Nothing returns Nothing")
        void mapOnNothingReturnsNothing() {
            Kind<MaybeKind.Witness, Integer> nothingKind = MAYBE.widen(Maybe.nothing());

            Kind<MaybeKind.Witness, String> result = functor.map(validMapper, nothingKind);

            Maybe<String> maybe = MAYBE.narrow(result);
            assertThat(maybe.isNothing()).isTrue();
        }

        @Test
        @DisplayName("map() with null-returning mapper returns Nothing")
        void mapWithNullReturningMapper() {
            Function<Integer, String> nullMapper = i -> null;

            Kind<MaybeKind.Witness, String> result = functor.map(nullMapper, validKind);

            Maybe<String> maybe = MAYBE.narrow(result);
            assertThat(maybe.isNothing()).isTrue();
        }

        @Test
        @DisplayName("map() chains multiple transformations")
        void mapChainsMultipleTransformations() {
            Kind<MaybeKind.Witness, String> result =
                    functor.map(validMapper.andThen(String::toUpperCase), validKind);

            Maybe<String> maybe = MAYBE.narrow(result);
            assertThat(maybe.isJust()).isTrue();
            assertThat(maybe.get()).isEqualTo("42");
        }
    }

    @Nested
    @DisplayName("Individual Components")
    class IndividualComponents {
        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
                    .<Integer>instance(functorTyped)
                    .<String>withKind(validKind)
                    .withMapper(validMapper)
                    .testOperations();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
                    .<Integer>instance(functorTyped)
                    .<String>withKind(validKind)
                    .withMapper(validMapper)
                    .testValidations();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
                    .<Integer>instance(functorTyped)
                    .<String>withKind(validKind)
                    .withMapper(validMapper)
                    .testExceptions();
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
                    .<Integer>instance(functorTyped)
                    .<String>withKind(validKind)
                    .withMapper(validMapper)
                    .withSecondMapper(secondMapper)
                    .withEqualityChecker(equalityChecker)
                    .testLaws();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {
        @Test
        @DisplayName("map() preserves Nothing through chains")
        void mapPreservesNothingThroughChains() {
            Kind<MaybeKind.Witness, Integer> nothingKind = MAYBE.widen(Maybe.nothing());

            Function<Integer, Integer> doubleFunc = i -> i * 2;
            Function<Integer, String> stringFunc = i -> "Value: " + i;

            Kind<MaybeKind.Witness, Integer> intermediate = functor.map(doubleFunc, nothingKind);
            Kind<MaybeKind.Witness, String> result = functor.map(stringFunc, intermediate);

            Maybe<String> maybe = MAYBE.narrow(result);
            assertThat(maybe.isNothing()).isTrue();
        }

        @Test
        @DisplayName("map() with complex transformations")
        void mapWithComplexTransformations() {
            Function<Integer, String> complexMapper =
                    i -> {
                        if (i < 0) return "negative";
                        if (i == 0) return "zero";
                        return "positive:" + i;
                    };

            Kind<MaybeKind.Witness, String> result = functor.map(complexMapper, validKind);
            Maybe<String> maybe = MAYBE.narrow(result);
            assertThat(maybe.get()).isEqualTo("positive:42");
        }

        @Test
        @DisplayName("map() with identity is idempotent")
        void mapWithIdentityIsIdempotent() {
            Function<Integer, Integer> identity = i -> i;

            Kind<MaybeKind.Witness, Integer> result = functor.map(identity, validKind);

            assertThat(MAYBE.narrow(result)).isEqualTo(MAYBE.narrow(validKind));
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {
        @Test
        @DisplayName("Test performance characteristics")
        void testPerformanceCharacteristics() {
            if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
                Kind<MaybeKind.Witness, Integer> start = validKind;

                long startTime = System.nanoTime();
                Kind<MaybeKind.Witness, Integer> result = start;
                for (int i = 0; i < 10000; i++) {
                    result = functor.map(x -> x + 1, result);
                }
                long duration = System.nanoTime() - startTime;

                assertThat(duration).isLessThan(100_000_000L); // Less than 100ms
            }
        }

        @Test
        @DisplayName("Nothing optimisation - map not called")
        void nothingOptimisationMapNotCalled() {
            Kind<MaybeKind.Witness, Integer> nothingKind = MAYBE.widen(Maybe.nothing());
            AtomicBoolean called = new AtomicBoolean(false);

            Function<Integer, String> tracker =
                    i -> {
                        called.set(true);
                        return i.toString();
                    };

            functor.map(tracker, nothingKind);

            assertThat(called).as("Mapper should not be called for Nothing").isFalse();
        }
    }
}