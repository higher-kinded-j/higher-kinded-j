// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 05: Error Handling Patterns
 *
 * <p>Learn how to handle errors gracefully using Either's fold() method and Try's recovery methods.
 *
 * <p>Key Concepts: - fold: pattern match on Either to handle both error and success cases -
 * Try.recover: recover from exceptions with a default value - MonadError typeclass: raiseError for
 * creating error values
 *
 * <p>Types covered: - Either<E, A>: explicit error type E - Try<A>: error is Throwable
 */
public class Tutorial05_MonadErrorHandling_Solution {

  /**
   * Exercise 1: Raising errors
   *
   * <p>raiseError creates an error value in the error channel.
   *
   * <p>Task: Use the MonadError instance to raise an error
   */
  @Test
  void exercise1_raisingErrors() {
    EitherMonad<String> monad = EitherMonad.instance();

    // Solution: Use monad.raiseError to create an error value
    Kind<EitherKind.Witness<String>, Integer> error = monad.raiseError("Invalid input");

    Either<String, Integer> result = EITHER.narrow(error);
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Invalid input");
  }

  /**
   * Exercise 2: Handling errors with fold
   *
   * <p>fold() allows you to pattern match on Either, providing different logic for Left and Right.
   *
   * <p>Task: Recover from a parse error using fold
   */
  @Test
  void exercise2_handleErrorWith() {
    Function<String, Either<String, Integer>> parse =
        s -> {
          try {
            return Either.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Either.left("Parse error");
          }
        };

    Either<String, Integer> failed = parse.apply("not-a-number");

    // Solution: Use fold to handle the error case and return 0
    Integer recovered = failed.fold(error -> 0, value -> value);

    assertThat(recovered).isEqualTo(0);
  }

  /**
   * Exercise 3: Recovering with a plain value using fold
   *
   * <p>fold() can be used to recover from an error by providing a default value.
   *
   * <p>Task: Provide a default value for errors
   */
  @Test
  void exercise3_recoverWithValue() {
    Either<String, Integer> error = Either.left("Database connection failed");

    // Solution: Use fold to recover with -1 for any error
    Integer recovered = error.fold(err -> -1, value -> value);

    assertThat(recovered).isEqualTo(-1);
  }

  /**
   * Exercise 4: Conditional error recovery
   *
   * <p>You can inspect the error and decide whether to recover or not using fold.
   *
   * <p>Task: Recover only from specific errors
   */
  @Test
  void exercise4_conditionalRecovery() {
    Either<String, Integer> error = Either.left("NOT_FOUND");

    // Solution: Check error type and return 0 for NOT_FOUND, -999 for other errors
    Integer recovered =
        error.fold(
            err -> {
              if (err.equals("NOT_FOUND")) {
                return 0;
              } else {
                return -999;
              }
            },
            value -> value);

    assertThat(recovered).isEqualTo(0);
  }

  /**
   * Exercise 5: Error handling in a chain
   *
   * <p>Errors can occur at any point in a chain of operations. Use fold at the end to recover.
   *
   * <p>Task: Handle errors in the middle of a computation chain
   */
  @Test
  void exercise5_errorHandlingInChain() {
    Function<String, Either<String, Integer>> parse =
        s -> {
          try {
            return Either.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Either.left("Parse error");
          }
        };

    Function<Integer, Either<String, Integer>> validatePositive =
        n -> n > 0 ? Either.right(n) : Either.left("Not positive");

    Either<String, String> input = Either.right("0");

    // Solution: Chain parse and validation, then use fold to recover with 1
    Integer result = input.flatMap(parse).flatMap(validatePositive).fold(err -> 1, value -> value);

    assertThat(result).isEqualTo(1);
  }

  /**
   * Exercise 6: Fallback values with fold
   *
   * <p>Use fold to provide a fallback Either if the primary one fails.
   *
   * <p>Task: Provide a fallback using fold
   */
  @Test
  void exercise6_orElseFallback() {
    Either<String, String> primary = Either.left("Primary failed");
    Either<String, String> fallback = Either.right("Fallback value");

    // Solution: Use fold to return fallback on error, or wrap success value
    Either<String, String> result = primary.fold(err -> fallback, value -> Either.right(value));

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo("Fallback value");
  }

  /**
   * Exercise 7: Try for exception handling
   *
   * <p>Try is like Either but specifically for catching exceptions.
   *
   * <p>Task: Use Try to safely perform a risky operation
   */
  @Test
  void exercise7_tryForExceptions() throws Throwable {
    Function<Integer, Integer> riskyDivision =
        n -> {
          if (n == 0) {
            throw new ArithmeticException("Division by zero");
          }
          return 100 / n;
        };

    // Solution: Wrap the risky operation in Try
    Try<Integer> result = Try.of(() -> riskyDivision.apply(0));

    assertThat(result.isFailure()).isTrue();

    // Solution: Recover from the exception with -1
    Try<Integer> recovered = result.recover(ex -> -1);

    assertThat(recovered.isSuccess()).isTrue();
    assertThat(recovered.get()).isEqualTo(-1);
  }

  /**
   * Congratulations! You've completed Tutorial 05: Error Handling Patterns
   *
   * <p>You now understand: ✓ How to raise errors with MonadError.raiseError ✓ How to handle errors
   * using Either.fold() ✓ How to recover with plain values using fold ✓ How to conditionally handle
   * specific errors ✓ How to provide fallbacks with fold ✓ How Try provides exception-safe
   * computations with recover()
   *
   * <p>Next: Tutorial 06 - Concrete Types
   */
}
