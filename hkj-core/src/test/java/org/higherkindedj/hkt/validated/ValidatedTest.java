// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Validated Complete Test Suite")
class ValidatedTest extends TypeClassTestBase<ValidatedKind.Witness<String>, Integer, String> {

    private ValidatedMonad<String> monad;
    private Semigroup<String> semigroup;

    @Override
    protected Kind<ValidatedKind.Witness<String>, Integer> createValidKind() {
        return VALIDATED.widen(Validated.valid(42));
    }

    @Override
    protected Kind<ValidatedKind.Witness<String>, Integer> createValidKind2() {
        return VALIDATED.widen(Validated.valid(24));
    }

    @Override
    protected Function<Integer, String> createValidMapper() {
        return Object::toString;
    }

    @Override
    protected BiPredicate<Kind<ValidatedKind.Witness<String>, ?>,
            Kind<ValidatedKind.Witness<String>, ?>> createEqualityChecker() {
        return (k1, k2) -> VALIDATED.narrow(k1).equals(VALIDATED.narrow(k2));
    }

    @Override
    protected Function<Integer, Kind<ValidatedKind.Witness<String>, String>> createValidFlatMapper() {
        return i -> VALIDATED.widen(Validated.valid(i.toString()));
    }

    @Override
    protected Kind<ValidatedKind.Witness<String>, Function<Integer, String>> createValidFunctionKind() {
        return VALIDATED.widen(Validated.valid(Object::toString));
    }

