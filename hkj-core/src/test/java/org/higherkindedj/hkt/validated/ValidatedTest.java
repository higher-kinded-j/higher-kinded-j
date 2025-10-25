// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Validated Complete Test Suite")
class ValidatedTest extends ValidatedTestBase {

    private ValidatedMonad<String> monad;
    private Semigroup<String> semigroup;

    @BeforeEach
    void setUpValidated() {
        semigroup = createDefaultSemigroup();
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

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isValid()
                    .hasValue("42")
                    .hasValueOfType(String.class);
        }

        @Test
        @DisplayName("Map preserves Invalid values")
        void mapPreservesInvalidValues() {
            Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

            Kind<ValidatedKind.Witness<String>, String> result = monad.map(validMapper, invalid);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError(DEFAULT_ERROR);
        }

        @Test
        @DisplayName("FlatMap chains Valid computations")
        void flatMapChainsValidComputations() {
            Kind<ValidatedKind.Witness<String>, String> result =
                    monad.flatMap(validFlatMapper, validKind);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isValid()
                    .hasValueSatisfying(v -> v.startsWith("mapped:"), "value starts with 'mapped:'");
        }

        @Test
        @DisplayName("FlatMap propagates Invalid values")
        void flatMapPropagatesInvalidValues() {
            Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

            Kind<ValidatedKind.Witness<String>, String> result = monad.flatMap(validFlatMapper, invalid);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError(DEFAULT_ERROR);
        }

