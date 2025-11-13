// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.state.StateAssert.assertThatStateTuple;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateMonad<S> Complete Test Suite")
class StateMonadTest extends StateTestBase<Integer> {

  private StateMonad<Integer> monad;

  @BeforeEach
  void setUpMonad() {
    monad = new StateMonad<>();
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<StateKind.Witness<Integer>>monad(StateMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(StateFunctor.class)
          .withApFrom(StateApplicative.class)
          .withFlatMapFrom(StateMonad.class)
          .selectTests()
          .skipExceptions() // Skip because State is lazy
          .test();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(StateMonadTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
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
          .withMessageContaining("Function f for StateMonad.flatMap cannot be null");
    }

    @Test
    @DisplayName("flatMap should validate Kind is non-null")
    void flatMapShouldValidateKindIsNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> monad.flatMap(validFlatMapper, null))
          .withMessageContaining("Kind for StateMonad.flatMap cannot be null");
    }

    @Test
    @DisplayName("flatMap should throw if mapper returns null")
    void flatMapShouldThrowIfMapperReturnsNull() {
      Function<Integer, Kind<StateKind.Witness<Integer>, String>> nullReturningMapper = i -> null;
      Kind<StateKind.Witness<Integer>, String> result =
          monad.flatMap(nullReturningMapper, validKind);

      assertThatThrownBy(() -> runState(result, getInitialState()))
          .hasMessageContaining("Function f in StateMonad.flatMap returned null");
    }
  }

  @Nested
  @DisplayName("Individual Test Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<StateKind.Witness<Integer>>monad(StateMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<StateKind.Witness<Integer>>monad(StateMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(StateFunctor.class)
          .withApFrom(StateApplicative.class)
          .withFlatMapFrom(StateMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
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

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<StateKind.Witness<Integer>>monad(StateMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    @Test
    @DisplayName("Left identity: flatMap(of(a), f) == f(a)")
    void leftIdentityLaw() {
      Integer value = testValue;
      Kind<StateKind.Witness<Integer>, Integer> ofValue = monad.of(value);
      Kind<StateKind.Witness<Integer>, String> leftSide = monad.flatMap(testFunction, ofValue);
      Kind<StateKind.Witness<Integer>, String> rightSide = testFunction.apply(value);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Monad Left Identity: flatMap(of(a), f) == f(a)")
          .isTrue();
    }

    @Test
    @DisplayName("Right identity: flatMap(m, of) == m")
    void rightIdentityLaw() {
      Function<Integer, Kind<StateKind.Witness<Integer>, Integer>> ofFunction = a -> monad.of(a);
      Kind<StateKind.Witness<Integer>, Integer> leftSide = monad.flatMap(ofFunction, validKind);

      assertThat(equalityChecker.test(leftSide, validKind))
          .as("Monad Right Identity: flatMap(m, of) == m")
          .isTrue();
    }

    @Test
    @DisplayName("Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativityLaw() {
      // Left side: flatMap(flatMap(m, f), g)
      Kind<StateKind.Witness<Integer>, String> intermediate =
          monad.flatMap(testFunction, validKind);
      Kind<StateKind.Witness<Integer>, String> leftSide =
          monad.flatMap(chainFunction, intermediate);

      // Right side: flatMap(m, a -> flatMap(f(a), g))
      Function<Integer, Kind<StateKind.Witness<Integer>, String>> combined =
          a -> monad.flatMap(chainFunction, testFunction.apply(a));
      Kind<StateKind.Witness<Integer>, String> rightSide = monad.flatMap(combined, validKind);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Monad Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
          .isTrue();
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
}
