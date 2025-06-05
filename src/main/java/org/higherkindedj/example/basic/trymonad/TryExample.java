// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryMonadError;

/** see {<a href="https://higher-kinded-j.github.io/try_monad.html">Try Monad</a>} */
public class TryExample {
  public static void main(String[] args) {
    TryExample example = new TryExample();
    example.basicExample();
    example.basicMonadExample();
  }

  public void basicExample() {
    Try<Integer> initialSuccess = Try.success(5);
    Try<String> mappedSuccess =
        initialSuccess.map(value -> "Value: " + value); // Success("Value: 5")

    Try<Integer> initialFailure = Try.failure(new RuntimeException("Fail"));
    Try<String> mappedFailure =
        initialFailure.map(value -> "Value: " + value); // Failure(RuntimeException)

    Try<Integer> mapThrows =
        initialSuccess.map(
            value -> {
              throw new NullPointerException();
            }); // Failure(NullPointerException)

    Function<Integer, Try<Double>> safeDivide =
        value ->
            (value == 0)
                ? Try.failure(new ArithmeticException("Div by zero"))
                : Try.success(10.0 / value);

    Try<Integer> inputSuccess = Try.success(2);
    Try<Double> result1 = inputSuccess.flatMap(safeDivide); // Success(5.0)

    Try<Integer> inputZero = Try.success(0);
    Try<Double> result2 = inputZero.flatMap(safeDivide); // Failure(ArithmeticException)

    Try<Integer> inputFailure = Try.failure(new RuntimeException("Initial fail"));
    Try<Double> result3 =
        inputFailure.flatMap(safeDivide); // Failure(RuntimeException) - initial failure propagates

    String message =
        result2.fold(
            successValue -> "Succeeded with " + successValue,
            failureThrowable ->
                "Failed with " + failureThrowable.getMessage()); // "Failed with Div by zero"

    Function<Throwable, Double> recoverHandler = throwable -> -1.0;
    Try<Double> recovered1 = result2.recover(recoverHandler); // Success(-1.0)
    Try<Double> recovered2 = result1.recover(recoverHandler); // Stays Success(5.0)

    Function<Throwable, Try<Double>> recoverWithHandler =
        throwable ->
            (throwable instanceof ArithmeticException)
                ? Try.success(Double.POSITIVE_INFINITY)
                : Try.failure(throwable);

    Try<Double> recoveredWith1 = result2.recoverWith(recoverWithHandler); // Success(Infinity)
    Try<Double> recoveredWith2 =
        result3.recoverWith(recoverWithHandler); // Failure(RuntimeException) - re-raised
  }

  public void basicMonadExample() {
    TryMonadError tryMonad = new TryMonadError();

    Kind<TryKind.Witness, Integer> tryKind1 = TRY.tryOf(() -> 10 / 2); // Success(5) Kind
    Kind<TryKind.Witness, Integer> tryKind2 = TRY.tryOf(() -> 10 / 0); // Failure(...) Kind

    // Map using Monad instance
    Kind<TryKind.Witness, String> mappedKind =
        tryMonad.map(Object::toString, tryKind1); // Success("5") Kind

    // FlatMap using Monad instance
    Function<Integer, Kind<TryKind.Witness, Double>> safeDivideKind =
        i -> TRY.tryOf(() -> 10.0 / i);
    Kind<TryKind.Witness, Double> flatMappedKind =
        tryMonad.flatMap(safeDivideKind, tryKind1); // Success(2.0) Kind

    // Handle error using MonadError instance
    Kind<TryKind.Witness, Integer> handledKind =
        tryMonad.handleErrorWith(
            tryKind2, // The Failure Kind
            error -> TRY.success(-1) // Recover to Success(-1) Kind
            );

    // Unwrap
    Try<String> mappedTry = TRY.narrow(mappedKind); // Success("5")
    Try<Double> flatMappedTry = TRY.narrow(flatMappedKind); // Success(2.0)
    Try<Integer> handledTry = TRY.narrow(handledKind); // Success(-1)

    System.out.println(mappedTry);
    System.out.println(flatMappedTry);
    System.out.println(handledTry);
  }
}
