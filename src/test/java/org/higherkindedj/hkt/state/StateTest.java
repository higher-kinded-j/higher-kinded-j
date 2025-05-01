package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.state.State.StateTuple;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Direct tests for the State<S, A> implementation. */
@DisplayName("State<S, A> Direct Tests")
class StateTest {

  // Simple Integer state for testing
  private final Integer initialState = 10;

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    void of_shouldCreateStateFromFunction() {
      Function<Integer, StateTuple<Integer, String>> f = s -> new StateTuple<>("Value:" + s, s + 1);
      State<Integer, String> state = State.of(f);
      StateTuple<Integer, String> result = state.run(initialState);
      assertThat(result.value()).isEqualTo("Value:10");
      assertThat(result.state()).isEqualTo(11);
    }

    @Test
    void of_shouldThrowNPEForNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> State.of(null))
          .withMessageContaining("runFunction cannot be null");
    }

    @Test
    void pure_shouldReturnAAndNotChangeState() {
      State<Integer, String> state = State.pure("Result");
      StateTuple<Integer, String> result = state.run(initialState);
      assertThat(result.value()).isEqualTo("Result");
      assertThat(result.state()).isEqualTo(initialState); // State unchanged
    }

    @Test
    void pure_shouldAllowNullValue() {
      State<Integer, String> state = State.pure(null);
      StateTuple<Integer, String> result = state.run(initialState);
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(initialState);
    }

    @Test
    void get_shouldReturnValueEqualToState() {
      State<Integer, Integer> state = State.get();
      StateTuple<Integer, Integer> result = state.run(initialState);
      assertThat(result.value()).isEqualTo(initialState);
      assertThat(result.state()).isEqualTo(initialState); // State unchanged
    }

    @Test
    void set_shouldReplaceStateAndReturnVoid() {
      Integer newState = 99;
      State<Integer, Void> state = State.set(newState);
      StateTuple<Integer, Void> result = state.run(initialState);
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(newState); // State is updated
    }

    @Test
    void set_shouldThrowNPEForNullState() {
      assertThatNullPointerException()
          .isThrownBy(() -> State.set(null))
          .withMessageContaining("newState cannot be null");
    }

    @Test
    void modify_shouldApplyFunctionToStateAndReturnVoid() {
      Function<Integer, Integer> doubler = s -> s * 2;
      State<Integer, Void> state = State.modify(doubler);
      StateTuple<Integer, Void> result = state.run(initialState); // initialState = 10
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(20); // State is doubled
    }

    @Test
    void modify_shouldThrowNPEForNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> State.modify(null))
          .withMessageContaining("state modification function cannot be null");
    }

    @Test
    void inspect_shouldApplyFunctionToStateAndReturnResult() {
      Function<Integer, String> checkEven = s -> (s % 2 == 0) ? "Even" : "Odd";
      State<Integer, String> state = State.inspect(checkEven);
      StateTuple<Integer, String> result = state.run(initialState); // 10 is even
      assertThat(result.value()).isEqualTo("Even");
      assertThat(result.state()).isEqualTo(initialState); // State unchanged

      StateTuple<Integer, String> resultOdd = state.run(7);
      assertThat(resultOdd.value()).isEqualTo("Odd");
      assertThat(resultOdd.state()).isEqualTo(7);
    }

    @Test
    void inspect_shouldThrowNPEForNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> State.inspect(null))
          .withMessageContaining("state inspection function cannot be null");
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceMethods {

    final State<Integer, Integer> incrementState =
        State.of(s -> new StateTuple<>(s + 1, s + 1)); // val=s+1, state=s+1
    final State<Integer, String> initialValueState = State.pure("Start"); // val="Start", state=s

    @Test
    void run_shouldExecuteFunctionWithInitialState() {
      StateTuple<Integer, Integer> result = incrementState.run(initialState); // s=10
      assertThat(result.value()).isEqualTo(11);
      assertThat(result.state()).isEqualTo(11);
    }

    @Test
    void map_shouldTransformValueAndKeepStateTransition() {
      // incrementState: 10 -> (11, 11)
      // map: (11, 11) -> ("Value:11", 11)
      State<Integer, String> mappedState = incrementState.map(i -> "Value:" + i);
      StateTuple<Integer, String> result = mappedState.run(initialState);
      assertThat(result.value()).isEqualTo("Value:11");
      assertThat(result.state()).isEqualTo(11);
    }

    @Test
    void map_shouldThrowNPEForNullMapper() {
      assertThatNullPointerException()
          .isThrownBy(() -> incrementState.map(null))
          .withMessageContaining("mapper function cannot be null");
    }

    @Test
    void flatMap_shouldComposeStateComputations() {
      // Step 1: State that increments state and returns the new state as value.
      State<Integer, Integer> state1 = State.of(s -> new StateTuple<>(s + 1, s + 1));
      // Step 2: State that takes the value (new state from step 1), multiplies it by 2 for the
      // result,
      //         and adds 5 to the state it receives.
      Function<Integer, State<Integer, String>> state2Func =
          val -> State.of(s1 -> new StateTuple<>("Result:" + (val * 2), s1 + 5));

      // Compose: state1.flatMap(state2Func)
      // Run with initialState = 10:
      // 1. state1.run(10) -> (value=11, state=11)
      // 2. state2Func.apply(11) -> returns State.of(s1 -> new StateTuple<>("Result:22", s1 + 5))
      // 3. The returned state runs with state=11: run(11) -> ("Result:22", 11 + 5) -> ("Result:22",
      // 16)
      State<Integer, String> composedState = state1.flatMap(state2Func);
      StateTuple<Integer, String> result = composedState.run(initialState);

      assertThat(result.value()).isEqualTo("Result:22");
      assertThat(result.state()).isEqualTo(16);
    }

    @Test
    void flatMap_shouldThrowNPEForNullMapperFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> incrementState.flatMap(null))
          .withMessageContaining("flatMap mapper function cannot be null");
    }

    @Test
    void flatMap_shouldThrowNPEIfMapperReturnsNull() {
      Function<Integer, State<Integer, String>> nullReturningMapper = i -> null;
      State<Integer, String> state = incrementState.flatMap(nullReturningMapper);

      // The exception occurs when state.run is called because the mapper returned null
      assertThatNullPointerException()
          .isThrownBy(() -> state.run(initialState))
          .withMessageContaining("flatMap function returned null State");
    }
  }

  @Nested
  @DisplayName("StateTuple Record Tests")
  class StateTupleTests {
    @Test
    void stateTuple_constructorAllowsNullValue() {
      StateTuple<String, Integer> tuple = new StateTuple<>(null, "newState");
      assertThat(tuple.value()).isNull();
      assertThat(tuple.state()).isEqualTo("newState");
    }

    @Test
    void stateTuple_constructorRequiresNonNullState() {
      assertThatNullPointerException()
          .isThrownBy(() -> new StateTuple<>("value", null))
          .withMessageContaining("Final state cannot be null");
    }

    @Test
    void stateTuple_equalsAndHashCode() {
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
    void stateTuple_toString() {
      StateTuple<String, Integer> tuple = new StateTuple<>(123, "myState");
      assertThat(tuple.toString()).isEqualTo("StateTuple[value=123, state=myState]");

      StateTuple<String, Integer> tupleNull = new StateTuple<>(null, "state2");
      assertThat(tupleNull.toString()).isEqualTo("StateTuple[value=null, state=state2]");
    }
  }

  // State is a functional interface, equality is reference based unless the lambdas are identical.
  // Adding a basic test to confirm this.
  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsHashCodeTests {
    @Test
    void stateEqualityIsReferenceBased() {
      State<Integer, Integer> s1 = State.get();
      State<Integer, Integer> s2 = State.get(); // May or may not be same instance
      State<Integer, Integer> s3 = s -> new StateTuple<>(s, s); // Different lambda syntax
      State<Integer, Integer> s4 = s1; // Same instance

      assertThat(s1).isNotEqualTo(s3); // Different lambda syntax
      assertThat(s1).isEqualTo(s4); // Same instance

      // Whether s1 == s2 depends on JVM lambda caching/interning, not guaranteed.
      // We won't assert s1 != s2 or s1 == s2 here.

      // Hashcode *might* be the same for identical lambdas, but not guaranteed.
      // Don't rely on hashcode equality for distinct lambda instances.
    }
  }
}
