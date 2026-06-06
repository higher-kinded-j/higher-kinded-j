// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.StateAssert.assertThatStateTuple;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("StateFunctor")
class StateFunctorTest extends StateTestBase<Integer> {

  private StateFunctor<Integer> functor;

  @BeforeEach
  void setUpFunctor() {
    functor = new StateFunctor<>();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state.StateLawFixtures#kinds")
    void identity(String label, Kind<StateKind.Witness<Integer>, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state.StateLawFixtures#kinds")
    void composition(String label, Kind<StateKind.Witness<Integer>, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}
   * <em>propagates</em> a thrown mapper exception immediately, but a State is lazy — the exception
   * surfaces only when the computation is run. That deferral is exercised by {@link
   * LazyEvaluation}.
   */
  @Test
  @DisplayName(
      "Functor contract — operations & validations (laws verified above; State defers the mapper"
          + " exception, verified below)")
  void functorContract() {
    TypeClassContract.<StateKind.Witness<Integer>>functor(StateFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Functor Operations")
  class FunctorOperations {

    @Test
    @DisplayName("map should transform value whilst preserving state transition")
    void mapShouldTransformValue() {
      State<Integer, Integer> state = State.of(s -> new StateTuple<>(s + 5, s * 2));
      Kind<StateKind.Witness<Integer>, Integer> kind = STATE.widen(state);

      Kind<StateKind.Witness<Integer>, String> mapped = functor.map(validMapper, kind);

      StateTuple<Integer, String> result = runState(mapped, getInitialState());
      assertThatStateTuple(result)
          .hasValue("15") // getInitialState() (10) + 5
          .hasState(20); // getInitialState() (10) * 2
    }

    @Test
    @DisplayName("map should compose correctly")
    void mapShouldComposeCorrectly() {
      Kind<StateKind.Witness<Integer>, String> mapped =
          functor.map(secondMapper, functor.map(validMapper, validKind));

      StateTuple<Integer, String> result = runState(mapped, getInitialState());
      assertThatStateTuple(result).hasValue("1"); // (1).toString().toUpperCase()
    }

    @Test
    @DisplayName("map should handle identity function")
    void mapShouldHandleIdentityFunction() {
      Function<Integer, Integer> identity = i -> i;
      Kind<StateKind.Witness<Integer>, Integer> mapped = functor.map(identity, validKind);

      assertThat(equalityChecker.test(mapped, validKind))
          .as("map with identity should equal original")
          .isTrue();
    }

    @Test
    @DisplayName("map should preserve state threading")
    void mapShouldPreserveStateThreading() {
      State<Integer, Integer> state = State.of(s -> new StateTuple<>(s, s + 10));
      Kind<StateKind.Witness<Integer>, Integer> kind = STATE.widen(state);

      Kind<StateKind.Witness<Integer>, String> mapped = functor.map(validMapper, kind);

      StateTuple<Integer, String> result = runState(mapped, getInitialState());
      assertThatStateTuple(result)
          .hasValue("10") // getInitialState() = 10
          .hasState(20); // 10 + 10
    }
  }

  @Nested
  @DisplayName("Lazy Evaluation")
  class LazyEvaluation {

    @Test
    @DisplayName("map should defer exceptions until run()")
    void mapShouldDeferExceptions() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<Integer, String> throwingMapper =
          _ -> {
            throw testException;
          };

      Kind<StateKind.Witness<Integer>, String> mapped = functor.map(throwingMapper, validKind);
      assertThat(mapped).as("map should return successfully (lazy evaluation)").isNotNull();

      assertThatThrownBy(() -> runState(mapped, getInitialState()))
          .as("Exception should be thrown during run()")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("map should allow building complex computations before evaluation")
    void mapShouldAllowBuildingComplexComputations() {
      // Step 1: Integer -> String (multiply by 2, convert to string)
      Kind<StateKind.Witness<Integer>, String> step1 =
          functor.map(i -> String.valueOf(i * 2), validKind);

      // Step 2: String -> String (prepend "Value: ")
      Kind<StateKind.Witness<Integer>, String> step2 = functor.map(s -> "Value: " + s, step1);

      // Step 3: String -> Integer (get length)
      Kind<StateKind.Witness<Integer>, Integer> computation = functor.map(String::length, step2);

      // Building the computation succeeds
      assertThat(computation).isNotNull();

      // Evaluation produces correct result
      StateTuple<Integer, Integer> result = runState(computation, getInitialState());
      assertThatStateTuple(result).hasValue(8); // "Value: 2".length() = 8
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("map should handle null-returning mapper")
    @SuppressWarnings("DataFlowIssue") // null-returning mapper exercises State's null handling
    void mapShouldHandleNullReturningMapper() {
      Function<Integer, String> nullMapper = _ -> null;
      Kind<StateKind.Witness<Integer>, String> mapped = functor.map(nullMapper, validKind);

      StateTuple<Integer, String> result = runState(mapped, getInitialState());
      assertThatStateTuple(result).hasNullValue().hasStateNonNull();
    }

    @Test
    @DisplayName("map should handle stateless computations")
    void mapShouldHandleStatelessComputations() {
      State<Integer, Integer> stateless = State.of(s -> new StateTuple<>(42, s));
      Kind<StateKind.Witness<Integer>, Integer> kind = STATE.widen(stateless);

      Kind<StateKind.Witness<Integer>, String> mapped = functor.map(validMapper, kind);

      StateTuple<Integer, String> result1 = runState(mapped, 10);
      StateTuple<Integer, String> result2 = runState(mapped, 100);

      assertThatStateTuple(result1).hasValue("42").hasState(10);

      assertThatStateTuple(result2).hasValue("42").hasState(100);
    }

    @Test
    @DisplayName("map should work with complex state transformations")
    void mapShouldWorkWithComplexStateTransformations() {
      State<Integer, Integer> complex = State.of(s -> new StateTuple<>(s * s, s + 1));
      Kind<StateKind.Witness<Integer>, Integer> kind = STATE.widen(complex);

      Kind<StateKind.Witness<Integer>, String> mapped = functor.map(i -> "Square: " + i, kind);

      StateTuple<Integer, String> result = runState(mapped, 5);
      assertThatStateTuple(result).hasValue("Square: 25").hasState(6);
    }
  }
}