    @Override
    protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
        return (a, b) -> a + "+" + b;
    }

    @Override
    protected Integer createTestValue() {
        return 100;
    }

    @Override
    protected Function<Integer, Kind<ValidatedKind.Witness<String>, String>> createTestFunction() {
        return i -> VALIDATED.widen(Validated.valid("value:" + i));
    }

    @Override
    protected Function<String, Kind<ValidatedKind.Witness<String>, String>> createChainFunction() {
        return s -> VALIDATED.widen(Validated.valid(s + ":chained"));
    }

    @BeforeEach
    void setUpValidated() {
        semigroup = Semigroups.string(",");
        monad = ValidatedMonad.instance(semigroup);
    }

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete Validated Monad test pattern")
        void runCompleteValidatedMonadTestPattern() {
            TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
                    .<Integer>instance(monad)
                    .<String>withKind(validKind)
                    .withMonadOperations(
                            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
                    .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
                    .configureValidation()
                    .useInheritanceValidation()
                    .withMapFrom(ValidatedMonad.class)
                    .withApFrom(ValidatedMonad.class)
                    .withFlatMapFrom(ValidatedMonad.class)
                    .testAll();
        }
    }

    @Nested
    @DisplayName("Operation Tests")
    class OperationTests {

        @Test
        @DisplayName("Map transforms Valid values")
        void mapTransformsValidValues() {
            Kind<ValidatedKind.Witness<String>, String> result = monad.map(validMapper, validKind);

            Validated<String, String> validated = VALIDATED.narrow(result);
            assertThat(validated.isValid()).isTrue();
            assertThat(validated.get()).isEqualTo("42");
        }

        @Test
        @DisplayName("Map preserves Invalid values")
        void mapPreservesInvalidValues() {
            Kind<ValidatedKind.Witness<String>, Integer> invalid =
                    VALIDATED.widen(Validated.invalid("error"));

            Kind<ValidatedKind.Witness<String>, String> result = monad.map(validMapper, invalid);

            Validated<String, String> validated = VALIDATED.narrow(result);
            assertThat(validated.isInvalid()).isTrue();
            assertThat(validated.getError()).isEqualTo("error");
        }

        @Test
        @DisplayName("FlatMap chains Valid computations")
        void flatMapChainsValidComputations() {
            Kind<ValidatedKind.Witness<String>, String> result =
                    monad.flatMap(validFlatMapper, validKind);

            Validated<String, String> validated = VALIDATED.narrow(result);
            assertThat(validated.isValid()).isTrue();
            assertThat(validated.get()).isEqualTo("42");
        }

        @Test
        @DisplayName("FlatMap propagates Invalid values")
        void flatMapPropagatesInvalidValues() {
            Kind<ValidatedKind.Witness<String>, Integer> invalid =
                    VALIDATED.widen(Validated.invalid("error"));

            Kind<ValidatedKind.Witness<String>, String> result =
                    monad.flatMap(validFlatMapper, invalid);

            Validated<String, String> validated = VALIDATED.narrow(result);
            assertThat(validated.isInvalid()).isTrue();
            assertThat(validated.getError()).isEqualTo("error");
        }

        @Test
        @DisplayName("Ap combines two Valid values")
        void apCombinesTwoValidValues() {
            Kind<ValidatedKind.Witness<String>, String> result =
                    monad.ap(validFunctionKind, validKind);

            Validated<String, String> validated = VALIDATED.narrow(result);
            assertThat(validated.isValid()).isTrue();
            assertThat(validated.get()).isEqualTo("42");
        }

        @Test
        @DisplayName("Ap accumulates errors from both Invalid values")
        void apAccumulatesErrorsFromBothInvalidValues() {
            Kind<ValidatedKind.Witness<String>, Integer> invalid1 =
                    VALIDATED.widen(Validated.invalid("error1"));
            Kind<ValidatedKind.Witness<String>, Function<Integer, String>> invalid2 =
                    VALIDATED.widen(Validated.invalid("error2"));

            Kind<ValidatedKind.Witness<String>, String> result = monad.ap(invalid2, invalid1);

            Validated<String, String> validated = VALIDATED.narrow(result);
            assertThat(validated.isInvalid()).isTrue();
            assertThat(validated.getError()).isEqualTo("error2,error1");
        }
    }

    @Nested
    @DisplayName("Individual Components")
    class IndividualComponents {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
                    .<Integer>instance(monad)
                    .<String>withKind(validKind)
                    .withMonadOperations(
                            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
                    .selectTests()
                    .onlyOperations()
                    .test();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
                    .<Integer>instance(monad)
                    .<String>withKind(validKind)
                    .withMonadOperations(
                            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
                    .configureValidation()
                    .useInheritanceValidation()
                    .withMapFrom(ValidatedMonad.class)
                    .withApFrom(ValidatedMonad.class)
                    .withFlatMapFrom(ValidatedMonad.class)
                    .testValidations();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
                    .<Integer>instance(monad)
                    .<String>withKind(validKind)
                    .withMonadOperations(
                            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
                    .selectTests()
                    .onlyExceptions()
                    .test();
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
                    .<Integer>instance(monad)
                    .<String>withKind(validKind)
                    .withMonadOperations(
                            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
                    .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
                    .selectTests()
                    .onlyLaws()
                    .test();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("ToString produces readable output for Valid")
        void toStringProducesReadableOutputForValid() {
            Validated<String, Integer> valid = Validated.valid(42);
            assertThat(valid.toString()).isEqualTo("Valid(42)");
        }

        @Test
        @DisplayName("ToString produces readable output for Invalid")
        void toStringProducesReadableOutputForInvalid() {
            Validated<String, Integer> invalid = Validated.invalid("error");
            assertThat(invalid.toString()).isEqualTo("Invalid(error)");
        }

        @Test
        @DisplayName("Equals compares Valid values correctly")
        void equalsComparesValidValuesCorrectly() {
            Validated<String, Integer> valid1 = Validated.valid(42);
            Validated<String, Integer> valid2 = Validated.valid(42);
            Validated<String, Integer> valid3 = Validated.valid(43);

            assertThat(valid1).isEqualTo(valid2);
            assertThat(valid1).isNotEqualTo(valid3);
        }

        @Test
        @DisplayName("Equals compares Invalid values correctly")
        void equalsComparesInvalidValuesCorrectly() {
            Validated<String, Integer> invalid1 = Validated.invalid("error");
            Validated<String, Integer> invalid2 = Validated.invalid("error");
            Validated<String, Integer> invalid3 = Validated.invalid("other");

            assertThat(invalid1).isEqualTo(invalid2);
            assertThat(invalid1).isNotEqualTo(invalid3);
        }

        @Test
        @DisplayName("Valid and Invalid are never equal")
        void validAndInvalidAreNeverEqual() {
            Validated<String, Integer> valid = Validated.valid(42);
            Validated<String, Integer> invalid = Validated.invalid("error");

            assertThat(valid).isNotEqualTo(invalid);
        }

        @Test
        @DisplayName("HashCode is consistent for Valid")
        void hashCodeIsConsistentForValid() {
            Validated<String, Integer> valid1 = Validated.valid(42);
            Validated<String, Integer> valid2 = Validated.valid(42);

            assertThat(valid1.hashCode()).isEqualTo(valid2.hashCode());
        }

        @Test
        @DisplayName("HashCode is consistent for Invalid")
        void hashCodeIsConsistentForInvalid() {
            Validated<String, Integer> invalid1 = Validated.invalid("error");
            Validated<String, Integer> invalid2 = Validated.invalid("error");

            assertThat(invalid1.hashCode()).isEqualTo(invalid2.hashCode());
        }
    }

    @Nested
    @DisplayName("Traverse Tests")
    class TraverseTests {

        private ValidatedTraverse<String> traverse;
        private Monoid<Integer> intMonoid;

        @BeforeEach
        void setUpTraverse() {
            traverse = ValidatedTraverse.instance();
            intMonoid = Monoids.integerAddition();
        }

        @Test
        @DisplayName("Run complete Traverse test pattern")
        void runCompleteTraverseTestPattern() {
            TypeClassTest.<ValidatedKind.Witness<String>>traverse(ValidatedTraverse.class)
                    .<Integer>instance(traverse)
                    .<String>withKind(validKind)
                    .withOperations(validMapper)
                    .withApplicative(monad, validFlatMapper)
                    .withFoldableOperations(intMonoid, i -> i)
                    .testAll();
        }
    }
}