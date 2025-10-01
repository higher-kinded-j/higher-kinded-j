// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedKindHelper;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeTraverse Complete Test Suite")
class MaybeTraverseTest extends TypeClassTestBase<MaybeKind.Witness, Integer, String> {

    private static final Integer SUCCESS_VALUE = 42;

    private Traverse<MaybeKind.Witness> traverse;
    private Applicative<ValidatedKind.Witness<String>> validatedApplicative;
    private Kind<MaybeKind.Witness, Integer> justKind;
    private Kind<MaybeKind.Witness, Integer> nothingKind;
    private Function<Integer, Kind<ValidatedKind.Witness<String>, String>> validTraverseFunction;
    private Monoid<String> validMonoid;
    private Function<Integer, String> validFoldMapFunction;
    private BiPredicate<Kind<ValidatedKind.Witness<String>, ?>, Kind<ValidatedKind.Witness<String>, ?>>
            validatedEqualityChecker;

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
    void setUpTraverse() {
        Traverse<MaybeKind.Witness> traverseInstance = MaybeTraverse.INSTANCE;
        traverse = traverseInstance;

        validatedApplicative = ValidatedMonad.instance(Monoids.string());
        justKind = createValidKind();
        nothingKind = MAYBE.widen(Maybe.nothing());
        validTraverseFunction =
                i -> ValidatedKindHelper.VALIDATED.widen(Validated.valid("Traversed:" + i));
        validMonoid = Monoids.string();
        validFoldMapFunction = TestFunctions.INT_TO_STRING;
        validatedEqualityChecker =
                (k1, k2) ->
                        ValidatedKindHelper.VALIDATED
                                .narrow(k1)
                                .equals(ValidatedKindHelper.VALIDATED.narrow(k2));

        validateRequiredFixtures();
    }

    @Nested
    @DisplayName("Complete Traverse Test Suite")
    class CompleteTraverseTestSuite {

