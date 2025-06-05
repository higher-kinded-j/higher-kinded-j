// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.trans.statet;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.trans.state_t.StateTKindHelper.STATE_T;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.trans.state_t.*;

/**
 * see {<a href="https://higher-kinded-j.github.io/statet_transformer.html">StateT Transformer</a>}
 */
public class StateTStackExample {

  private static final OptionalMonad OPT_MONAD = new OptionalMonad();
  private static final StateTMonad<List<Integer>, OptionalKind.Witness> ST_OPT_MONAD =
      StateTMonad.instance(OPT_MONAD);

  // Helper to lift a state function into StateT<List<Integer>, OptionalKind.Witness, A>
  private static <A> Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, A> liftOpt(
      Function<List<Integer>, Kind<OptionalKind.Witness, StateTuple<List<Integer>, A>>> f) {
    return STATE_T.stateT(f, OPT_MONAD);
  }

  // push operation
  public static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Void> push(
      Integer value) {
    return liftOpt(
        stack -> {
          List<Integer> newStack = new LinkedList<>(stack);
          newStack.add(0, value); // Add to front
          return OPTIONAL.widen(Optional.of(StateTuple.of(newStack, null)));
        });
  }

  // pop operation
  public static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Integer> pop() {
    return liftOpt(
        stack -> {
          if (stack.isEmpty()) {
            return OPTIONAL.widen(Optional.empty()); // Cannot pop from empty stack
          }
          List<Integer> newStack = new LinkedList<>(stack);
          Integer poppedValue = newStack.remove(0);
          return OPTIONAL.widen(Optional.of(StateTuple.of(newStack, poppedValue)));
        });
  }

  public static void main(String[] args) {
    Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Integer> computation =
        ST_OPT_MONAD.flatMap(
            v1 -> // v1 is Void from push(10)
            ST_OPT_MONAD.flatMap(
                    v2 -> // v2 is Void from push(20)
                    ST_OPT_MONAD.flatMap(
                            popped1 -> // popped1 should be 20
                            ST_OPT_MONAD.flatMap(
                                    popped2 -> { // popped2 should be 10
                                      // Do something with popped1 and popped2
                                      System.out.println(
                                          "Popped in order: " + popped1 + ", then " + popped2);
                                      // Final value of the computation
                                      return ST_OPT_MONAD.of(popped1 + popped2);
                                    },
                                    pop()),
                            pop()),
                    push(20)),
            push(10));

    List<Integer> initialStack = Collections.emptyList();
    Kind<OptionalKind.Witness, StateTuple<List<Integer>, Integer>> resultWrapped =
        STATE_T.runStateT(computation, initialStack);

    Optional<StateTuple<List<Integer>, Integer>> resultOpt = OPTIONAL.narrow(resultWrapped);

    resultOpt.ifPresentOrElse(
        tuple -> {
          System.out.println("Final value: " + tuple.value()); // Expected: 30
          System.out.println("Final stack: " + tuple.state()); // Expected: [] (empty)
        },
        () -> System.out.println("Computation resulted in empty Optional."));

    // Example of popping an empty stack
    Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Integer> popEmptyStack = pop();
    Optional<StateTuple<List<Integer>, Integer>> emptyPopResult =
        OPTIONAL.narrow(STATE_T.runStateT(popEmptyStack, Collections.emptyList()));
    System.out.println(
        "Popping empty stack was successful: " + emptyPopResult.isPresent()); // false
  }
}
