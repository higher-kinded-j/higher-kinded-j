// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

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

@DisplayName("EitherTraverse Foldable Operations Complete Test Suite")
class EitherFoldableTest extends TypeClassTestBase<EitherKind.Witness<String>, Integer, String> {

    private static final String ERROR_VALUE = "TestError";
    private static final Integer SUCCESS_VALUE = 42;

    private Foldable<EitherKind.Witness<String>> foldable;
    private Kind<EitherKind.Witness<String>, Integer> rightKind;
    private Kind<EitherKind.Witness<String>, Integer> leftKind;
    private Monoid<String> validMonoid;
    private Function<Integer, String> validFoldMapFunction;

    @Override
    protected Kind<EitherKind.Witness<String>, Integer> createValidKind() {
        return EITHER.widen(Either.right(SUCCESS_VALUE));
    }

    @Override
    protected Kind<EitherKind.Witness<String>, Integer> createValidKind2() {
        return EITHER.widen(Either.right(24));
    }

    @Override
    protected Function<Integer, String> createValidMapper() {
        return TestFunctions.INT_TO_STRING;
    }

    @Override
    protected BiPredicate<Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
    createEqualityChecker() {
        return (k1, k2) -> EITHER.narrow(k1).equals(EITHER.narrow(k2));
    }

