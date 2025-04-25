package org.simulation.hkt.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.function.Function3;
import org.simulation.hkt.function.Function4;
import org.simulation.hkt.state.State.StateTuple; // Ensure import


import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.simulation.hkt.state.StateKindHelper.*;

/**
 * Tests for StateMonad<S>. Using Integer as the state type S.
 */
@DisplayName("StateMonad<Integer> Tests")
class StateMonadTest {

  // Concrete State type for tests
  private StateMonad<Integer> stateMonad;
  private final Integer initialState = 0; // Starting state for tests

  @BeforeEach
  void setUp() {
    stateMonad = new StateMonad<>();
  }

  // Helper to run and get the tuple (value, state)
  private <A> StateTuple<Integer, A> runS(Kind<StateKind<Integer, ?>, A> kind, Integer startState) {
    return runState(kind, startState);
  }

  // --- Basic Operations ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateStateReturningValueAndUnchangedState() {
      Kind<StateKind<Integer, ?>, String> kind = stateMonad.of("constantValue");
      StateTuple<Integer, String> result = runS(kind, initialState);
      assertThat(result.value()).isEqualTo("constantValue");
      assertThat(result.state()).isEqualTo(initialState); // State unchanged
    }

    @Test
    void of_shouldAllowNullValue() {
      Kind<StateKind<Integer, ?>, String> kind = stateMonad.of(null);
      StateTuple<Integer, String> result = runS(kind, initialState);
      assertThat(result.value()).isNull();
      assertThat(result.state()).isEqualTo(initialState);
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionToResultValueAndKeepStateTransition() {
      // State: s -> (s+1, s+1)
      Kind<StateKind<Integer, ?>, Integer> incKind = wrap(State.of(s -> new StateTuple<>(s + 1, s + 1)));

      // Map: x -> "Val:" + x
      Kind<StateKind<Integer, ?>, String> mappedKind = stateMonad.map(i -> "Val:" + i, incKind);

      // Run: 0 -> state1.run(0) -> (1, 1) -> map applies func -> (Val:1, 1)
      StateTuple<Integer, String> result = runS(mappedKind, initialState); // Start at 0
      assertThat(result.value()).isEqualTo("Val:1");
      assertThat(result.state()).isEqualTo(1);
    }

    @Test
    void map_shouldChainFunctions() {
      // State: s -> (s * 2, s + 5)
      Kind<StateKind<Integer, ?>, Integer> initialKind = wrap(State.of(s -> new StateTuple<>(s * 2, s + 5)));

      // Map: double -> string
      Kind<StateKind<Integer, ?>, String> mappedKind = stateMonad.map(
          value -> "Str:" + value, // Second map (applies to Double)
          stateMonad.map(
              val -> val / 2.0, // First map (applies to Integer)
              initialKind)
      );

      // Run with 10:
      // initial: (20, 15)
      // map1: (10.0, 15)
      // map2: ("Str:10.0", 15)
      StateTuple<Integer, String> result = runS(mappedKind, 10);
      assertThat(result.value()).isEqualTo("Str:10.0");
      assertThat(result.state()).isEqualTo(15);
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    @Test
    void ap_shouldApplyStateFunctionToStateValue() {
      // State<Integer, Function<Integer, String>>: s -> (i -> "F"+i+s, s+1)
      Kind<StateKind<Integer, ?>, Function<Integer, String>> funcKind =
          wrap(State.of(s -> new StateTuple<>(i -> "F" + i + s, s + 1)));

      // State<Integer, Integer>: s -> (s*10, s+2)
      Kind<StateKind<Integer, ?>, Integer> valKind =
          wrap(State.of(s -> new StateTuple<>(s * 10, s + 2)));

      Kind<StateKind<Integer, ?>, String> resultKind = stateMonad.ap(funcKind, valKind);

      // Run with 10:
      // 1. funcKind.run(10) -> (f = i->"F"+i+10, s1=11)
      // 2. valKind.run(s1=11) -> (a = 11*10=110, s2=11+2=13)
      // 3. f(a) -> "F"+110+10 -> "F11010" // Corrected trace based on function definition
      // Final result: ("F11010", 13)
      StateTuple<Integer, String> result = runS(resultKind, 10);

      // Assert based on the actual execution trace:
      assertThat(result.value()).isEqualTo("F11010"); // Corrected Assertion
      assertThat(result.state()).isEqualTo(13);
    }

    @Test
    void ap_shouldWorkWithPureFunctionAndValue() {
      Kind<StateKind<Integer, ?>, Function<Integer, String>> funcKind = stateMonad.of(i -> "Num" + i); // (i->"Num"+i, s)
      Kind<StateKind<Integer, ?>, Integer> valKind = stateMonad.of(100); // (100, s)
      Kind<StateKind<Integer, ?>, String> resultKind = stateMonad.ap(funcKind, valKind);

      // Run with 5:
      // 1. funcKind.run(5) -> (f=i->"Num"+i, s1=5)
      // 2. valKind.run(s1=5) -> (a=100, s2=5)
      // 3. f(a) -> "Num"+100
      // Final: ("Num100", 5)
      StateTuple<Integer, String> result = runS(resultKind, 5);
      assertThat(result.value()).isEqualTo("Num100");
      assertThat(result.state()).isEqualTo(5); // State unchanged
    }

    @Test
    @DisplayName("ap should throw NullPointerException if wrapped function is null")
    void ap_shouldThrowNPEForNullFunction() {
      // State that produces a null function value but updates state
      Kind<StateKind<Integer, ?>, Function<Integer, String>> funcKindNull =
          wrap(State.of(s -> new StateTuple<>(null, s + 10))); // Value is null

      // A valid value state
      Kind<StateKind<Integer, ?>, Integer> valKind =
          wrap(State.of(s -> new StateTuple<>(s * 10, s + 2)));

      // We need to run the state to trigger the exception inside the lambda
      Kind<StateKind<Integer, ?>, String> resultKind = stateMonad.ap(funcKindNull, valKind);

      // Assert that running the resulting state computation throws the specific NPE
      assertThatThrownBy(() -> runS(resultKind, 10))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Function wrapped in State for 'ap' was null");
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    @Test
    void flatMap_shouldSequenceComputationsAndPassState() {
      // Define State 1: Get current state, return it as value, increment state.
      // Action: s -> (s, s+1)
      Kind<StateKind<Integer, ?>, Integer> getState = StateKindHelper.get();
      Kind<StateKind<Integer, ?>, Void> incState = StateKindHelper.modify((Integer i) -> i + 1);

      // Combine get and modify using flatMap
      Kind<StateKind<Integer, ?>, Integer> getStateAndInc = stateMonad.flatMap(
          originalState -> stateMonad.map(
              voidResult -> originalState, // After modify returns Void, map the result back to the original state value
              incState                    // The action to perform (modifies state, returns Void)
          ),
          getState // Start by getting the state
      );
      // --- End of definition for getStateAndInc ---


      // State 2 (Function): Takes value from State 1 (which is the original state value),
      //                    multiplies it by 2, returns as value. Adds 10 to current state.
      // Action: original_s -> State: current_s -> ("Val:" + (original_s * 2), current_s + 10)
      Function<Integer, Kind<StateKind<Integer, ?>, String>> processValueAndAdd10 =
          originalStateValue -> wrap(State.of(
              currentState -> new StateTuple<>("Val:" + (originalStateValue * 2), currentState + 10)
          ));

      // Chain State 1 and State 2
      Kind<StateKind<Integer, ?>, String> resultKind = stateMonad.flatMap(processValueAndAdd10, getStateAndInc);

      // Run the combined state computation starting with state 10:
      // 1. getStateAndInc runs:
      //    - getState gets state 10, value is 10.
      //    - flatMap passes 10 to the lambda.
      //    - incState runs with state 10, modifies state to 11, returns Void.
      //    - map takes Void, returns the original state value 10.
      //    - Result of getStateAndInc: (value=10, state=11)
      // 2. flatMap takes the result value 10 from step 1.
      // 3. Calls processValueAndAdd10(10), which returns the State: s -> ("Val:20", s + 10).
      // 4. Runs this returned State with the state from step 1, which is 11.
      // 5. Execution: ("Val:20", 11 + 10) -> ("Val:20", 21).
      StateTuple<Integer, String> result = runS(resultKind, 10);
      assertThat(result.value()).isEqualTo("Val:20");
      assertThat(result.state()).isEqualTo(21);
    }
  }


  // --- Law Tests ---

  // Helper functions for laws
  final Function<Integer, String> intToString = Object::toString;
  final Function<String, String> appendWorld = s -> s + " world";

  // Kind<StateKind<Integer, ?>, Integer>
  final Kind<StateKind<Integer, ?>, Integer> mValue = wrap(State.of(s -> new StateTuple<>(s * 10, s + 1))); // (s*10, s+1)

  // Function Integer -> Kind<StateKind<Integer, ?>, String>
  final Function<Integer, Kind<StateKind<Integer, ?>, String>> f =
      i -> wrap(State.of(s -> new StateTuple<>("v" + i, s + i))); // ("vi", s+i)

  // Function String -> Kind<StateKind<Integer, ?>, String>
  final Function<String, Kind<StateKind<Integer, ?>, String>> g =
      str -> wrap(State.of(s -> new StateTuple<>(str + "!", s + str.length()))); // (str+"!", s+len(str))


  // Helper to compare results of running state computations
  private <A> void assertStateEquals(Kind<StateKind<Integer, ?>, A> k1, Kind<StateKind<Integer, ?>, A> k2, Integer startState) {
    StateTuple<Integer, A> res1 = runS(k1, startState);
    StateTuple<Integer, A> res2 = runS(k2, startState);
    assertThat(res1).isEqualTo(res2);
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      Kind<StateKind<Integer, ?>, Integer> fa = mValue;
      Kind<StateKind<Integer, ?>, Integer> result = stateMonad.map(Function.identity(), fa);
      assertStateEquals(result, fa, initialState);
      assertStateEquals(result, fa, 99); // Test with different state
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<StateKind<Integer, ?>, Integer> fa = mValue;
      Function<Integer, String> fMap = i -> "v" + i;
      Function<String, String> gMap = s -> s + "!";
      Function<Integer, String> gComposeF = gMap.compose(fMap);

      Kind<StateKind<Integer, ?>, String> leftSide = stateMonad.map(gComposeF, fa);
      Kind<StateKind<Integer, ?>, String> rightSide = stateMonad.map(gMap, stateMonad.map(fMap, fa));

      assertStateEquals(leftSide, rightSide, initialState);
      assertStateEquals(leftSide, rightSide, 5);
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {
    Kind<StateKind<Integer, ?>, Integer> v = mValue; // (s*10, s+1)
    Kind<StateKind<Integer, ?>, Function<Integer, String>> fKind = wrap(State.of(s -> new StateTuple<>(intToString, s * 2))); // (intToString, s*2)
    Kind<StateKind<Integer, ?>, Function<String, String>> gKind = wrap(State.of(s -> new StateTuple<>(appendWorld, s + 5))); // (appendWorld, s+5)

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<StateKind<Integer, ?>, Function<Integer, Integer>> idFuncKind = stateMonad.of(Function.identity()); // (id, s)
      Kind<StateKind<Integer, ?>, Integer> result = stateMonad.ap(idFuncKind, v);
      assertStateEquals(result, v, initialState);
      assertStateEquals(result, v, 7);
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> func = i -> "X" + i;
      Kind<StateKind<Integer, ?>, Function<Integer, String>> apFunc = stateMonad.of(func); // (func, s)
      Kind<StateKind<Integer, ?>, Integer> apVal = stateMonad.of(x); // (x, s)
      Kind<StateKind<Integer, ?>, String> leftSide = stateMonad.ap(apFunc, apVal);
      Kind<StateKind<Integer, ?>, String> rightSide = stateMonad.of(func.apply(x)); // (func(x), s)

      assertStateEquals(leftSide, rightSide, initialState);
      assertStateEquals(leftSide, rightSide, 8);
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      Kind<StateKind<Integer, ?>, String> leftSide = stateMonad.ap(fKind, stateMonad.of(y));
      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<StateKind<Integer, ?>, Function<Function<Integer, String>, String>> evalKind = stateMonad.of(evalWithY);
      Kind<StateKind<Integer, ?>, String> rightSide = stateMonad.ap(evalKind, fKind);

      assertStateEquals(leftSide, rightSide, initialState);
      assertStateEquals(leftSide, rightSide, 9);
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<Function<String, String>, Function<Function<Integer, String>, Function<Integer, String>>> composeMap =
          gg -> ff -> gg.compose(ff);
      Kind<StateKind<Integer, ?>, Function<Function<Integer, String>, Function<Integer, String>>> mappedCompose =
          stateMonad.map(composeMap, gKind);
      Kind<StateKind<Integer, ?>, Function<Integer, String>> ap1 =
          stateMonad.ap(mappedCompose, fKind);
      Kind<StateKind<Integer, ?>, String> leftSide =
          stateMonad.ap(ap1, v);
      Kind<StateKind<Integer, ?>, String> innerAp =
          stateMonad.ap(fKind, v);
      Kind<StateKind<Integer, ?>, String> rightSide =
          stateMonad.ap(gKind, innerAp);

      assertStateEquals(leftSide, rightSide, 10);
      assertStateEquals(leftSide, rightSide, 3); // Test another state
    }
  }


  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      int value = 5;
      Kind<StateKind<Integer, ?>, Integer> ofValue = stateMonad.of(value); // (5, s)
      Kind<StateKind<Integer, ?>, String> leftSide = stateMonad.flatMap(f, ofValue);
      Kind<StateKind<Integer, ?>, String> rightSide = f.apply(value); // f(5) -> State: s -> ("v5", s+5)

      assertStateEquals(leftSide, rightSide, initialState);
      assertStateEquals(leftSide, rightSide, 11);
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<StateKind<Integer, ?>, Integer>> ofFunc = i -> stateMonad.of(i); // i -> (i, s)
      Kind<StateKind<Integer, ?>, Integer> leftSide = stateMonad.flatMap(ofFunc, mValue);
      assertStateEquals(leftSide, mValue, initialState);
      assertStateEquals(leftSide, mValue, 12);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<StateKind<Integer, ?>, String> innerFlatMap = stateMonad.flatMap(f, mValue);
      Kind<StateKind<Integer, ?>, String> leftSide = stateMonad.flatMap(g, innerFlatMap);
      Function<Integer, Kind<StateKind<Integer, ?>, String>> rightSideFunc =
          a -> stateMonad.flatMap(g, f.apply(a));
      Kind<StateKind<Integer, ?>, String> rightSide = stateMonad.flatMap(rightSideFunc, mValue);

      assertStateEquals(leftSide, rightSide, initialState);
      assertStateEquals(leftSide, rightSide, 13);
    }
  }

  // --- mapN Tests (using default Applicative implementations) ---
  // State S = Integer
  @Nested
  @DisplayName("mapN tests")
  class MapNTests {
    // s -> (value, newState)
    Kind<StateKind<Integer, ?>, Integer> st1 = wrap(State.of(s -> new StateTuple<>(s + 1, s + 1))); // (s+1, s+1)
    Kind<StateKind<Integer, ?>, String> st2 = wrap(State.of(s -> new StateTuple<>("S" + s, s * 2))); // ("Ss", s*2)
    Kind<StateKind<Integer, ?>, Double> st3 = wrap(State.of(s -> new StateTuple<>(s / 2.0, s + 5))); // (s/2.0, s+5)
    Kind<StateKind<Integer, ?>, Boolean> st4 = wrap(State.of(s -> new StateTuple<>(s > 10, s - 1))); // (s>10, s-1)

    @Test
    void map2_combinesStateAndValues() {
      Kind<StateKind<Integer, ?>, String> result = stateMonad.map2(
          st1, st2, (i, s) -> i + ":" + s
      );
      StateTuple<Integer, String> res = runS(result, 10);
      assertThat(res.value()).isEqualTo("11:S11");
      assertThat(res.state()).isEqualTo(22);
    }

    @Test
    void map3_combinesStateAndValues() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%d/%s/%.1f", i, s, d);
      Kind<StateKind<Integer, ?>, String> result = stateMonad.map3(st1, st2, st3, f3);
      StateTuple<Integer, String> res = runS(result, 10);
      assertThat(res.value()).isEqualTo("11/S11/11.0");
      assertThat(res.state()).isEqualTo(27);
    }

    @Test
    void map4_combinesStateAndValues() {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%d-%s-%.1f-%b", i, s, d, b);
      Kind<StateKind<Integer, ?>, String> result = stateMonad.map4(st1, st2, st3, st4, f4);
      StateTuple<Integer, String> res = runS(result, 10);
      assertThat(res.value()).isEqualTo("11-S11-11.0-true");
      assertThat(res.state()).isEqualTo(26);
    }
  }
}