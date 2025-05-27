// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.state;

import static org.higherkindedj.hkt.state.StateKindHelper.unwrap;
import static org.higherkindedj.hkt.state.StateKindHelper.wrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state.StateTuple;

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
    Kind<StateKind.Witness<CounterState>, Void> incrementCounter =
        wrap(State.modify(s -> new CounterState(s.count() + 1)));

    Kind<StateKind.Witness<CounterState>, Integer> getCount =
        wrap(State.inspect(CounterState::count));

    // Stack Example Actions:
    // Define 'push' as a Function that returns a Kind (a parameterised action)
    Function<Integer, Kind<StateKind.Witness<StackState>, Void>> push =
        value ->
            wrap(
                State.modify(
                    s -> {
                      List<Integer> newList = new ArrayList<>(s.stack()); // Using diamond operator
                      newList.add(value);
                      return new StackState(Collections.unmodifiableList(newList));
                    }));

    Kind<StateKind.Witness<StackState>, Integer> pop =
        wrap(
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
        wrap(
            State.inspect(
                s ->
                    s.stack().isEmpty()
                        ? 0
                        : s.stack().get(s.stack().size() - 1))); // Assuming 0 for empty peek

    // Stack Example: Push 10, Push 20, Pop, then Pop again and sum their results
    Kind<StateKind.Witness<StackState>, Integer> stackProgram =
        stackStateMonad.flatMap( // flatMap 1: applies to push.apply(10) (ma_for_flatMap1)
            _ignoredFromPush1 ->
                stackStateMonad.flatMap( // flatMap 2: applies to push.apply(20) (ma_for_flatMap2)
                    _ignoredFromPush2 ->
                        stackStateMonad
                            .flatMap( // flatMap 3: applies to the first pop (ma_for_flatMap3)
                                poppedValue1 ->
                                    stackStateMonad.map( // map: applies to the second pop
                                        poppedValue2 ->
                                            poppedValue1
                                                + poppedValue2, // f_for_map (Function<Integer,
                                        // Integer>)
                                        pop // ma_for_map (Kind<StackState, Integer> for the second
                                        // pop)
                                        ),
                                pop // ma_for_flatMap3 (Kind<StackState, Integer> for the first pop)
                                ),
                    push.apply(20) // ma_for_flatMap2
                    ),
            push.apply(10) // ma_for_flatMap1
            );

    // To use these actions, you would typically run them with an initial state.
    // For example:
    CounterState initialCounter = new CounterState(0);
    StateTuple<CounterState, Void> afterIncrement = unwrap(incrementCounter).run(initialCounter);
    System.out.println("Counter after increment: " + afterIncrement.state().count()); // Output: 1

    StateTuple<CounterState, Integer> currentCountResult =
        unwrap(getCount).run(afterIncrement.state());
    System.out.println("Current count: " + currentCountResult.value()); // Output: 1

    StackState initialStack = new StackState(Collections.emptyList());
    Kind<StateKind.Witness<StackState>, Void> pushTen = push.apply(10);
    Kind<StateKind.Witness<StackState>, Void> pushTwenty = push.apply(20);

    // Chaining actions (simplified example, real chaining uses flatMap from StateMonad)
    StateTuple<StackState, Void> afterPush10 = unwrap(pushTen).run(initialStack);
    StateTuple<StackState, Void> afterPush20 = unwrap(pushTwenty).run(afterPush10.state());
    System.out.println("Stack after pushes: " + afterPush20.state().stack()); // Output: [10, 20]

    StateTuple<StackState, Integer> poppedValueResult = unwrap(pop).run(afterPush20.state());
    System.out.println("Popped value: " + poppedValueResult.value()); // Output: 20
    System.out.println("Stack after pop: " + poppedValueResult.state().stack()); // Output: [10]
  }

  record CounterState(int count) {}

  record StackState(java.util.List<Integer> stack) {}
}
