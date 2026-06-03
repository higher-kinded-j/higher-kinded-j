// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ValidatedApplicative")
class ValidatedApplicativeTest extends ValidatedTestBase {

  private Applicative<ValidatedKind.Witness<String>> applicative;

  @BeforeEach
  void setUpApplicative() {
    // Accumulating semigroup so ap/mapN combine errors rather than short-circuit.
    applicative = Instances.validated(createDefaultSemigroup());
  }

  // No separate Applicative contract smoke: Instances.validated(...) returns the MonadError, whose
  // operations, validations and exceptions are already verified by the contract in
  // ValidatedMonadTest. The applicative laws are verified in the Laws block below.

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#kinds")
    void identity(String label, Kind<ValidatedKind.Witness<String>, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#kinds")
    void composition(String label, Kind<ValidatedKind.Witness<String>, Integer> w) {
      Kind<ValidatedKind.Witness<String>, Function<String, String>> u =
          VALIDATED.widen(Validated.valid(s -> "u(" + s + ")"));
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> v =
          VALIDATED.widen(Validated.valid(i -> "v" + i));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("Of wraps value in Valid")
    void ofWrapsValueInValid() {
      Kind<ValidatedKind.Witness<String>, Integer> result = applicative.of(DEFAULT_VALID_VALUE);
      assertThatValidated(result).isValid().hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Ap applies Valid function to Valid value")
    void apAppliesValidFunctionToValidValue() {
      Function<Integer, String> fn = n -> "Value: " + n;
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.valid(fn));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind = validKind(DEFAULT_VALID_VALUE);

      Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);
      assertThatValidated(result).isValid().hasValue("Value: 42");
    }

    @Test
    @DisplayName("Ap accumulates errors from both Invalid function and Invalid value")
    void apAccumulatesErrorsFromBothInvalidFunctionAndInvalidValue() {
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.invalid("error1"));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind = invalidKind("error2");

      Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);
      assertThatValidated(result).isInvalid().hasError("error1, error2");
    }

    @Test
    @DisplayName("Ap propagates Invalid function")
    void apPropagatesInvalidFunction() {
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.invalid(DEFAULT_ERROR));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind = validKind(DEFAULT_VALID_VALUE);

      Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);
      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("Ap propagates Invalid value")
    void apPropagatesInvalidValue() {
      Function<Integer, String> fn = n -> "Value: " + n;
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.valid(fn));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind = invalidKind(DEFAULT_ERROR);

      Kind<ValidatedKind.Witness<String>, String> result = applicative.ap(fnKind, valueKind);
      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("Map2 combines two Valid values")
    void map2CombinesTwoValidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> kind1 = validKind(10);
      Kind<ValidatedKind.Witness<String>, Integer> kind2 = validKind(20);

      Kind<ValidatedKind.Witness<String>, String> result =
          applicative.map2(kind1, kind2, (a, b) -> a + "+" + b);
      assertThatValidated(result).isValid().hasValue("10+20");
    }

    @Test
    @DisplayName("Map2 accumulates errors from both Invalid values")
    void map2AccumulatesErrorsFromBothInvalidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> kind1 = invalidKind("error1");
      Kind<ValidatedKind.Witness<String>, Integer> kind2 = invalidKind("error2");

      Kind<ValidatedKind.Witness<String>, String> result =
          applicative.map2(kind1, kind2, (a, b) -> a + "+" + b);
      assertThatValidated(result).isInvalid().hasError("error1, error2");
    }
  }

  @Nested
  @DisplayName("Error Accumulation Tests")
  class ErrorAccumulationTests {

    @Test
    @DisplayName("Map3 accumulates three errors")
    void map3AccumulatesThreeErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> kind1 = invalidKind("error1");
      Kind<ValidatedKind.Witness<String>, Integer> kind2 = invalidKind("error2");
      Kind<ValidatedKind.Witness<String>, Integer> kind3 = invalidKind("error3");

      Kind<ValidatedKind.Witness<String>, String> result =
          applicative.map3(kind1, kind2, kind3, (a, b, c) -> a + "+" + b + "+" + c);
      assertThatValidated(result).isInvalid().hasError("error1, error2, error3");
    }

    @Test
    @DisplayName("Map4 accumulates four errors")
    void map4AccumulatesFourErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> kind1 = invalidKind("error1");
      Kind<ValidatedKind.Witness<String>, Integer> kind2 = invalidKind("error2");
      Kind<ValidatedKind.Witness<String>, Integer> kind3 = invalidKind("error3");
      Kind<ValidatedKind.Witness<String>, Integer> kind4 = invalidKind("error4");

      Kind<ValidatedKind.Witness<String>, String> result =
          applicative.map4(
              kind1, kind2, kind3, kind4, (a, b, c, d) -> a + "+" + b + "+" + c + "+" + d);
      assertThatValidated(result).isInvalid().hasError("error1, error2, error3, error4");
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
      assertThatValidated(result).isValid().hasValue(15);
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
          Instances.validated(reverseSemigroup);

      Kind<ValidatedKind.Witness<String>, Integer> kind1 = invalidKind("error1");
      Kind<ValidatedKind.Witness<String>, Integer> kind2 = invalidKind("error2");

      Kind<ValidatedKind.Witness<String>, String> result =
          reverseApplicative.map2(kind1, kind2, (a, b) -> a + "+" + b);
      assertThatValidated(result).isInvalid().hasError("error2, error1");
    }
  }
}
