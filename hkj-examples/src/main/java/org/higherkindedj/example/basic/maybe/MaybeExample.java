// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.maybe;

import module java.base;
import module org.higherkindedj.core;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;

/** see {<a href="https://higher-kinded-j.github.io/maybe_monad.html">Maybe Monad</a>} */
public class MaybeExample {

  public static <A, B> Kind<MaybeKind.Witness, B> processData(
      Kind<MaybeKind.Witness, A> inputKind,
      Function<A, B> mapper,
      B defaultValueOnAbsence,
      MaybeMonad monad) {
    Kind<MaybeKind.Witness, B> mappedKind = monad.map(mapper, inputKind);

    return monad.handleErrorWith(mappedKind, (Unit unitVal) -> monad.of(defaultValueOnAbsence));
  }

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("\n Map Example");
    mapExample();
    System.out.println("\n flatMap Example");
    flatMapExample();
    System.out.println("\n ap Example");
    apExample();
    System.out.println("\n handleErrorWith Example");
    handleErrorWithExample();
    System.out.println("\n Monad Example");
    monadExample();
  }

  void monadExample() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    Kind<MaybeKind.Witness, Integer> presentIntKind = MAYBE.just(100);
    Kind<MaybeKind.Witness, Integer> absentIntKind = MAYBE.nothing();
    Kind<MaybeKind.Witness, String> nullInputStringKind = maybeMonad.of(null);

    Function<Integer, String> intToStatus = n -> "Status: " + n;
    Kind<MaybeKind.Witness, String> mappedPresent = maybeMonad.map(intToStatus, presentIntKind);
    Kind<MaybeKind.Witness, String> mappedAbsent = maybeMonad.map(intToStatus, absentIntKind);

    System.out.println("Mapped (Present): " + MAYBE.narrow(mappedPresent));
    System.out.println("Mapped (Absent): " + MAYBE.narrow(mappedAbsent));

    Function<Integer, Kind<MaybeKind.Witness, String>> intToPositiveStatusKind =
        n -> (n > 0) ? maybeMonad.of("Positive: " + n) : MAYBE.nothing();

    Kind<MaybeKind.Witness, String> flatMappedPresent =
        maybeMonad.flatMap(intToPositiveStatusKind, presentIntKind);
    Kind<MaybeKind.Witness, String> flatMappedZero =
        maybeMonad.flatMap(intToPositiveStatusKind, maybeMonad.of(0));

    System.out.println("FlatMapped (Present Positive): " + MAYBE.narrow(flatMappedPresent));
    System.out.println("FlatMapped (Zero): " + MAYBE.narrow(flatMappedZero));

    Kind<MaybeKind.Witness, String> fromOf = maybeMonad.of("Direct Value");

    Kind<MaybeKind.Witness, String> fromRaiseError = maybeMonad.raiseError(Unit.INSTANCE);
    System.out.println("From 'of': " + MAYBE.narrow(fromOf));
    System.out.println("From 'raiseError': " + MAYBE.narrow(fromRaiseError));
    System.out.println("From 'of(null)': " + MAYBE.narrow(nullInputStringKind));

    Function<Unit, Kind<MaybeKind.Witness, Integer>> recoverWithDefault =
        unitVal -> maybeMonad.of(-1);

    Kind<MaybeKind.Witness, Integer> recoveredFromAbsent =
        maybeMonad.handleErrorWith(absentIntKind, recoverWithDefault);
    Kind<MaybeKind.Witness, Integer> notRecoveredFromPresent =
        maybeMonad.handleErrorWith(presentIntKind, recoverWithDefault);

    System.out.println("Recovered (from Absent): " + MAYBE.narrow(recoveredFromAbsent));
    System.out.println("Recovered (from Present): " + MAYBE.narrow(notRecoveredFromPresent));

    Kind<MaybeKind.Witness, String> processedPresent =
        processData(presentIntKind, x -> "Processed: " + x, "N/A", maybeMonad);
    Kind<MaybeKind.Witness, String> processedAbsent =
        processData(absentIntKind, x -> "Processed: " + x, "N/A", maybeMonad);

    System.out.println("Generic Process (Present): " + MAYBE.narrow(processedPresent));
    System.out.println("Generic Process (Absent): " + MAYBE.narrow(processedAbsent));

    Maybe<String> finalMappedMaybe = MAYBE.narrow(mappedPresent);
    System.out.println("Final unwrapped mapped maybe: " + finalMappedMaybe);
  }

  void handleErrorWithExample() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    Function<Unit, Kind<MaybeKind.Witness, String>> recover = unitVal -> MAYBE.just("Recovered");

    Kind<MaybeKind.Witness, String> handledJust =
        maybeMonad.handleErrorWith(MAYBE.just("Original"), recover);
    Kind<MaybeKind.Witness, String> handledNothing =
        maybeMonad.handleErrorWith(MAYBE.nothing(), recover);

    System.out.println("HandleError (Just): " + MAYBE.narrow(handledJust));
    System.out.println("HandleError (Nothing): " + MAYBE.narrow(handledNothing));
  }

  void apExample() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    Kind<MaybeKind.Witness, Integer> justNum = MAYBE.just(10);
    Kind<MaybeKind.Witness, Integer> nothingNum = MAYBE.nothing();
    Kind<MaybeKind.Witness, Function<Integer, String>> justFunc = MAYBE.just(i -> "Result: " + i);
    Kind<MaybeKind.Witness, Function<Integer, String>> nothingFunc = MAYBE.nothing();

    Kind<MaybeKind.Witness, String> apApplied = maybeMonad.ap(justFunc, justNum);
    Kind<MaybeKind.Witness, String> apNothingFunc = maybeMonad.ap(nothingFunc, justNum);
    Kind<MaybeKind.Witness, String> apNothingVal = maybeMonad.ap(justFunc, nothingNum);

    System.out.println("Ap (Applied): " + MAYBE.narrow(apApplied));
    System.out.println("Ap (Nothing Func): " + MAYBE.narrow(apNothingFunc));
    System.out.println("Ap (Nothing Val): " + MAYBE.narrow(apNothingVal));
  }

  void flatMapExample() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    Function<String, Kind<MaybeKind.Witness, Integer>> parseString =
        s -> {
          try {
            return MAYBE.just(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return MAYBE.nothing();
          }
        };

    Kind<MaybeKind.Witness, String> justFiveStr = MAYBE.just("5");
    Kind<MaybeKind.Witness, Integer> parsedJust = maybeMonad.flatMap(parseString, justFiveStr);

    Kind<MaybeKind.Witness, String> justNonNumStr = MAYBE.just("abc");
    Kind<MaybeKind.Witness, Integer> parsedNonNum = maybeMonad.flatMap(parseString, justNonNumStr);

    System.out.println("FlatMap (Just): " + MAYBE.narrow(parsedJust));
    System.out.println("FlatMap (NonNum): " + MAYBE.narrow(parsedNonNum));
  }

  void mapExample() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    Kind<MaybeKind.Witness, Integer> justNum = MAYBE.just(10);
    Kind<MaybeKind.Witness, Integer> nothingNum = MAYBE.nothing();

    Function<Integer, String> numToString = n -> "Val: " + n;
    Kind<MaybeKind.Witness, String> justStr = maybeMonad.map(numToString, justNum);
    Kind<MaybeKind.Witness, String> nothingStr = maybeMonad.map(numToString, nothingNum);

    Function<Integer, String> numToNull = n -> null;
    Kind<MaybeKind.Witness, String> mappedToNull = maybeMonad.map(numToNull, justNum);

    System.out.println("Map (Just): " + MAYBE.narrow(justStr));
    System.out.println("Map (Nothing): " + MAYBE.narrow(nothingStr));
    System.out.println("Map (To Null): " + MAYBE.narrow(mappedToNull));
  }
}
