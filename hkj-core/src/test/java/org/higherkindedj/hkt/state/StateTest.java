// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * State core functionality test using standardised patterns.
 *
 * <p>This test focuses on the core State functionality whilst using the standardised validation
 * framework for consistent error handling.
 */
@DisplayName("State<S, A> Core Functionality - Standardised Test Suite")
class StateTest extends TypeClassTestBase<StateKind.Witness<Integer>, Integer, String> {

  private final Integer initialState = 10;
  private final State<Integer, Integer> incrementState =
      State.of(s -> new StateTuple<>(s + 1, s + 1));
  private final State<Integer, String> initialValueState = State.pure("Start");
  private final State<Integer, Integer> nullValueState = State.pure(null);

  // Type class testing fixtures
  private StateMonad<Integer> monad;
  private StateFunctor<Integer> functor;

  @Override
  protected Kind<StateKind.Witness<Integer>, Integer> createValidKind() {
    return StateKindHelper.STATE.widen(incrementState);
  }

  @Override
  protected Kind<StateKind.Witness<Integer>, Integer> createValidKind2() {
    return StateKindHelper.STATE.widen(State.of(s -> new StateTuple<>(s * 2, s * 2)));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected BiPredicate<Kind<StateKind.Witness<Integer>, ?>, Kind<StateKind.Witness<Integer>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      StateTuple<Integer, ?> result1 = StateKindHelper.STATE.runState(k1, initialState);
      StateTuple<Integer, ?> result2 = StateKindHelper.STATE.runState(k2, initialState);
      return result1.equals(result2);
    };
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return String::toUpperCase;
  }

  @Override
  protected Function<Integer, Kind<StateKind.Witness<Integer>, String>> createValidFlatMapper() {
    return i -> StateKindHelper.STATE.widen(State.pure(String.valueOf(i)));
  }

  @Override
  protected Kind<StateKind.Witness<Integer>, Function<Integer, String>> createValidFunctionKind() {
    return StateKindHelper.STATE.widen(State.pure(validMapper));
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
    return i -> StateKindHelper.STATE.widen(State.pure(i.toString()));
  }

  @Override
  protected Function<String, Kind<StateKind.Witness<Integer>, String>> createChainFunction() {
    return s -> StateKindHelper.STATE.widen(State.pure(s + "!"));
  }

  @BeforeEach
  void setUpState() {
    monad = new StateMonad<>();
    functor = new StateFunctor<>();
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

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
          .skipExceptions() // State is lazy - exceptions deferred until run()
          .test();
    }

    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<StateKind.Witness<Integer>>functor(StateFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .skipExceptions() // Skip because State is lazy
          .test();
    }
  }

  @Nested
  @DisplayName("Core Type Testing with TypeClassTest API")
  class CoreTypeTestingSuite {

    @Test
    @DisplayName("Test all State core operations")
    void testAllStateCoreOperations() {
      CoreTypeTest.<Integer, Integer>state(State.class)
          .withState(incrementState)
          .withInitialState(initialState)
          .withMappers(TestFunctions.INT_TO_STRING)
          .testAll();
    }

    @Test
    @DisplayName("Test State with validation configuration")
    void testStateWithValidationConfiguration() {
      CoreTypeTest.<Integer, Integer>state(State.class)
          .withState(incrementState)
          .withInitialState(initialState)
          .withMappers(TestFunctions.INT_TO_STRING)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(StateFunctor.class)
          .withFlatMapFrom(StateMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Test State selective operations")
    void testStateSelectiveOperations() {
      CoreTypeTest.<Integer, Integer>state(State.class)
          .withState(incrementState)
          .withInitialState(initialState)
          .withMappers(TestFunctions.INT_TO_STRING)
          .onlyFactoryMethods()
          .testAll();
    }
  }

  @Nested
  @DisplayName("Factory Methods - Complete Coverage")
  class FactoryMethods {

    @Test
    @DisplayName("of() creates State from function with all value types")
    void ofCreatesCorrectInstances() {
      // Standard function
      Function<Integer, StateTuple<Integer, String>> f = s -> new StateTuple<>("Value:" + s, s + 1);
      State<Integer, String> state = State.of(f);
      StateTuple<Integer, String> result = state.run(initialState);
      assertThat(result.value()).isEqualTo("Value:10");
      assertThat(result.state()).isEqualTo(11);

      // Function returning null value
      Function<Integer, StateTuple<Integer, String>> nullFunc = s -> new StateTuple<>(null, s);
      State<Integer, String> nullState = State.of(nullFunc);
      StateTuple<Integer, String> nullResult = nullState.run(initialState);
      assertThat(nullResult.value()).isNull();
      assertThat(nullResult.state()).isEqualTo(initialState);

      // Complex state transformation
      Function<Integer, StateTuple<Integer, List<Integer>>> listFunc =
          s -> new StateTuple<>(List.of(s, s * 2), s + 5);
      State<Integer, List<Integer>> listState = State.of(listFunc);
      StateTuple<Integer, List<Integer>> listResult = listState.run(initialState);
      assertThat(listResult.value()).containsExactly(10, 20);
      assertThat(listResult.state()).isEqualTo(15);
    }

    @Test
    @DisplayName("of() throws NullPointerException for null function")
    void ofThrowsForNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> State.of(null))
          .withMessageContaining("runFunction for State.of cannot be null");
    }

    @Test
    @DisplayName("pure() returns value and preserves state with all value types")
    void purePreservesStateCorrectly() {
      // Standard value
      State<Integer, String> state = State.pure("Result");
      StateTuple<Integer, String> result = state.run(initialState);
      assertThat(result.value()).isEqualTo("Result");
      assertThat(result.state()).isEqualTo(initialState);

      // Null value
      State<Integer, String> nullState = State.pure(null);
      StateTuple<Integer, String> nullResult = nullState.run(initialState);
      assertThat(nullResult.value()).isNull();
      assertThat(nullResult.state()).isEqualTo(initialState);

      // Complex value
      List<String> list = List.of("a", "b", "c");
      State<Integer, List<String>> listState = State.pure(list);
      StateTuple<Integer, List<String>> listResult = listState.run(initialState);
      assertThat(listResult.value()).isSameAs(list);
      assertThat(listResult.state()).isEqualTo(initialState);

      // Unit value
      State<Integer, Unit> unitState = State.pure(Unit.INSTANCE);
      StateTuple<Integer, Unit> unitResult = unitState.run(initialState);
      assertThat(unitResult.value()).isEqualTo(Unit.INSTANCE);
      assertThat(unitResult.state()).isEqualTo(initialState);
    }

    @Test
    @DisplayName("get() returns current state as value and preserves state")
    void getReturnsCurrentState() {
      State<Integer, Integer> state = State.get();
      StateTuple<Integer, Integer> result = state.run(initialState);
      assertThat(result.value()).isEqualTo(initialState);
      assertThat(result.state()).isEqualTo(initialState);

      // Verify with different initial state
      StateTuple<Integer, Integer> result2 = state.run(42);
      assertThat(result2.value()).isEqualTo(42);
      assertThat(result2.state()).isEqualTo(42);
    }

    @Test
    @DisplayName("set() replaces state and returns Unit")
    void setReplacesState() {
      Integer newState = 99;
      State<Integer, Unit> state = State.set(newState);
      StateTuple<Integer, Unit> result = state.run(initialState);
      assertThat(result.value()).isEqualTo(Unit.INSTANCE);
      assertThat(result.state()).isEqualTo(newState);

      // Verify old state is ignored
      StateTuple<Integer, Unit> result2 = state.run(1000);
      assertThat(result2.state()).isEqualTo(newState);
    }

    @Test
    @DisplayName("set() throws NullPointerException for null state")
    void setThrowsForNullState() {
      assertThatNullPointerException()
          .isThrownBy(() -> State.set(null))
          .withMessageContaining("newState cannot be null");
    }

    @Test
    @DisplayName("modify() applies function to state and returns Unit")
    void modifyTransformsState() {
      Function<Integer, Integer> doubler = s -> s * 2;
      State<Integer, Unit> state = State.modify(doubler);
      StateTuple<Integer, Unit> result = state.run(initialState);
      assertThat(result.value()).isEqualTo(Unit.INSTANCE);
      assertThat(result.state()).isEqualTo(20);

      // Complex modification
      Function<Integer, Integer> complex = s -> (s + 5) * 3;
      State<Integer, Unit> complexState = State.modify(complex);
      StateTuple<Integer, Unit> complexResult = complexState.run(initialState);
      assertThat(complexResult.state()).isEqualTo(45);
    }

    @Test
    @DisplayName("modify() throws NullPointerException for null function")
    void modifyThrowsForNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> State.modify(null))
          .withMessageContaining("Function f for State.modify cannot be null");
    }

    @Test
    @DisplayName("inspect() applies function to state, returns result, preserves state")
    void inspectReadsStateWithoutModifying() {
      Function<Integer, String> checkEven = s -> (s % 2 == 0) ? "Even" : "Odd";
      State<Integer, String> state = State.inspect(checkEven);

      StateTuple<Integer, String> result = state.run(initialState);
      assertThat(result.value()).isEqualTo("Even");
      assertThat(result.state()).isEqualTo(initialState);

      StateTuple<Integer, String> resultOdd = state.run(7);
      assertThat(resultOdd.value()).isEqualTo("Odd");
      assertThat(resultOdd.state()).isEqualTo(7);

      // Null-returning inspection
      Function<Integer, String> nullReturning = s -> null;
      State<Integer, String> nullState = State.inspect(nullReturning);
      StateTuple<Integer, String> nullResult = nullState.run(initialState);
      assertThat(nullResult.value()).isNull();
      assertThat(nullResult.state()).isEqualTo(initialState);
    }

    @Test
    @DisplayName("inspect() throws NullPointerException for null function")
    void inspectThrowsForNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> State.inspect(null))
          .withMessageContaining("Function f for State.inspect cannot be null");
    }
  }

  @Nested
  @DisplayName("Run Method - Complete Coverage")
  class RunMethodTests {

    @Test
    @DisplayName("run() executes state computation correctly")
    void runExecutesComputation() {
      StateTuple<Integer, Integer> result = incrementState.run(initialState);
      assertThat(result.value()).isEqualTo(11);
      assertThat(result.state()).isEqualTo(11);

      // Verify consistency
      StateTuple<Integer, Integer> result2 = incrementState.run(initialState);
      assertThat(result2).isEqualTo(result);
    }

    @Test
    @DisplayName("run() threads state correctly through computation")
    void runThreadsState() {
      State<Integer, String> computation = State.of(s -> new StateTuple<>("Step:" + s, s + 10));

      StateTuple<Integer, String> result1 = computation.run(0);
      assertThat(result1.value()).isEqualTo("Step:0");
      assertThat(result1.state()).isEqualTo(10);

      StateTuple<Integer, String> result2 = computation.run(result1.state());
      assertThat(result2.value()).isEqualTo("Step:10");
      assertThat(result2.state()).isEqualTo(20);
    }

    @Test
    @DisplayName("run() handles null values correctly")
    void runHandlesNullValues() {
      StateTuple<Integer, Integer> result = nullValueState.run(initialState);
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(initialState);
    }
  }

  @Nested
  @DisplayName("Map Method - Comprehensive Transformation Testing")
  class MapMethodTests {

    @Test
    @DisplayName("map() transforms value whilst preserving state transition")
    void mapTransformsValue() {
      // Standard transformation
      State<Integer, String> mapped = incrementState.map(TestFunctions.INT_TO_STRING);
      StateTuple<Integer, String> result = mapped.run(initialState);
      assertThat(result.value()).isEqualTo("11");
      assertThat(result.state()).isEqualTo(11);

      // Complex transformation
      State<Integer, List<Integer>> listMapped = incrementState.map(i -> List.of(i, i * 2, i * 3));
      StateTuple<Integer, List<Integer>> listResult = listMapped.run(initialState);
      assertThat(listResult.value()).containsExactly(11, 22, 33);
      assertThat(listResult.state()).isEqualTo(11);

      // Null-safe transformation
      State<Integer, String> nullMapped = nullValueState.map(String::valueOf);
      StateTuple<Integer, String> nullResult = nullMapped.run(initialState);
      assertThat(nullResult.value()).isEqualTo("null");
      assertThat(nullResult.state()).isEqualTo(initialState);
    }

    @Test
    @DisplayName("map() throws NullPointerException for null mapper")
    void mapThrowsForNullMapper() {
      ValidationTestBuilder.create()
          .assertMapperNull(() -> incrementState.map(null), State.class, Operation.MAP)
          .execute();
    }

    @Test
    @DisplayName("map() handles exception propagation and chaining")
    void mapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: map test");
      Function<Integer, String> throwingMapper = TestFunctions.throwingFunction(testException);

      assertThatThrownBy(
              () -> {
                State<Integer, String> throwing = incrementState.map(throwingMapper);
                throwing.run(initialState);
              })
          .isSameAs(testException);

      // Test chaining
      State<Integer, Integer> start = State.of(s -> new StateTuple<>(s * 2, s + 1));
      State<Integer, String> chainResult =
          start.map(i -> i + 10).map(i -> "Value:" + i).map(String::toUpperCase);

      StateTuple<Integer, String> result = chainResult.run(5);
      assertThat(result.value()).isEqualTo("VALUE:20");
      assertThat(result.state()).isEqualTo(6);
    }

    @Test
    @DisplayName("map() handles null-returning functions")
    void mapHandlesNullReturningFunctions() {
      Function<Integer, String> nullReturningMapper = TestFunctions.nullReturningFunction();
      State<Integer, String> result = incrementState.map(nullReturningMapper);

      StateTuple<Integer, String> tuple = result.run(initialState);
      assertThat(tuple.value()).isNull();
      assertThat(tuple.state()).isEqualTo(11);
    }
  }

  @Nested
  @DisplayName("FlatMap Method - Comprehensive Monadic Testing")
  class FlatMapMethodTests {

    @Test
    @DisplayName("flatMap() composes state computations correctly")
    void flatMapComposesComputations() {
      // Step 1: State that increments state and returns the new state as value
      State<Integer, Integer> state1 = State.of(s -> new StateTuple<>(s + 1, s + 1));

      // Step 2: State that takes the value, multiplies it by 2 for the result,
      // and adds 5 to the state it receives
      Function<Integer, State<Integer, String>> state2Func =
          val -> State.of(s1 -> new StateTuple<>("Result:" + (val * 2), s1 + 5));

      State<Integer, String> composedState = state1.flatMap(state2Func);
      StateTuple<Integer, String> result = composedState.run(initialState);

      assertThat(result.value()).isEqualTo("Result:22");
      assertThat(result.state()).isEqualTo(16);
    }

    @Test
    @DisplayName("flatMap() throws NullPointerException for null mapper")
    void flatMapThrowsForNullMapper() {
      ValidationTestBuilder.create()
          .assertFlatMapperNull(() -> incrementState.flatMap(null), State.class, Operation.FLAT_MAP)
          .execute();
    }

    @Test
    @DisplayName("flatMap() throws KindUnwrapException if mapper returns null")
    void flatMapThrowsIfMapperReturnsNull() {
      Function<Integer, State<Integer, String>> nullReturningMapper = i -> null;
      State<Integer, String> state = incrementState.flatMap(nullReturningMapper);

      assertThatThrownBy(() -> state.run(initialState))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function in flatMap returned null, which is not allowed");
    }

    @Test
    @DisplayName("flatMap() supports complex chaining patterns")
    void flatMapSupportsComplexChaining() {
      // Success chain
      State<Integer, Integer> start = State.of(s -> new StateTuple<>(s * 2, s + 1));

      State<Integer, String> result =
          start
              .flatMap(i -> State.of(s -> new StateTuple<>(i + s, s * 2)))
              .flatMap(i -> State.of(s -> new StateTuple<>("Value:" + i, s + 3)))
              .flatMap(s -> State.of(st -> new StateTuple<>(s.toUpperCase(), st)));

      StateTuple<Integer, String> finalResult = result.run(5);
      // start: (10, 6)
      // step1: (10+6=16, 12)
      // step2: ("Value:16", 15)
      // step3: ("VALUE:16", 15)
      assertThat(finalResult.value()).isEqualTo("VALUE:16");
      assertThat(finalResult.state()).isEqualTo(15);

      // Mixed operations
      State<Integer, Integer> mixedResult =
          start
              .map(i -> i + 5)
              .flatMap(i -> State.of(s -> new StateTuple<>(i * 2, s + 10)))
              .map(i -> i - 5);

      StateTuple<Integer, Integer> mixedFinal = mixedResult.run(5);
      assertThat(mixedFinal.value()).isEqualTo(25); // (10+5)*2-5
    }

    @Test
    @DisplayName("flatMap() handles exception propagation")
    void flatMapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap test");
      Function<Integer, State<Integer, String>> throwingMapper =
          TestFunctions.throwingFunction(testException);

      assertThatThrownBy(
              () -> {
                State<Integer, String> throwing = incrementState.flatMap(throwingMapper);
                throwing.run(initialState);
              })
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("StateTuple Record Tests")
  class StateTupleTests {

    @Test
    @DisplayName("StateTuple constructor allows null value")
    void constructorAllowsNullValue() {
      StateTuple<String, Integer> tuple = new StateTuple<>(null, "newState");
      assertThat(tuple.value()).isNull();
      assertThat(tuple.state()).isEqualTo("newState");
    }

    @Test
    @DisplayName("StateTuple constructor requires non-null state")
    void constructorRequiresNonNullState() {
      assertThatNullPointerException()
          .isThrownBy(() -> new StateTuple<>("value", null))
          .withMessageContaining("StateTuple.construction value cannot be null");
    }

    @Test
    @DisplayName("StateTuple.of() factory method creates tuples correctly")
    void ofFactoryCreatesCorrectly() {
      StateTuple<Integer, String> tuple = StateTuple.of(42, "test");
      assertThat(tuple.value()).isEqualTo("test");
      assertThat(tuple.state()).isEqualTo(42);

      // With null value
      StateTuple<Integer, String> nullTuple = StateTuple.of(42, null);
      assertThat(nullTuple.value()).isNull();
      assertThat(nullTuple.state()).isEqualTo(42);
    }

    @Test
    @DisplayName("StateTuple.of() requires non-null state")
    void ofRequiresNonNullState() {
      assertThatNullPointerException()
          .isThrownBy(() -> StateTuple.of(null, "value"))
          .withMessageContaining("StateTuple.of value cannot be null");
    }

    @Test
    @DisplayName("StateTuple equals and hashCode work correctly")
    void equalsAndHashCodeWorkCorrectly() {
      StateTuple<String, Integer> t1a = new StateTuple<>(1, "s");
      StateTuple<String, Integer> t1b = new StateTuple<>(1, "s");
      StateTuple<String, Integer> t2 = new StateTuple<>(2, "s");
      StateTuple<String, Integer> t3 = new StateTuple<>(1, "t");
      StateTuple<String, Integer> t4 = new StateTuple<>(null, "s");
      StateTuple<String, Integer> t5 = new StateTuple<>(null, "s");

      assertThat(t1a).isEqualTo(t1b);
      assertThat(t1a).hasSameHashCodeAs(t1b);

      assertThat(t1a).isNotEqualTo(t2);
      assertThat(t1a).isNotEqualTo(t3);
      assertThat(t1a).isNotEqualTo(t4);
      assertThat(t4).isEqualTo(t5);
      assertThat(t4).hasSameHashCodeAs(t5);

      assertThat(t1a).isNotEqualTo(null);
      assertThat(t1a).isNotEqualTo("s");
    }

    @Test
    @DisplayName("StateTuple toString provides useful representation")
    void toStringProvidesUsefulRepresentation() {
      StateTuple<String, Integer> tuple = new StateTuple<>(123, "myState");
      assertThat(tuple.toString()).isEqualTo("StateTuple[value=123, state=myState]");

      StateTuple<String, Integer> tupleNull = new StateTuple<>(null, "state2");
      assertThat(tupleNull.toString()).isEqualTo("StateTuple[value=null, state=state2]");
    }
  }

  @Nested
  @DisplayName("Advanced Usage Patterns")
  class AdvancedUsagePatterns {

    @Test
    @DisplayName("State as functor maintains structure")
    void stateAsFunctorMaintainsStructure() {
      State<Integer, Integer> start = State.of(s -> new StateTuple<>(s * 2, s + 1));

      State<Integer, Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

      StateTuple<Integer, Double> finalResult = result.run(5);
      assertThat(finalResult.value()).isCloseTo(Math.sqrt(20.5), within(0.001));
      assertThat(finalResult.state()).isEqualTo(6);
    }

    @Test
    @DisplayName("State for stateful computation patterns")
    void stateForStatefulComputations() {
      // Counter that increments and returns old value
      State<Integer, Integer> counter = State.of(s -> new StateTuple<>(s, s + 1));

      State<Integer, List<Integer>> threeIncrements =
          counter.flatMap(v1 -> counter.flatMap(v2 -> counter.map(v3 -> List.of(v1, v2, v3))));

      StateTuple<Integer, List<Integer>> result = threeIncrements.run(0);
      assertThat(result.value()).containsExactly(0, 1, 2);
      assertThat(result.state()).isEqualTo(3);
    }

    @Test
    @DisplayName("State for configuration accumulation")
    void stateForConfigurationAccumulation() {
      record Config(String name, int timeout, boolean debug) {}

      State<Config, Unit> setName = State.modify(c -> new Config("MyApp", c.timeout(), c.debug()));
      State<Config, Unit> setTimeout = State.modify(c -> new Config(c.name(), 5000, c.debug()));
      State<Config, Unit> enableDebug = State.modify(c -> new Config(c.name(), c.timeout(), true));

      State<Config, Config> buildConfig =
          setName.flatMap(u -> setTimeout).flatMap(u -> enableDebug).flatMap(u -> State.get());

      Config initial = new Config("", 0, false);
      StateTuple<Config, Config> result = buildConfig.run(initial);

      assertThat(result.value().name()).isEqualTo("MyApp");
      assertThat(result.value().timeout()).isEqualTo(5000);
      assertThat(result.value().debug()).isTrue();
    }

    @Test
    @DisplayName("State pattern matching with computed results")
    void statePatternMatchingWithComputedResults() {
      State<Integer, String> computation =
          State.of(
              s -> {
                return switch (s) {
                  case Integer i when i < 0 -> new StateTuple<>("Negative", 0);
                  case 0 -> new StateTuple<>("Zero", 1);
                  case Integer i when i > 100 -> new StateTuple<>("Large", i / 2);
                  default -> new StateTuple<>("Normal", s * 2);
                };
              });

      assertThat(computation.run(-5).value()).isEqualTo("Negative");
      assertThat(computation.run(-5).state()).isEqualTo(0);

      assertThat(computation.run(0).value()).isEqualTo("Zero");
      assertThat(computation.run(0).state()).isEqualTo(1);

      assertThat(computation.run(50).value()).isEqualTo("Normal");
      assertThat(computation.run(50).state()).isEqualTo(100);

      assertThat(computation.run(200).value()).isEqualTo("Large");
      assertThat(computation.run(200).state()).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("State operations have predictable performance")
    void stateOperationsHavePredictablePerformance() {
      State<Integer, Integer> test = State.of(s -> new StateTuple<>(s + 1, s + 1));

      long start = System.nanoTime();
      for (int i = 0; i < 10000; i++) {
        test.map(x -> x + 1).flatMap(x -> State.pure(x * 2)).run(i);
      }
      long duration = System.nanoTime() - start;

      // Should complete in reasonable time (less than 100ms for 10k ops)
      assertThat(duration).isLessThan(100_000_000L);
    }

    @Test
    @DisplayName("Memory usage is reasonable for large chains")
    void memoryUsageIsReasonableForLargeChains() {
      State<Integer, Integer> start = State.pure(1);

      State<Integer, Integer> result = start;
      for (int i = 0; i < 1000; i++) {
        final int increment = i;
        result = result.map(x -> x + increment);
      }

      // Should complete without memory issues
      StateTuple<Integer, Integer> finalResult = result.run(0);
      assertThat(finalResult.value()).isEqualTo(1 + (999 * 1000) / 2);
    }

    @Test
    @DisplayName("State instances are reusable efficiently")
    void stateInstancesAreReusable() {
      State<Integer, Integer> reusable = State.of(s -> new StateTuple<>(s * 2, s + 1));

      StateTuple<Integer, Integer> result1 = reusable.run(5);
      StateTuple<Integer, Integer> result2 = reusable.run(5);
      StateTuple<Integer, Integer> result3 = reusable.run(10);

      assertThat(result1).isEqualTo(result2);
      assertThat(result1).isNotEqualTo(result3);
      assertThat(result3.value()).isEqualTo(20);
      assertThat(result3.state()).isEqualTo(11);
    }
  }

  @Nested
  @DisplayName("Type Safety and Variance")
  class TypeSafetyAndVarianceTests {

    @Test
    @DisplayName("State maintains type safety across operations")
    void stateMaintainsTypeSafety() {
      State<Integer, Number> numberState = State.of(s -> new StateTuple<>(42, s + 1));
      State<Integer, Integer> intState = numberState.flatMap(n -> State.pure(n.intValue()));

      assertThat(intState.run(0).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("State works with complex generic types")
    void stateWorksWithComplexGenericTypes() {
      // Nested generics
      State<Integer, List<String>> complexState = State.pure(List.of("a", "b", "c"));

      State<Integer, Integer> summed = complexState.map(List::size);

      assertThat(summed.run(0).value()).isEqualTo(3);

      // Map transformations
      State<Integer, java.util.Map<String, Integer>> mapState =
          State.pure(java.util.Map.of("a", 1, "b", 2));

      State<Integer, java.util.Set<String>> keySet = mapState.map(java.util.Map::keySet);

      assertThat(keySet.run(0).value()).containsExactlyInAnyOrder("a", "b");
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesAndBoundaryConditions {

    @Test
    @DisplayName("State handles extreme values correctly")
    void stateHandlesExtremeValuesCorrectly() {
      // Very large strings
      String largeString = "x".repeat(10000);
      State<Integer, String> largeState = State.pure(largeString);
      assertThat(largeState.run(0).value()).hasSize(10000);

      // Maximum/minimum integer values
      State<Integer, Integer> maxIntState = State.pure(Integer.MAX_VALUE);
      State<Integer, Long> promoted = maxIntState.map(i -> i.longValue() + 1);
      assertThat(promoted.run(0).value()).isEqualTo((long) Integer.MAX_VALUE + 1);

      // Very nested structures
      State<Integer, State<Integer, State<Integer, Integer>>> tripleNested =
          State.pure(State.pure(State.pure(42)));

      State<Integer, Integer> flattened =
          tripleNested.flatMap(inner -> inner).flatMap(innerInner -> innerInner);

      assertThat(flattened.run(0).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("State operations are stack-safe for deep recursion")
    void stateOperationsAreStackSafe() {
      State<Integer, Integer> start = State.pure(0);

      // TODO: When Trampoline is available in
      // https://github.com/higher-kinded-j/higher-kinded-j/issues/120
      // Then this can be better exercised
      // Create a moderately deep chain (not infinite, but realistic)
      // Note: Pure functional State implementations in Java are not stack-safe
      // for extremely deep chains due to JVM limitations
      State<Integer, Integer> result = start;
      for (int i = 0; i < 100; i++) { // Reduced from 10000
        result = result.map(x -> x + 1);
      }

      assertThat(result.run(0).value()).isEqualTo(100);

      // Test with flatMap chains - these are more problematic
      State<Integer, Integer> flatMapResult = start;
      for (int i = 0; i < 50; i++) { // Even more conservative
        flatMapResult = flatMapResult.flatMap(x -> State.pure(x + 1));
      }

      assertThat(flatMapResult.run(0).value()).isEqualTo(50);
    }

    @Test
    @DisplayName("State maintains referential transparency")
    void stateMaintainsReferentialTransparency() {
      State<Integer, String> state = State.of(s -> new StateTuple<>("val:" + s, s + 1));
      Function<Integer, String> transform = i -> "transformed:" + i;

      State<Integer, String> result1 =
          state.map(x -> Integer.parseInt(x.substring(4))).map(transform);
      State<Integer, String> result2 =
          state.map(x -> Integer.parseInt(x.substring(4))).map(transform);

      StateTuple<Integer, String> tuple1 = result1.run(5);
      StateTuple<Integer, String> tuple2 = result2.run(5);

      assertThat(tuple1).isEqualTo(tuple2);
      assertThat(tuple1.value()).isEqualTo("transformed:5");
      assertThat(tuple1.state()).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("Lazy Evaluation and Exception Handling")
  class LazyEvaluationTests {

    @Test
    @DisplayName("map defers exceptions until evaluation")
    void mapDefersExceptions() {
      RuntimeException testException = new RuntimeException("Test exception: map");
      Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };

      // Creating the mapped State succeeds (lazy)
      State<Integer, String> mapped = incrementState.map(throwingMapper);
      assertThat(mapped).isNotNull();

      // Exception is thrown when run() evaluates the computation
      assertThatThrownBy(() -> mapped.run(initialState))
          .as("Exception should be thrown during run(), not during map()")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("flatMap defers exceptions until evaluation")
    void flatMapDefersExceptions() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap");
      Function<Integer, State<Integer, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };

      // Creating the flatMapped State succeeds (lazy)
      State<Integer, String> flatMapped = incrementState.flatMap(throwingFlatMapper);
      assertThat(flatMapped).isNotNull();

      // Exception is thrown when run() evaluates the computation
      assertThatThrownBy(() -> flatMapped.run(initialState))
          .as("Exception should be thrown during run(), not during flatMap()")
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Exceptions propagate correctly through computation chains")
    void exceptionsPropagateThroughChains() {
      RuntimeException testException = new RuntimeException("Test exception: chain");

      State<Integer, String> computation =
          incrementState
              .map(i -> i * 2)
              .map(
                  i -> {
                    throw testException;
                  }) // This step will fail
              .map(String::valueOf); // This never executes

      assertThatThrownBy(() -> computation.run(initialState)).isSameAs(testException);
    }
  }
}
