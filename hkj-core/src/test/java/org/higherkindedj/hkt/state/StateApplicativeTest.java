// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.StateAssert.assertThatStateTuple;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("StateApplicative")
class StateApplicativeTest extends StateTestBase<Integer> {

  private StateApplicative<Integer> applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = new StateApplicative<>();
  }

  // No separate Applicative contract smoke: the State Monad extends this Applicative, so its
  // of/ap/map2 null-argument validation and (deferred) exception behaviour are already covered by
  // the contract in StateMonadTest. A dedicated Applicative contract would only duplicate it.

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state.StateLawFixtures#kinds")
    void identity(String label, Kind<StateKind.Witness<Integer>, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.state.StateLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.state.StateLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state.StateLawFixtures#kinds")
    void composition(String label, Kind<StateKind.Witness<Integer>, Integer> w) {
      Kind<StateKind.Witness<Integer>, Function<String, String>> u =
          STATE.widen(State.of(s -> new StateTuple<>(str -> "u(" + str + ")", s)));
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> v =
          STATE.widen(State.of(s -> new StateTuple<>(i -> "v" + i, s)));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Applicative Operations")
  class ApplicativeOperations {

    @Test
    @DisplayName("of should lift value into State context")
    void ofShouldLiftValue() {
      Kind<StateKind.Witness<Integer>, String> kind = applicative.of("test");

      StateTuple<Integer, String> result = runState(kind, getInitialState());
      assertThatStateTuple(result).hasValue("test").hasState(getInitialState());
    }

    @Test
    @DisplayName("of should handle null value")
    void ofShouldHandleNullValue() {
      Kind<StateKind.Witness<Integer>, String> kind = applicative.of(null);

      StateTuple<Integer, String> result = runState(kind, getInitialState());
      assertThatStateTuple(result).hasNullValue().hasState(getInitialState());
    }

    @Test
    @DisplayName("ap should apply function from State to value in State")
    void apShouldApplyFunction() {
      State<Integer, Function<Integer, String>> funcState =
          State.of(s -> new StateTuple<>(i -> "Value: " + i, s + 5));
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> ff = STATE.widen(funcState);

      State<Integer, Integer> valueState = State.of(s -> new StateTuple<>(s * 2, s + 10));
      Kind<StateKind.Witness<Integer>, Integer> fa = STATE.widen(valueState);

      Kind<StateKind.Witness<Integer>, String> result = applicative.ap(ff, fa);

      StateTuple<Integer, String> tuple = runState(result, getInitialState());
      // First state runs: (func, 15), then second: (30, 25), then apply: "Value: 30"
      assertThatStateTuple(tuple).hasValue("Value: 30").hasState(25);
    }

    @Test
    @DisplayName("ap should thread state correctly")
    void apShouldThreadStateCorrectly() {
      Kind<StateKind.Witness<Integer>, String> result =
          applicative.ap(validFunctionKind, validKind);

      StateTuple<Integer, String> tuple = runState(result, getInitialState());
      assertThatStateTuple(tuple)
          .hasValue("1") // (1).toString()
          .hasState(getInitialState());
    }

    @Test
    @DisplayName("map2 should combine two State computations")
    void map2ShouldCombineTwoComputations() {
      State<Integer, Integer> state1 = State.of(s -> new StateTuple<>(s + 5, s * 2));
      State<Integer, Integer> state2 = State.of(s -> new StateTuple<>(s - 3, s + 10));

      Kind<StateKind.Witness<Integer>, Integer> k1 = STATE.widen(state1);
      Kind<StateKind.Witness<Integer>, Integer> k2 = STATE.widen(state2);

      BiFunction<Integer, Integer, String> combiner = (i1, i2) -> "Result: " + i1 + ", " + i2;

      Kind<StateKind.Witness<Integer>, String> result = applicative.map2(k1, k2, combiner);

      StateTuple<Integer, String> tuple = runState(result, getInitialState());
      // state1: (15, 20), state2: (17, 30), combine: "Result: 15, 17"
      assertThatStateTuple(tuple).hasValue("Result: 15, 17").hasState(30);
    }

    @Test
    @DisplayName("map2 should preserve state threading order")
    void map2ShouldPreserveStateThreadingOrder() {
      State<Integer, String> state1 = State.of(s -> new StateTuple<>("A", s + 1));
      State<Integer, String> state2 = State.of(s -> new StateTuple<>("B", s + 2));

      Kind<StateKind.Witness<Integer>, String> k1 = STATE.widen(state1);
      Kind<StateKind.Witness<Integer>, String> k2 = STATE.widen(state2);

      BiFunction<String, String, String> combiner = (s1, s2) -> s1 + s2;

      Kind<StateKind.Witness<Integer>, String> result = applicative.map2(k1, k2, combiner);

      StateTuple<Integer, String> tuple = runState(result, 0);
      assertThatStateTuple(tuple).hasValue("AB").hasState(3); // 0 + 1 + 2
    }
  }

  // The standard ap/map2 null-argument validations are covered by the contract in StateMonadTest
  // (the State Monad extends this Applicative). Only the State-specific deferred null-check below —
  // a State wrapping a null function — is kept here.
  @Nested
  @DisplayName("Applicative Validations")
  class ApplicativeValidations {

    @Test
    @DisplayName("ap should validate function in State is non-null")
    void apShouldValidateFunctionInStateIsNonNull() {
      State<Integer, Function<Integer, String>> nullFunc = State.pure(null);
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> ff = STATE.widen(nullFunc);

      Kind<StateKind.Witness<Integer>, String> result = applicative.ap(ff, validKind);

      assertThatNullPointerException()
          .isThrownBy(() -> runState(result, getInitialState()))
          .withMessageContaining("Function wrapped in State for 'ap' was null");
    }
  }

  @Nested
  @DisplayName("Lazy Evaluation")
  class LazyEvaluation {

    @Test
    @DisplayName("ap should defer exceptions until run()")
    void apShouldDeferExceptions() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<Integer, String> throwingFunc =
          _ -> {
            throw testException;
          };

      Kind<StateKind.Witness<Integer>, Function<Integer, String>> ff = applicative.of(throwingFunc);
      Kind<StateKind.Witness<Integer>, String> result = applicative.ap(ff, validKind);

      assertThat(result).as("ap should return successfully (lazy evaluation)").isNotNull();

      assertThatThrownBy(() -> runState(result, getInitialState()))
          .as("Exception should be thrown during run()")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map2 should defer exceptions until run()")
    void map2ShouldDeferExceptions() {
      RuntimeException testException = new RuntimeException("Test exception");
      BiFunction<Integer, Integer, String> throwingCombiner =
          (_, _) -> {
            throw testException;
          };

      Kind<StateKind.Witness<Integer>, String> result =
          applicative.map2(validKind, validKind2, throwingCombiner);

      assertThat(result).as("map2 should return successfully (lazy evaluation)").isNotNull();

      assertThatThrownBy(() -> runState(result, getInitialState()))
          .as("Exception should be thrown during run()")
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("ap should handle stateless functions")
    void apShouldHandleStatelessFunctions() {
      State<Integer, Function<Integer, String>> funcState =
          State.of(s -> new StateTuple<>(_ -> "Constant", s));
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> ff = STATE.widen(funcState);

      Kind<StateKind.Witness<Integer>, String> result = applicative.ap(ff, validKind);

      StateTuple<Integer, String> tuple = runState(result, getInitialState());
      assertThatStateTuple(tuple).hasValue("Constant");
    }

    @Test
    @DisplayName("map2 should handle both computations affecting state")
    void map2ShouldHandleBothComputationsAffectingState() {
      State<Integer, Integer> accumulator = State.of(s -> new StateTuple<>(s, s + 1));

      Kind<StateKind.Witness<Integer>, Integer> k1 = STATE.widen(accumulator);
      Kind<StateKind.Witness<Integer>, Integer> k2 = STATE.widen(accumulator);

      BiFunction<Integer, Integer, String> combiner = (i1, i2) -> i1 + "," + i2;

      Kind<StateKind.Witness<Integer>, String> result = applicative.map2(k1, k2, combiner);

      StateTuple<Integer, String> tuple = runState(result, 0);
      assertThatStateTuple(tuple)
          .hasValue("0,1") // First gets 0, second gets 1
          .hasState(2); // State incremented twice
    }

    @Test
    @DisplayName("Nested applicative operations should thread state correctly")
    void nestedApplicativeOperations() {
      Kind<StateKind.Witness<Integer>, Integer> k1 = applicative.of(5);
      Kind<StateKind.Witness<Integer>, Integer> k2 = applicative.of(10);
      Kind<StateKind.Witness<Integer>, Integer> k3 = applicative.of(15);

      BiFunction<Integer, Integer, Integer> sum = Integer::sum;

      Kind<StateKind.Witness<Integer>, Integer> sum12 = applicative.map2(k1, k2, sum);
      Kind<StateKind.Witness<Integer>, Integer> sum123 = applicative.map2(sum12, k3, sum);

      StateTuple<Integer, Integer> result = runState(sum123, getInitialState());
      assertThatStateTuple(result).hasValue(30).hasState(getInitialState());
    }
  }
}
