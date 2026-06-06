// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("TryApplicative")
class TryApplicativeTest extends TryTestBase {

  private Applicative<TryKind.Witness> applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = Instances.monadError(try_());
    validateApplicativeFixtures();
  }

  // No separate Applicative contract smoke: this instance is the Try MonadError, so its map/ap/map2
  // null-argument validation is already covered by the contract in TryMonadTest. The exception
  // behaviour the contract would assert (propagation) does not apply to Try — ap captures a thrown
  // function exception as a Failure, which is exercised in the ap operation tests below.

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#kinds")
    void identity(String label, Kind<TryKind.Witness, String> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on \"{0}\"")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#values")
    void homomorphism(String value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on \"{0}\"")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#values")
    void interchange(String value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#kinds")
    void composition(String label, Kind<TryKind.Witness, String> w) {
      Kind<TryKind.Witness, Function<Integer, String>> u = TRY.widen(Try.success(i -> "u" + i));
      Kind<TryKind.Witness, Function<String, Integer>> v = TRY.widen(Try.success(String::length));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }
  }

  @Nested
  @DisplayName("of() Operation Tests")
  class OfOperationTests {

    @Test
    @DisplayName("of() wraps a value in Success")
    void ofWrapsValueInSuccess() {
      assertThatTry(applicative.of("test")).isSuccess().hasValue("test");
    }

    @Test
    @DisplayName("of() wraps a null value in Success")
    @SuppressWarnings("DataFlowIssue") // Success may legitimately hold a null value
    void ofWrapsNullInSuccess() {
      assertThatTry(applicative.of(null))
          .isSuccess()
          .hasValueSatisfying(v -> assertThat(v).isNull());
    }
  }

  @Nested
  @DisplayName("ap() Operation Tests")
  class ApOperationTests {

    @Test
    @DisplayName("ap() with Success function and Success value applies the function")
    void apWithSuccessAndSuccessAppliesFunction() {
      Kind<TryKind.Witness, Function<String, Integer>> funcKind =
          TRY.widen(Try.success(String::length));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success("hello"));

      var result = applicative.ap(funcKind, valueKind);
      assertThatTry(result).isSuccess().hasValue(5);
    }

    @Test
    @DisplayName("ap() with a Failure function returns that Failure")
    void apWithFailureFunctionReturnsFailure() {
      RuntimeException exception = new RuntimeException("Function failure");
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = TRY.widen(Try.failure(exception));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success("hello"));

      var result = applicative.ap(funcKind, valueKind);
      assertThatTry(result).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("ap() with a Success function and a Failure value returns that Failure")
    void apWithSuccessFunctionAndFailureValueReturnsFailure() {
      Kind<TryKind.Witness, Function<String, Integer>> funcKind =
          TRY.widen(Try.success(String::length));
      RuntimeException exception = new RuntimeException("Value failure");
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.failure(exception));

      var result = applicative.ap(funcKind, valueKind);
      assertThatTry(result).isFailure().hasException(exception);
    }

    @Test
    @DisplayName("ap() with both Failure returns the function Failure first")
    void apWithBothFailureReturnsFunctionFailureFirst() {
      RuntimeException funcException = new RuntimeException("Function failure");
      RuntimeException valueException = new RuntimeException("Value failure");
      Kind<TryKind.Witness, Function<String, Integer>> funcKind =
          TRY.widen(Try.failure(funcException));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.failure(valueException));

      var result = applicative.ap(funcKind, valueKind);
      assertThatTry(result).isFailure().hasException(funcException);
    }

    @Test
    @DisplayName("ap() captures an exception thrown by the function as a Failure")
    void apCapturesExceptionThrownByFunction() {
      RuntimeException boom = new RuntimeException("Function threw");
      Function<String, Integer> throwing =
          _ -> {
            throw boom;
          };
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = TRY.widen(Try.success(throwing));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success("test"));

      var result = applicative.ap(funcKind, valueKind);
      assertThatTry(result).isFailure().hasException(boom);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  @SuppressWarnings({"DataFlowIssue", "ConstantValue"}) // Success may hold a null value
  class EdgeCaseTests {

    @Test
    @DisplayName("ap() with a function returning null yields a Success(null)")
    void apWithFunctionReturningNull() {
      Function<String, Integer> nullFunc = _ -> null;
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = TRY.widen(Try.success(nullFunc));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success("test"));

      var result = applicative.ap(funcKind, valueKind);
      assertThatTry(result).isSuccess().hasValueSatisfying(v -> assertThat(v).isNull());
    }

    @Test
    @DisplayName("ap() handles a null value in Success")
    void apHandlesNullValue() {
      Function<String, Integer> safeFunc = s -> s == null ? -1 : s.length();
      Kind<TryKind.Witness, Function<String, Integer>> funcKind = TRY.widen(Try.success(safeFunc));
      Kind<TryKind.Witness, String> valueKind = TRY.widen(Try.success(null));

      var result = applicative.ap(funcKind, valueKind);
      assertThatTry(result).isSuccess().hasValue(-1);
    }
  }
}
