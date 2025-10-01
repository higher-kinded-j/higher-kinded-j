// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeTraverse Foldable Operations Complete Test Suite")
class MaybeFoldableTest extends TypeClassTestBase<MaybeKind.Witness, Integer, String> {

    private static final Integer SUCCESS_VALUE = 42;

    private Foldable<MaybeKind.Witness> foldable;
    private Kind<MaybeKind.Witness, Integer> justKind;
    private Kind<MaybeKind.Witness, Integer> nothingKind;
    private Monoid<String> validMonoid;
    private Function<Integer, String> validFoldMapFunction;

    @Override
    protected Kind<MaybeKind.Witness, Integer> createValidKind() {
        return MAYBE.widen(Maybe.just(SUCCESS_VALUE));
    }

    @Override
    protected Kind<MaybeKind.Witness, Integer> createValidKind2() {
        return MAYBE.widen(Maybe.just(24));
    }

    @Override
    protected Function<Integer, String> createValidMapper() {
        return TestFunctions.INT_TO_STRING;
    }

    @Override
    protected BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>>
    createEqualityChecker() {
        return (k1, k2) -> MAYBE.narrow(k1).equals(MAYBE.narrow(k2));
    }

    @BeforeEach
    void setUpFoldable() {
        foldable = MaybeTraverse.INSTANCE;
        justKind = validKind;
        nothingKind = MAYBE.widen(Maybe.nothing());
        validMonoid = Monoids.string();
        validFoldMapFunction = TestFunctions.INT_TO_STRING;
        validateRequiredFixtures();
    }

    @Nested
    @DisplayName("Complete Foldable Test Suite")
    class CompleteFoldableTestSuite {

