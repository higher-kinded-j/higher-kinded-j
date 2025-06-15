// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.unit.Unit;

/** see {<a href="https://higher-kinded-j.github.io/optional_monad.html">Optional Monad</a>} */
public class OptionalExample {

  public static void main(String[] args) {
    OptionalExample example = new OptionalExample();
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

  public void mapExample() {
    OptionalMonad optionalMonad = OptionalMonad.INSTANCE;
    // Using OPTIONAL.widen which calls the instance method on OptionalKindHelper.OPTIONAL
    Kind<OptionalKind.Witness, Integer> presentNumber = OPTIONAL.widen(Optional.of(10));
    Kind<OptionalKind.Witness, Integer> emptyNumber = OPTIONAL.widen(Optional.empty());

    Function<Integer, String> intToString = i -> "Number: " + i;
    Kind<OptionalKind.Witness, String> presentString =
        optionalMonad.map(intToString, presentNumber);

    Kind<OptionalKind.Witness, String> emptyString = optionalMonad.map(intToString, emptyNumber);

    Function<Integer, String> intToNull =
        i -> null; // Optional.ofNullable(null) will result in Optional.empty()
    Kind<OptionalKind.Witness, String> mappedToNull = optionalMonad.map(intToNull, presentNumber);

    // Using OPTIONAL.narrow which calls the instance method on OptionalKindHelper.OPTIONAL
    System.out.println("Map (Present): " + OPTIONAL.narrow(presentString));
    System.out.println("Map (Empty): " + OPTIONAL.narrow(emptyString));
    System.out.println("Map (To Null): " + OPTIONAL.narrow(mappedToNull));
  }

  public void flatMapExample() {
    OptionalMonad optionalMonad = OptionalMonad.INSTANCE;
    Kind<OptionalKind.Witness, String> presentInput = OPTIONAL.widen(Optional.of("5"));
    Kind<OptionalKind.Witness, String> emptyInput = OPTIONAL.widen(Optional.empty());

    Function<String, Kind<OptionalKind.Witness, Integer>> parseToIntKind =
        s -> {
          try {
            return OPTIONAL.widen(Optional.of(Integer.parseInt(s)));
          } catch (NumberFormatException e) {
            return OPTIONAL.widen(Optional.empty());
          }
        };

    Kind<OptionalKind.Witness, Integer> parsedPresent =
        optionalMonad.flatMap(parseToIntKind, presentInput);

    Kind<OptionalKind.Witness, Integer> parsedEmpty =
        optionalMonad.flatMap(parseToIntKind, emptyInput);

    Kind<OptionalKind.Witness, String> nonNumericInput = OPTIONAL.widen(Optional.of("abc"));
    Kind<OptionalKind.Witness, Integer> parsedNonNumeric =
        optionalMonad.flatMap(parseToIntKind, nonNumericInput);

    System.out.println("FlatMap (Present): " + OPTIONAL.narrow(parsedPresent));
    System.out.println("FlatMap (Empty Input): " + OPTIONAL.narrow(parsedEmpty));
    System.out.println("FlatMap (Non-numeric): " + OPTIONAL.narrow(parsedNonNumeric));
  }

  public void apExample() {
    OptionalMonad optionalMonad = OptionalMonad.INSTANCE;

    Kind<OptionalKind.Witness, Function<Integer, String>> presentFuncKind =
        OPTIONAL.widen(Optional.of(i -> "Value: " + i));
    Kind<OptionalKind.Witness, Function<Integer, String>> emptyFuncKind =
        OPTIONAL.widen(Optional.empty());

    Kind<OptionalKind.Witness, Integer> presentValueKind = OPTIONAL.widen(Optional.of(100));
    Kind<OptionalKind.Witness, Integer> emptyValueKind = OPTIONAL.widen(Optional.empty());

    Kind<OptionalKind.Witness, String> result1 =
        optionalMonad.ap(presentFuncKind, presentValueKind);

    Kind<OptionalKind.Witness, String> result2 = optionalMonad.ap(emptyFuncKind, presentValueKind);

    Kind<OptionalKind.Witness, String> result3 = optionalMonad.ap(presentFuncKind, emptyValueKind);

    System.out.println("Ap (Both Present): " + OPTIONAL.narrow(result1));
    System.out.println("Ap (Function Empty): " + OPTIONAL.narrow(result2));
    System.out.println("Ap (Value Empty): " + OPTIONAL.narrow(result3));
  }

  public void handleErrorWithExample() {
    OptionalMonad optionalMonad = OptionalMonad.INSTANCE;

    Kind<OptionalKind.Witness, String> presentKind = OPTIONAL.widen(Optional.of("Exists"));
    Kind<OptionalKind.Witness, String> emptyKind = OPTIONAL.widen(Optional.empty());

    Function<Unit, Kind<OptionalKind.Witness, String>> recoveryFunction =
        ignoredUnit -> OPTIONAL.widen(Optional.of("Recovered Value"));

    Kind<OptionalKind.Witness, String> handledPresent =
        optionalMonad.handleErrorWith(presentKind, recoveryFunction);

    Kind<OptionalKind.Witness, String> handledEmpty =
        optionalMonad.handleErrorWith(emptyKind, recoveryFunction);

    System.out.println("HandleError (Present): " + OPTIONAL.narrow(handledPresent));
    System.out.println("HandleError (Empty): " + OPTIONAL.narrow(handledEmpty));
  }

  public void monadExample() {
    OptionalMonad optionalMonad = OptionalMonad.INSTANCE;

    Kind<OptionalKind.Witness, Integer> presentIntKind = OPTIONAL.widen(Optional.of(10));

    Kind<OptionalKind.Witness, Integer> emptyIntKind = optionalMonad.raiseError(Unit.INSTANCE);

    Function<Integer, String> intToMessage = n -> "Value is " + n;
    Kind<OptionalKind.Witness, String> mappedPresent =
        optionalMonad.map(intToMessage, presentIntKind);
    Kind<OptionalKind.Witness, String> mappedEmpty = optionalMonad.map(intToMessage, emptyIntKind);

    System.out.println("Mapped (Present): " + OPTIONAL.narrow(mappedPresent));
    System.out.println("Mapped (Empty): " + OPTIONAL.narrow(mappedEmpty));

    Function<Integer, Kind<OptionalKind.Witness, Double>> intToOptionalDouble =
        n -> (n > 0) ? optionalMonad.of(n / 2.0) : optionalMonad.raiseError(Unit.INSTANCE);

    Kind<OptionalKind.Witness, Double> flatMappedPresent =
        optionalMonad.flatMap(intToOptionalDouble, presentIntKind);
    Kind<OptionalKind.Witness, Double> flatMappedEmpty =
        optionalMonad.flatMap(intToOptionalDouble, emptyIntKind);
    Kind<OptionalKind.Witness, Integer> zeroIntKind =
        optionalMonad.of(0); // OPTIONAL.widen(Optional.of(0)) would also work
    Kind<OptionalKind.Witness, Double> flatMappedZero =
        optionalMonad.flatMap(intToOptionalDouble, zeroIntKind);

    System.out.println("FlatMapped (Present): " + OPTIONAL.narrow(flatMappedPresent));
    System.out.println("FlatMapped (Empty): " + OPTIONAL.narrow(flatMappedEmpty));
    System.out.println("FlatMapped (Zero): " + OPTIONAL.narrow(flatMappedZero));

    Function<Unit, Kind<OptionalKind.Witness, Integer>> recoverWithDefault =
        unitVal -> optionalMonad.of(-1); // OPTIONAL.widen(Optional.of(-1)) would also work

    Kind<OptionalKind.Witness, Integer> recoveredFromEmpty =
        optionalMonad.handleErrorWith(emptyIntKind, recoverWithDefault);
    Kind<OptionalKind.Witness, Integer> notRecoveredFromPresent =
        optionalMonad.handleErrorWith(presentIntKind, recoverWithDefault);

    System.out.println("Recovered (from Empty): " + OPTIONAL.narrow(recoveredFromEmpty));
    System.out.println("Recovered (from Present): " + OPTIONAL.narrow(notRecoveredFromPresent));

    Optional<String> finalMappedOptional = OPTIONAL.narrow(mappedPresent);
    System.out.println("Final unwrapped mapped optional: " + finalMappedOptional);
  }
}
