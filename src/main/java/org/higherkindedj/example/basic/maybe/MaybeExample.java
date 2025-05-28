// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.maybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.unit.Unit;

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

  public static void main(String[] args) {
    MaybeExample example = new MaybeExample();
    System.out.println("\n Map Example");
    example.mapExample();
    System.out.println("\n flatMap Example");
    example.flatMapExample();
    System.out.println("\n ap Example");
    example.apExample();
    System.out.println("\n handleErrorWith Example");
    example.handleErrorWithExample();
    System.out.println("\n Monad Example");
    example.monadExample();
  }

  void monadExample() {
    MaybeMonad maybeMonad = new MaybeMonad();

    Kind<MaybeKind.Witness, Integer> presentIntKind = MaybeKindHelper.just(100);
    Kind<MaybeKind.Witness, Integer> absentIntKind = MaybeKindHelper.nothing();
    Kind<MaybeKind.Witness, String> nullInputStringKind = maybeMonad.of(null);

    Function<Integer, String> intToStatus = n -> "Status: " + n;
    Kind<MaybeKind.Witness, String> mappedPresent = maybeMonad.map(intToStatus, presentIntKind);
    Kind<MaybeKind.Witness, String> mappedAbsent = maybeMonad.map(intToStatus, absentIntKind);

    System.out.println("Mapped (Present): " + MaybeKindHelper.unwrap(mappedPresent));
    System.out.println("Mapped (Absent): " + MaybeKindHelper.unwrap(mappedAbsent));

    Function<Integer, Kind<MaybeKind.Witness, String>> intToPositiveStatusKind =
        n -> (n > 0) ? maybeMonad.of("Positive: " + n) : MaybeKindHelper.nothing();

    Kind<MaybeKind.Witness, String> flatMappedPresent =
        maybeMonad.flatMap(intToPositiveStatusKind, presentIntKind);
    Kind<MaybeKind.Witness, String> flatMappedZero =
        maybeMonad.flatMap(intToPositiveStatusKind, maybeMonad.of(0));

    System.out.println(
        "FlatMapped (Present Positive): " + MaybeKindHelper.unwrap(flatMappedPresent));
    System.out.println("FlatMapped (Zero): " + MaybeKindHelper.unwrap(flatMappedZero));

    Kind<MaybeKind.Witness, String> fromOf = maybeMonad.of("Direct Value");

    Kind<MaybeKind.Witness, String> fromRaiseError = maybeMonad.raiseError(Unit.INSTANCE);
    System.out.println("From 'of': " + MaybeKindHelper.unwrap(fromOf));
    System.out.println("From 'raiseError': " + MaybeKindHelper.unwrap(fromRaiseError));
    System.out.println("From 'of(null)': " + MaybeKindHelper.unwrap(nullInputStringKind));

    Function<Unit, Kind<MaybeKind.Witness, Integer>> recoverWithDefault =
        unitVal -> maybeMonad.of(-1);

    Kind<MaybeKind.Witness, Integer> recoveredFromAbsent =
        maybeMonad.handleErrorWith(absentIntKind, recoverWithDefault);
    Kind<MaybeKind.Witness, Integer> notRecoveredFromPresent =
        maybeMonad.handleErrorWith(presentIntKind, recoverWithDefault);

    System.out.println("Recovered (from Absent): " + MaybeKindHelper.unwrap(recoveredFromAbsent));
    System.out.println(
        "Recovered (from Present): " + MaybeKindHelper.unwrap(notRecoveredFromPresent));

    Kind<MaybeKind.Witness, String> processedPresent =
        processData(presentIntKind, x -> "Processed: " + x, "N/A", maybeMonad);
    Kind<MaybeKind.Witness, String> processedAbsent =
        processData(absentIntKind, x -> "Processed: " + x, "N/A", maybeMonad);

    System.out.println("Generic Process (Present): " + MaybeKindHelper.unwrap(processedPresent));
    System.out.println("Generic Process (Absent): " + MaybeKindHelper.unwrap(processedAbsent));

    Maybe<String> finalMappedMaybe = MaybeKindHelper.unwrap(mappedPresent);
    System.out.println("Final unwrapped mapped maybe: " + finalMappedMaybe);
  }

  void handleErrorWithExample() {
    MaybeMonad maybeMonad = new MaybeMonad();
    Function<Unit, Kind<MaybeKind.Witness, String>> recover =
        unitVal -> MaybeKindHelper.just("Recovered");

    Kind<MaybeKind.Witness, String> handledJust =
        maybeMonad.handleErrorWith(MaybeKindHelper.just("Original"), recover);
    Kind<MaybeKind.Witness, String> handledNothing =
        maybeMonad.handleErrorWith(MaybeKindHelper.nothing(), recover);

    System.out.println("HandleError (Just): " + MaybeKindHelper.unwrap(handledJust));
    System.out.println("HandleError (Nothing): " + MaybeKindHelper.unwrap(handledNothing));
  }

  void apExample() {
    MaybeMonad maybeMonad = new MaybeMonad();
    Kind<MaybeKind.Witness, Integer> justNum = MaybeKindHelper.just(10);
    Kind<MaybeKind.Witness, Integer> nothingNum = MaybeKindHelper.nothing();
    Kind<MaybeKind.Witness, Function<Integer, String>> justFunc =
        MaybeKindHelper.just(i -> "Result: " + i);
    Kind<MaybeKind.Witness, Function<Integer, String>> nothingFunc = MaybeKindHelper.nothing();

    Kind<MaybeKind.Witness, String> apApplied = maybeMonad.ap(justFunc, justNum);
    Kind<MaybeKind.Witness, String> apNothingFunc = maybeMonad.ap(nothingFunc, justNum);
    Kind<MaybeKind.Witness, String> apNothingVal = maybeMonad.ap(justFunc, nothingNum);

    System.out.println("Ap (Applied): " + MaybeKindHelper.unwrap(apApplied));
    System.out.println("Ap (Nothing Func): " + MaybeKindHelper.unwrap(apNothingFunc));
    System.out.println("Ap (Nothing Val): " + MaybeKindHelper.unwrap(apNothingVal));
  }

  void flatMapExample() {
    MaybeMonad maybeMonad = new MaybeMonad();
    Function<String, Kind<MaybeKind.Witness, Integer>> parseString =
        s -> {
          try {
            return MaybeKindHelper.just(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return MaybeKindHelper.nothing();
          }
        };

    Kind<MaybeKind.Witness, String> justFiveStr = MaybeKindHelper.just("5");
    Kind<MaybeKind.Witness, Integer> parsedJust = maybeMonad.flatMap(parseString, justFiveStr);

    Kind<MaybeKind.Witness, String> justNonNumStr = MaybeKindHelper.just("abc");
    Kind<MaybeKind.Witness, Integer> parsedNonNum = maybeMonad.flatMap(parseString, justNonNumStr);

    System.out.println("FlatMap (Just): " + MaybeKindHelper.unwrap(parsedJust));
    System.out.println("FlatMap (NonNum): " + MaybeKindHelper.unwrap(parsedNonNum));
  }

  void mapExample() {
    MaybeMonad maybeMonad = new MaybeMonad();
    Kind<MaybeKind.Witness, Integer> justNum = MaybeKindHelper.just(10);
    Kind<MaybeKind.Witness, Integer> nothingNum = MaybeKindHelper.nothing();

    Function<Integer, String> numToString = n -> "Val: " + n;
    Kind<MaybeKind.Witness, String> justStr = maybeMonad.map(numToString, justNum);
    Kind<MaybeKind.Witness, String> nothingStr = maybeMonad.map(numToString, nothingNum);

    Function<Integer, String> numToNull = n -> null;
    Kind<MaybeKind.Witness, String> mappedToNull = maybeMonad.map(numToNull, justNum);

    System.out.println("Map (Just): " + MaybeKindHelper.unwrap(justStr));
    System.out.println("Map (Nothing): " + MaybeKindHelper.unwrap(nothingStr));
    System.out.println("Map (To Null): " + MaybeKindHelper.unwrap(mappedToNull));
  }
}
