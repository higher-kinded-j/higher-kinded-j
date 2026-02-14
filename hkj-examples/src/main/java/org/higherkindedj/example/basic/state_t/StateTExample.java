// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.state_t;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.state_t.StateTKind; // For StateTKind.Witness
import org.higherkindedj.hkt.state_t.StateTMonad;

public class StateTExample {

  public static void main(String[] args) {
    OptionalMonad optionalMonad = OptionalMonad.INSTANCE;

    Function<Integer, Kind<OptionalKind.Witness, StateTuple<Integer, String>>> runFn =
        currentState -> {
          if (currentState < 0) {
            return OPTIONAL.widen(Optional.empty());
          }
          return OPTIONAL.widen(
              Optional.of(StateTuple.of(currentState + 1, "Value: " + currentState)));
        };

    StateT<Integer, OptionalKind.Witness, String> stateTExplicit =
        StateT.create(runFn, optionalMonad);

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> stateTKind = stateTExplicit;

    StateTMonad<Integer, OptionalKind.Witness> stateTMonad = StateTMonad.instance(optionalMonad);

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> pureStateT =
        stateTMonad.of("pure value");

    Optional<StateTuple<Integer, String>> pureResult =
        OPTIONAL.narrow(STATE_T.runStateT(pureStateT, 10));
    System.out.println("Pure StateT result: " + pureResult);

    // running computations

    Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultOptionalTuple =
        STATE_T.runStateT(stateTKind, 10);

    Optional<StateTuple<Integer, String>> actualOptional = OPTIONAL.narrow(resultOptionalTuple);

    if (actualOptional.isPresent()) {
      StateTuple<Integer, String> tuple = actualOptional.get();
      System.out.println("New State (from stateTExplicit): " + tuple.state());
      System.out.println("Value (from stateTExplicit): " + tuple.value());
    } else {
      System.out.println("actualOptional was empty for initial state 10");
    }

    // Example with negative initial state (expecting empty Optional)
    Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultEmptyOptional =
        STATE_T.runStateT(stateTKind, -5);
    Optional<StateTuple<Integer, String>> actualEmpty = OPTIONAL.narrow(resultEmptyOptional);
    // Output: Is empty: true
    System.out.println("Is empty (for initial state -5): " + actualEmpty.isEmpty());

    // Composing StateT Actions

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> initialComputation =
        StateT.create(s -> OPTIONAL.widen(Optional.of(StateTuple.of(s + 1, s * 2))), optionalMonad);

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> mappedComputation =
        stateTMonad.map(val -> "Computed: " + val, initialComputation);

    // Run mappedComputation with initial state 5:
    // 1. initialComputation runs: state becomes 6, value is 10. Wrapped in Optional.
    // 2. map's function ("Computed: " + 10) is applied to 10.
    // Result: Optional.of(StateTuple(6, "Computed: 10"))
    Optional<StateTuple<Integer, String>> mappedResult =
        OPTIONAL.narrow(STATE_T.runStateT(mappedComputation, 5));
    System.out.print("Mapped result (initial state 5): ");
    mappedResult.ifPresentOrElse(System.out::println, () -> System.out.println("Empty"));
    // Output: StateTuple[state=6, value=Computed: 10]

    // stateTMonad and optionalMonad are defined
    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> firstStep =
        StateT.create(
            s -> OPTIONAL.widen(Optional.of(StateTuple.of(s + 1, s * 10))), optionalMonad);

    Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>>
        secondStepFn =
            prevValue ->
                StateT.create(
                    s -> {
                      if (prevValue > 100) {
                        return OPTIONAL.widen(
                            Optional.of(StateTuple.of(s + prevValue, "Large: " + prevValue)));
                      } else {
                        return OPTIONAL.widen(Optional.empty());
                      }
                    },
                    optionalMonad);

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> combined =
        stateTMonad.flatMap(secondStepFn, firstStep);

    // Run with initial state 15
    // 1. firstStep(15): state=16, value=150. Wrapped in Optional.of.
    // 2. secondStepFn(150) is called. It returns a new StateT.
    // 3. The new StateT is run with state=16:
    //    Its function: s' (which is 16) -> Optional.of(StateTuple(16 + 150, "Large: 150"))
    //    Result: Optional.of(StateTuple(166, "Large: 150"))
    Optional<StateTuple<Integer, String>> combinedResult =
        OPTIONAL.narrow(STATE_T.runStateT(combined, 15));
    System.out.print("Combined result (initial state 15): ");
    combinedResult.ifPresentOrElse(System.out::println, () -> System.out.println("Empty"));

    // Output: StateTuple[state=166, value=Large: 150]

    // Run with initial state 5
    // 1. firstStep(5): state=6, value=50. Wrapped in Optional.of.
    // 2. secondStepFn(50) is called.
    // 3. The new StateT is run with state=6:
    //    Its function: s' (which is 6) -> Optional.empty()
    //    Result: Optional.empty()
    Optional<StateTuple<Integer, String>> combinedEmptyResult =
        OPTIONAL.narrow(STATE_T.runStateT(combined, 5));
    // Output: true
    System.out.println(
        "Is empty from small initial (state 5 for combined): " + combinedEmptyResult.isEmpty());
  }

  public static <S, F extends WitnessArity<TypeArity.Unary>> Kind<StateTKind.Witness<S, F>, S> get(
      Monad<F> monadF) {
    Function<S, Kind<F, StateTuple<S, S>>> runFn = s -> monadF.of(StateTuple.of(s, s));
    return StateT.create(runFn, monadF);
  }

  // Usage: stateTMonad.flatMap(currentState -> ..., get(optionalMonad))

  public static <S, F extends WitnessArity<TypeArity.Unary>>
      Kind<StateTKind.Witness<S, F>, Void> set(S newState, Monad<F> monadF) {
    Function<S, Kind<F, StateTuple<S, Void>>> runFn =
        s -> monadF.of(StateTuple.of(newState, (Void) null));
    return StateT.create(runFn, monadF);
  }

  public static <S, F extends WitnessArity<TypeArity.Unary>>
      Kind<StateTKind.Witness<S, F>, Void> modify(Function<S, S> f, Monad<F> monadF) {
    Function<S, Kind<F, StateTuple<S, Void>>> runFn =
        s -> monadF.of(StateTuple.of(f.apply(s), (Void) null));
    return StateT.create(runFn, monadF);
  }

  public static <S, F extends WitnessArity<TypeArity.Unary>, A>
      Kind<StateTKind.Witness<S, F>, A> gets(Function<S, A> f, Monad<F> monadF) {
    Function<S, Kind<F, StateTuple<S, A>>> runFn = s -> monadF.of(StateTuple.of(s, f.apply(s)));
    return StateT.create(runFn, monadF);
  }
}
