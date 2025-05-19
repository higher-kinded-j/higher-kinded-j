// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.optional;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;

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
    // OptionalKindHelper.unwrap(presentString) would be Optional.of("Number: 10")

    Kind<OptionalKind.Witness, String> emptyString = optionalMonad.map(intToString, emptyNumber);
    // OptionalKindHelper.unwrap(emptyString) would be Optional.empty()

    Function<Integer, String> intToNull = i -> null;
    Kind<OptionalKind.Witness, String> mappedToNull = optionalMonad.map(intToNull, presentNumber);
    // OptionalKindHelper.unwrap(mappedToNull) would be Optional.empty()

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
    // OptionalKindHelper.unwrap(parsedPresent) would be Optional.of(5)

    Kind<OptionalKind.Witness, Integer> parsedEmpty =
        optionalMonad.flatMap(parseToIntKind, emptyInput);
    // OptionalKindHelper.unwrap(parsedEmpty) would be Optional.empty()

    OptionalKind<String> nonNumericInput = OptionalKindHelper.wrap(Optional.of("abc"));
    Kind<OptionalKind.Witness, Integer> parsedNonNumeric =
        optionalMonad.flatMap(parseToIntKind, nonNumericInput);
    // OptionalKindHelper.unwrap(parsedNonNumeric) would be Optional.empty()

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

    // Both present
    Kind<OptionalKind.Witness, String> result1 =
        optionalMonad.ap(presentFuncKind, presentValueKind);
    // OptionalKindHelper.unwrap(result1) is Optional.of("Value: 100")

    // Function empty
    Kind<OptionalKind.Witness, String> result2 = optionalMonad.ap(emptyFuncKind, presentValueKind);
    // OptionalKindHelper.unwrap(result2) is Optional.empty()

    // Value empty
    Kind<OptionalKind.Witness, String> result3 = optionalMonad.ap(presentFuncKind, emptyValueKind);
    // OptionalKindHelper.unwrap(result3) is Optional.empty()

    System.out.println("Ap (Both Present): " + OptionalKindHelper.unwrap(result1));
    System.out.println("Ap (Function Empty): " + OptionalKindHelper.unwrap(result2));
    System.out.println("Ap (Value Empty): " + OptionalKindHelper.unwrap(result3));
  }

  public void handleErrorWithExample() {
    OptionalMonad optionalMonad = new OptionalMonad();

    Kind<OptionalKind.Witness, String> presentKind = OptionalKindHelper.wrap(Optional.of("Exists"));
    OptionalKind<String> emptyKind = OptionalKindHelper.wrap(Optional.empty());

    Function<Void, Kind<OptionalKind.Witness, String>> recoveryFunction =
        ignoredVoid -> OptionalKindHelper.wrap(Optional.of("Recovered Value"));

    // Handling error on a present OptionalKind
    Kind<OptionalKind.Witness, String> handledPresent =
        optionalMonad.handleErrorWith(presentKind, recoveryFunction);
    // OptionalKindHelper.unwrap(handledPresent) is Optional.of("Exists")

    // Handling error on an empty OptionalKind
    Kind<OptionalKind.Witness, String> handledEmpty =
        optionalMonad.handleErrorWith(emptyKind, recoveryFunction);

    // OptionalKindHelper.unwrap(handledEmpty) is Optional.of("Recovered Value")
    System.out.println("HandleError (Present): " + OptionalKindHelper.unwrap(handledPresent));
    System.out.println("HandleError (Empty): " + OptionalKindHelper.unwrap(handledEmpty));
  }

  public void monadExample() {
    OptionalMonad optionalMonad = new OptionalMonad();

    // 1. Create OptionalKind instances
    OptionalKind<Integer> presentIntKind = OptionalKindHelper.wrap(Optional.of(10));
    Kind<OptionalKind.Witness, Integer> emptyIntKind =
        optionalMonad.raiseError(null); // Creates empty

    // 2. Use map
    Function<Integer, String> intToMessage = n -> "Value is " + n;
    Kind<OptionalKind.Witness, String> mappedPresent =
        optionalMonad.map(intToMessage, presentIntKind);
    Kind<OptionalKind.Witness, String> mappedEmpty = optionalMonad.map(intToMessage, emptyIntKind);

    System.out.println(
        "Mapped (Present): " + OptionalKindHelper.unwrap(mappedPresent)); // Optional[Value is 10]
    System.out.println(
        "Mapped (Empty): " + OptionalKindHelper.unwrap(mappedEmpty)); // Optional.empty

    // 3. Use flatMap
    Function<Integer, Kind<OptionalKind.Witness, Double>> intToOptionalDouble =
        n -> (n > 0) ? optionalMonad.of(n / 2.0) : optionalMonad.raiseError(null);

    Kind<OptionalKind.Witness, Double> flatMappedPresent =
        optionalMonad.flatMap(intToOptionalDouble, presentIntKind);
    Kind<OptionalKind.Witness, Double> flatMappedEmpty =
        optionalMonad.flatMap(intToOptionalDouble, emptyIntKind);
    Kind<OptionalKind.Witness, Integer> zeroIntKind = optionalMonad.of(0);
    Kind<OptionalKind.Witness, Double> flatMappedZero =
        optionalMonad.flatMap(intToOptionalDouble, zeroIntKind);

    System.out.println(
        "FlatMapped (Present): " + OptionalKindHelper.unwrap(flatMappedPresent)); // Optional[5.0]
    System.out.println(
        "FlatMapped (Empty): " + OptionalKindHelper.unwrap(flatMappedEmpty)); // Optional.empty
    System.out.println(
        "FlatMapped (Zero): " + OptionalKindHelper.unwrap(flatMappedZero)); // Optional.empty

    // 4. Use 'of' and 'raiseError' (already shown in creation)
    Kind<OptionalKind.Witness, String> kindFromValue =
        optionalMonad.of("World"); // Wraps Optional.of("World")
    Kind<OptionalKind.Witness, Integer> kindFromNullValue =
        optionalMonad.of(null); // Wraps Optional.empty()

    // 5. Use handleErrorWith
    Function<Void, Kind<OptionalKind.Witness, Integer>> recoverWithDefault =
        v -> optionalMonad.of(-1); // Default value if empty

    Kind<OptionalKind.Witness, Integer> recoveredFromEmpty =
        optionalMonad.handleErrorWith(emptyIntKind, recoverWithDefault);
    Kind<OptionalKind.Witness, Integer> notRecoveredFromPresent =
        optionalMonad.handleErrorWith(presentIntKind, recoverWithDefault);

    System.out.println(
        "Recovered (from Empty): " + OptionalKindHelper.unwrap(recoveredFromEmpty)); // Optional[-1]
    System.out.println(
        "Recovered (from Present): "
            + OptionalKindHelper.unwrap(notRecoveredFromPresent)); // Optional[10]

    // Unwrap to get back the standard Optional
    Optional<String> finalMappedOptional = OptionalKindHelper.unwrap(mappedPresent);
    System.out.println("Final unwrapped mapped optional: " + finalMappedOptional);
  }
}
