// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("TryMonad")
class TryMonadTest extends TryTestBase {

  private MonadError<TryKind.Witness, Throwable> monad;

  @BeforeEach
  void setUpMonad() {
    monad = Instances.monadError(try_());
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value \"{0}\"")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#values")
    void leftIdentity(String value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#kinds")
    void rightIdentity(String label, Kind<TryKind.Witness, String> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#kinds")
    void associativity(String label, Kind<TryKind.Witness, String> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}/{@code
   * flatMap} <em>propagate</em> a thrown function exception, but {@code Try} instead captures it as
   * a {@link Try.Failure}. That capture behaviour is exercised directly in {@link
   * FlatMapOperations} below.
   */
  @Test
  @DisplayName(
      "Monad contract — operations & validations (laws verified above; Try captures exceptions so"
          + " EXCEPTIONS is verified by the capture tests below)")
  void monadContract() {
    TypeClassContract.<TryKind.Witness>monad(TryMonad.class)
        .<String>instance(monad)
        .<Integer>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("flatMap operations")
  class FlatMapOperations {

    @Test
    @DisplayName("flatMap() on Success applies the function")
    void flatMapOnSuccessAppliesFunction() {
      var result = monad.flatMap(validFlatMapper, validKind);
      assertThatTry(result).isSuccess().hasValue(DEFAULT_SUCCESS_VALUE.length());
    }

    @Test
    @DisplayName("flatMap() on Success can return a Failure")
    void flatMapOnSuccessCanReturnFailure() {
      RuntimeException inner = new RuntimeException("Inner failure");
      Function<String, Kind<TryKind.Witness, Integer>> toFailure =
          _ -> TRY.widen(Try.failure(inner));
      var result = monad.flatMap(toFailure, validKind);
      assertThatTry(result).isFailure().hasException(inner);
    }

    @Test
    @DisplayName("flatMap() on Failure passes the failure through")
    void flatMapOnFailurePassesThrough() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      var result = monad.flatMap(validFlatMapper, failure);
      assertThatTry(result).isFailure().hasException(DEFAULT_TEST_EXCEPTION);
    }

    @Test
    @DisplayName("flatMap() captures an exception thrown by the function as a Failure")
    void flatMapCapturesExceptionThrownByFunction() {
      RuntimeException boom = new RuntimeException("Function threw");
      Function<String, Kind<TryKind.Witness, Integer>> throwing =
          _ -> {
            throw boom;
          };
      var result = monad.flatMap(throwing, validKind);
      assertThatTry(result).isFailure().hasException(boom);
    }

    @Test
    @DisplayName("flatMap() captures a null result from the function as a Failure")
    @SuppressWarnings("DataFlowIssue") // null-returning function exercises flatMap's null guard
    void flatMapCapturesNullResultAsFailure() {
      Function<String, Kind<TryKind.Witness, Integer>> nullFunc = _ -> null;
      var result = monad.flatMap(nullFunc, validKind);
      assertThatTry(result)
          .isFailure()
          .hasExceptionSatisfying(
              ex -> assertThat(ex).hasMessageContaining("Function f in flatMap returned null"));
    }
  }

  @Nested
  @DisplayName("of and ap")
  class OfAndApOperations {

    @Test
    @DisplayName("of() wraps a value in Success")
    void ofWrapsValueInSuccess() {
      var result = monad.of("success");
      assertThatTry(result).isSuccess().hasValue("success");
    }

    @Test
    @DisplayName("of() wraps a null value in Success")
    @SuppressWarnings("DataFlowIssue") // Success may legitimately hold a null value
    void ofWrapsNullInSuccess() {
      var result = monad.of(null);
      assertThatTry(result).isSuccess().hasValueSatisfying(v -> assertThat(v).isNull());
    }

    @Test
    @DisplayName("ap() applies the function when both are Success")
    void apAppliesFunctionWhenBothSuccess() {
      var result = monad.ap(validFunctionKind, validKind);
      assertThatTry(result).isSuccess().hasValue(DEFAULT_SUCCESS_VALUE.length());
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("flatMap() handles a null value inside Success")
    @SuppressWarnings("ConstantValue") // a Success may legitimately hold a null value
    void flatMapHandlesNullValueInSuccess() {
      Kind<TryKind.Witness, String> successNull = TRY.widen(Try.success(null));
      Function<String, Kind<TryKind.Witness, Integer>> safe =
          s -> TRY.widen(Try.success(s == null ? -1 : s.length()));
      var result = monad.flatMap(safe, successNull);
      assertThatTry(result).isSuccess().hasValue(-1);
    }

    @Test
    @DisplayName("Deep flatMap chaining accumulates correctly")
    void deepFlatMapChaining() {
      Kind<TryKind.Witness, Integer> result = TRY.widen(Try.success(1));
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }
      assertThatTry(result).isSuccess().hasValue(46);
    }

    @Test
    @DisplayName("A Failure early in a flatMap chain short-circuits and preserves the cause")
    void flatMapWithEarlyFailureShortCircuits() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);

      Function<String, Kind<TryKind.Witness, Integer>> length =
          s -> TRY.widen(Try.success(s.length()));
      Function<Integer, Kind<TryKind.Witness, String>> stringify =
          i -> TRY.widen(Try.success(i.toString()));

      Kind<TryKind.Witness, Integer> step1 = monad.flatMap(length, failure);
      Kind<TryKind.Witness, String> step2 = monad.flatMap(stringify, step1);

      assertThatTry(step2).isFailure().hasException(DEFAULT_TEST_EXCEPTION);
    }

    @Test
    @DisplayName("A complete flatMap pipeline composes Success values")
    void complexChainOfFlatMapOperations() {
      Function<String, Kind<TryKind.Witness, Integer>> step1 =
          s -> TRY.widen(Try.success(s.length()));
      Function<Integer, Kind<TryKind.Witness, Integer>> step2 = i -> TRY.widen(Try.success(i * 2));
      Function<Integer, Kind<TryKind.Witness, String>> step3 =
          i -> TRY.widen(Try.success("Result: " + i));

      Kind<TryKind.Witness, Integer> r1 = monad.flatMap(step1, validKind);
      Kind<TryKind.Witness, Integer> r2 = monad.flatMap(step2, r1);
      Kind<TryKind.Witness, String> r3 = monad.flatMap(step3, r2);

      assertThatTry(r3).isSuccess().hasValue("Result: " + (DEFAULT_SUCCESS_VALUE.length() * 2));
    }
  }
}
