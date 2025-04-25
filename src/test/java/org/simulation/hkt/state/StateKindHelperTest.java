package org.simulation.hkt.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.simulation.hkt.state.StateKindHelper.*;
import static org.simulation.hkt.state.State.StateTuple; // Import inner record

@DisplayName("StateKindHelper Tests")
class StateKindHelperTest {

  // Simple Integer state for testing
  private final Integer initialState = 100;
  private final State<Integer, String> baseState = State.of(s -> new StateTuple<>("V:" + s, s + 1));


  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForState() {
      Kind<StateKind<Integer, ?>, String> kind = wrap(baseState);
      assertThat(kind).isInstanceOf(StateHolder.class);
      // Unwrap to verify
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
      Kind<StateKind<Integer, ?>, String> kind = wrap(baseState);
      assertThat(unwrap(kind)).isSameAs(baseState);
    }

    // Dummy Kind implementation that is not StateHolder
    record DummyStateKind<S, A>() implements Kind<StateKind<S, ?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<StateKind<Integer, ?>, String> unknownKind = new DummyStateKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyStateKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullState() {
      StateHolder<Integer, String> holderWithNull = new StateHolder<>(null);
      // Cast needed for test setup
      @SuppressWarnings("unchecked")
      Kind<StateKind<Integer, ?>, String> kind = (Kind<StateKind<Integer, ?>, String>) (Kind<?,?>) holderWithNull;

      assertThatThrownBy(() -> unwrap(kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_HOLDER_STATE_MSG);
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    void pure_shouldWrapStatePure() {
      Kind<StateKind<Integer, ?>, String> kind = StateKindHelper.pure("pureValue");
      StateTuple<Integer, String> result = runState(kind, initialState);
      assertThat(result.value()).isEqualTo("pureValue");
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    void get_shouldWrapStateGet() {
      Kind<StateKind<Integer, ?>, Integer> kind = StateKindHelper.get();
      StateTuple<Integer, Integer> result = runState(kind, initialState);
      assertThat(result.value()).isEqualTo(initialState);
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    void set_shouldWrapStateSet() {
      Integer newState = 555;
      Kind<StateKind<Integer, ?>, Void> kind = StateKindHelper.set(newState);
      StateTuple<Integer, Void> result = runState(kind, initialState);
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(newState);
    }

    @Test
    void modify_shouldWrapStateModify() {
      Function<Integer, Integer> tripler = s -> s * 3;
      Kind<StateKind<Integer, ?>, Void> kind = StateKindHelper.modify(tripler);
      StateTuple<Integer, Void> result = runState(kind, initialState); // 100
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(300);
    }

    @Test
    void inspect_shouldWrapStateInspect() {
      Function<Integer, String> describe = s -> "State is " + s;
      Kind<StateKind<Integer, ?>, String> kind = StateKindHelper.inspect(describe);
      StateTuple<Integer, String> result = runState(kind, initialState);
      assertThat(result.value()).isEqualTo("State is 100");
      assertThat(result.state()).isEqualTo(initialState);
    }
  }

  @Nested
  @DisplayName("Run/Eval/Exec Helpers")
  class RunEvalExecTests {
    final Kind<StateKind<Integer, ?>, String> kind = wrap(baseState); // ("V:" + s, s + 1)

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
      assertThatThrownBy(() -> runState(null, initialState))
          .isInstanceOf(KindUnwrapException.class); // Propagates unwrap exception
    }
    @Test
    void evalState_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> evalState(null, initialState))
          .isInstanceOf(KindUnwrapException.class);
    }
    @Test
    void execState_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> execState(null, initialState))
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