// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.maybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;

public class MaybeExample {

  public static <A, B> Kind<MaybeKind.Witness, B> processData(
      Kind<MaybeKind.Witness, A> inputKind,
      Function<A, B> mapper,
      B defaultValueOnAbsence,
      MaybeMonad monad) {
    // inputKind is now Kind<MaybeKind.Witness, A>, which is compatible with monad.map
    Kind<MaybeKind.Witness, B> mappedKind = monad.map(mapper, inputKind);

    // The result of monad.map is Kind<MaybeKind.Witness, B>.
    // The handler (Void v) -> monad.of(defaultValueOnAbsence) also produces Kind<MaybeKind.Witness,
    // B>.
    return monad.handleErrorWith(mappedKind, (Void v) -> monad.of(defaultValueOnAbsence));
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

    // 1. Create MaybeKind instances
    Kind<MaybeKind.Witness, Integer> presentIntKind = MaybeKindHelper.just(100);
    Kind<MaybeKind.Witness, Integer> absentIntKind = MaybeKindHelper.nothing();
    Kind<MaybeKind.Witness, String> nullInputStringKind = maybeMonad.of(null); // Becomes Nothing

    // 2. Use map
    Function<Integer, String> intToStatus = n -> "Status: " + n;
    Kind<MaybeKind.Witness, String> mappedPresent = maybeMonad.map(intToStatus, presentIntKind);
    Kind<MaybeKind.Witness, String> mappedAbsent = maybeMonad.map(intToStatus, absentIntKind);

    System.out.println(
        "Mapped (Present): " + MaybeKindHelper.unwrap(mappedPresent)); // Just(Status: 100)
    System.out.println("Mapped (Absent): " + MaybeKindHelper.unwrap(mappedAbsent)); // Nothing

    // 3. Use flatMap
    Function<Integer, Kind<MaybeKind.Witness, String>> intToPositiveStatusKind =
        n -> (n > 0) ? maybeMonad.of("Positive: " + n) : MaybeKindHelper.nothing();

    Kind<MaybeKind.Witness, String> flatMappedPresent =
        maybeMonad.flatMap(intToPositiveStatusKind, presentIntKind);
    Kind<MaybeKind.Witness, String> flatMappedZero =
        maybeMonad.flatMap(intToPositiveStatusKind, maybeMonad.of(0)); // 0 is not > 0

    System.out.println(
        "FlatMapped (Present Positive): "
            + MaybeKindHelper.unwrap(flatMappedPresent)); // Just(Positive: 100)
    System.out.println("FlatMapped (Zero): " + MaybeKindHelper.unwrap(flatMappedZero)); // Nothing

    // 4. Use 'of' and 'raiseError'
    Kind<MaybeKind.Witness, String> fromOf = maybeMonad.of("Direct Value");
    Kind<MaybeKind.Witness, String> fromRaiseError = maybeMonad.raiseError(null); // Creates Nothing
    System.out.println("From 'of': " + MaybeKindHelper.unwrap(fromOf)); // Just(Direct Value)
    System.out.println("From 'raiseError': " + MaybeKindHelper.unwrap(fromRaiseError)); // Nothing
    System.out.println(
        "From 'of(null)': " + MaybeKindHelper.unwrap(nullInputStringKind)); // Nothing

    // 5. Use handleErrorWith
    Function<Void, Kind<MaybeKind.Witness, Integer>> recoverWithDefault =
        v -> maybeMonad.of(-1); // Default value if absent

    Kind<MaybeKind.Witness, Integer> recoveredFromAbsent =
        maybeMonad.handleErrorWith(absentIntKind, recoverWithDefault);
    Kind<MaybeKind.Witness, Integer> notRecoveredFromPresent =
        maybeMonad.handleErrorWith(presentIntKind, recoverWithDefault);

    System.out.println(
        "Recovered (from Absent): " + MaybeKindHelper.unwrap(recoveredFromAbsent)); // Just(-1)
    System.out.println(
        "Recovered (from Present): "
            + MaybeKindHelper.unwrap(notRecoveredFromPresent)); // Just(100)

    // Using the generic processData function
    Kind<MaybeKind.Witness, String> processedPresent =
        processData(presentIntKind, x -> "Processed: " + x, "N/A", maybeMonad);
    Kind<MaybeKind.Witness, String> processedAbsent =
        processData(absentIntKind, x -> "Processed: " + x, "N/A", maybeMonad);

    System.out.println(
        "Generic Process (Present): "
            + MaybeKindHelper.unwrap(processedPresent)); // Just(Processed: 100)
    System.out.println(
        "Generic Process (Absent): " + MaybeKindHelper.unwrap(processedAbsent)); // Just(N/A)

    // Unwrap to get back the standard Maybe
    Maybe<String> finalMappedMaybe = MaybeKindHelper.unwrap(mappedPresent);
    System.out.println("Final unwrapped mapped maybe: " + finalMappedMaybe); // Just(Status: 100)
  }

  void handleErrorWithExample() {
    MaybeMonad maybeMonad = new MaybeMonad();
    Function<Void, Kind<MaybeKind.Witness, String>> recover =
        v -> MaybeKindHelper.just("Recovered");

    Kind<MaybeKind.Witness, String> handledJust =
        maybeMonad.handleErrorWith(MaybeKindHelper.just("Original"), recover); // Just("Original")
    Kind<MaybeKind.Witness, String> handledNothing =
        maybeMonad.handleErrorWith(MaybeKindHelper.nothing(), recover); // Just("Recovered")

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

    Kind<MaybeKind.Witness, String> apApplied =
        maybeMonad.ap(justFunc, justNum); // Just("Result: 10")
    Kind<MaybeKind.Witness, String> apNothingFunc = maybeMonad.ap(nothingFunc, justNum); // Nothing
    Kind<MaybeKind.Witness, String> apNothingVal = maybeMonad.ap(justFunc, nothingNum); // Nothing

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
    Kind<MaybeKind.Witness, Integer> parsedJust =
        maybeMonad.flatMap(parseString, justFiveStr); // Just(5)

    Kind<MaybeKind.Witness, String> justNonNumStr = MaybeKindHelper.just("abc");
    Kind<MaybeKind.Witness, Integer> parsedNonNum =
        maybeMonad.flatMap(parseString, justNonNumStr); // Nothing

    System.out.println("FlatMap (Just): " + MaybeKindHelper.unwrap(parsedJust));
    System.out.println("FlatMap (NonNum): " + MaybeKindHelper.unwrap(parsedNonNum));
  }

  void mapExample() {
    MaybeMonad maybeMonad = new MaybeMonad();
    Kind<MaybeKind.Witness, Integer> justNum = MaybeKindHelper.just(10);
    Kind<MaybeKind.Witness, Integer> nothingNum = MaybeKindHelper.nothing();

    Function<Integer, String> numToString = n -> "Val: " + n;
    Kind<MaybeKind.Witness, String> justStr =
        maybeMonad.map(numToString, justNum); // Just("Val: 10")
    Kind<MaybeKind.Witness, String> nothingStr = maybeMonad.map(numToString, nothingNum); // Nothing

    Function<Integer, String> numToNull = n -> null;
    Kind<MaybeKind.Witness, String> mappedToNull = maybeMonad.map(numToNull, justNum); // Nothing

    System.out.println("Map (Just): " + MaybeKindHelper.unwrap(justStr));
    System.out.println("Map (Nothing): " + MaybeKindHelper.unwrap(nothingStr));
    System.out.println("Map (To Null): " + MaybeKindHelper.unwrap(mappedToNull));
  }
}