        @Test
        @DisplayName("Run complete Foldable test pattern")
        void runCompleteFoldableTestPattern() {
            TypeClassTest.<MaybeKind.Witness>foldable(MaybeTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(justKind)
                    .withOperations(validMonoid, validFoldMapFunction)
                    .testAll();
        }

        @Test
        @DisplayName("Validate test structure follows standards")
        void validateTestStructure() {
            TestPatternValidator.ValidationResult result =
                    TestPatternValidator.validateAndReport(MaybeFoldableTest.class);

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
        @DisplayName("foldMap() on Just applies function")
        void foldMapOnJustAppliesFunction() {
            Monoid<String> stringMonoid = Monoids.string();
            Function<Integer, String> foldFunction = i -> "Value:" + i;

            String result = foldable.foldMap(stringMonoid, foldFunction, justKind);

            assertThat(result).isEqualTo("Value:" + SUCCESS_VALUE);
        }

        @Test
        @DisplayName("foldMap() on Nothing returns monoid empty")
        void foldMapOnNothingReturnsEmpty() {
            Monoid<String> stringMonoid = Monoids.string();
            Function<Integer, String> foldFunction = i -> "Value:" + i;

            String result = foldable.foldMap(stringMonoid, foldFunction, nothingKind);

            assertThat(result).isEqualTo(stringMonoid.empty());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("foldMap() with different monoids")
        void foldMapWithDifferentMonoids() {
            // Integer addition
            Monoid<Integer> intAddition = Monoids.integerAddition();
            Function<Integer, Integer> doubleFunc = i -> i * 2;
            Integer intResult = foldable.foldMap(intAddition, doubleFunc, justKind);
            assertThat(intResult).isEqualTo(SUCCESS_VALUE * 2);

            // Integer multiplication
            Monoid<Integer> intMultiplication = Monoids.integerMultiplication();
            Function<Integer, Integer> identityFunc = i -> i;
            Integer multResult = foldable.foldMap(intMultiplication, identityFunc, justKind);
            assertThat(multResult).isEqualTo(SUCCESS_VALUE);

            // Boolean AND
            Monoid<Boolean> andMonoid = Monoids.booleanAnd();
            Function<Integer, Boolean> isPositive = i -> i > 0;
            Boolean andResult = foldable.foldMap(andMonoid, isPositive, justKind);
            assertThat(andResult).isTrue();

            // Boolean OR
            Monoid<Boolean> orMonoid = Monoids.booleanOr();
            Function<Integer, Boolean> isNegative = i -> i < 0;
            Boolean orResult = foldable.foldMap(orMonoid, isNegative, justKind);
            assertThat(orResult).isFalse();
        }
    }

    @Nested
    @DisplayName("Individual Test Components")
    class IndividualComponents {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTest.<MaybeKind.Witness>foldable(MaybeTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(justKind)
                    .withOperations(validMonoid, validFoldMapFunction)
                    .testOperations();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTest.<MaybeKind.Witness>foldable(MaybeTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(justKind)
                    .withOperations(validMonoid, validFoldMapFunction)
                    .testValidations();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.<MaybeKind.Witness>foldable(MaybeTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(justKind)
                    .withOperations(validMonoid, validFoldMapFunction)
                    .testExceptions();
        }
    }

    @Nested
    @DisplayName("Monoid Properties Tests")
    class MonoidPropertiesTests {

        @Test
        @DisplayName("foldMap() respects monoid identity")
        void foldMapRespectsMonoidIdentity() {
            Monoid<String> stringMonoid = Monoids.string();

            // Nothing should always give identity
            String nothingResult =
                    foldable.foldMap(stringMonoid, TestFunctions.INT_TO_STRING, nothingKind);
            assertThat(nothingResult).isEqualTo(stringMonoid.empty());
        }

        @Test
        @DisplayName("foldMap() with list monoid")
        void foldMapWithListMonoid() {
            Monoid<List<Integer>> listMonoid = Monoids.list();
            Function<Integer, List<Integer>> singletonList = List::of;

            List<Integer> justResult = foldable.foldMap(listMonoid, singletonList, justKind);
            assertThat(justResult).containsExactly(SUCCESS_VALUE);

            List<Integer> nothingResult = foldable.foldMap(listMonoid, singletonList, nothingKind);
            assertThat(nothingResult).isEmpty();
        }

        @Test
        @DisplayName("foldMap() with set monoid")
        void foldMapWithSetMonoid() {
            Monoid<Set<Integer>> setMonoid = Monoids.set();
            Function<Integer, Set<Integer>> singletonSet = Set::of;

            Set<Integer> justResult = foldable.foldMap(setMonoid, singletonSet, justKind);
            assertThat(justResult).containsExactly(SUCCESS_VALUE);

            Set<Integer> nothingResult = foldable.foldMap(setMonoid, singletonSet, nothingKind);
            assertThat(nothingResult).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("foldMap() with complex transformations")
        void foldMapWithComplexTransformations() {
            Monoid<String> stringMonoid = Monoids.string();

            Function<Integer, String> complexFunction =
                    i -> {
                        if (i < 0) return "negative,";
                        if (i == 0) return "zero,";
                        return "positive:" + i + ",";
                    };

            String result = foldable.foldMap(stringMonoid, complexFunction, justKind);
            assertThat(result).isEqualTo("positive:" + SUCCESS_VALUE + ",");
        }

        @Test
        @DisplayName("foldMap() with nested structures")
        void foldMapWithNestedStructures() {
            Kind<MaybeKind.Witness, List<Integer>> listJust = MAYBE.widen(Maybe.just(List.of(1, 2, 3)));

            Foldable<MaybeKind.Witness> foldableList = MaybeTraverse.INSTANCE;
            Monoid<Integer> intMonoid = Monoids.integerAddition();

            Function<List<Integer>, Integer> sumFunction =
                    list -> list.stream().mapToInt(Integer::intValue).sum();

            Integer result = foldableList.foldMap(intMonoid, sumFunction, listJust);
            assertThat(result).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("foldMap() efficient with Nothing values")
        void foldMapEfficientWithNothingValues() {
            // Nothing values should not execute function, so even expensive functions are safe
            Function<Integer, String> expensiveFunc = i -> "expensive:" + i;

            String result = foldable.foldMap(validMonoid, expensiveFunc, nothingKind);

            // Should complete quickly without calling expensive function
            assertThat(result).isEqualTo(validMonoid.empty());
        }
    }
}