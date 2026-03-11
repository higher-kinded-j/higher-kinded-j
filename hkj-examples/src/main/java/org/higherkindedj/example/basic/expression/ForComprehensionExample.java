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
import org.higherkindedj.optics.Lens;

/**
 * Demonstrates For-comprehensions with various monads.
 *
 * <p>Examples include:
 *
 * <ul>
 *   <li>List monad with filtering
 *   <li>Maybe monad with short-circuiting
 *   <li>StateT monad transformer for stateful computations
 *   <li>Extended arities (6-8 step comprehensions)
 *   <li>Bridging from For to ForState with toState()
 *   <li>Parallel composition with par() for independent bindings
 * </ul>
 */
public class ForComprehensionExample {

  // --- Domain model for toState bridge example ---

  record Dashboard(String user, int count, boolean ready) {}

  static final Lens<Dashboard, Boolean> readyLens =
      Lens.of(Dashboard::ready, (d, v) -> new Dashboard(d.user(), d.count(), v));

  static final Lens<Dashboard, Integer> countLens =
      Lens.of(Dashboard::count, (d, v) -> new Dashboard(d.user(), v, d.ready()));

  public static void main(String[] args) {
    System.out.println("--- For Comprehension with ListMonad ---");
    listExample();
    System.out.println("\n--- For Comprehension with MaybeMonad ---");
    maybeExample();
    System.out.println("\n--- For Comprehension with StateT Monad Transformer ---");
    stateTExample();
    System.out.println("\n--- For Comprehension with Extended Arities (6+) ---");
    highArityExample();
    System.out.println("\n--- For → ForState Bridge with toState() ---");
    toStateBridgeExample();
    System.out.println("\n--- Parallel Composition with par() ---");
    parallelExample();
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
    // For-comprehensions support up to 12 chained bindings.
    // Steps 2-12 are generated by the hkj-processor annotation processor.
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

  /**
   * Demonstrates the toState() bridge that transitions from a For comprehension (tuple-based value
   * accumulation) into a ForState comprehension (named state with lenses).
   *
   * <p>This is ideal for workflows that start by gathering values and then need to build and refine
   * a structured record.
   */
  private static void toStateBridgeExample() {
    final IdMonad idMonad = IdMonad.instance();
    final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    // 1. Basic bridge: gather values with For, then build a record with ForState
    Kind<IdKind.Witness, String> basic =
        For.from(idMonad, Id.of("Alice"))
            .from(name -> Id.of(name.length()))
            .toState((name, count) -> new Dashboard(name, count, false))
            .modify(countLens, c -> c * 10)
            .update(readyLens, true)
            .yield(d -> d.user() + ": count=" + d.count() + ", ready=" + d.ready());

    System.out.println("  Basic bridge: " + IdKindHelper.ID.unwrap(basic));
    // Basic bridge: Alice: count=50, ready=true

    // 2. Bridge with filtering: MonadZero preserves when() across the transition
    Kind<MaybeKind.Witness, Dashboard> filtered =
        For.from(maybeMonad, MAYBE.just("Grace"))
            .from(name -> MAYBE.just(8))
            .toState((name, count) -> new Dashboard(name, count, false))
            .when(d -> d.count() > 5) // guard on the state record
            .update(readyLens, true)
            .yield();

    System.out.println("  Filtered bridge (passes): " + MAYBE.narrow(filtered));
    // Filtered bridge (passes): Just(Dashboard[user=Grace, count=8, ready=true])

    // 3. Bridge with pre-filtering: filter before transitioning to ForState
    Kind<MaybeKind.Witness, Dashboard> preFiltered =
        For.from(maybeMonad, MAYBE.just("Iris"))
            .when(name -> name.startsWith("I")) // filter in For phase
            .from(name -> MAYBE.just(name.length()))
            .toState((name, len) -> new Dashboard(name, len, false))
            .update(readyLens, true)
            .yield();

    System.out.println("  Pre-filtered bridge: " + MAYBE.narrow(preFiltered));
    // Pre-filtered bridge: Just(Dashboard[user=Iris, count=4, ready=true])

    // 4. Full pipeline: three-step For accumulation into ForState lens pipeline
    Kind<IdKind.Witness, String> fullPipeline =
        For.from(idMonad, Id.of("data"))
            .from(d -> Id.of(d.length()))
            .let(t -> t._1() + ":" + t._2())
            .toState((data, len, label) -> new Dashboard(label, len, false))
            .modify(countLens, c -> c * 10)
            .update(readyLens, true)
            .yield(d -> d.user() + "|" + d.count() + "|" + d.ready());

    System.out.println("  Full pipeline: " + IdKindHelper.ID.unwrap(fullPipeline));
    // Full pipeline: data:4|40|true
  }

  /**
   * Demonstrates par() for parallel/applicative composition of independent bindings.
   *
   * <p>par() uses Applicative semantics (map2/ap) rather than Monad (flatMap), expressing that
   * bindings are independent. For types like VTask/IO this enables concurrent execution; for others
   * it documents the independence even if execution is sequential.
   */
  private static void parallelExample() {
    final IdMonad idMonad = IdMonad.instance();
    final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    final ListMonad listMonad = ListMonad.INSTANCE;

    // 1. par(2) with Id — combine two independent values
    Kind<IdKind.Witness, String> idResult =
        For.par(idMonad, Id.of("hello"), Id.of("world")).yield((a, b) -> a + " " + b);

    System.out.println("  Id par(2): " + IdKindHelper.ID.unwrap(idResult));
    // hello world

    // 2. par(3) with Id — combine three independent values
    Kind<IdKind.Witness, Integer> idSum =
        For.par(idMonad, Id.of(10), Id.of(20), Id.of(30)).yield((a, b, c) -> a + b + c);

    System.out.println("  Id par(3): " + IdKindHelper.ID.unwrap(idSum));
    // 60

    // 3. par(2) with Maybe — short-circuits on Nothing
    Kind<MaybeKind.Witness, String> maybeResult =
        For.par(maybeMonad, MAYBE.just("Alice"), MAYBE.just(42))
            .yield((name, age) -> name + " is " + age);

    System.out.println("  Maybe par(2): " + MAYBE.narrow(maybeResult));
    // Just(Alice is 42)

    Kind<MaybeKind.Witness, String> maybeShort =
        For.par(maybeMonad, MAYBE.just("Bob"), MAYBE.<Integer>nothing())
            .yield((name, age) -> name + " is " + age);

    System.out.println("  Maybe par(2) with Nothing: " + MAYBE.narrow(maybeShort));
    // Nothing

    // 4. par() then from() — parallel followed by sequential
    Kind<IdKind.Witness, String> parThenFrom =
        For.par(idMonad, Id.of("Alice"), Id.of(5))
            .from(t -> Id.of(t._1() + " has " + t._2() + " letters"))
            .yield((name, len, sentence) -> sentence.toUpperCase());

    System.out.println("  par then from: " + IdKindHelper.ID.unwrap(parThenFrom));
    // ALICE HAS 5 LETTERS

    // 5. par(2) with List — cartesian product (applicative, not monadic)
    Kind<ListKind.Witness, String> listResult =
        For.par(listMonad, LIST.widen(List.of("A", "B")), LIST.widen(List.of(1, 2)))
            .yield((letter, num) -> letter + num);

    System.out.println("  List par(2): " + LIST.narrow(listResult));
    // [A1, A2, B1, B2]
  }
}
