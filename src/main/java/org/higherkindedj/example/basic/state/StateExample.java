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
        wrap(State.modify(s -> new CounterState(s.count() + 1)));

    Kind<StateKind.Witness<CounterState>, Integer> getCount =
        wrap(State.inspect(CounterState::count));

    // Stack Example Actions:
    // Define 'push' as a Function that returns a Kind (a parameterised action)
    // State.modify now returns State<S, Unit>
    Function<Integer, Kind<StateKind.Witness<StackState>, Unit>> push =
        value ->
            wrap(
                State.modify(
                    s -> {
                      List<Integer> newList = new ArrayList<>(s.stack());
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
        stackStateMonad.flatMap(
            _ignoredFromPush1 ->
                stackStateMonad.flatMap(
                    _ignoredFromPush2 ->
                        stackStateMonad.flatMap(
                            poppedValue1 ->
                                stackStateMonad.map(
                                    poppedValue2 -> poppedValue1 + poppedValue2, pop),
                            pop),
                    push.apply(20)),
            push.apply(10));

    // To use these actions, you would typically run them with an initial state.
    CounterState initialCounter = new CounterState(0);
    // afterIncrement's value part is Unit
    StateTuple<CounterState, Unit> afterIncrement = unwrap(incrementCounter).run(initialCounter);
    System.out.println("Counter after increment: " + afterIncrement.state().count());

    StateTuple<CounterState, Integer> currentCountResult =
        unwrap(getCount).run(afterIncrement.state());
    System.out.println("Current count: " + currentCountResult.value());

    StackState initialStack = new StackState(Collections.emptyList());
    Kind<StateKind.Witness<StackState>, Unit> pushTen = push.apply(10);
    Kind<StateKind.Witness<StackState>, Unit> pushTwenty = push.apply(20);

    StateTuple<StackState, Unit> afterPush10 = unwrap(pushTen).run(initialStack);
    StateTuple<StackState, Unit> afterPush20 = unwrap(pushTwenty).run(afterPush10.state());
    System.out.println("Stack after pushes: " + afterPush20.state().stack());

    StateTuple<StackState, Integer> poppedValueResult = unwrap(pop).run(afterPush20.state());
    System.out.println("Popped value: " + poppedValueResult.value());
    System.out.println("Stack after pop: " + poppedValueResult.state().stack());
  }

  record CounterState(int count) {}

  record StackState(java.util.List<Integer> stack) {}
}