        @Test
        @DisplayName("Ap combines two Valid values")
        void apCombinesTwoValidValues() {
            Kind<ValidatedKind.Witness<String>, String> result = monad.ap(validFunctionKind, validKind);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated).isValid();
        }

        @Test
        @DisplayName("Ap accumulates errors from both Invalid values")
        void apAccumulatesErrorsFromBothInvalidValues() {
            Kind<ValidatedKind.Witness<String>, Integer> invalid1 =
                    invalidKind("error1");
            Kind<ValidatedKind.Witness<String>, java.util.function.Function<Integer, String>> invalid2 =
                    VALIDATED.widen(Validated.invalid("error2"));

            Kind<ValidatedKind.Witness<String>, String> result = monad.ap(invalid2, invalid1);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError("error2, error1");
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
            Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);
            assertThat(valid.toString()).isEqualTo("Valid(42)");
        }

        @Test
        @DisplayName("ToString produces readable output for Invalid")
        void toStringProducesReadableOutputForInvalid() {
            Validated<String, Integer> invalid = Validated.invalid(DEFAULT_ERROR);
            assertThat(invalid.toString()).isEqualTo("Invalid(error)");
        }

        @Test
        @DisplayName("Equals compares Valid values correctly")
        void equalsComparesValidValuesCorrectly() {
            Validated<String, Integer> valid1 = Validated.valid(DEFAULT_VALID_VALUE);
            Validated<String, Integer> valid2 = Validated.valid(DEFAULT_VALID_VALUE);
            Validated<String, Integer> valid3 = Validated.valid(43);

            assertThatValidated(valid1).isEqualTo(valid2);
            assertThatValidated(valid1).isNotEqualTo(valid3);
        }

        @Test
        @DisplayName("Equals compares Invalid values correctly")
        void equalsComparesInvalidValuesCorrectly() {
            Validated<String, Integer> invalid1 = Validated.invalid(DEFAULT_ERROR);
            Validated<String, Integer> invalid2 = Validated.invalid(DEFAULT_ERROR);
            Validated<String, Integer> invalid3 = Validated.invalid(ALTERNATIVE_ERROR);

            assertThatValidated(invalid1).isEqualTo(invalid2);
            assertThatValidated(invalid1).isNotEqualTo(invalid3);
        }

        @Test
        @DisplayName("Valid and Invalid are never equal")
        void validAndInvalidAreNeverEqual() {
            Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);
            Validated<String, Integer> invalid = Validated.invalid(DEFAULT_ERROR);

            assertThatValidated(valid).isNotEqualTo(invalid);
        }

        @Test
        @DisplayName("HashCode is consistent for Valid")
        void hashCodeIsConsistentForValid() {
            Validated<String, Integer> valid1 = Validated.valid(DEFAULT_VALID_VALUE);
            Validated<String, Integer> valid2 = Validated.valid(DEFAULT_VALID_VALUE);

            assertThat(valid1.hashCode()).isEqualTo(valid2.hashCode());
        }

        @Test
        @DisplayName("HashCode is consistent for Invalid")
        void hashCodeIsConsistentForInvalid() {
            Validated<String, Integer> invalid1 = Validated.invalid(DEFAULT_ERROR);
            Validated<String, Integer> invalid2 = Validated.invalid(DEFAULT_ERROR);

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

    @Nested
    @DisplayName("toEither Conversion Tests")
    class ToEitherConversionTests {

        @Test
        @DisplayName("Valid toEither produces Right")
        void validToEitherProducesRight() {
            Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

            Either<String, Integer> either = valid.toEither();

            assertThat(either.isRight()).isTrue();
            assertThat(either.isLeft()).isFalse();
            assertThat(either.getRight()).isEqualTo(DEFAULT_VALID_VALUE);
        }

        @Test
        @DisplayName("Invalid toEither produces Left")
        void invalidToEitherProducesLeft() {
            Validated<String, Integer> invalid = Validated.invalid("error message");

            Either<String, Integer> either = invalid.toEither();

            assertThat(either.isLeft()).isTrue();
            assertThat(either.isRight()).isFalse();
            assertThat(either.getLeft()).isEqualTo("error message");
        }

        @Test
        @DisplayName("Valid toEither roundtrip preserves value")
        void validToEitherRoundtripPreservesValue() {
            Validated<String, Integer> original = Validated.valid(DEFAULT_VALID_VALUE);
            Either<String, Integer> either = original.toEither();

            assertThat(either.isRight()).isTrue();
            assertThat(either.getRight()).isEqualTo(original.get());
        }

        @Test
        @DisplayName("Invalid toEither roundtrip preserves error")
        void invalidToEitherRoundtripPreservesError() {
            Validated<String, Integer> original = Validated.invalid(DEFAULT_ERROR);
            Either<String, Integer> either = original.toEither();

            assertThat(either.isLeft()).isTrue();
            assertThat(either.getLeft()).isEqualTo(original.getError());
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Valid factory creates Valid instance")
        void validFactoryCreatesValidInstance() {
            Validated<String, Integer> validated = Validated.valid(DEFAULT_VALID_VALUE);

            assertThatValidated(validated)
                    .isValid()
                    .hasValue(DEFAULT_VALID_VALUE)
                    .hasValueOfType(Integer.class);
        }

        @Test
        @DisplayName("Invalid factory creates Invalid instance")
        void invalidFactoryCreatesInvalidInstance() {
            Validated<String, Integer> validated = Validated.invalid(DEFAULT_ERROR);

            assertThatValidated(validated)
                    .isInvalid()
                    .hasError(DEFAULT_ERROR)
                    .hasErrorOfType(String.class);
        }

        @Test
        @DisplayName("Valid factory rejects null value")
        void validFactoryRejectsNullValue() {
            assertThatThrownBy(() -> Validated.<String, Integer>valid(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("value")
                    .hasMessageContaining("Validated")
                    .hasMessageContaining("construction");
        }

        @Test
        @DisplayName("Invalid factory rejects null error")
        void invalidFactoryRejectsNullError() {
            assertThatThrownBy(() -> Validated.<String, Integer>invalid(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("error")
                    .hasMessageContaining("Validated");
        }
    }

    @Nested
    @DisplayName("Fold Operations")
    class FoldOperations {

        @Test
        @DisplayName("Fold applies valid mapper on Valid")
        void foldAppliesValidMapperOnValid() {
            Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

            String result = valid.fold(
                    error -> "Error: " + error,
                    value -> "Value: " + value
            );

            assertThat(result).isEqualTo("Value: 42");
        }

        @Test
        @DisplayName("Fold applies invalid mapper on Invalid")
        void foldAppliesInvalidMapperOnInvalid() {
            Validated<String, Integer> invalid = Validated.invalid(DEFAULT_ERROR);

            String result = invalid.fold(
                    error -> "Error: " + error,
                    value -> "Value: " + value
            );

            assertThat(result).isEqualTo("Error: error");
        }

        @Test
        @DisplayName("Fold validates invalid mapper is non-null")
        void foldValidatesInvalidMapperIsNonNull() {
            Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

            assertThatThrownBy(() -> valid.fold(null, v -> "valid"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("invalidMapper")
                    .hasMessageContaining("Validated")
                    .hasMessageContaining("fold");
        }

        @Test
        @DisplayName("Fold validates valid mapper is non-null")
        void foldValidatesValidMapperIsNonNull() {
            Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

            assertThatThrownBy(() -> valid.fold(e -> "invalid", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("validMapper")
                    .hasMessageContaining("Validated")
                    .hasMessageContaining("fold");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Chain multiple Valid operations")
        void chainMultipleValidOperations() {
            Validated<String, Integer> result = Validated.<String, Integer>valid(10)
                    .map(n -> n * 2)
                    .flatMap(n -> Validated.<String, Integer>valid(n + 5))
                    .map(n -> n * 3);

            assertThatValidated(result)
                    .isValid()
                    .hasValue(75); // (10 * 2 + 5) * 3
        }

        @Test
        @DisplayName("Chain operations short-circuits on Invalid")
        void chainOperationsShortCircuitsOnInvalid() {
            Validated<String, Integer> result = Validated.<String, Integer>valid(10)
                    .map(n -> n * 2)
                    .flatMap(n -> Validated.<String, Integer>invalid("computation failed"))
                    .map(n -> n * 3); // This should not execute

            assertThatValidated(result)
                    .isInvalid()
                    .hasError("computation failed");
        }


            @Test
        @DisplayName("Combining Valid values with map2")
        void combiningValidValuesWithMap2() {
            Kind<ValidatedKind.Witness<String>, Integer> v1 = validKind(10);
            Kind<ValidatedKind.Witness<String>, Integer> v2 = validKind(20);

            Kind<ValidatedKind.Witness<String>, Integer> result =
                    monad.map2(v1, v2, (a, b) -> a + b);

            Validated<String, Integer> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isValid()
                    .hasValue(30);
        }

        @Test
        @DisplayName("Combining Invalid values accumulates errors")
        void combiningInvalidValuesAccumulatesErrors() {
            Kind<ValidatedKind.Witness<String>, Integer> v1 = invalidKind("error1");
            Kind<ValidatedKind.Witness<String>, Integer> v2 = invalidKind("error2");

            Kind<ValidatedKind.Witness<String>, Integer> result =
                    monad.map2(v1, v2, (a, b) -> a + b);

            Validated<String, Integer> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError("error1, error2");
        }
    }

    @Nested
    @DisplayName("Type Variance Tests")
    class TypeVarianceTests {

        @Test
        @DisplayName("Map changes value type")
        void mapChangesValueType() {
            Validated<String, Integer> intValid = Validated.valid(42);
            Validated<String, String> stringValid = intValid.map(Object::toString);

            assertThatValidated(stringValid)
                    .isValid()
                    .hasValue("42")
                    .hasValueOfType(String.class);
        }

        @Test
        @DisplayName("FlatMap changes value type")
        void flatMapChangesValueType() {
            Validated<String, Integer> intValid = Validated.valid(42);
            Validated<String, Boolean> boolValid =
                    intValid.flatMap(n -> Validated.valid(n > 40));

            assertThatValidated(boolValid)
                    .isValid()
                    .hasValue(true)
                    .hasValueOfType(Boolean.class);
        }

        @Test
        @DisplayName("Error type remains consistent through transformations")
        void errorTypeRemainsConsistentThroughTransformations() {
            Validated<String, Integer> start = Validated.invalid("initial error");

            Validated<String, String> afterMap = start.map(Object::toString);
            assertThatValidated(afterMap)
                    .isInvalid()
                    .hasError("initial error")
                    .hasErrorOfType(String.class);

            Validated<String, Boolean> afterFlatMap =
                    afterMap.flatMap(s -> Validated.valid(s.isEmpty()));
            assertThatValidated(afterFlatMap)
                    .isInvalid()
                    .hasError("initial error")
                    .hasErrorOfType(String.class);
        }
    }
}