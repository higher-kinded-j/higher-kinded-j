// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.higherkindedj.hkt.test.fixtures.TypeClassTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("StateTMonad Complete Test Suite ")
// (Outer: OptionalKind.Witness)
class StateTMonadTest
    extends TypeClassTestBase<StateTKind.Witness<String, OptionalKind.Witness>, Integer, String> {

  private Monad<OptionalKind.Witness> outerMonad = Instances.monadError(optional());
  private Monad<StateTKind.Witness<String, OptionalKind.Witness>> stateTMonad =
      Instances.stateT(outerMonad);

  @BeforeEach
  void setUpMonad() {
    outerMonad = Instances.monadError(optional());
    stateTMonad = Instances.stateT(outerMonad);
  }

  private <A> Optional<StateTuple<String, A>> unwrapKindToOptionalStateTuple(
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> kind) {
    var stateT = STATE_T.narrow(kind);
    Kind<OptionalKind.Witness, StateTuple<String, A>> outerKind = stateT.runStateT("initial");
    return OPTIONAL.narrow(outerKind);
  }

  private <R> Kind<StateTKind.Witness<String, OptionalKind.Witness>, R> pureT(R value) {
    return STATE_T.widen(createStateT(s -> StateTuple.of(s, value)));
  }

  private <R> StateT<String, OptionalKind.Witness, R> createStateT(
      Function<String, StateTuple<String, R>> localFn) {
    return StateT.create(s -> outerMonad.of(localFn.apply(s)), outerMonad);
  }

  private <A, B>
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<A, B>> pureFunction(
          Function<A, B> func) {
    return STATE_T.widen(createStateT(s -> StateTuple.of(s, func)));
  }

  // TypeClassTestBase implementations
  @Override
  protected Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> createValidKind() {
    return pureT(10);
  }

  @Override
  protected Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> createValidKind2() {
    return pureT(20);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected BiPredicate<
          Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>,
          Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>>
      createEqualityChecker() {
    return StateTLawFixtures.EQ;
  }

  @Override
  protected Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>
      createValidFlatMapper() {
    return i -> pureT("v" + i);
  }

  @Override
  protected Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<Integer, String>>
      createValidFunctionKind() {
    return pureFunction(Object::toString);
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> a + "+" + b;
  }

  @Override
  protected Integer createTestValue() {
    return 5;
  }

  @Override
  protected Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>
      createTestFunction() {
    return i -> pureT("v" + i);
  }

  @Override
  protected Function<String, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>
      createChainFunction() {
    return s -> pureT(s + "!");
  }

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}/{@code
   * flatMap} <em>propagate</em> a thrown function exception immediately, but a {@code StateT} is
   * lazy — the exception surfaces only when the computation is run. That deferral is exercised by
   * the type-specific "only thrown when run" ap/flatMap tests.
   */
  @Test
  @DisplayName(
      "Monad contract — operations & validations (laws verified in the *LawTests below; StateT"
          + " defers exceptions)")
  void monadContract() {
    TypeClassContract.<StateTKind.Witness<String, OptionalKind.Witness>>monad(StateTMonad.class)
        .<Integer>instance(stateTMonad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Functor Operations")
  class FunctorOperationTests {

    @Test
    @DisplayName("map should apply function preserving state")
    void map_shouldApplyFunctionPreservingState() {
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> input = pureT(10);
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
          stateTMonad.map(Object::toString, input);

      Optional<StateTuple<String, String>> tuple = unwrapKindToOptionalStateTuple(result);
      assertThat(tuple).isPresent();
      assertThat(tuple.get().value()).isEqualTo("10");
      assertThat(tuple.get().state()).isEqualTo("initial");
    }

    @Test
    @DisplayName("map should thread state through transformation")
    void map_shouldThreadState() {
      StateT<String, OptionalKind.Witness, Integer> stateT =
          createStateT(s -> StateTuple.of(s + "_modified", 10));
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> input = STATE_T.widen(stateT);

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
          stateTMonad.map(Object::toString, input);

      Optional<StateTuple<String, String>> tuple = unwrapKindToOptionalStateTuple(result);
      assertThat(tuple).isPresent();
      assertThat(tuple.get().value()).isEqualTo("10");
      assertThat(tuple.get().state()).isEqualTo("initial_modified");
    }

    @Test
    @DisplayName("map should handle null result by converting to state tuple")
    @SuppressWarnings("NullableProblems") // the mapper deliberately returns null to verify wrapping
    void map_shouldHandleNullResult() {
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> input = pureT(10);
      Function<Integer, @Nullable String> nullReturningMapper = _ -> null;

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
          stateTMonad.map(nullReturningMapper, input);

      Optional<StateTuple<String, String>> tuple = unwrapKindToOptionalStateTuple(result);
      assertThat(tuple).isPresent();
      assertThat(tuple.get().value()).isNull();
    }
  }

  @Nested
  @DisplayName("Applicative Operations")
  class ApplicativeOperationTests {

    final Function<Integer, String> multiplyToString = i -> "Res:" + (i * 2);

    @Test
    @DisplayName("ap: pure(func) ap pure(val) should apply function")
    void ap_pureFuncPureVal_shouldApplyFunction() {
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<Integer, String>> ff =
          pureFunction(multiplyToString);
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa = pureT(10);

      var result = stateTMonad.ap(ff, fa);
      assertThat(unwrapKindToOptionalStateTuple(result))
          .hasValueSatisfying(
              tuple -> {
                assertThat(tuple.value()).isEqualTo("Res:20");
                assertThat(tuple.state()).isEqualTo("initial");
              });
    }

    @Test
    @DisplayName("ap should thread state through function and argument evaluation")
    void ap_shouldThreadState() {
      // Function that modifies state
      StateT<String, OptionalKind.Witness, Function<Integer, String>> funcStateT =
          createStateT(s -> StateTuple.of(s + "_func", multiplyToString));
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<Integer, String>> ff =
          STATE_T.widen(funcStateT);

      // Value that also modifies state
      StateT<String, OptionalKind.Witness, Integer> valStateT =
          createStateT(s -> StateTuple.of(s + "_val", 10));
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa = STATE_T.widen(valStateT);

      var result = stateTMonad.ap(ff, fa);
      assertThat(unwrapKindToOptionalStateTuple(result))
          .hasValueSatisfying(
              tuple -> {
                assertThat(tuple.value()).isEqualTo("Res:20");
                // State should be threaded: initial -> initial_func -> initial_func_val
                assertThat(tuple.state()).isEqualTo("initial_func_val");
              });
    }

    @Test
    @DisplayName("ap should throw when function throws")
    void ap_funcThrows_shouldThrowException() {
      RuntimeException ex = new RuntimeException("Function apply crashed");
      Function<Integer, String> throwingFunc =
          _ -> {
            throw ex;
          };

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<Integer, String>> ff =
          pureFunction(throwingFunc);
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa = pureT(10);

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
          stateTMonad.ap(ff, fa);

      // The exception is only thrown when we actually RUN the StateT
      assertThatThrownBy(() -> STATE_T.narrow(result).runStateT("initial"))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
    }
  }

  @Nested
  @DisplayName("Monad Operations")
  class MonadOperationTests {

    @Test
    @DisplayName("flatMap: pure value, function returns pure should work")
    void flatMap_pureValueFuncReturnsPure() {
      var initialPure = pureT(10);
      Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>
          funcReturnsPure = i -> pureT("Value:" + i);

      var result = stateTMonad.flatMap(funcReturnsPure, initialPure);

      assertThat(unwrapKindToOptionalStateTuple(result))
          .hasValueSatisfying(
              tuple -> {
                assertThat(tuple.value()).isEqualTo("Value:10");
                assertThat(tuple.state()).isEqualTo("initial");
              });
    }

    @Test
    @DisplayName("flatMap should thread state through both computations")
    void flatMap_shouldThreadState() {
      // First computation modifies state
      StateT<String, OptionalKind.Witness, Integer> firstStateT =
          createStateT(s -> StateTuple.of(s + "_first", 10));
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> initial =
          STATE_T.widen(firstStateT);

      // Second computation also modifies state
      Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>> func =
          i -> {
            StateT<String, OptionalKind.Witness, String> secondStateT =
                createStateT(s -> StateTuple.of(s + "_second", "Value:" + i));
            return STATE_T.widen(secondStateT);
          };

      var result = stateTMonad.flatMap(func, initial);

      assertThat(unwrapKindToOptionalStateTuple(result))
          .hasValueSatisfying(
              tuple -> {
                assertThat(tuple.value()).isEqualTo("Value:10");
                // State threaded: initial -> initial_first -> initial_first_second
                assertThat(tuple.state()).isEqualTo("initial_first_second");
              });
    }

    @Test
    @DisplayName("flatMap should propagate exceptions from function")
    void flatMap_functionThrowsRuntimeException() {
      var initialPure = pureT(30);
      RuntimeException runtimeEx = new RuntimeException("Error in function application!");
      Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>> funcThrows =
          _ -> {
            throw runtimeEx;
          };

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
          stateTMonad.flatMap(funcThrows, initialPure);

      // The exception is only thrown when we actually RUN the StateT
      assertThatThrownBy(() -> STATE_T.narrow(result).runStateT("initial"))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(runtimeEx);
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawTests {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state_t.StateTLawFixtures#kinds")
    void identity(
        String label, Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa) {
      FunctorLaws.assertIdentity(stateTMonad, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state_t.StateTLawFixtures#kinds")
    void composition(
        String label, Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa) {
      FunctorLaws.assertComposition(stateTMonad, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLawTests {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state_t.StateTLawFixtures#kinds")
    void identity(String label, Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> w) {
      ApplicativeLaws.assertIdentity(stateTMonad, w, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.state_t.StateTLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(stateTMonad, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.state_t.StateTLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(stateTMonad, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state_t.StateTLawFixtures#kinds")
    void composition(
        String label, Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> w) {
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<String, String>> u =
          pureT(secondMapper);
      ApplicativeLaws.assertComposition(stateTMonad, u, validFunctionKind, w, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawTests {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.state_t.StateTLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(stateTMonad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state_t.StateTLawFixtures#kinds")
    void rightIdentity(
        String label, Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> m) {
      MonadLaws.assertRightIdentity(stateTMonad, m, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.state_t.StateTLawFixtures#kinds")
    void associativity(
        String label, Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> m) {
      MonadLaws.assertAssociativity(stateTMonad, m, testFunction, chainFunction, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("of with null value")
    void of_withNullValue() {
      var result = stateTMonad.of(null);
      assertThat(unwrapKindToOptionalStateTuple(result))
          .hasValueSatisfying(
              tuple -> {
                assertThat(tuple.value()).isNull();
                assertThat(tuple.state()).isEqualTo("initial");
              });
    }

    @Test
    @DisplayName("map with identity should preserve structure")
    void map_withIdentity() {
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> input = pureT(42);
      Function<Integer, Integer> identity = i -> i;

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> result =
          stateTMonad.map(identity, input);

      Optional<StateTuple<String, Integer>> tuple = unwrapKindToOptionalStateTuple(result);
      assertThat(tuple).isPresent();
      assertThat(tuple.get().value()).isEqualTo(42);
      assertThat(tuple.get().state()).isEqualTo("initial");
    }

    @Test
    @DisplayName("flatMap with function returning same type")
    void flatMap_functionReturnsSameType() {
      var initial = pureT(10);
      Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer>> func =
          i -> pureT(i * 2);

      var result = stateTMonad.flatMap(func, initial);

      assertThat(unwrapKindToOptionalStateTuple(result))
          .hasValueSatisfying(
              tuple -> {
                assertThat(tuple.value()).isEqualTo(20);
                assertThat(tuple.state()).isEqualTo("initial");
              });
    }

    @Test
    @DisplayName("complex state threading through multiple operations")
    void complexStateThreading() {
      // Start with a state modification
      StateT<String, OptionalKind.Witness, Integer> step1 =
          createStateT(s -> StateTuple.of(s + "_step1", 1));

      // Map to transform value, preserving state thread
      Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> afterMap =
          stateTMonad.map(i -> i + 10, STATE_T.widen(step1));

      // FlatMap to continue state threading
      Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>> step2 =
          i -> {
            StateT<String, OptionalKind.Witness, String> stateT =
                createStateT(s -> StateTuple.of(s + "_step2", "final:" + i));
            return STATE_T.widen(stateT);
          };

      Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
          stateTMonad.flatMap(step2, afterMap);

      assertThat(unwrapKindToOptionalStateTuple(result))
          .hasValueSatisfying(
              tuple -> {
                assertThat(tuple.value()).isEqualTo("final:11");
                // State threaded through all operations
                assertThat(tuple.state()).isEqualTo("initial_step1_step2");
              });
    }
  }
}
