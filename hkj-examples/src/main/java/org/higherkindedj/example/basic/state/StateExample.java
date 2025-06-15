// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.unit.Unit;

/** see {<a href="https://higher-kinded-j.github.io/state_monad.html">State Monad</a>} */
public class StateExample {

  public static void main(String[] args) {
    new StateExample().runStateExample();
  }

  public void runStateExample() {
    // Monad instances (can be useful for type guidance or chaining with flatMap)
    StateMonad<CounterState> counterStateMonad = new StateMonad<>();
    StateMonad<StackState> stackStateMonad = new StateMonad<>();

    // Counter Example Actions:
    // State.modify now returns State<S, Unit>, so incrementCounter is Kind<..., Unit>
    Kind<StateKind.Witness<CounterState>, Unit> incrementCounter =
        STATE.widen(State.modify(s -> new CounterState(s.count() + 1)));

    Kind<StateKind.Witness<CounterState>, Integer> getCount =
        STATE.widen(State.inspect(CounterState::count));

    // Stack Example Actions:
    // Define 'push' as a Function that returns a Kind (a parameterised action)
    // State.modify now returns State<S, Unit>
    Function<Integer, Kind<StateKind.Witness<StackState>, Unit>> push =
        value ->
            STATE.widen(
                State.modify(
                    s -> {
                      List<Integer> newList = new ArrayList<>(s.stack());
                      newList.add(value);
                      return new StackState(Collections.unmodifiableList(newList));
                    }));

    Kind<StateKind.Witness<StackState>, Integer> pop =
        STATE.widen(
            State.of(
                s -> {
                  if (s.stack().isEmpty()) {
                    return new StateTuple<>(0, s); // Assuming 0 for empty pop
                  }
                  List<Integer> currentStack = s.stack();
                  int value = currentStack.get(currentStack.size() - 1);
                  List<Integer> newStack =
                      new ArrayList<>(currentStack.subList(0, currentStack.size() - 1));
                  return new StateTuple<>(
                      value, new StackState(Collections.unmodifiableList(newStack)));
                }));

    Kind<StateKind.Witness<StackState>, Integer> peek =
        STATE.widen(
            State.inspect(
                s ->
                    s.stack().isEmpty()
                        ? 0
                        : s.stack().get(s.stack().size() - 1))); // Assuming 0 for empty peek

    // Stack Example: Push 10, Push 20, Pop, then Pop again and sum their results
    // This is much cleaner with a For comprehension.
    var stackProgram =
        For.from(stackStateMonad, push.apply(10)) // a is Unit
            .from(a -> push.apply(20)) // b is Unit
            .from(b -> pop) // c is 20
            .from(t -> pop) // d is 10
            .yield((a, b, c, d) -> c + d); // yield 20 + 10

    // To use these actions, you would typically run them with an initial state.
    CounterState initialCounter = new CounterState(0);
    // afterIncrement's value part is Unit
    StateTuple<CounterState, Unit> afterIncrement =
        STATE.narrow(incrementCounter).run(initialCounter);
    System.out.println("Counter after increment: " + afterIncrement.state().count());

    StateTuple<CounterState, Integer> currentCountResult =
        STATE.narrow(getCount).run(afterIncrement.state());
    System.out.println("Current count: " + currentCountResult.value());

    StackState initialStack = new StackState(Collections.emptyList());

    StateTuple<StackState, Integer> stackProgramResult = STATE.runState(stackProgram, initialStack);

    System.out.println("Stack program result: " + stackProgramResult.value());
    System.out.println("Stack after program: " + stackProgramResult.state().stack());
  }

  record CounterState(int count) {}

  record StackState(java.util.List<Integer> stack) {}
}
