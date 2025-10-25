// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedApplicative Complete Test Suite")
class ValidatedApplicativeTest extends ValidatedTestBase {

    private Applicative<ValidatedKind.Witness<String>> applicative;
    private Semigroup<String> stringSemigroup;

    @BeforeEach
    void setUpApplicative() {
        stringSemigroup = (a, b) -> a + ", " + b;
        applicative = ValidatedMonad.instance(stringSemigroup);
    }

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete Applicative test pattern")
        void runCompleteApplicativeTestPattern() {
            TypeClassTest.<ValidatedKind.Witness<String>>applicative(ValidatedMonad.class)
                    .<Integer>instance(applicative)
                    .<String>withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .withLawsTesting(DEFAULT_VALID_VALUE, validMapper, equalityChecker)
                    .testAll();
        }
    }

    @Nested
    @DisplayName("Operation Tests")
    class OperationTests {

        @Test
        @DisplayName("Of wraps value in Valid")
        void ofWrapsValueInValid() {
            Kind<ValidatedKind.Witness<String>, Integer> result =
                    applicative.of(DEFAULT_VALID_VALUE);

            Validated<String, Integer> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isValid()
                    .hasValue(DEFAULT_VALID_VALUE);
        }

        @Test
        @DisplayName("Ap applies Valid function to Valid value")
        void apAppliesValidFunctionToValidValue() {
            Function<Integer, String> fn = n -> "Value: " + n;
            Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
                    VALIDATED.widen(Validated.valid(fn));
            Kind<ValidatedKind.Witness<String>, Integer> valueKind =
                    validKind(DEFAULT_VALID_VALUE);

            Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isValid()
                    .hasValue("Value: 42");
        }

        @Test
        @DisplayName("Ap accumulates errors from both Invalid function and Invalid value")
        void apAccumulatesErrorsFromBothInvalidFunctionAndInvalidValue() {
            Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
                    VALIDATED.widen(Validated.invalid("error1"));
            Kind<ValidatedKind.Witness<String>, Integer> valueKind =
                    invalidKind("error2");

            Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError("error1, error2");
        }

        @Test
        @DisplayName("Ap propagates Invalid function")
        void apPropagatesInvalidFunction() {
            Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
                    VALIDATED.widen(Validated.invalid(DEFAULT_ERROR));
            Kind<ValidatedKind.Witness<String>, Integer> valueKind =
                    validKind(DEFAULT_VALID_VALUE);

            Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError(DEFAULT_ERROR);
        }

        @Test
        @DisplayName("Ap propagates Invalid value")
        void apPropagatesInvalidValue() {
            Function<Integer, String> fn = n -> "Value: " + n;
            Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
                    VALIDATED.widen(Validated.valid(fn));
            Kind<ValidatedKind.Witness<String>, Integer> valueKind =
                    invalidKind(DEFAULT_ERROR);

            Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError(DEFAULT_ERROR);
        }

        @Test
        @DisplayName("Map2 combines two Valid values")
        void map2CombinesTwoValidValues() {
            Kind<ValidatedKind.Witness<String>, Integer> kind1 = validKind(10);
            Kind<ValidatedKind.Witness<String>, Integer> kind2 = validKind(20);

            Kind<ValidatedKind.Witness<String>, String> result =
                    applicative.map2(kind1, kind2, (a, b) -> a + "+" + b);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isValid()
                    .hasValue("10+20");
        }

        @Test
        @DisplayName("Map2 accumulates errors from both Invalid values")
        void map2AccumulatesErrorsFromBothInvalidValues() {
            Kind<ValidatedKind.Witness<String>, Integer> kind1 =
                    invalidKind("error1");
            Kind<ValidatedKind.Witness<String>, Integer> kind2 =
                    invalidKind("error2");

            Kind<ValidatedKind.Witness<String>, String> result =
                    applicative.map2(kind1, kind2, (a, b) -> a + "+" + b);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError("error1, error2");
        }
    }

    @Nested
    @DisplayName("Individual Component Tests")
    class IndividualComponentTests {

        @Test
        @DisplayName("Test operations only")
        void testOperationsOnly() {
            TypeClassTest.<ValidatedKind.Witness<String>>applicative(ValidatedMonad.class)
                    .<Integer>instance(applicative)
                    .<String>withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .testOperations();
        }

        @Test
        @DisplayName("Test validations only")
        void testValidationsOnly() {
            TypeClassTest.<ValidatedKind.Witness<String>>applicative(ValidatedMonad.class)
                    .<Integer>instance(applicative)
                    .<String>withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .testValidations();
        }

        @Test
        @DisplayName("Test exception propagation only")
        void testExceptionPropagationOnly() {
            TypeClassTest.<ValidatedKind.Witness<String>>applicative(ValidatedMonad.class)
                    .<Integer>instance(applicative)
                    .<String>withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .testExceptions();
        }

        @Test
        @DisplayName("Test laws only")
        void testLawsOnly() {
            TypeClassTest.<ValidatedKind.Witness<String>>applicative(ValidatedMonad.class)
                    .<Integer>instance(applicative)
                    .<String>withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .withLawsTesting(DEFAULT_VALID_VALUE, validMapper, equalityChecker)
                    .testLaws();
        }
    }

    @Nested
    @DisplayName("Validation Configuration Tests")
    class ValidationConfigurationTests {

        @Test
        @DisplayName("Test with inheritance-based validation")
        void testWithInheritanceBasedValidation() {
            TypeClassTest.<ValidatedKind.Witness<String>>applicative(ValidatedMonad.class)
                    .<Integer>instance(applicative)
                    .<String>withKind(validKind)
                    .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
                    .configureValidation()
                    .useInheritanceValidation()
                    .withMapFrom(ValidatedMonad.class)
                    .withApFrom(ValidatedMonad.class)
                    .withMap2From(ValidatedMonad.class)
                    .testValidations();
        }
    }

    @Nested
    @DisplayName("Error Accumulation Tests")
    class ErrorAccumulationTests {

        @Test
        @DisplayName("Map3 accumulates three errors")
        void map3AccumulatesThreeErrors() {
            Kind<ValidatedKind.Witness<String>, Integer> kind1 =
                    invalidKind("error1");
            Kind<ValidatedKind.Witness<String>, Integer> kind2 =
                    invalidKind("error2");
            Kind<ValidatedKind.Witness<String>, Integer> kind3 =
                    invalidKind("error3");

            Kind<ValidatedKind.Witness<String>, String> result =
                    applicative.map3(kind1, kind2, kind3, (a, b, c) -> a + "+" + b + "+" + c);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError("error1, error2, error3");
        }

        @Test
        @DisplayName("Map4 accumulates four errors")
        void map4AccumulatesFourErrors() {
            Kind<ValidatedKind.Witness<String>, Integer> kind1 =
                    invalidKind("error1");
            Kind<ValidatedKind.Witness<String>, Integer> kind2 =
                    invalidKind("error2");
            Kind<ValidatedKind.Witness<String>, Integer> kind3 =
                    invalidKind("error3");
            Kind<ValidatedKind.Witness<String>, Integer> kind4 =
                    invalidKind("error4");

            Kind<ValidatedKind.Witness<String>, String> result =
                    applicative.map4(
                            kind1, kind2, kind3, kind4, (a, b, c, d) -> a + "+" + b + "+" + c + "+" + d);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError("error1, error2, error3, error4");
        }

        @Test
        @DisplayName("Map5 combines valid values correctly")
        void map5CombinesValidValuesCorrectly() {
            Kind<ValidatedKind.Witness<String>, Integer> kind1 = validKind(1);
            Kind<ValidatedKind.Witness<String>, Integer> kind2 = validKind(2);
            Kind<ValidatedKind.Witness<String>, Integer> kind3 = validKind(3);
            Kind<ValidatedKind.Witness<String>, Integer> kind4 = validKind(4);
            Kind<ValidatedKind.Witness<String>, Integer> kind5 = validKind(5);

            Kind<ValidatedKind.Witness<String>, Integer> result =
                    applicative.map5(kind1, kind2, kind3, kind4, kind5, (a, b, c, d, e) -> a + b + c + d + e);

            Validated<String, Integer> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isValid()
                    .hasValue(15);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Of with null value throws exception")
        void ofWithNullValueThrowsException() {
            // ValidatedMonad.of validates that the value cannot be null
            assertThatThrownBy(() -> applicative.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ValidatedMonad.of value cannot be null");
        }

        @Test
        @DisplayName("Error accumulation order matches semigroup")
        void errorAccumulationOrderMatchesSemigroup() {
            Semigroup<String> reverseSemigroup = (a, b) -> b + ", " + a;
            Applicative<ValidatedKind.Witness<String>> reverseApplicative =
                    ValidatedMonad.instance(reverseSemigroup);

            Kind<ValidatedKind.Witness<String>, Integer> kind1 =
                    invalidKind("error1");
            Kind<ValidatedKind.Witness<String>, Integer> kind2 =
                    invalidKind("error2");

            Kind<ValidatedKind.Witness<String>, String> result =
                    reverseApplicative.map2(kind1, kind2, (a, b) -> a + "+" + b);

            Validated<String, String> validated = narrowToValidated(result);
            assertThatValidated(validated)
                    .isInvalid()
                    .hasError("error2, error1");
        }
    }
}