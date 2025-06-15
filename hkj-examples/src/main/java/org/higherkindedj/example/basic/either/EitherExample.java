// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.NoSuchElementException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;

/** see {<a href="https://higher-kinded-j.github.io/either_monad.html">Either Monad</a>} */
public class EitherExample {
  public static void main(String[] args) {
    EitherExample example = new EitherExample();
    example.basicUsage();
    example.basicFlatMap();
    example.basicMonadExample();
  }

  public void basicUsage() {
    // Success case
    Either<String, Integer> success = Either.right(123);

    // Failure case
    Either<String, Integer> failure = Either.left("File not found");

    if (success.isRight()) {
      System.out.println("It's Right!");
    }
    if (failure.isLeft()) {
      System.out.println("It's Left!");
    }

    // Null values are permitted in Left or Right by default in this implementation
    Either<String, Integer> rightNull = Either.right(null);
    Either<String, Integer> leftNull = Either.left(null);

    try {
      Integer value = success.getRight(); // Returns 123
      String error = failure.getLeft(); // Returns "File not found"
      // String errorFromSuccess = success.getLeft(); // Throws NoSuchElementException
    } catch (NoSuchElementException e) {
      System.err.println("Attempted to get the wrong side: " + e.getMessage());
    }

    // Fold
    String resultMessage =
        failure.fold(
            leftValue -> "Operation failed with: " + leftValue, // Function for Left
            rightValue -> "Operation succeeded with: " + rightValue // Function for Right
            );
    // resultMessage will be "Operation failed with: File not found"

    String successMessage =
        success.fold(leftValue -> "Error: " + leftValue, rightValue -> "Success: " + rightValue);
    // successMessage will be "Success: 123"

    Function<Integer, String> intToString = Object::toString;

    Either<String, String> mappedSuccess = success.map(intToString); // Right(123) -> Right("123")
    Either<String, String> mappedFailure =
        failure.map(intToString); // Left(...) -> Left(...) unchanged

    System.out.println(mappedSuccess); // Output: Right(value=123)
    System.out.println(mappedFailure); // Output: Left(value=File not found)
  }

  public void basicFlatMap() {

    // Example: Parse string, then check if positive
    Function<String, Either<String, Integer>> parse =
        s -> {
          try {
            return Either.right(Integer.parseInt(s.trim()));
          } catch (NumberFormatException e) {
            return Either.left("Invalid number");
          }
        };
    Function<Integer, Either<String, Integer>> checkPositive =
        i -> (i > 0) ? Either.right(i) : Either.left("Number not positive");

    Either<String, String> input1 = Either.right(" 10 ");
    Either<String, String> input2 = Either.right(" -5 ");
    Either<String, String> input3 = Either.right(" abc ");
    Either<String, String> input4 = Either.left("Initial error");

    // Chain parse then checkPositive
    Either<String, Integer> result1 = input1.flatMap(parse).flatMap(checkPositive); // Right(10)
    Either<String, Integer> result2 =
        input2.flatMap(parse).flatMap(checkPositive); // Left("Number not positive")
    Either<String, Integer> result3 =
        input3.flatMap(parse).flatMap(checkPositive); // Left("Invalid number")
    Either<String, Integer> result4 =
        input4.flatMap(parse).flatMap(checkPositive); // Left("Initial error")

    System.out.println(result1);
    System.out.println(result2);
    System.out.println(result3);
    System.out.println(result4);
  }

  public void basicMonadExample() {

    EitherMonad<String> eitherMonad = EitherMonad.instance();

    Either<String, Integer> myEither = Either.right(10);
    // F_WITNESS is EitherKind.Witness<String>, A is Integer
    Kind<EitherKind.Witness<String>, Integer> eitherKind = EITHER.widen(myEither);

    // Using map via the Monad instance
    Kind<EitherKind.Witness<String>, String> mappedKind =
        eitherMonad.map(Object::toString, eitherKind);
    System.out.println("mappedKind: " + EITHER.narrow(mappedKind)); // Output: Right[value = 10]

    // Using flatMap via the Monad instance
    Function<Integer, Kind<EitherKind.Witness<String>, Double>> nextStep =
        i -> EITHER.widen((i > 5) ? Either.right(i / 2.0) : Either.left("TooSmall"));
    Kind<EitherKind.Witness<String>, Double> flatMappedKind =
        eitherMonad.flatMap(nextStep, eitherKind);

    // Creating a Left Kind using raiseError
    Kind<EitherKind.Witness<String>, Integer> errorKind =
        eitherMonad.raiseError("E101"); // L is String here

    // Handling an error
    Kind<EitherKind.Witness<String>, Integer> handledKind =
        eitherMonad.handleErrorWith(
            errorKind,
            error -> {
              System.out.println("Handling error: " + error);
              return eitherMonad.of(0); // Recover with Right(0)
            });

    Either<String, Integer> finalEither = EITHER.narrow(handledKind);
    System.out.println("Final unwrapped Either: " + finalEither); // Output: Right(0)
  }
}
