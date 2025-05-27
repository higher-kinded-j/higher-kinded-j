// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.state.StateKindHelper.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
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
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForState() {
      // Corrected Kind declaration
      Kind<StateKind.Witness<Integer>, String> kind = wrap(baseState);
      assertThat(kind).isInstanceOf(StateHolder.class);
      // Unwrap to verify - unwrap expects Kind<StateKind.Witness, A>
      assertThat(unwrap(kind)).isSameAs(baseState);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input State cannot be null");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_shouldReturnOriginalState() {
      // Corrected Kind declaration
      Kind<StateKind.Witness<Integer>, String> kind = wrap(baseState);
      assertThat(unwrap(kind)).isSameAs(baseState);
    }

    // Dummy Kind implementation - for its use, F should be StateKind.Witness
    // The original DummyStateKind<S, A>() implements Kind<StateKind<S, ?>, A>
    // This should be Kind<StateKind.Witness, A> if it's to be used with State HKTs.
    // However, the test is for unwrap's behavior with non-StateHolder Kinds.
    // For the specific test unwrap_shouldThrowForUnknownKindType,
    // the goal is to pass something that is *not* a StateHolder but *is* a Kind<StateKind.Witness,
    // A>.
    // Let's make a more direct dummy that fits what unwrap expects as a general Kind for the
    // witness.
    record DummyNonStateHolderKind<A>() implements Kind<StateKind.Witness<A>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null)) // Pass null directly
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      // This dummy kind needs to be Kind<StateKind.Witness, String> to be passed to unwrap
      // but not be a StateHolder.
      Kind<StateKind.Witness<String>, String> unknownKind = new DummyNonStateHolderKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
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
      Kind<StateKind.Witness<Integer>, String> kind = StateKindHelper.pure("pureValue");
      // runState expects Kind<StateKind.Witness<S>, A>
      StateTuple<Integer, String> result = runState(kind, initialState);
      assertThat(result.value()).isEqualTo("pureValue");
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    void get_shouldWrapStateGet() {
      Kind<StateKind.Witness<Integer>, Integer> kind = StateKindHelper.get();
      StateTuple<Integer, Integer> result = runState(kind, initialState);
      assertThat(result.value()).isEqualTo(initialState);
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    void set_shouldWrapStateSet() {
      Integer newState = 555;
      Kind<StateKind.Witness<Integer>, Void> kind = StateKindHelper.set(newState);
      StateTuple<Integer, Void> result = runState(kind, initialState);
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(newState);
    }

    @Test
    void modify_shouldWrapStateModify() {
      Function<Integer, Integer> tripler = s -> s * 3;
      Kind<StateKind.Witness<Integer>, Void> kind = StateKindHelper.modify(tripler);
      StateTuple<Integer, Void> result = runState(kind, initialState); // 100
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(300);
    }

    @Test
    void inspect_shouldWrapStateInspect() {
      Function<Integer, String> describe = s -> "State is " + s;
      Kind<StateKind.Witness<Integer>, String> kind = StateKindHelper.inspect(describe);
      StateTuple<Integer, String> result = runState(kind, initialState);
      assertThat(result.value()).isEqualTo("State is 100");
      assertThat(result.state()).isEqualTo(initialState);
    }
  }

  @Nested
  @DisplayName("Run/Eval/Exec Helpers")
  class RunEvalExecTests {
    // Corrected Kind declaration; baseState already has explicitly typed lambda
    final Kind<StateKind.Witness<Integer>, String> kind = wrap(baseState); // ("V:" + s, s + 1)

    @Test
    void runState_shouldExecuteAndReturnTuple() {
      StateTuple<Integer, String> result = runState(kind, initialState); // 100
      assertThat(result.value()).isEqualTo("V:100");
      assertThat(result.state()).isEqualTo(101);
    }

    @Test
    void evalState_shouldExecuteAndReturnValue() {
      String value = evalState(kind, initialState); // 100
      assertThat(value).isEqualTo("V:100");
    }

    @Test
    void execState_shouldExecuteAndReturnState() {
      Integer finalState = execState(kind, initialState); // 100
      assertThat(finalState).isEqualTo(101);
    }

    @Test
    void runState_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> runState(null, initialState)) // Pass null directly
          .isInstanceOf(KindUnwrapException.class); // Propagates unwrap exception
    }

    @Test
    void evalState_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> evalState(null, initialState)) // Pass null directly
          .isInstanceOf(KindUnwrapException.class);
    }

    @Test
    void execState_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> execState(null, initialState)) // Pass null directly
          .isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {
    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<StateKindHelper> constructor = StateKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
