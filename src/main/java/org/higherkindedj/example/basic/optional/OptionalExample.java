// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.optional;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
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
    OptionalMonad optionalMonad = new OptionalMonad();
    OptionalKind<Integer> presentNumber = OptionalKindHelper.wrap(Optional.of(10));
    OptionalKind<Integer> emptyNumber = OptionalKindHelper.wrap(Optional.empty());

    Function<Integer, String> intToString = i -> "Number: " + i;
    Kind<OptionalKind.Witness, String> presentString =
        optionalMonad.map(intToString, presentNumber);

    Kind<OptionalKind.Witness, String> emptyString = optionalMonad.map(intToString, emptyNumber);

    Function<Integer, String> intToNull = i -> null;
    Kind<OptionalKind.Witness, String> mappedToNull = optionalMonad.map(intToNull, presentNumber);

    System.out.println("Map (Present): " + OptionalKindHelper.unwrap(presentString));
    System.out.println("Map (Empty): " + OptionalKindHelper.unwrap(emptyString));
    System.out.println("Map (To Null): " + OptionalKindHelper.unwrap(mappedToNull));
  }

  public void flatMapExample() {
    OptionalMonad optionalMonad = new OptionalMonad();
    OptionalKind<String> presentInput = OptionalKindHelper.wrap(Optional.of("5"));
    OptionalKind<String> emptyInput = OptionalKindHelper.wrap(Optional.empty());

    Function<String, Kind<OptionalKind.Witness, Integer>> parseToIntKind =
        s -> {
          try {
            return OptionalKindHelper.wrap(Optional.of(Integer.parseInt(s)));
          } catch (NumberFormatException e) {
            return OptionalKindHelper.wrap(Optional.empty());
          }
        };

    Kind<OptionalKind.Witness, Integer> parsedPresent =
        optionalMonad.flatMap(parseToIntKind, presentInput);

    Kind<OptionalKind.Witness, Integer> parsedEmpty =
        optionalMonad.flatMap(parseToIntKind, emptyInput);

    OptionalKind<String> nonNumericInput = OptionalKindHelper.wrap(Optional.of("abc"));
    Kind<OptionalKind.Witness, Integer> parsedNonNumeric =
        optionalMonad.flatMap(parseToIntKind, nonNumericInput);

    System.out.println("FlatMap (Present): " + OptionalKindHelper.unwrap(parsedPresent));
    System.out.println("FlatMap (Empty Input): " + OptionalKindHelper.unwrap(parsedEmpty));
    System.out.println("FlatMap (Non-numeric): " + OptionalKindHelper.unwrap(parsedNonNumeric));
  }

  public void apExample() {
    OptionalMonad optionalMonad = new OptionalMonad();

    OptionalKind<Function<Integer, String>> presentFuncKind =
        OptionalKindHelper.wrap(Optional.of(i -> "Value: " + i));
    OptionalKind<Function<Integer, String>> emptyFuncKind =
        OptionalKindHelper.wrap(Optional.empty());

    OptionalKind<Integer> presentValueKind = OptionalKindHelper.wrap(Optional.of(100));
    OptionalKind<Integer> emptyValueKind = OptionalKindHelper.wrap(Optional.empty());

    Kind<OptionalKind.Witness, String> result1 =
        optionalMonad.ap(presentFuncKind, presentValueKind);

    Kind<OptionalKind.Witness, String> result2 = optionalMonad.ap(emptyFuncKind, presentValueKind);

    Kind<OptionalKind.Witness, String> result3 = optionalMonad.ap(presentFuncKind, emptyValueKind);

    System.out.println("Ap (Both Present): " + OptionalKindHelper.unwrap(result1));
    System.out.println("Ap (Function Empty): " + OptionalKindHelper.unwrap(result2));
    System.out.println("Ap (Value Empty): " + OptionalKindHelper.unwrap(result3));
  }

  public void handleErrorWithExample() {
    OptionalMonad optionalMonad = new OptionalMonad();

    Kind<OptionalKind.Witness, String> presentKind = OptionalKindHelper.wrap(Optional.of("Exists"));
    OptionalKind<String> emptyKind = OptionalKindHelper.wrap(Optional.empty());

    Function<Unit, Kind<OptionalKind.Witness, String>> recoveryFunction =
        ignoredUnit -> OptionalKindHelper.wrap(Optional.of("Recovered Value"));

    Kind<OptionalKind.Witness, String> handledPresent =
        optionalMonad.handleErrorWith(presentKind, recoveryFunction);

    Kind<OptionalKind.Witness, String> handledEmpty =
        optionalMonad.handleErrorWith(emptyKind, recoveryFunction);

    System.out.println("HandleError (Present): " + OptionalKindHelper.unwrap(handledPresent));
    System.out.println("HandleError (Empty): " + OptionalKindHelper.unwrap(handledEmpty));
  }

  public void monadExample() {
    OptionalMonad optionalMonad = new OptionalMonad();

    OptionalKind<Integer> presentIntKind = OptionalKindHelper.wrap(Optional.of(10));

    Kind<OptionalKind.Witness, Integer> emptyIntKind = optionalMonad.raiseError(Unit.INSTANCE);

    Function<Integer, String> intToMessage = n -> "Value is " + n;
    Kind<OptionalKind.Witness, String> mappedPresent =
        optionalMonad.map(intToMessage, presentIntKind);
    Kind<OptionalKind.Witness, String> mappedEmpty = optionalMonad.map(intToMessage, emptyIntKind);

    System.out.println("Mapped (Present): " + OptionalKindHelper.unwrap(mappedPresent));
    System.out.println("Mapped (Empty): " + OptionalKindHelper.unwrap(mappedEmpty));

    Function<Integer, Kind<OptionalKind.Witness, Double>> intToOptionalDouble =
        n -> (n > 0) ? optionalMonad.of(n / 2.0) : optionalMonad.raiseError(Unit.INSTANCE);

    Kind<OptionalKind.Witness, Double> flatMappedPresent =
        optionalMonad.flatMap(intToOptionalDouble, presentIntKind);
    Kind<OptionalKind.Witness, Double> flatMappedEmpty =
        optionalMonad.flatMap(intToOptionalDouble, emptyIntKind);
    Kind<OptionalKind.Witness, Integer> zeroIntKind = optionalMonad.of(0);
    Kind<OptionalKind.Witness, Double> flatMappedZero =
        optionalMonad.flatMap(intToOptionalDouble, zeroIntKind);

    System.out.println("FlatMapped (Present): " + OptionalKindHelper.unwrap(flatMappedPresent));
    System.out.println("FlatMapped (Empty): " + OptionalKindHelper.unwrap(flatMappedEmpty));
    System.out.println("FlatMapped (Zero): " + OptionalKindHelper.unwrap(flatMappedZero));

    Kind<OptionalKind.Witness, String> kindFromValue = optionalMonad.of("World");
    Kind<OptionalKind.Witness, Integer> kindFromNullValue = optionalMonad.of(null);

    Function<Unit, Kind<OptionalKind.Witness, Integer>> recoverWithDefault =
        unitVal -> optionalMonad.of(-1);

    Kind<OptionalKind.Witness, Integer> recoveredFromEmpty =
        optionalMonad.handleErrorWith(emptyIntKind, recoverWithDefault);
    Kind<OptionalKind.Witness, Integer> notRecoveredFromPresent =
        optionalMonad.handleErrorWith(presentIntKind, recoverWithDefault);

    System.out.println("Recovered (from Empty): " + OptionalKindHelper.unwrap(recoveredFromEmpty));
    System.out.println(
        "Recovered (from Present): " + OptionalKindHelper.unwrap(notRecoveredFromPresent));

    Optional<String> finalMappedOptional = OptionalKindHelper.unwrap(mappedPresent);
    System.out.println("Final unwrapped mapped optional: " + finalMappedOptional);
  }
}
