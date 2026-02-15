// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.expression;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.higherkindedj.hkt.state_t.StateTMonad;
import org.higherkindedj.hkt.tuple.Tuple3;

public class ForComprehensionExample {

  public static void main(String[] args) {
    System.out.println("--- For Comprehension with ListMonad ---");
    listExample();
    System.out.println("\n--- For Comprehension with MaybeMonad ---");
    maybeExample();
    System.out.println("\n--- For Comprehension with StateT Monad Transformer ---");
    stateTExample();
    System.out.println("\n--- For Comprehension with Extended Arities (6+) ---");
    highArityExample();
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
    final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

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

    final Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Unit>> add =
        n ->
            StateT.create(
                s -> optionalMonad.of(StateTuple.of(s + n, Unit.INSTANCE)), optionalMonad);

    final Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> get =
        StateT.create(s -> optionalMonad.of(StateTuple.of(s, s)), optionalMonad);

    // ✨ Use `peek` to create a version of `get` that logs the state when it's read.
    final Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> getAndLog =
        stateTMonad.peek(
            state -> System.out.println("DEBUG: State retrieved from context is " + state), get);

    final Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> statefulComputation =
        For.from(stateTMonad, add.apply(10))
            .from(a -> add.apply(5))
            .from(b -> getAndLog) // Use the peek-wrapped action here
            .let(t -> "The state is " + t._3())
            .yield((a, b, c, d) -> d + ", original value was " + c);

    final Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultOptional =
        STATE_T.runStateT(statefulComputation, 0);

    final Optional<StateTuple<Integer, String>> result = OPTIONAL.narrow(resultOptional);

    result.ifPresent(
        res -> {
          System.out.println("Final value: " + res.value());
          System.out.println("Final state: " + res.state());
        });
  }

  private static void highArityExample() {
    // For-comprehensions support up to 8 chained bindings.
    // Steps 2-8 are generated by the hkj-processor annotation processor.
    final IdMonad idMonad = IdMonad.instance();

    // Six-step comprehension building a formatted string
    Kind<IdKind.Witness, String> result =
        For.from(idMonad, Id.of("Alice"))
            .let(name -> name.length()) // b = 5
            .from(t -> Id.of(t._1().toUpperCase())) // c = "ALICE"
            .let(t -> t._2() * 10) // d = 50
            .let(t -> t._3() + "!") // e = "ALICE!"
            .let(t -> t._1() + " has " + t._2() + " letters") // f
            .yield(
                (name, len, upper, score, exclaimed, summary) ->
                    summary + " (score: " + score + ")");

    System.out.println("6-step result: " + IdKindHelper.ID.unwrap(result));
    // 6-step result: Alice has 5 letters (score: 50)

    // Seven-step comprehension with List monad (cartesian product)
    final ListMonad listMonad = ListMonad.INSTANCE;

    Kind<ListKind.Witness, String> dice =
        For.from(listMonad, LIST.widen(List.of(1, 2, 3)))
            .from(a -> LIST.widen(List.of(1, 2, 3)))
            .let(t -> t._1() + t._2())
            .let(t -> t._3() > 3)
            .when(t -> t._4())
            .let(t -> t._1() + "+" + t._2() + "=" + t._3())
            .yield((a, b, sum, isHigher, summary) -> summary);

    System.out.println("Dice pairs summing > 3: " + LIST.narrow(dice));

    // Eight-step comprehension — the maximum arity
    Kind<IdKind.Witness, String> eightStep =
        For.from(idMonad, Id.of("Bob"))
            .let(name -> name.length()) // b = 3
            .from(t -> Id.of(t._1().toLowerCase())) // c = "bob"
            .let(t -> t._2() * 100) // d = 300
            .let(t -> t._3() + "!") // e = "bob!"
            .let(t -> t._1() + "#" + t._4()) // f = "Bob#300"
            .let(t -> t._6().length()) // g = 7
            .let(t -> "result=" + t._6() + " (len " + t._7() + ")") // h
            .yield((name, len, lower, score, excl, tag, tagLen, summary) -> summary);

    System.out.println("8-step result: " + IdKindHelper.ID.unwrap(eightStep));
    // 8-step result: result=Bob#300 (len 7)
  }
}