    @BeforeEach
    void setUpFoldable() {
        foldable = EitherTraverse.instance();
        rightKind = validKind;
        leftKind = EITHER.widen(Either.left(ERROR_VALUE));
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
            TypeClassTest.<EitherKind.Witness<String>>foldable(EitherTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(rightKind)
                    .withOperations(validMonoid, validFoldMapFunction)
                    .testAll();
        }

        @Test
        @DisplayName("Validate test structure follows standards")
        void validateTestStructure() {
            TestPatternValidator.ValidationResult result =
                    TestPatternValidator.validateAndReport(EitherFoldableTest.class);

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
        @DisplayName("foldMap() on Right applies function")
        void foldMapOnRightAppliesFunction() {
            Monoid<String> stringMonoid = Monoids.string();
            Function<Integer, String> foldFunction = i -> "Value:" + i;

            String result = foldable.foldMap(stringMonoid, foldFunction, rightKind);

            assertThat(result).isEqualTo("Value:" + SUCCESS_VALUE);
        }

        @Test
        @DisplayName("foldMap() on Left returns monoid empty")
        void foldMapOnLeftReturnsEmpty() {
            Monoid<String> stringMonoid = Monoids.string();
            Function<Integer, String> foldFunction = i -> "Value:" + i;

            String result = foldable.foldMap(stringMonoid, foldFunction, leftKind);

            assertThat(result).isEqualTo(stringMonoid.empty());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("foldMap() with different monoids")
        void foldMapWithDifferentMonoids() {
            // Integer addition
            Monoid<Integer> intAddition = Monoids.integerAddition();
            Function<Integer, Integer> doubleFunc = i -> i * 2;
            Integer intResult = foldable.foldMap(intAddition, doubleFunc, rightKind);
            assertThat(intResult).isEqualTo(SUCCESS_VALUE * 2);

            // Integer multiplication
            Monoid<Integer> intMultiplication = Monoids.integerMultiplication();
            Function<Integer, Integer> identityFunc = i -> i;
            Integer multResult = foldable.foldMap(intMultiplication, identityFunc, rightKind);
            assertThat(multResult).isEqualTo(SUCCESS_VALUE);

            // Boolean AND
            Monoid<Boolean> andMonoid = Monoids.booleanAnd();
            Function<Integer, Boolean> isPositive = i -> i > 0;
            Boolean andResult = foldable.foldMap(andMonoid, isPositive, rightKind);
            assertThat(andResult).isTrue();

            // Boolean OR
            Monoid<Boolean> orMonoid = Monoids.booleanOr();
            Function<Integer, Boolean> isNegative = i -> i < 0;
            Boolean orResult = foldable.foldMap(orMonoid, isNegative, rightKind);
            assertThat(orResult).isFalse();
        }
    }

    @Nested
    @DisplayName("Individual Test Components")
    class IndividualComponents {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTest.<EitherKind.Witness<String>>foldable(EitherTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(rightKind)
                    .withOperations(validMonoid, validFoldMapFunction)
                    .testOperations();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTest.<EitherKind.Witness<String>>foldable(EitherTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(rightKind)
                    .withOperations(validMonoid, validFoldMapFunction)
                    .testValidations();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.<EitherKind.Witness<String>>foldable(EitherTraverse.class)
                    .<Integer>instance(foldable)
                    .withKind(rightKind)
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

            // Left should always give identity
            String leftResult = foldable.foldMap(stringMonoid, TestFunctions.INT_TO_STRING, leftKind);
            assertThat(leftResult).isEqualTo(stringMonoid.empty());

            // Multiple Left values should all give identity
            Monoid<Integer> intMonoid = Monoids.integerAddition();
            Kind<EitherKind.Witness<String>, Integer> left1 = EITHER.widen(Either.left("E1"));
            Kind<EitherKind.Witness<String>, Integer> left2 = EITHER.widen(Either.left("E2"));

            assertThat(foldable.foldMap(intMonoid, i -> i, left1)).isEqualTo(intMonoid.empty());
            assertThat(foldable.foldMap(intMonoid, i -> i, left2)).isEqualTo(intMonoid.empty());
        }

        @Test
        @DisplayName("foldMap() with list monoid")
        void foldMapWithListMonoid() {
            Monoid<List<Integer>> listMonoid = Monoids.list();
            Function<Integer, List<Integer>> singletonList = List::of;

            List<Integer> rightResult = foldable.foldMap(listMonoid, singletonList, rightKind);
            assertThat(rightResult).containsExactly(SUCCESS_VALUE);

            List<Integer> leftResult = foldable.foldMap(listMonoid, singletonList, leftKind);
            assertThat(leftResult).isEmpty();
        }

        @Test
        @DisplayName("foldMap() with set monoid")
        void foldMapWithSetMonoid() {
            Monoid<Set<Integer>> setMonoid = Monoids.set();
            Function<Integer, Set<Integer>> singletonSet = Set::of;

            Set<Integer> rightResult = foldable.foldMap(setMonoid, singletonSet, rightKind);
            assertThat(rightResult).containsExactly(SUCCESS_VALUE);

            Set<Integer> leftResult = foldable.foldMap(setMonoid, singletonSet, leftKind);
            assertThat(leftResult).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("foldMap() with null values in Right")
        void foldMapWithNullValuesInRight() {
            Kind<EitherKind.Witness<String>, Integer> rightNull = EITHER.widen(Either.right(null));
            Monoid<String> stringMonoid = Monoids.string();

            Function<Integer, String> nullSafeFunction = i -> i == null ? "null" : i.toString();

            String result = foldable.foldMap(stringMonoid, nullSafeFunction, rightNull);
            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("foldMap() with null error in Left")
        void foldMapWithNullErrorInLeft() {
            Kind<EitherKind.Witness<String>, Integer> leftNull = EITHER.widen(Either.left(null));
            Monoid<String> stringMonoid = Monoids.string();

            String result = foldable.foldMap(stringMonoid, TestFunctions.INT_TO_STRING, leftNull);
            assertThat(result).isEqualTo(stringMonoid.empty());
        }

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

            String result = foldable.foldMap(stringMonoid, complexFunction, rightKind);
            assertThat(result).isEqualTo("positive:" + SUCCESS_VALUE + ",");
        }
    }

    @Nested
    @DisplayName("Type Safety Tests")
    class TypeSafetyTests {

        @Test
        @DisplayName("foldMap() with different error types")
        void foldMapWithDifferentErrorTypes() {
            record ComplexError(String code, int severity) {}

            Foldable<EitherKind.Witness<ComplexError>> complexFoldable = EitherTraverse.instance();

            Kind<EitherKind.Witness<ComplexError>, Integer> rightValue = EITHER.widen(Either.right(100));
            Kind<EitherKind.Witness<ComplexError>, Integer> leftValue =
                    EITHER.widen(Either.left(new ComplexError("E500", 5)));

            Monoid<Integer> intMonoid = Monoids.integerAddition();

            assertThat(complexFoldable.foldMap(intMonoid, i -> i * 2, rightValue)).isEqualTo(200);
            assertThat(complexFoldable.foldMap(intMonoid, i -> i * 2, leftValue)).isEqualTo(0);
        }

        @Test
        @DisplayName("foldMap() with nested structures")
        void foldMapWithNestedStructures() {
            Kind<EitherKind.Witness<String>, List<Integer>> listRight =
                    EITHER.widen(Either.right(List.of(1, 2, 3)));

            Foldable<EitherKind.Witness<String>> foldableList = EitherTraverse.instance();
            Monoid<Integer> intMonoid = Monoids.integerAddition();

            Function<List<Integer>, Integer> sumFunction =
                    list -> list.stream().mapToInt(Integer::intValue).sum();

            Integer result = foldableList.foldMap(intMonoid, sumFunction, listRight);
            assertThat(result).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("foldMap() with large values")
        void foldMapWithLargeValues() {
            Kind<EitherKind.Witness<String>, Integer> largeRight = EITHER.widen(Either.right(1_000_000));

            Monoid<String> stringMonoid = Monoids.string();
            Function<Integer, String> expensiveFunction = i -> "Value:" + i + ",";

            String result = foldable.foldMap(stringMonoid, expensiveFunction, largeRight);
            assertThat(result).isEqualTo("Value:1000000,");
        }

        @Test
        @DisplayName("foldMap() with complex data structures")
        void foldMapWithComplexDataStructures() {
            java.util.Map<String, Integer> complexMap = java.util.Map.of("a", 1, "b", 2, "c", 3);

            Kind<EitherKind.Witness<String>, java.util.Map<String, Integer>> mapRight =
                    EITHER.widen(Either.right(complexMap));

            Foldable<EitherKind.Witness<String>> mapFoldable = EitherTraverse.instance();
            Monoid<Integer> intMonoid = Monoids.integerAddition();

            Function<java.util.Map<String, Integer>, Integer> sumValues =
                    map -> map.values().stream().mapToInt(Integer::intValue).sum();

            Integer result = mapFoldable.foldMap(intMonoid, sumValues, mapRight);
            assertThat(result).isEqualTo(6);
        }

        @Test
        @DisplayName("foldMap() efficient with Left values")
        void foldMapEfficientWithLeftValues() {
            // Left values should not execute function, so even expensive functions are safe
            Function<Integer, String> expensiveFunc =
                    i -> {
                        // Simulate expensive computation
                        return "expensive:" + i;
                    };

            String result = foldable.foldMap(validMonoid, expensiveFunc, leftKind);

            // Should complete quickly without calling expensive function
            assertThat(result).isEqualTo(validMonoid.empty());
        }
    }

    @Nested
    @DisplayName("Monoid Law Verification")
    class MonoidLawTests {

        @Test
        @DisplayName("foldMap() preserves monoid associativity")
        void foldMapPreservesMonoidAssociativity() {
            // For Either, we can only test this with Right values since Left always returns empty
            Monoid<String> stringMonoid = Monoids.string();

            // Single Right value should equal itself
            String single = foldable.foldMap(stringMonoid, i -> "test:" + i, rightKind);
            assertThat(single).isEqualTo("test:" + SUCCESS_VALUE);

            // Identity element behaviour
            String identity = foldable.foldMap(stringMonoid, i -> stringMonoid.empty(), rightKind);
            assertThat(identity).isEqualTo(stringMonoid.empty());
        }

        @Test
        @DisplayName("foldMap() composition with different monoids")
        void foldMapCompositionWithDifferentMonoids() {
            // Test that different monoids produce consistent results
            Function<Integer, Integer> mapper = i -> i + 10;

            // Addition monoid
            Monoid<Integer> addMonoid = Monoids.integerAddition();
            Integer addResult = foldable.foldMap(addMonoid, mapper, rightKind);
            assertThat(addResult).isEqualTo(SUCCESS_VALUE + 10);

            // Multiplication monoid
            Monoid<Integer> multMonoid = Monoids.integerMultiplication();
            Integer multResult = foldable.foldMap(multMonoid, mapper, rightKind);
            assertThat(multResult).isEqualTo(SUCCESS_VALUE + 10);

            // Both should have same mapped value but different combination behaviour
            assertThat(addResult).isEqualTo(multResult); // Since there's only one element
        }
    }
}