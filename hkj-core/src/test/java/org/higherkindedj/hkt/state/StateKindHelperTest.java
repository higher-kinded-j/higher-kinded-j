// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.state.StateKindHelper.*;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_KIND_TEMPLATE;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.unit.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateKindHelper Tests")
class StateKindHelperTest extends TypeClassTestBase<StateKind.Witness<Integer>, Integer, String> {

  // Simple Integer state for testing
  private final Integer initialState = 100;
  // Explicitly type lambda parameter 's' to ensure it's Integer
  private final State<Integer, String> baseState =
      State.of((Integer s) -> new StateTuple<>("V:" + s, s + 1));

  @Override
  protected Kind<StateKind.Witness<Integer>, Integer> createValidKind() {
    return STATE.widen(State.of((Integer s) -> new StateTuple<>(s + 1, s + 1)));
  }

  @Override
  protected Kind<StateKind.Witness<Integer>, Integer> createValidKind2() {
    return STATE.widen(State.of((Integer s) -> new StateTuple<>(s * 2, s * 2)));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
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

  @Override
  protected Function<String, String> createSecondMapper() {
    return String::toUpperCase;
  }

  @Override
  protected Function<Integer, Kind<StateKind.Witness<Integer>, String>> createValidFlatMapper() {
    return i -> STATE.widen(State.pure(String.valueOf(i)));
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
  protected Function<String, Kind<StateKind.Witness<Integer>, String>> createChainFunction() {
    return s -> STATE.widen(State.pure(s + "!"));
  }

  @Nested
  @DisplayName("STATE.widen()")
  class WidenTests {
    @Test
    @DisplayName("widen should return holder for State")
    void widenShouldReturnHolderForState() {
      Kind<StateKind.Witness<Integer>, String> kind = STATE.widen(baseState);
      assertThat(kind).isInstanceOf(StateKindHelper.StateHolder.class);
      // Unwrap to verify
      assertThat(STATE.narrow(kind)).isSameAs(baseState);
    }

    @Test
    @DisplayName("widen should throw for null input")
    void widenShouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> STATE.widen(null))
          .withMessageContaining("Input State cannot be null");
    }
  }

  @Nested
  @DisplayName("STATE.narrow()")
  class NarrowTests {
    @Test
    @DisplayName("narrow should return original State")
    void narrowShouldReturnOriginalState() {
      Kind<StateKind.Witness<Integer>, String> kind = STATE.widen(baseState);
      assertThat(STATE.narrow(kind)).isSameAs(baseState);
    }

    // Dummy Kind implementation for testing invalid types
    record DummyNonStateHolderKind<A>() implements Kind<StateKind.Witness<A>, A> {}

    @Test
    @DisplayName("narrow should throw for null input")
    void narrowShouldThrowForNullInput() {
      assertThatThrownBy(() -> STATE.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(String.format(NULL_KIND_TEMPLATE, "State"));
    }

    @Test
    @DisplayName("narrow should throw for unknown Kind type")
    void narrowShouldThrowForUnknownKindType() {
      Kind<StateKind.Witness<String>, String> unknownKind = new DummyNonStateHolderKind<>();
      assertThatThrownBy(() -> STATE.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Kind instance cannot be narrowed to " + State.class.getSimpleName());
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    @DisplayName("pure should wrap State.pure")
    void pureShouldWrapStatePure() {
      Kind<StateKind.Witness<Integer>, String> kind = STATE.pure("pureValue");
      StateTuple<Integer, String> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo("pureValue");
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    @DisplayName("get should wrap State.get")
    void getShouldWrapStateGet() {
      Kind<StateKind.Witness<Integer>, Integer> kind = STATE.get();
      StateTuple<Integer, Integer> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo(initialState);
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    @DisplayName("set should wrap State.set")
    void setShouldWrapStateSet() {
      Integer newState = 555;
      Kind<StateKind.Witness<Integer>, Unit> kind = STATE.set(newState);
      StateTuple<Integer, Unit> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo(Unit.INSTANCE);
      assertThat(result.state()).isEqualTo(newState);
    }

    @Test
    @DisplayName("modify should wrap State.modify")
    void modifyShouldWrapStateModify() {
      Function<Integer, Integer> tripler = s -> s * 3;
      Kind<StateKind.Witness<Integer>, Unit> kind = STATE.modify(tripler);
      StateTuple<Integer, Unit> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo(Unit.INSTANCE);
      assertThat(result.state()).isEqualTo(300);
    }

    @Test
    @DisplayName("inspect should wrap State.inspect")
    void inspectShouldWrapStateInspect() {
      Function<Integer, String> describe = s -> "State is " + s;
      Kind<StateKind.Witness<Integer>, String> kind = STATE.inspect(describe);
      StateTuple<Integer, String> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo("State is 100");
      assertThat(result.state()).isEqualTo(initialState);
    }
  }

  @Nested
  @DisplayName("Run/Eval/Exec Helpers")
  class RunEvalExecTests {
    final Kind<StateKind.Witness<Integer>, String> kind =
        STATE.widen(baseState); // ("V:" + s, s + 1)

    @Test
    @DisplayName("runState should execute and return tuple")
    void runStateShouldExecuteAndReturnTuple() {
      StateTuple<Integer, String> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo("V:100");
      assertThat(result.state()).isEqualTo(101);
    }

    @Test
    @DisplayName("evalState should execute and return value")
    void evalStateShouldExecuteAndReturnValue() {
      String value = STATE.evalState(kind, initialState);
      assertThat(value).isEqualTo("V:100");
    }

    @Test
    @DisplayName("execState should execute and return state")
    void execStateShouldExecuteAndReturnState() {
      Integer finalState = STATE.execState(kind, initialState);
      assertThat(finalState).isEqualTo(101);
    }

    @Test
    @DisplayName("runState should throw if Kind is invalid")
    void runStateShouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> STATE.runState(null, initialState))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("evalState should throw if Kind is invalid")
    void evalStateShouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> STATE.evalState(null, initialState))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("execState should throw if Kind is invalid")
    void execStateShouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> STATE.execState(null, initialState))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("CoreTypeTest Integration")
  class CoreTypeTestIntegration {

    @Test
    @DisplayName("Test KindHelper using CoreTypeTest API")
    void testKindHelperUsingCoreTypeTestApi() {
      State<Integer, String> testState = State.pure("test");

      CoreTypeTest.stateKindHelper(testState).test();
    }

    @Test
    @DisplayName("Test KindHelper with performance tests")
    void testKindHelperWithPerformanceTests() {
      State<Integer, Integer> testState = State.of(s -> new StateTuple<>(s * 2, s + 1));

      CoreTypeTest.stateKindHelper(testState).withPerformanceTests().test();
    }

    @Test
    @DisplayName("Test KindHelper with selective tests")
    void testKindHelperWithSelectiveTests() {
      State<Integer, String> testState = State.pure("selective");

      CoreTypeTest.stateKindHelper(testState).skipValidations().skipEdgeCases().test();
    }
  }
}