        @Test
        @DisplayName("Run complete Traverse test pattern")
        void runCompleteTraverseTestPattern() {
            TypeClassTest.<MaybeKind.Witness>traverse(MaybeTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(justKind)
                    .withOperations(validMapper)
                    .withApplicative(validatedApplicative, validTraverseFunction)  // G inferred here
                    .withFoldableOperations(validMonoid, validFoldMapFunction)     // M inferred here
                    .withEqualityChecker(validatedEqualityChecker)
                    .testAll();
        }

        @Test
        @DisplayName("Validate test structure follows standards")
        void validateTestStructure() {
            TestPatternValidator.ValidationResult result =
                    TestPatternValidator.validateAndReport(MaybeTraverseTest.class);

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
        @DisplayName("traverse() on Just with successful function")
        void traverseJustSuccessful() {
            Function<Integer, Kind<ValidatedKind.Witness<String>, String>> traverseFunc =
                    i -> ValidatedKindHelper.VALIDATED.widen(Validated.valid("Traversed:" + i));

            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, String>> result =
                    traverse.traverse(validatedApplicative, traverseFunc, justKind);

            Validated<String, Kind<MaybeKind.Witness, String>> validated =
                    ValidatedKindHelper.VALIDATED.narrow(result);
            assertThat(validated.isValid()).isTrue();

            Maybe<String> maybe = MAYBE.narrow(validated.get());
            assertThat(maybe.isJust()).isTrue();
            assertThat(maybe.get()).isEqualTo("Traversed:" + SUCCESS_VALUE);
        }

        @Test
        @DisplayName("traverse() on Just with failing function")
        void traverseJustFailing() {
            Function<Integer, Kind<ValidatedKind.Witness<String>, String>> failingFunc =
                    i -> ValidatedKindHelper.VALIDATED.widen(Validated.invalid("Validation failed"));

            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, String>> result =
                    traverse.traverse(validatedApplicative, failingFunc, justKind);

            Validated<String, Kind<MaybeKind.Witness, String>> validated =
                    ValidatedKindHelper.VALIDATED.narrow(result);
            assertThat(validated.isInvalid()).isTrue();
            assertThat(validated.getError()).isEqualTo("Validation failed");
        }

        @Test
        @DisplayName("traverse() on Nothing lifts Nothing into applicative context")
        void traverseNothingLiftsNothing() {
            Function<Integer, Kind<ValidatedKind.Witness<String>, String>> traverseFunc =
                    i -> ValidatedKindHelper.VALIDATED.widen(Validated.valid("Traversed:" + i));

            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, String>> result =
                    traverse.traverse(validatedApplicative, traverseFunc, nothingKind);

            Validated<String, Kind<MaybeKind.Witness, String>> validated =
                    ValidatedKindHelper.VALIDATED.narrow(result);
            assertThat(validated.isValid()).isTrue();

            Maybe<String> maybe = MAYBE.narrow(validated.get());
            assertThat(maybe.isNothing()).isTrue();
        }

        @Test
        @DisplayName("foldMap() on Just applies function")
        void foldMapOnJustAppliesFunction() {
            Monoid<String> stringMonoid = Monoids.string();
            Function<Integer, String> foldFunction = i -> "Value:" + i;

            String result = traverse.foldMap(stringMonoid, foldFunction, justKind);

            assertThat(result).isEqualTo("Value:" + SUCCESS_VALUE);
        }

        @Test
        @DisplayName("foldMap() on Nothing returns monoid empty")
        void foldMapOnNothingReturnsEmpty() {
            Monoid<String> stringMonoid = Monoids.string();
            Function<Integer, String> foldFunction = i -> "Value:" + i;

            String result = traverse.foldMap(stringMonoid, foldFunction, nothingKind);

            assertThat(result).isEqualTo(stringMonoid.empty());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("map() applies function to Just value")
        void mapAppliesFunctionToJust() {
            Kind<MaybeKind.Witness, String> result = traverse.map(validMapper, justKind);

            Maybe<String> maybe = MAYBE.narrow(result);
            assertThat(maybe.isJust()).isTrue();
            assertThat(maybe.get()).isEqualTo(SUCCESS_VALUE.toString());
        }

        @Test
        @DisplayName("map() preserves Nothing unchanged")
        void mapPreservesNothing() {
            Kind<MaybeKind.Witness, String> result = traverse.map(validMapper, nothingKind);

            Maybe<String> maybe = MAYBE.narrow(result);
            assertThat(maybe.isNothing()).isTrue();
        }
    }

    @Nested
    @DisplayName("Individual Components")
    class IndividualComponents {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTest.<MaybeKind.Witness>traverse(MaybeTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(justKind)
                    .withOperations(validMapper)
                    .withApplicative(validatedApplicative, validTraverseFunction)  // G inferred here
                    .withFoldableOperations(validMonoid, validFoldMapFunction)
                    .testOperations();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTest.<MaybeKind.Witness>traverse(MaybeTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(justKind)
                    .withOperations(validMapper)
                    .withApplicative(validatedApplicative, validTraverseFunction)  // G inferred here
                    .withFoldableOperations(validMonoid, validFoldMapFunction)
                    .testValidations();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.<MaybeKind.Witness>traverse(MaybeTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(justKind)
                    .withOperations(validMapper)
                    .withApplicative(validatedApplicative, validTraverseFunction)  // G inferred here
                    .withFoldableOperations(validMonoid, validFoldMapFunction)
                    .testExceptions();
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTest.<MaybeKind.Witness>traverse(MaybeTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(justKind)
                    .withOperations(validMapper)
                    .withApplicative(validatedApplicative, validTraverseFunction)  // G inferred here
                    .withFoldableOperations(validMonoid, validFoldMapFunction)
                    .withEqualityChecker(validatedEqualityChecker)
                    .testLaws();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("traverse() with conditional function")
        void traverseWithConditionalFunction() {
            Function<Integer, Kind<ValidatedKind.Witness<String>, String>> conditionalFunc =
                    i ->
                            i > 50
                                    ? ValidatedKindHelper.VALIDATED.widen(Validated.valid(i.toString()))
                                    : ValidatedKindHelper.VALIDATED.widen(Validated.invalid("Value too small"));

            // Should fail because 42 <= 50
            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, String>> failResult =
                    traverse.traverse(validatedApplicative, conditionalFunc, justKind);
            Validated<String, Kind<MaybeKind.Witness, String>> failValidated =
                    ValidatedKindHelper.VALIDATED.narrow(failResult);
            assertThat(failValidated.isInvalid()).isTrue();
            assertThat(failValidated.getError()).isEqualTo("Value too small");

            // Should succeed with value > 50
            Kind<MaybeKind.Witness, Integer> bigJust = MAYBE.widen(Maybe.just(100));
            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, String>> successResult =
                    traverse.traverse(validatedApplicative, conditionalFunc, bigJust);

            Validated<String, Kind<MaybeKind.Witness, String>> successValidated =
                    ValidatedKindHelper.VALIDATED.narrow(successResult);
            assertThat(successValidated.isValid()).isTrue();
            assertThat(MAYBE.narrow(successValidated.get()).get()).isEqualTo("100");
        }

        @Test
        @DisplayName("foldMap() with different monoids")
        void foldMapWithDifferentMonoids() {
            // Integer addition
            Monoid<Integer> intAddition = Monoids.integerAddition();
            Function<Integer, Integer> doubleFunc = i -> i * 2;
            Integer intResult = traverse.foldMap(intAddition, doubleFunc, justKind);
            assertThat(intResult).isEqualTo(SUCCESS_VALUE * 2);

            // Nothing case returns empty
            Integer nothingResult = traverse.foldMap(intAddition, doubleFunc, nothingKind);
            assertThat(nothingResult).isEqualTo(intAddition.empty());
            assertThat(nothingResult).isZero();

            // Boolean AND
            Monoid<Boolean> andMonoid = Monoids.booleanAnd();
            Function<Integer, Boolean> isPositive = i -> i > 0;
            Boolean andResult = traverse.foldMap(andMonoid, isPositive, justKind);
            assertThat(andResult).isTrue();
        }

        @Test
        @DisplayName("sequenceA() turns Just<Valid<A>> into Valid<Just<A>>")
        void sequenceJustValidToValidJust() {
            Kind<ValidatedKind.Witness<String>, Integer> validatedKind =
                    ValidatedKindHelper.VALIDATED.widen(Validated.valid(SUCCESS_VALUE));
            Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> input =
                    MAYBE.widen(Maybe.just(validatedKind));

            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> result =
                    traverse.sequenceA(validatedApplicative, input);

            Validated<String, Kind<MaybeKind.Witness, Integer>> validated =
                    ValidatedKindHelper.VALIDATED.narrow(result);
            assertThat(validated.isValid()).isTrue();

            Maybe<Integer> maybe = MAYBE.narrow(validated.get());
            assertThat(maybe.isJust()).isTrue();
            assertThat(maybe.get()).isEqualTo(SUCCESS_VALUE);
        }

        @Test
        @DisplayName("sequenceA() turns Just<Invalid<E>> into Invalid<E>")
        void sequenceJustInvalidToInvalid() {
            Kind<ValidatedKind.Witness<String>, Integer> invalidKind =
                    ValidatedKindHelper.VALIDATED.widen(Validated.invalid("Error"));
            Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> input =
                    MAYBE.widen(Maybe.just(invalidKind));

            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> result =
                    traverse.sequenceA(validatedApplicative, input);

            Validated<String, Kind<MaybeKind.Witness, Integer>> validated =
                    ValidatedKindHelper.VALIDATED.narrow(result);
            assertThat(validated.isInvalid()).isTrue();
            assertThat(validated.getError()).isEqualTo("Error");
        }

        @Test
        @DisplayName("sequenceA() preserves Nothing values")
        void sequenceNothingPreservesNothing() {
            Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> nothingInput =
                    MAYBE.widen(Maybe.nothing());

            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> result =
                    traverse.sequenceA(validatedApplicative, nothingInput);

            Validated<String, Kind<MaybeKind.Witness, Integer>> validated =
                    ValidatedKindHelper.VALIDATED.narrow(result);
            assertThat(validated.isValid()).isTrue();

            Maybe<Integer> maybe = MAYBE.narrow(validated.get());
            assertThat(maybe.isNothing()).isTrue();
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("traverse() efficient with Nothing values")
        void traverseEfficientWithNothingValues() {
            if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
                // Nothing values should not traverse, so even expensive functions are safe
                Function<Integer, Kind<ValidatedKind.Witness<String>, String>> expensiveFunc =
                        i -> ValidatedKindHelper.VALIDATED.widen(Validated.valid("expensive:" + i));

                Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, String>> result =
                        traverse.traverse(validatedApplicative, expensiveFunc, nothingKind);

                // Should complete quickly without calling expensive function
                Validated<String, Kind<MaybeKind.Witness, String>> validated =
                        ValidatedKindHelper.VALIDATED.narrow(result);
                assertThat(MAYBE.narrow(validated.get()).isNothing()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("traverse() integrates with map")
        void traverseIntegratesWithMap() {
            Kind<MaybeKind.Witness, Integer> start = justKind;

            Function<Integer, String> mapper = i -> "mapped:" + i;
            Kind<MaybeKind.Witness, String> mapped = traverse.map(mapper, start);

            Function<String, Kind<ValidatedKind.Witness<String>, String>> traverseFunc =
                    s -> ValidatedKindHelper.VALIDATED.widen(Validated.valid(s.toUpperCase()));

            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, String>> result =
                    traverse.traverse(validatedApplicative, traverseFunc, mapped);

            Validated<String, Kind<MaybeKind.Witness, String>> validated =
                    ValidatedKindHelper.VALIDATED.narrow(result);
            assertThat(MAYBE.narrow(validated.get()).get()).isEqualTo("MAPPED:" + SUCCESS_VALUE);
        }

        @Test
        @DisplayName("traverse() integrates with foldMap")
        void traverseIntegratesWithFoldMap() {
            Monoid<String> stringMonoid = Monoids.string();

            // First fold, then traverse the result
            String folded = traverse.foldMap(stringMonoid, i -> "fold:" + i, justKind);

            Kind<MaybeKind.Witness, String> foldedKind = MAYBE.widen(Maybe.just(folded));

            Function<String, Kind<ValidatedKind.Witness<String>, String>> traverseFunc =
                    s -> ValidatedKindHelper.VALIDATED.widen(Validated.valid("traversed:" + s));

            Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, String>> result =
                    traverse.traverse(validatedApplicative, traverseFunc, foldedKind);

            Validated<String, Kind<MaybeKind.Witness, String>> validated =
                    ValidatedKindHelper.VALIDATED.narrow(result);
            assertThat(MAYBE.narrow(validated.get()).get()).isEqualTo("traversed:fold:" + SUCCESS_VALUE);
        }
    }
}