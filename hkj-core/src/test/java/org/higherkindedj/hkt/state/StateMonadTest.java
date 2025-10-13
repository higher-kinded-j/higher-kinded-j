// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.higherkindedj.hkt.unit.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateMonad Complete Test Suite")
class StateMonadTest extends TypeClassTestBase<StateKind.Witness<Integer>, Integer, String> {

  private StateMonad<Integer> monad;
  private final Integer initialState = 0;

  @Override
  protected Kind<StateKind.Witness<Integer>, Integer> createValidKind() {
    return STATE.widen(State.of(s -> new StateTuple<>(s + 1, s + 1)));
  }

  @Override
  protected Kind<StateKind.Witness<Integer>, Integer> createValidKind2() {
    return STATE.widen(State.of(s -> new StateTuple<>(s * 2, s + 5)));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<StateKind.Witness<Integer>, String>> createValidFlatMapper() {
    return i -> STATE.widen(State.of(s -> new StateTuple<>("flat:" + i, s + i)));
  }

  @Override
  protected Kind<StateKind.Witness<Integer>, Function<Integer, String>> createValidFunctionKind() {
    return STATE.widen(State.of(s -> new StateTuple<>(TestFunctions.INT_TO_STRING, s + 1)));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return 5;
  }

  @Override
  protected Function<Integer, Kind<StateKind.Witness<Integer>, String>> createTestFunction() {
    return i -> STATE.widen(State.of(s -> new StateTuple<>("v" + i, s + i)));
  }

  @Override
  protected Function<String, Kind<StateKind.Witness<Integer>, String>> createChainFunction() {
    return str -> STATE.widen(State.of(s -> new StateTuple<>(str + "!", s + str.length())));
  }

  @Override
  protected BiPredicate<Kind<StateKind.Witness<Integer>, ?>, Kind<StateKind.Witness<Integer>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      StateTuple<Integer, ?> res1 = STATE.runState(k1, initialState);
      StateTuple<Integer, ?> res2 = STATE.runState(k2, initialState);
      return res1.equals(res2);
    };
  }

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
      StateTuple<Integer, String> result = STATE.runState(kind, initialState);

      assertThat(result.value()).isEqualTo("constantValue");
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    @DisplayName("of() allows null value")
    void ofAllowsNullValue() {
      Kind<StateKind.Witness<Integer>, String> kind = monad.of(null);
      StateTuple<Integer, String> result = STATE.runState(kind, initialState);

      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    @DisplayName("map() applies function to result value and keeps state transition")
    void mapAppliesFunctionToResultValue() {
      Kind<StateKind.Witness<Integer>, Integer> incKind =
          STATE.widen(State.of((Integer s) -> new StateTuple<>(s + 1, s + 1)));

      Kind<StateKind.Witness<Integer>, String> mappedKind = monad.map(i -> "Val:" + i, incKind);

      StateTuple<Integer, String> result = STATE.runState(mappedKind, initialState);
      assertThat(result.value()).isEqualTo("Val:1");
      assertThat(result.state()).isEqualTo(1);
    }

    @Test
    @DisplayName("map() chains functions correctly")
    void mapChainsFunctions() {
      Kind<StateKind.Witness<Integer>, Integer> initialKind =
          STATE.widen(State.of((Integer s) -> new StateTuple<>(s * 2, s + 5)));

      Kind<StateKind.Witness<Integer>, String> mappedKind =
          monad.map(value -> "Str:" + value, monad.map(val -> val / 2.0, initialKind));

      StateTuple<Integer, String> result = STATE.runState(mappedKind, 10);
      assertThat(result.value()).isEqualTo("Str:10.0");
      assertThat(result.state()).isEqualTo(15);
    }

    @Test
    @DisplayName("ap() applies State function to State value")
    void apAppliesStateFunctionToStateValue() {
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> funcKind =
          STATE.widen(State.of((Integer s) -> new StateTuple<>(i -> "F" + i + s, s + 1)));

      Kind<StateKind.Witness<Integer>, Integer> valKind =
          STATE.widen(State.of((Integer s) -> new StateTuple<>(s * 10, s + 2)));

      Kind<StateKind.Witness<Integer>, String> resultKind = monad.ap(funcKind, valKind);

      StateTuple<Integer, String> result = STATE.runState(resultKind, 10);

      assertThat(result.value()).isEqualTo("F11010");
      assertThat(result.state()).isEqualTo(13);
    }

    @Test
    @DisplayName("ap() works with pure function and value")
    void apWorksWithPureFunctionAndValue() {
      Kind<StateKind.Witness<Integer>, Function<Integer, String>> funcKind =
          monad.of(i -> "Num" + i);
      Kind<StateKind.Witness<Integer>, Integer> valKind = monad.of(100);
      Kind<StateKind.Witness<Integer>, String> resultKind = monad.ap(funcKind, valKind);

      StateTuple<Integer, String> result = STATE.runState(resultKind, 5);
      assertThat(result.value()).isEqualTo("Num100");
      assertThat(result.state()).isEqualTo(5);
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
                      (Integer currentState) ->
                          new StateTuple<>("Val:" + (originalStateValue * 2), currentState + 10)));

      Kind<StateKind.Witness<Integer>, String> resultKind =
          monad.flatMap(processValueAndAdd10, getStateAndInc);

      StateTuple<Integer, String> result = STATE.runState(resultKind, 10);
      assertThat(result.value()).isEqualTo("Val:20");
      assertThat(result.state()).isEqualTo(21);
    }

    @Test
    @DisplayName("map2() combines State and values")
    void map2CombinesStateAndValues() {
      Kind<StateKind.Witness<Integer>, Integer> st1 =
          STATE.widen(State.of((Integer s) -> new StateTuple<>(s + 1, s + 1)));
      Kind<StateKind.Witness<Integer>, String> st2 =
          STATE.widen(State.of((Integer s) -> new StateTuple<>("S" + s, s * 2)));

      Kind<StateKind.Witness<Integer>, String> result =
          monad.map2(st1, st2, (i, sVal) -> i + ":" + sVal);

      StateTuple<Integer, String> res = STATE.runState(result, 10);
      assertThat(res.value()).isEqualTo("11:S11");
      assertThat(res.state()).isEqualTo(22);
    }
  }

  @Nested
  @DisplayName("Individual Components")
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
      assertThatThrownBy(() -> STATE.runState(mappedResult, initialState))
          .as("map should propagate function exceptions during execution")
          .isSameAs(testException);

      // Test flatMap exception propagation
      Function<Integer, Kind<StateKind.Witness<Integer>, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };
      Kind<StateKind.Witness<Integer>, String> flatMappedResult =
          monad.flatMap(throwingFlatMapper, validKind);
      assertThatThrownBy(() -> STATE.runState(flatMappedResult, initialState))
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
      assertThatThrownBy(() -> STATE.runState(apResult, initialState))
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
  @DisplayName("Core Type Tests")
  class CoreTypeTests {

    @Test
    @DisplayName("Test State core operations")
    void testStateCoreOperations() {
      CoreTypeTest.<Integer, Integer>state(State.class)
          .withState(State.get())
          .withInitialState(initialState)
          .withMappers(validMapper)
          .testAll();
    }

    @Test
    @DisplayName("Test State factory methods")
    void testStateFactoryMethods() {
      CoreTypeTest.<Integer, String>state(State.class)
          .withState(State.pure("test"))
          .withInitialState(initialState)
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
      Kind<StateKind.Witness<Integer>, Integer> start =
          STATE.widen(State.of(s -> new StateTuple<>(1, s)));

      Kind<StateKind.Witness<Integer>, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      StateTuple<Integer, Integer> finalResult = STATE.runState(result, initialState);
      assertThat(finalResult.value()).isEqualTo(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("State threading through computations")
    void stateThreadingThroughComputations() {
      State<Integer, Integer> getInitial = State.get();
      State<Integer, String> threadedState =
          getInitial
              .flatMap(s -> State.pure("value"))
              .flatMap(v -> State.set(initialState))
              .flatMap(u -> State.get())
              .map(Object::toString);

      Kind<StateKind.Witness<Integer>, String> widened = STATE.widen(threadedState);
      StateTuple<Integer, String> result = STATE.runState(widened, 10);

      assertThat(result.state()).isEqualTo(initialState);
      assertThat(result.value()).isNotNull();
    }

    @Test
    @DisplayName("Pure leaves state unchanged")
    void pureLeavesStateUnchanged() {
      State<Integer, String> pureState = State.pure("test");
      StateTuple<Integer, String> result = pureState.run(initialState);

      assertThat(result.state()).isSameAs(initialState);
      assertThat(result.value()).isEqualTo("test");
    }

    @Test
    @DisplayName("Get returns current state")
    void getReturnsCurrentState() {
      State<Integer, Integer> getState = State.get();
      StateTuple<Integer, Integer> result = getState.run(initialState);

      assertThat(result.value()).isSameAs(initialState);
      assertThat(result.state()).isSameAs(initialState);
    }

    @Test
    @DisplayName("StateTuple factory method works correctly")
    void stateTupleFactoryMethodWorks() {
      StateTuple<Integer, String> tuple = StateTuple.of(initialState, "value");

      assertThat(tuple.state()).isSameAs(initialState);
      assertThat(tuple.value()).isEqualTo("value");
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("FlatMap efficient with many operations")
    void flatMapEfficientWithManyOperations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<StateKind.Witness<Integer>, Integer> start =
            STATE.widen(State.of(s -> new StateTuple<>(1, s)));

        Kind<StateKind.Witness<Integer>, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int increment = i;
          result = monad.flatMap(x -> monad.of(x + increment), result);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        StateTuple<Integer, Integer> finalResult = STATE.runState(result, initialState);
        assertThat(finalResult.value()).isEqualTo(expectedSum);
      }
    }
  }

  @Nested
  @DisplayName("Exception Tests")
  class ExceptionTests {

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
      assertThatThrownBy(() -> STATE.runState(result, initialState))
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
      assertThatThrownBy(() -> STATE.runState(result, initialState))
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

      assertThatThrownBy(() -> STATE.runState(result, initialState))
          .as("ap should propagate exceptions during execution")
          .isSameAs(testException);
    }
  }
}
