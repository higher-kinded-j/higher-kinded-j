// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateApplicative<S> Complete Test Suite")
class StateApplicativeTest extends TypeClassTestBase<StateKind.Witness<Integer>, Integer, String> {

  private final Integer initialState = 10;
  private StateApplicative<Integer> applicative;

  @Override
  protected Kind<StateKind.Witness<Integer>, Integer> createValidKind() {
    State<Integer, Integer> state = State.of(s -> new StateTuple<>(s + 1, s + 1));
    return STATE.widen(state);
  }

  @Override
  protected Kind<StateKind.Witness<Integer>, Integer> createValidKind2() {
    State<Integer, Integer> state = State.of(s -> new StateTuple<>(s * 2, s * 2));
    return STATE.widen(state);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return String::toUpperCase;
  }

  @Override
  protected Kind<StateKind.Witness<Integer>, Function<Integer, String>> createValidFunctionKind() {
    return STATE.widen(State.pure(validMapper));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (i1, i2) -> String.valueOf(i1 + i2);
  }

  @Override
  protected Integer createTestValue() {
    return 42;
  }

  @Override
  protected Function<Integer, Kind<StateKind.Witness<Integer>, String>> createTestFunction() {
    return i -> STATE.widen(State.pure(i.toString()));
  }

  @Override
  protected BiPredicate<Kind<StateKind.Witness<Integer>, ?>, Kind<StateKind.Witness<Integer>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      StateTuple<Integer, ?> result1 = STATE.runState(k1, initialState);
      StateTuple<Integer, ?> result2 = STATE.runState(k2, initialState);
      return result1.equals(result2);
    };
  }

  @BeforeEach
  void setUpApplicative() {
    applicative = new StateApplicative<>();
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Applicative test pattern")
    void runCompleteApplicativeTestPattern() {
      TypeClassTest.<StateKind.Witness<Integer>>applicative(StateApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(StateFunctor.class)
          .withApFrom(StateApplicative.class)
          .withMap2From(Applicative.class)
          .selectTests()
          .skipExceptions() // State is lazy - exceptions deferred until run()
          .test();
    }
  }

  @Nested
  @DisplayName("Applicative Operations")
  class ApplicativeOperations {

    @Test
    @DisplayName("of should lift value into State context")
    void ofShouldLiftValue() {
      Kind<StateKind.Witness<Integer>, String> kind = applicative.of("test");

      StateTuple<Integer, String> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo("test");
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    @DisplayName("of should handle null value")
    void ofShouldHandleNullValue() {
      Kind<StateKind.Witness<Integer>, String> kind = applicative.of(null);

      StateTuple<Integer, String> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(initialState);
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

      StateTuple<Integer, String> tuple = STATE.runState(result, initialState);
      // First state runs: (func, 15), then second: (30, 25), then apply: "Value: 30"
      assertThat(tuple.value()).isEqualTo("Value: 30");
      assertThat(tuple.state()).isEqualTo(25);
    }

    @Test
    @DisplayName("ap should thread state correctly")
    void apShouldThreadStateCorrectly() {
      Kind<StateKind.Witness<Integer>, String> result =
          applicative.ap(validFunctionKind, validKind);

      StateTuple<Integer, String> tuple = STATE.runState(result, initialState);
      assertThat(tuple.value()).isEqualTo("11"); // (10 + 1).toString()
      assertThat(tuple.state()).isEqualTo(11);
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

      StateTuple<Integer, String> tuple = STATE.runState(result, initialState);
      // state1: (15, 20), state2: (17, 30), combine: "Result: 15, 17"
      assertThat(tuple.value()).isEqualTo("Result: 15, 17");
      assertThat(tuple.state()).isEqualTo(30);
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

      StateTuple<Integer, String> tuple = STATE.runState(result, 0);
      assertThat(tuple.value()).isEqualTo("AB");
      assertThat(tuple.state()).isEqualTo(3); // 0 + 1 + 2
    }
  }

  @Nested
  @DisplayName("Applicative Validations")
  class ApplicativeValidations {

    @Test
    @DisplayName("ap should validate function Kind is non-null")
    void apShouldValidateFunctionKindIsNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.ap(null, validKind))
          .withMessageContaining("Kind for StateApplicative.ap")
          .withMessageContaining("function");
    }

    @Test
    @DisplayName("ap should validate argument Kind is non-null")
    void apShouldValidateArgumentKindIsNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.ap(validFunctionKind, null))
          .withMessageContaining("Kind for StateApplicative.ap")
          .withMessageContaining("argument");
    }

    @Test
    @DisplayName("ap should validate function in State is non-null")
    void apShouldValidateFunctionInStateIsNonNull() {
      State<Integer, Function<Integer, String>> nullFunc = State.pure(null);
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> ff = STATE.widen(nullFunc);

      Kind<StateKind.Witness<Integer>, String> result = applicative.ap(ff, validKind);

      assertThatNullPointerException()
          .isThrownBy(() -> STATE.runState(result, initialState))
          .withMessageContaining("Function wrapped in State for 'ap' was null");
    }

    @Test
    @DisplayName("map2 should validate first Kind is non-null")
    void map2ShouldValidateFirstKindIsNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.map2(null, validKind2, validCombiningFunction))
          .withMessageContaining("cannot be null");
    }

    @Test
    @DisplayName("map2 should validate second Kind is non-null")
    void map2ShouldValidateSecondKindIsNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.map2(validKind, null, validCombiningFunction))
          .withMessageContaining("cannot be null");
    }

    @Test
    @DisplayName("map2 should validate combining function is non-null")
    void map2ShouldValidateCombiningFunctionIsNonNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  applicative.map2(
                      validKind, validKind2, (BiFunction<Integer, Integer, String>) null))
          .withMessageContaining("combining function")
          .withMessageContaining("map2");
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
          i -> {
            throw testException;
          };

      Kind<StateKind.Witness<Integer>, Function<Integer, String>> ff = applicative.of(throwingFunc);
      Kind<StateKind.Witness<Integer>, String> result = applicative.ap(ff, validKind);

      assertThat(result).as("ap should return successfully (lazy evaluation)").isNotNull();

      assertThatThrownBy(() -> STATE.runState(result, initialState))
          .as("Exception should be thrown during run()")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map2 should defer exceptions until run()")
    void map2ShouldDeferExceptions() {
      RuntimeException testException = new RuntimeException("Test exception");
      BiFunction<Integer, Integer, String> throwingCombiner =
          (i1, i2) -> {
            throw testException;
          };

      Kind<StateKind.Witness<Integer>, String> result =
          applicative.map2(validKind, validKind2, throwingCombiner);

      assertThat(result).as("map2 should return successfully (lazy evaluation)").isNotNull();

      assertThatThrownBy(() -> STATE.runState(result, initialState))
          .as("Exception should be thrown during run()")
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    @Test
    @DisplayName("Identity law: ap(of(id), v) == v")
    void identityLaw() {
      Function<Integer, Integer> identity = i -> i;
      Kind<StateKind.Witness<Integer>, Function<Integer, Integer>> idFunc =
          applicative.of(identity);
      Kind<StateKind.Witness<Integer>, Integer> result = applicative.ap(idFunc, validKind);

      assertThat(equalityChecker.test(result, validKind))
          .as("Applicative Identity Law: ap(of(id), fa) == fa")
          .isTrue();
    }

    @Test
    @DisplayName("Homomorphism law: ap(of(f), of(x)) == of(f(x))")
    void homomorphismLaw() {
      Function<Integer, String> f = validMapper;
      Integer x = testValue;

      Kind<StateKind.Witness<Integer>, Function<Integer, String>> funcKind = applicative.of(f);
      Kind<StateKind.Witness<Integer>, Integer> valueKind = applicative.of(x);

      // Left side: ap(of(f), of(x))
      Kind<StateKind.Witness<Integer>, String> leftSide = applicative.ap(funcKind, valueKind);

      // Right side: of(f(x))
      Kind<StateKind.Witness<Integer>, String> rightSide = applicative.of(f.apply(x));

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Applicative Homomorphism Law: ap(of(f), of(x)) == of(f(x))")
          .isTrue();
    }

    @Test
    @DisplayName("Interchange law: ap(ff, of(x)) == ap(of(f -> f(x)), ff)")
    void interchangeLaw() {
      Integer x = testValue;
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> funcKind =
          applicative.of(validMapper);
      Kind<StateKind.Witness<Integer>, Integer> valueKind = applicative.of(x);

      // Left side: ap(ff, of(x))
      Kind<StateKind.Witness<Integer>, String> leftSide = applicative.ap(funcKind, valueKind);

      // Right side: ap(of(f -> f(x)), ff)
      Function<Function<Integer, String>, String> applyToValue = f -> f.apply(x);
      Kind<StateKind.Witness<Integer>, Function<Function<Integer, String>, String>> applyFunc =
          applicative.of(applyToValue);
      Kind<StateKind.Witness<Integer>, String> rightSide = applicative.ap(applyFunc, funcKind);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Applicative Interchange Law: ap(ff, of(x)) == ap(of(f -> f(x)), ff)")
          .isTrue();
    }

    @Test
    @DisplayName("map2 should be consistent with ap")
    void map2ConsistentWithAp() {
      BiFunction<Integer, Integer, String> combiner = (i1, i2) -> i1 + "+" + i2;

      // Using map2
      Kind<StateKind.Witness<Integer>, String> viaMap2 =
          applicative.map2(validKind, validKind2, combiner);

      // Using ap
      Function<Integer, Function<Integer, String>> curriedCombiner =
          i1 -> i2 -> combiner.apply(i1, i2);
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> partiallyApplied =
          applicative.map(curriedCombiner, validKind);
      Kind<StateKind.Witness<Integer>, String> viaAp = applicative.ap(partiallyApplied, validKind2);

      assertThat(equalityChecker.test(viaMap2, viaAp))
          .as("map2 should be consistent with ap")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("CoreTypeTest Integration")
  class CoreTypeTestIntegration {

    @Test
    @DisplayName("Test State core operations using CoreTypeTest API")
    void testStateCoreOperations() {
      State<Integer, Integer> state = State.of(s -> new StateTuple<>(s + 1, s + 1));

      CoreTypeTest.<Integer, Integer>state(State.class)
          .withState(state)
          .withInitialState(initialState)
          .withMappers(TestFunctions.INT_TO_STRING)
          .testAll();
    }

    @Test
    @DisplayName("Test State with validation configuration for Applicative")
    void testStateWithApplicativeValidation() {
      State<Integer, Integer> state = State.of(s -> new StateTuple<>(s * 2, s + 5));

      CoreTypeTest.<Integer, Integer>state(State.class)
          .withState(state)
          .withInitialState(initialState)
          .withMappers(TestFunctions.INT_TO_STRING)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(StateFunctor.class)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("ap should handle stateless functions")
    void apShouldHandleStatelessFunctions() {
      State<Integer, Function<Integer, String>> funcState =
          State.of(s -> new StateTuple<>(i -> "Constant", s));
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> ff = STATE.widen(funcState);

      Kind<StateKind.Witness<Integer>, String> result = applicative.ap(ff, validKind);

      StateTuple<Integer, String> tuple = STATE.runState(result, initialState);
      assertThat(tuple.value()).isEqualTo("Constant");
    }

    @Test
    @DisplayName("map2 should handle both computations affecting state")
    void map2ShouldHandleBothComputationsAffectingState() {
      State<Integer, Integer> accumulator = State.of(s -> new StateTuple<>(s, s + 1));

      Kind<StateKind.Witness<Integer>, Integer> k1 = STATE.widen(accumulator);
      Kind<StateKind.Witness<Integer>, Integer> k2 = STATE.widen(accumulator);

      BiFunction<Integer, Integer, String> combiner = (i1, i2) -> i1 + "," + i2;

      Kind<StateKind.Witness<Integer>, String> result = applicative.map2(k1, k2, combiner);

      StateTuple<Integer, String> tuple = STATE.runState(result, 0);
      assertThat(tuple.value()).isEqualTo("0,1"); // First gets 0, second gets 1
      assertThat(tuple.state()).isEqualTo(2); // State incremented twice
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

      StateTuple<Integer, Integer> result = STATE.runState(sum123, initialState);
      assertThat(result.value()).isEqualTo(30);
      assertThat(result.state()).isEqualTo(initialState);
    }
  }
}
