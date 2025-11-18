// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

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
 * Tutorial 05: MonadError - Explicit Error Handling
 *
 * <p>MonadError extends Monad with operations for raising and handling errors in a principled way.
 *
 * <p>Key Concepts: - raiseError: creates an error value - handleErrorWith: recovers from errors
 * with another computation - recover: recovers from errors with a plain value - orElse: provides a
 * fallback value
 *
 * <p>Types with MonadError: - Either<E, A>: explicit error type E - Try<A>: error is Throwable -
 * Validated<E, A>: accumulates errors
 */
public class Tutorial05_MonadErrorHandling {

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

    // TODO: Replace ___ with code that raises an error "Invalid input"
    // Hint: Use monad.raiseError(...)
    Kind<EitherKind.Witness<String>, Integer> error = ___;

    Either<String, Integer> result = EITHER.narrow(error);
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Invalid input");
  }

  /**
   * Exercise 2: Handling errors with handleErrorWith
   *
   * <p>handleErrorWith allows you to recover from an error by providing an alternative
   * computation.
   *
   * <p>Task: Recover from a parse error by trying an alternative
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

    // TODO: Replace ___ with code that handles the error
    // If there's an error, return Either.right(0) as a default
    // Hint: failed.handleErrorWith(error -> Either.right(0))
    Either<String, Integer> recovered = ___;

    assertThat(recovered.isRight()).isTrue();
    assertThat(recovered.getRight()).isEqualTo(0);
  }

  /**
   * Exercise 3: Recovering with a plain value
   *
   * <p>recover is like handleErrorWith but takes a plain value instead of a computation.
   *
   * <p>Task: Provide a default value for errors
   */
  @Test
  void exercise3_recoverWithValue() {
    Either<String, Integer> error = Either.left("Database connection failed");

    // TODO: Replace ___ with code that recovers with a default value
    // Hint: error.recover(err -> -1)
    Either<String, Integer> recovered = ___;

    assertThat(recovered.isRight()).isTrue();
    assertThat(recovered.getRight()).isEqualTo(-1);
  }

  /**
   * Exercise 4: Conditional error recovery
   *
   * <p>You can inspect the error and decide whether to recover or not.
   *
   * <p>Task: Recover only from specific errors
   */
  @Test
  void exercise4_conditionalRecovery() {
    Either<String, Integer> error = Either.left("NOT_FOUND");

    // TODO: Replace ___ with code that:
    // - Recovers with 0 if error is "NOT_FOUND"
    // - Re-raises other errors
    Either<String, Integer> recovered =
        error.handleErrorWith(
            err -> {
              if (err.equals("NOT_FOUND")) {
                return ___;
              } else {
                return ___;
              }
            });

    assertThat(recovered.isRight()).isTrue();
    assertThat(recovered.getRight()).isEqualTo(0);
  }

  /**
   * Exercise 5: Error handling in a chain
   *
   * <p>Errors can occur at any point in a chain of operations.
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

    // TODO: Replace ___ with a chain that:
    // 1. Parses the input
    // 2. Validates it's positive (will fail for "0")
    // 3. Handles any error by returning Either.right(1)
    Either<String, Integer> result = ___;

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo(1);
  }

  /**
   * Exercise 6: orElse for fallback values
   *
   * <p>orElse provides an alternative computation if the current one fails.
   *
   * <p>Task: Use orElse to try a fallback
   */
  @Test
  void exercise6_orElseFallback() {
    Either<String, String> primary = Either.left("Primary failed");
    Either<String, String> fallback = Either.right("Fallback value");

    // TODO: Replace ___ with code that uses orElse
    // Hint: primary.orElse(fallback)
    Either<String, String> result = ___;

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
  void exercise7_tryForExceptions() {
    Function<Integer, Integer> riskyDivision =
        n -> {
          if (n == 0) {
            throw new ArithmeticException("Division by zero");
          }
          return 100 / n;
        };

    // TODO: Replace ___ with code that wraps the risky operation in Try
    // Hint: Try.of(() -> riskyDivision.apply(0))
    Try<Integer> result = ___;

    assertThat(result.isFailure()).isTrue();

    // TODO: Replace ___ with code that recovers from the exception
    // Hint: result.recover(ex -> -1)
    Try<Integer> recovered = ___;

    assertThat(recovered.isSuccess()).isTrue();
    assertThat(recovered.get()).isEqualTo(-1);
  }

  /**
   * Congratulations! You've completed Tutorial 05: Monad Error Handling
   *
   * <p>You now understand: ✓ How to raise errors with raiseError ✓ How to handle errors with
   * handleErrorWith ✓ How to recover with plain values using recover ✓ How to conditionally handle
   * specific errors ✓ How to use orElse for fallbacks ✓ How Try provides exception-safe
   * computations
   *
   * <p>Next: Tutorial 06 - Concrete Types
   */
}
