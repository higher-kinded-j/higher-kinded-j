// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.expression;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.trans.state_t.StateTKindHelper.STATE_T;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.trans.state_t.StateT;
import org.higherkindedj.hkt.trans.state_t.StateTKind;
import org.higherkindedj.hkt.trans.state_t.StateTMonad;
import org.higherkindedj.hkt.tuple.Tuple3;
import org.higherkindedj.hkt.unit.Unit;

public class ForComprehensionExample {

  public static void main(String[] args) {
    System.out.println("--- For Comprehension with ListMonad ---");
    listExample();
    System.out.println("\n--- For Comprehension with MaybeMonad ---");
    maybeExample();
    System.out.println("\n--- For Comprehension with StateT Monad Transformer ---");
    stateTExample();
  }

  private static void listExample() {
    final ListMonad listMonad = ListMonad.INSTANCE;

    final Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2, 3));
    final Kind<ListKind.Witness, Integer> list2 = LIST.widen(Arrays.asList(10, 20));

    final Kind<ListKind.Witness, String> result =
        For.from(listMonad, list1)
            .from(_ -> list2)
            .when(t -> (t._1() + t._2()) % 2 != 0) // Filter for odd sums
            .let(t -> "Sum: " + (t._1() + t._2()))
            .yield((a, b, c) -> a + " + " + b + " = " + c);

    final List<String> narrow = LIST.narrow(result);
    System.out.println("Result of List comprehension: " + narrow);
  }

  private static void maybeExample() {
    final MaybeMonad maybeMonad = new MaybeMonad();

    final Kind<MaybeKind.Witness, Integer> maybeInt = MAYBE.just(10);
    final Kind<MaybeKind.Witness, String> maybeString = MAYBE.just("Hello");
    final Kind<MaybeKind.Witness, Double> nothing = MAYBE.nothing();

    // Example with all Just values
    final Kind<MaybeKind.Witness, String> result1 =
        For.from(maybeMonad, maybeInt)
            .from(i -> maybeString)
            .let(t -> t._1() > 5)
            .when(Tuple3::_3) // when t._3 is true
            .yield((i, s, b) -> s + " " + i + " is > 5? " + b);

    System.out.println("Maybe comprehension (all Just): " + MAYBE.narrow(result1));

    // Example with a Nothing value, which will short-circuit
    final Kind<MaybeKind.Witness, String> result2 =
        For.from(maybeMonad, maybeInt)
            .from(i -> maybeString)
            .from(t -> nothing) // This will cause the entire computation to be Nothing
            .yield((i, s, d) -> "This will not be yielded");

    System.out.println("Maybe comprehension (with Nothing): " + MAYBE.narrow(result2));
  }

  private static void stateTExample() {
    final OptionalMonad optionalMonad = OptionalMonad.INSTANCE;
    final StateTMonad<Integer, OptionalKind.Witness> stateTMonad =
        StateTMonad.instance(optionalMonad);

    // Helper to create a StateT that modifies the state and returns Unit
    final Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Unit>> add =
        n ->
            StateT.create(
                s -> optionalMonad.of(StateTuple.of(s + n, Unit.INSTANCE)), optionalMonad);

    // Helper to create a StateT that gets the current state as its value
    final Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> get =
        StateT.create(s -> optionalMonad.of(StateTuple.of(s, s)), optionalMonad);

    final Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> statefulComputation =
        For.from(stateTMonad, add.apply(10)) // State becomes 10, a = Unit
            .from(a -> add.apply(5)) // State becomes 15, b = Unit
            .from(b -> get) // State is 15, c = 15
            .let(t -> "The state is " + t._3()) // d = "The state is 15"
            .yield((a, b, c, d) -> d + ", original value was " + c);

    // Run the computation with an initial state of 0
    final Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultOptional =
        STATE_T.runStateT(statefulComputation, 0);

    final Optional<StateTuple<Integer, String>> result = OPTIONAL.narrow(resultOptional);

    result.ifPresent(
        res -> {
          System.out.println("Final value: " + res.value());
          System.out.println("Final state: " + res.state());
        });
  }
}
