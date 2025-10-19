// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedApplicative Complete Test Suite")
class ValidatedApplicativeTest
    extends TypeClassTestBase<ValidatedKind.Witness<String>, Integer, String> {

  private Applicative<ValidatedKind.Witness<String>> applicative;
  private Semigroup<String> stringSemigroup;

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
  protected Kind<ValidatedKind.Witness<String>, Function<Integer, String>>
      createValidFunctionKind() {
    Function<Integer, String> fn = n -> "Value: " + n;
    return VALIDATED.widen(Validated.valid(fn));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> a + "+" + b;
  }

  @Override
  protected BiPredicate<
          Kind<ValidatedKind.Witness<String>, ?>, Kind<ValidatedKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      Validated<String, ?> v1 = VALIDATED.narrow(k1);
      Validated<String, ?> v2 = VALIDATED.narrow(k2);
      return v1.equals(v2);
    };
  }

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
          .withLawsTesting(42, Object::toString, equalityChecker)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("Of wraps value in Valid")
    void ofWrapsValueInValid() {
      Kind<ValidatedKind.Witness<String>, Integer> result = applicative.of(42);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Ap applies Valid function to Valid value")
    void apAppliesValidFunctionToValidValue() {
      Function<Integer, String> fn = n -> "Value: " + n;
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.valid(fn));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind = VALIDATED.widen(Validated.valid(42));

      Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("Ap accumulates errors from both Invalid function and Invalid value")
    void apAccumulatesErrorsFromBothInvalidFunctionAndInvalidValue() {
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.invalid("error1"));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind =
          VALIDATED.widen(Validated.invalid("error2"));

      Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1, error2");
    }

    @Test
    @DisplayName("Ap propagates Invalid function")
    void apPropagatesInvalidFunction() {
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.invalid("error"));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind = VALIDATED.widen(Validated.valid(42));

      Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error");
    }

    @Test
    @DisplayName("Ap propagates Invalid value")
    void apPropagatesInvalidValue() {
      Function<Integer, String> fn = n -> "Value: " + n;
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.valid(fn));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind =
          VALIDATED.widen(Validated.invalid("error"));

      Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error");
    }

    @Test
    @DisplayName("Map2 combines two Valid values")
    void map2CombinesTwoValidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> kind1 = VALIDATED.widen(Validated.valid(10));
      Kind<ValidatedKind.Witness<String>, Integer> kind2 = VALIDATED.widen(Validated.valid(20));

      Kind<ValidatedKind.Witness<String>, String> result =
          applicative.map2(kind1, kind2, (a, b) -> a + "+" + b);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo("10+20");
    }

    @Test
    @DisplayName("Map2 accumulates errors from both Invalid values")
    void map2AccumulatesErrorsFromBothInvalidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> kind1 =
          VALIDATED.widen(Validated.invalid("error1"));
      Kind<ValidatedKind.Witness<String>, Integer> kind2 =
          VALIDATED.widen(Validated.invalid("error2"));

      Kind<ValidatedKind.Witness<String>, String> result =
          applicative.map2(kind1, kind2, (a, b) -> a + "+" + b);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1, error2");
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
          .withLawsTesting(42, Object::toString, equalityChecker)
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
          VALIDATED.widen(Validated.invalid("error1"));
      Kind<ValidatedKind.Witness<String>, Integer> kind2 =
          VALIDATED.widen(Validated.invalid("error2"));
      Kind<ValidatedKind.Witness<String>, Integer> kind3 =
          VALIDATED.widen(Validated.invalid("error3"));

      Kind<ValidatedKind.Witness<String>, String> result =
          applicative.map3(kind1, kind2, kind3, (a, b, c) -> a + "+" + b + "+" + c);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1, error2, error3");
    }

    @Test
    @DisplayName("Map4 accumulates four errors")
    void map4AccumulatesFourErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> kind1 =
          VALIDATED.widen(Validated.invalid("error1"));
      Kind<ValidatedKind.Witness<String>, Integer> kind2 =
          VALIDATED.widen(Validated.invalid("error2"));
      Kind<ValidatedKind.Witness<String>, Integer> kind3 =
          VALIDATED.widen(Validated.invalid("error3"));
      Kind<ValidatedKind.Witness<String>, Integer> kind4 =
          VALIDATED.widen(Validated.invalid("error4"));

      Kind<ValidatedKind.Witness<String>, String> result =
          applicative.map4(
              kind1, kind2, kind3, kind4, (a, b, c, d) -> a + "+" + b + "+" + c + "+" + d);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1, error2, error3, error4");
    }

    @Test
    @DisplayName("Map5 combines valid values correctly")
    void map5CombinesValidValuesCorrectly() {
      Kind<ValidatedKind.Witness<String>, Integer> kind1 = VALIDATED.widen(Validated.valid(1));
      Kind<ValidatedKind.Witness<String>, Integer> kind2 = VALIDATED.widen(Validated.valid(2));
      Kind<ValidatedKind.Witness<String>, Integer> kind3 = VALIDATED.widen(Validated.valid(3));
      Kind<ValidatedKind.Witness<String>, Integer> kind4 = VALIDATED.widen(Validated.valid(4));
      Kind<ValidatedKind.Witness<String>, Integer> kind5 = VALIDATED.widen(Validated.valid(5));

      Kind<ValidatedKind.Witness<String>, Integer> result =
          applicative.map5(kind1, kind2, kind3, kind4, kind5, (a, b, c, d, e) -> a + b + c + d + e);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(15);
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
          VALIDATED.widen(Validated.invalid("error1"));
      Kind<ValidatedKind.Witness<String>, Integer> kind2 =
          VALIDATED.widen(Validated.invalid("error2"));

      Kind<ValidatedKind.Witness<String>, String> result =
          reverseApplicative.map2(kind1, kind2, (a, b) -> a + "+" + b);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error2, error1");
    }
  }
}
