// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.StateAssert.assertThatStateTuple;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("StateMonad")
class StateMonadTest extends StateTestBase<Integer> {

  private StateMonad<Integer> monad;

  @BeforeEach
  void setUpMonad() {
    monad = new StateMonad<>();
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("values")
    void leftIdentity(Integer value) {
      Monad<StateKind.Witness<Integer>> m = monad;
      MonadLaws.assertLeftIdentity(m, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("fixtures")
    void rightIdentity(String label, Kind<StateKind.Witness<Integer>, Integer> ma) {
      Monad<StateKind.Witness<Integer>> m = monad;
      MonadLaws.assertRightIdentity(m, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("fixtures")
    void associativity(String label, Kind<StateKind.Witness<Integer>, Integer> ma) {
      Monad<StateKind.Witness<Integer>> m = monad;
      MonadLaws.assertAssociativity(m, ma, testFunction, chainFunction, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of(
              "State(s -> (s, 42))",
              STATE.widen(State.<Integer, Integer>of(s -> new StateTuple<>(s, 42)))),
          Arguments.of(
              "State(s -> (s+1, s))",
              STATE.widen(State.<Integer, Integer>of(s -> new StateTuple<>(s + 1, s)))));
    }

    static Stream<Arguments> values() {
      return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("of() creates State returning value and unchanged state")
    void ofCreatesStateReturningValue() {
      Kind<StateKind.Witness<Integer>, String> kind = monad.of("constantValue");
      StateTuple<Integer, String> result = runState(kind, getInitialState());

      assertThatStateTuple(result).hasValue("constantValue").hasState(getInitialState());
    }

    @Test
    @DisplayName("of() allows null value")
    void ofAllowsNullValue() {
      Kind<StateKind.Witness<Integer>, String> kind = monad.of(null);
      StateTuple<Integer, String> result = runState(kind, getInitialState());

      assertThatStateTuple(result).hasNullValue().hasState(getInitialState());
    }

    @Test
    @DisplayName("map() applies function to result value and keeps state transition")
    void mapAppliesFunctionToResultValue() {
      State<Integer, Integer> incrementState = State.of(s -> new StateTuple<>(s + 1, s + 1));
      Kind<StateKind.Witness<Integer>, Integer> incKind = STATE.widen(incrementState);

      Kind<StateKind.Witness<Integer>, String> mappedKind = monad.map(i -> "Val:" + i, incKind);

      StateTuple<Integer, String> result = runState(mappedKind, getInitialState());
      assertThatStateTuple(result)
          .hasValue("Val:11") // getInitialState() + 1
          .hasState(11); // getInitialState() + 1
    }

    @Test
    @DisplayName("map() chains functions correctly")
    void mapChainsFunctions() {
      State<Integer, Integer> initialState = State.of(s -> new StateTuple<>(s * 2, s + 5));
      Kind<StateKind.Witness<Integer>, Integer> initialKind = STATE.widen(initialState);

      Kind<StateKind.Witness<Integer>, String> mappedKind =
          monad.map(value -> "Str:" + value, monad.map(val -> val / 2.0, initialKind));

      StateTuple<Integer, String> result = runState(mappedKind, getInitialState());
      assertThatStateTuple(result)
          .hasValue("Str:10.0") // (10 * 2) / 2.0
          .hasState(15); // 10 + 5
    }

    @Test
    @DisplayName("ap() applies State function to State value")
    void apAppliesStateFunctionToStateValue() {
      State<Integer, Function<Integer, String>> funcState =
          State.of(s -> new StateTuple<>(i -> "F" + i + s, s + 1));
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> funcKind = STATE.widen(funcState);

      State<Integer, Integer> valState = State.of(s -> new StateTuple<>(s * 10, s + 2));
      Kind<StateKind.Witness<Integer>, Integer> valKind = STATE.widen(valState);

      Kind<StateKind.Witness<Integer>, String> resultKind = monad.ap(funcKind, valKind);

      StateTuple<Integer, String> result = runState(resultKind, getInitialState());

      // funcState runs first: (func, 11)
      // valState runs second: (110, 13)
      // func(110) with captured s=10 from funcState: "F11010"
      assertThatStateTuple(result).hasValue("F11010").hasState(13);
    }

    @Test
    @DisplayName("ap() works with pure function and value")
    void apWorksWithPureFunctionAndValue() {
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> funcKind =
          monad.of(i -> "Num" + i);
      Kind<StateKind.Witness<Integer>, Integer> valKind = monad.of(100);
      Kind<StateKind.Witness<Integer>, String> resultKind = monad.ap(funcKind, valKind);

      StateTuple<Integer, String> result = runState(resultKind, 5);
      assertThatStateTuple(result).hasValue("Num100").hasState(5);
    }

    @Test
    @DisplayName("flatMap() sequences computations and passes state")
    void flatMapSequencesComputationsAndPassesState() {
      Kind<StateKind.Witness<Integer>, Integer> getState = STATE.get();
      Kind<StateKind.Witness<Integer>, Unit> incState = STATE.modify((Integer i) -> i + 1);

      Kind<StateKind.Witness<Integer>, Integer> getStateAndInc =
          monad.flatMap(
              originalState -> monad.map(voidResult -> originalState, incState), getState);

      Function<Integer, Kind<StateKind.Witness<Integer>, String>> processValueAndAdd10 =
          originalStateValue ->
              STATE.widen(
                  State.of(
                      currentState ->
                          new StateTuple<>("Val:" + (originalStateValue * 2), currentState + 10)));

      Kind<StateKind.Witness<Integer>, String> resultKind =
          monad.flatMap(processValueAndAdd10, getStateAndInc);

      StateTuple<Integer, String> result = runState(resultKind, getInitialState());
      // getState: (10, 10), incState: (Unit, 11), map to: (10, 11)
      // processValueAndAdd10(10): ("Val:20", 21)
      assertThatStateTuple(result).hasValue("Val:20").hasState(21);
    }

    @Test
    @DisplayName("map2() combines State and values")
    void map2CombinesStateAndValues() {
      State<Integer, Integer> st1 = State.of(s -> new StateTuple<>(s + 1, s + 1));
      State<Integer, String> st2 = State.of(s -> new StateTuple<>("S" + s, s * 2));

      Kind<StateKind.Witness<Integer>, Integer> k1 = STATE.widen(st1);
      Kind<StateKind.Witness<Integer>, String> k2 = STATE.widen(st2);

      Kind<StateKind.Witness<Integer>, String> result =
          monad.map2(k1, k2, (i, sVal) -> i + ":" + sVal);

      StateTuple<Integer, String> res = runState(result, getInitialState());
      // st1: (11, 11), st2: ("S11", 22)
      assertThatStateTuple(res).hasValue("11:S11").hasState(22);
    }
  }

  @Nested
  @DisplayName("Monad Validations")
  class MonadValidations {

    @Test
    @DisplayName("flatMap should validate mapper is non-null")
    void flatMapShouldValidateMapperIsNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> monad.flatMap(null, validKind))
          .withMessageContaining("f for flatMap cannot be null");
    }

    @Test
    @DisplayName("flatMap should validate Kind is non-null")
    void flatMapShouldValidateKindIsNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> monad.flatMap(validFlatMapper, null))
          .withMessageContaining("Kind for flatMap cannot be null");
    }

    @Test
    @DisplayName("flatMap should throw if mapper returns null")
    void flatMapShouldThrowIfMapperReturnsNull() {
      Function<Integer, Kind<StateKind.Witness<Integer>, String>> nullReturningMapper = i -> null;
      Kind<StateKind.Witness<Integer>, String> result =
          monad.flatMap(nullReturningMapper, validKind);

      assertThatThrownBy(() -> runState(result, getInitialState()))
          .hasMessageContaining("Function f in flatMap returned null");
    }
  }

  @Nested
  @DisplayName("Exception Propagation (State-specific, on run)")
  class ExceptionPropagation {

    @Test
    @DisplayName("Functions throwing during run() propagate")
    void exceptionsThrownInRunPropagate() {
      // State is lazy, so exceptions are thrown during execution, not construction
      RuntimeException testException = new RuntimeException("Test exception");

      // Test map exception propagation
      Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };
      Kind<StateKind.Witness<Integer>, String> mappedResult = monad.map(throwingMapper, validKind);
      assertThatThrownBy(() -> runState(mappedResult, getInitialState()))
          .as("map should propagate function exceptions during execution")
          .isSameAs(testException);

      // Test flatMap exception propagation
      Function<Integer, Kind<StateKind.Witness<Integer>, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };
      Kind<StateKind.Witness<Integer>, String> flatMappedResult =
          monad.flatMap(throwingFlatMapper, validKind);
      assertThatThrownBy(() -> runState(flatMappedResult, getInitialState()))
          .as("flatMap should propagate function exceptions during execution")
          .isSameAs(testException);

      // Test ap exception propagation
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> throwingFuncKind =
          STATE.widen(
              State.of(
                  (Integer s) -> {
                    throw testException;
                  }));
      Kind<StateKind.Witness<Integer>, String> apResult = monad.ap(throwingFuncKind, validKind);
      assertThatThrownBy(() -> runState(apResult, getInitialState()))
          .as("ap should propagate exceptions during execution")
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Core Type Tests")
  class CoreTypeTests {

    @Test
    @DisplayName("Test State core operations")
    void testStateCoreOperations() {
      CoreTypeTest.<Integer, Integer>state(State.class)
          .withState(State.get())
          .withInitialState(getInitialState())
          .withMappers(validMapper)
          .testAll();
    }

    @Test
    @DisplayName("Test State factory methods")
    void testStateFactoryMethods() {
      CoreTypeTest.<Integer, String>state(State.class)
          .withState(State.pure("test"))
          .withInitialState(getInitialState())
          .withoutMappers()
          .onlyFactoryMethods()
          .testAll();
    }

    @Test
    @DisplayName("Test State KindHelper")
    void testStateKindHelper() {
      CoreTypeTest.stateKindHelper(State.pure(42)).test();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      State<Integer, Integer> start = State.of(s -> new StateTuple<>(1, s));
      Kind<StateKind.Witness<Integer>, Integer> result = STATE.widen(start);

      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      StateTuple<Integer, Integer> finalResult = runState(result, getInitialState());
      // 1 + 0 + 1 + 2 + ... + 9 = 1 + 45 = 46
      assertThatStateTuple(finalResult).hasValue(46).hasState(getInitialState());
    }

    @Test
    @DisplayName("State threading through computations")
    void stateThreadingThroughComputations() {
      State<Integer, Integer> getInitial = State.get();
      State<Integer, String> threadedState =
          getInitial
              .flatMap(s -> State.pure("value"))
              .flatMap(v -> State.set(getInitialState()))
              .flatMap(u -> State.get())
              .map(Object::toString);

      Kind<StateKind.Witness<Integer>, String> widened = STATE.widen(threadedState);
      StateTuple<Integer, String> result = runState(widened, getInitialState());

      assertThatStateTuple(result).hasState(getInitialState()).hasValueNonNull();
    }

    @Test
    @DisplayName("Pure leaves state unchanged")
    void pureLeavesStateUnchanged() {
      State<Integer, String> pureState = State.pure("test");
      StateTuple<Integer, String> result = pureState.run(getInitialState());

      assertThatStateTuple(result).hasState(getInitialState()).hasValue("test");
    }

    @Test
    @DisplayName("Get returns current state")
    void getReturnsCurrentState() {
      State<Integer, Integer> getState = State.get();
      StateTuple<Integer, Integer> result = getState.run(getInitialState());

      assertThatStateTuple(result).hasValue(getInitialState()).hasState(getInitialState());
    }

    @Test
    @DisplayName("StateTuple factory method works correctly")
    void stateTupleFactoryMethodWorks() {
      StateTuple<Integer, String> tuple = StateTuple.of(getInitialState(), "value");

      assertThatStateTuple(tuple).hasState(getInitialState()).hasValue("value");
    }
  }

  @Nested
  @DisplayName("Lazy Evaluation")
  class LazyEvaluation {

    @Test
    @DisplayName("map() propagates exceptions during execution")
    void mapPropagatesExceptionsDuringExecution() {
      RuntimeException testException = new RuntimeException("Test exception: map test");
      Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };

      Kind<StateKind.Witness<Integer>, String> result = monad.map(throwingMapper, validKind);

      // Exception is thrown when we run the state computation, not when constructing it
      assertThatThrownBy(() -> runState(result, getInitialState()))
          .as("map should propagate function exceptions during execution")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("flatMap() propagates exceptions during execution")
    void flatMapPropagatesExceptionsDuringExecution() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap test");
      Function<Integer, Kind<StateKind.Witness<Integer>, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };

      Kind<StateKind.Witness<Integer>, String> result =
          monad.flatMap(throwingFlatMapper, validKind);

      // Exception is thrown when we run the state computation, not when constructing it
      assertThatThrownBy(() -> runState(result, getInitialState()))
          .as("flatMap should propagate function exceptions during execution")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("ap() propagates exceptions during execution")
    void apPropagatesExceptionsDuringExecution() {
      RuntimeException testException = new RuntimeException("Test exception: ap test");

      Kind<StateKind.Witness<Integer>, Function<Integer, String>> throwingFuncKind =
          STATE.widen(
              State.of(
                  (Integer s) -> {
                    throw testException;
                  }));

      Kind<StateKind.Witness<Integer>, String> result = monad.ap(throwingFuncKind, validKind);

      assertThatThrownBy(() -> runState(result, getInitialState()))
          .as("ap should propagate exceptions during execution")
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("instance() factory")
  class InstanceFactory {

    @Test
    @DisplayName("returns a non-null, reused singleton across calls and state types")
    void returnsReusedSingleton() {
      StateMonad<Integer> a = StateMonad.instance();
      StateMonad<Integer> b = StateMonad.instance();
      StateMonad<String> other = StateMonad.instance();

      assertThat(a).isNotNull();
      assertThat(b).isSameAs(a);
      // StateMonad carries no per-S state, so one object serves every S.
      assertThat((Object) other).isSameAs(a);
    }

    @Test
    @DisplayName("is a fully functional Monad equivalent to the constructed instance")
    void isFunctionalAndEquivalent() {
      StateMonad<Integer> instanceMonad = StateMonad.instance();

      State<Integer, Integer> increment = State.of(s -> new StateTuple<>(s + 1, s + 1));
      Kind<StateKind.Witness<Integer>, Integer> incKind = STATE.widen(increment);

      Kind<StateKind.Witness<Integer>, String> viaInstance =
          instanceMonad.flatMap(i -> instanceMonad.of("v" + i), incKind);
      Kind<StateKind.Witness<Integer>, String> viaCtor =
          new StateMonad<Integer>().flatMap(i -> instanceMonad.of("v" + i), incKind);

      assertThatStateTuple(runState(viaInstance, getInitialState()))
          .hasValue("v11") // getInitialState() + 1
          .hasState(11);
      // parity with the public constructor
      assertThatStateTuple(runState(viaCtor, getInitialState())).hasValue("v11").hasState(11);
    }
  }
}
