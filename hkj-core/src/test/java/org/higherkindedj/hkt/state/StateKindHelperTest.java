// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.state.StateKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.unit.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateKindHelper Tests")
class StateKindHelperTest {

  // Simple Integer state for testing
  private final Integer initialState = 100;
  // Explicitly type lambda parameter 's' to ensure it's Integer
  private final State<Integer, String> baseState =
      State.of((Integer s) -> new StateTuple<>("V:" + s, s + 1));

  @Nested
  @DisplayName("STATE.widen()")
  class WrapTests {
    @Test
    void widen_shouldReturnHolderForState() {
      // Corrected Kind declaration
      Kind<StateKind.Witness<Integer>, String> kind = STATE.widen(baseState);
      assertThat(kind).isInstanceOf(StateHolder.class);
      // Unwrap to verify - STATE.narrow( expects Kind<StateKind.Witness, A>
      assertThat(STATE.narrow(kind)).isSameAs(baseState);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> STATE.widen(null))
          .withMessageContaining("Input State cannot be null");
    }
  }

  @Nested
  @DisplayName("STATE.narrow()")
  class UnwrapTests {
    @Test
    void narrow_shouldReturnOriginalState() {
      // Corrected Kind declaration
      Kind<StateKind.Witness<Integer>, String> kind = STATE.widen(baseState);
      assertThat(STATE.narrow(kind)).isSameAs(baseState);
    }

    // Dummy Kind implementation - for its use, F should be StateKind.Witness
    // The original DummyStateKind<S, A>() implements Kind<StateKind<S, ?>, A>
    // This should be Kind<StateKind.Witness, A> if it's to be used with State HKTs.
    // However, the test is for STATE.narrow('s behavior with non-StateHolder Kinds.
    // For the specific test STATE.narrow(_shouldThrowForUnknownKindType,
    // the goal is to pass something that is *not* a StateHolder but *is* a Kind<StateKind.Witness,
    // A>.
    // Let's make a more direct dummy that fits what STATE.narrow( expects as a general Kind for the
    // witness.
    record DummyNonStateHolderKind<A>() implements Kind<StateKind.Witness<A>, A> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> STATE.narrow(null)) // Pass null directly
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      // This dummy kind needs to be Kind<StateKind.Witness, String> to be passed to unwrap
      // but not be a StateHolder.
      Kind<StateKind.Witness<String>, String> unknownKind = new DummyNonStateHolderKind<>();
      assertThatThrownBy(() -> STATE.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyNonStateHolderKind.class.getName());
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    void pure_shouldWrapStatePure() {
      // StateKindHelper.pure returns StateKind<S,A> which is Kind<StateKind.Witness, A>
      Kind<StateKind.Witness<Integer>, String> kind = STATE.pure("pureValue");
      // runState expects Kind<StateKind.Witness<S>, A>
      StateTuple<Integer, String> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo("pureValue");
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    void get_shouldWrapStateGet() {
      Kind<StateKind.Witness<Integer>, Integer> kind = STATE.get();
      StateTuple<Integer, Integer> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo(initialState);
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    void set_shouldWrapStateSet() {
      Integer newState = 555;
      Kind<StateKind.Witness<Integer>, Unit> kind = STATE.set(newState);
      StateTuple<Integer, Unit> result = STATE.runState(kind, initialState);
      assertThat(result.value()).isEqualTo(Unit.INSTANCE);
      assertThat(result.state()).isEqualTo(newState);
    }

    @Test
    void modify_shouldWrapStateModify() {
      Function<Integer, Integer> tripler = s -> s * 3;
      Kind<StateKind.Witness<Integer>, Unit> kind = STATE.modify(tripler);
      StateTuple<Integer, Unit> result = STATE.runState(kind, initialState); // 100
      assertThat(result.value()).isEqualTo(Unit.INSTANCE);
      assertThat(result.state()).isEqualTo(300);
    }

    @Test
    void inspect_shouldWrapStateInspect() {
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
    // Corrected Kind declaration; baseState already has explicitly typed lambda
    final Kind<StateKind.Witness<Integer>, String> kind =
        STATE.widen(baseState); // ("V:" + s, s + 1)

    @Test
    void runState_shouldExecuteAndReturnTuple() {
      StateTuple<Integer, String> result = STATE.runState(kind, initialState); // 100
      assertThat(result.value()).isEqualTo("V:100");
      assertThat(result.state()).isEqualTo(101);
    }

    @Test
    void evalState_shouldExecuteAndReturnValue() {
      String value = STATE.evalState(kind, initialState); // 100
      assertThat(value).isEqualTo("V:100");
    }

    @Test
    void execState_shouldExecuteAndReturnState() {
      Integer finalState = STATE.execState(kind, initialState); // 100
      assertThat(finalState).isEqualTo(101);
    }

    @Test
    void runState_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> STATE.runState(null, initialState)) // Pass null directly
          .isInstanceOf(KindUnwrapException.class); // Propagates STATE.narrow( exception
    }

    @Test
    void evalState_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> STATE.evalState(null, initialState)) // Pass null directly
          .isInstanceOf(KindUnwrapException.class);
    }

    @Test
    void execState_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> STATE.execState(null, initialState)) // Pass null directly
          .isInstanceOf(KindUnwrapException.class);
    }
  }
}
