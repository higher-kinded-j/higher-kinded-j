// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.state.StateAssert.assertThatStateTuple;
import static org.higherkindedj.hkt.state.StateKindHelper.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateKindHelper Tests")
class StateKindHelperTest extends StateTestBase<Integer> {

  // Simple Integer state for testing
  // Uses getInitialState() which returns 10 by default
  private final State<Integer, String> baseState =
      State.of((Integer s) -> new StateTuple<>("V:" + s, s + 1));

  // No need to override getInitialState() or getAlternativeState()
  // We use the defaults from StateTestBase

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
          .hasMessageContaining("Cannot narrow null Kind for %s".formatted("State"));
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
      StateTuple<Integer, String> result = runState(kind, getInitialState());

      assertThatStateTuple(result).hasValue("pureValue").hasState(getInitialState());
    }

    @Test
    @DisplayName("get should wrap State.get")
    void getShouldWrapStateGet() {
      Kind<StateKind.Witness<Integer>, Integer> kind = STATE.get();
      StateTuple<Integer, Integer> result = runState(kind, getInitialState());

      assertThatStateTuple(result).hasValue(getInitialState()).hasState(getInitialState());
    }

    @Test
    @DisplayName("set should wrap State.set")
    void setShouldWrapStateSet() {
      Integer newState = 555;
      Kind<StateKind.Witness<Integer>, Unit> kind = STATE.set(newState);
      StateTuple<Integer, Unit> result = runState(kind, getInitialState());

      assertThatStateTuple(result).hasValue(Unit.INSTANCE).hasState(newState);
    }

    @Test
    @DisplayName("modify should wrap State.modify")
    void modifyShouldWrapStateModify() {
      java.util.function.Function<Integer, Integer> tripler = s -> s * 3;
      Kind<StateKind.Witness<Integer>, Unit> kind = STATE.modify(tripler);
      StateTuple<Integer, Unit> result = runState(kind, getInitialState());

      assertThatStateTuple(result).hasValue(Unit.INSTANCE).hasState(30); // 10 * 3 = 30
    }

    @Test
    @DisplayName("inspect should wrap State.inspect")
    void inspectShouldWrapStateInspect() {
      java.util.function.Function<Integer, String> describe = s -> "State is " + s;
      Kind<StateKind.Witness<Integer>, String> kind = STATE.inspect(describe);
      StateTuple<Integer, String> result = runState(kind, getInitialState());

      assertThatStateTuple(result)
          .hasValue("State is 10") // getInitialState() = 10
          .hasState(getInitialState());
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
      StateTuple<Integer, String> result = runState(kind, getInitialState());

      assertThatStateTuple(result)
          .hasValue("V:10") // getInitialState() = 10
          .hasState(11); // 10 + 1 = 11
    }

    @Test
    @DisplayName("evalState should execute and return value")
    void evalStateShouldExecuteAndReturnValue() {
      String value = evalState(kind, getInitialState());
      assertThat(value).isEqualTo("V:10");
    }

    @Test
    @DisplayName("execState should execute and return state")
    void execStateShouldExecuteAndReturnState() {
      Integer finalState = execState(kind, getInitialState());
      assertThat(finalState).isEqualTo(11); // 10 + 1 = 11
    }

    @Test
    @DisplayName("runState should throw if Kind is invalid")
    void runStateShouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> runState(null, getInitialState()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("evalState should throw if Kind is invalid")
    void evalStateShouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> evalState(null, getInitialState()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("execState should throw if Kind is invalid")
    void execStateShouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> execState(null, getInitialState()))
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
    @DisplayName("Test KindHelper with selective tests")
    void testKindHelperWithSelectiveTests() {
      State<Integer, String> testState = State.pure("selective");

      CoreTypeTest.stateKindHelper(testState).skipValidations().skipEdgeCases().test();
    }
  }
}
