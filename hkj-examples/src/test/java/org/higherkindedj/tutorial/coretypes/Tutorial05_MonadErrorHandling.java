// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 05: Error Handling Patterns — MonadError, fold, recover.
 *
 * <p>Pain → Promise. Imperative Java mixes thrown exceptions, custom Result types, and the classic
 * null-on-failure return per call site. {@link MonadError} unifies these as a typeclass capability:
 * the same {@code raiseError}, {@code handleError}, {@code recover} surface works for {@link
 * Either}, {@link Try}, {@link org.higherkindedj.hkt.maybe.Maybe}, and {@link
 * org.higherkindedj.hkt.validated.Validated}.
 *
 * <pre>
 *   // Either: typed error
 *   Either&lt;Error, A&gt; e = parse(input);
 *   var safe = e.fold(err -&gt; defaultValue, value -&gt; value);
 *
 *   // Try: error is Throwable
 *   Try&lt;A&gt; t = Try.of(() -&gt; parse(input));
 *   var safe = t.recover(err -&gt; defaultValue);
 * </pre>
 *
 * <p>Java idiom anchor: {@code fold} ↔ exhaustive pattern match; {@code recover} ↔ try/catch with a
 * fallback value; {@code raiseError} ↔ explicit error construction.
 *
 * <p>Key Concepts: - fold: pattern match on Either to handle both error and success cases -
 * Try.recover: recover from exceptions with a default value - MonadError typeclass: raiseError for
 * creating error values
 *
 * <p>Types covered: - Either<E, A>: explicit error type E - Try<A>: error is Throwable
 */
public class Tutorial05_MonadErrorHandling {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Raising errors with {@code raiseError}.
   *
   * <pre>
   *   // Nudge:    monad.raiseError lifts an error value into the failure channel.
   *   // Strategy: monad.raiseError("Invalid input")
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: raiseError lifts an error into the failure channel")
  void exercise1_raisingErrors() {
    MonadError<EitherKind.Witness<String>, String> monad = Instances.monadError(either());

    // TODO: Replace null with code that raises an error "Invalid input"
    // Hint: Use monad.raiseError(...)
    Kind<EitherKind.Witness<String>, Integer> error = answerRequired();

    Either<String, Integer> result = EITHER.narrow(error);
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Invalid input");
  }

  /**
   * Exercise 2: Handle errors with {@code fold}.
   *
   * <pre>
   *   // Nudge:    fold takes two functions: one for Left, one for Right.
   *   // Strategy: failed.fold(err -&gt; 0, value -&gt; value)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: fold pattern-matches both branches of Either")
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

    // TODO: Replace null with code that handles the error using fold
    // If there's an error (Left), return 0; if success (Right), return the value
    // Hint: failed.fold(error -> 0, value -> value)
    Integer recovered = answerRequired();

    assertThat(recovered).isEqualTo(0);
  }

  /**
   * Exercise 3: Recover with a plain value using fold.
   *
   * <pre>
   *   // Nudge:    Same fold shape as exercise 2; the default goes in the err branch.
   *   // Strategy: error.fold(err -&gt; -1, value -&gt; value)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: fold provides a default for the error branch")
  void exercise3_recoverWithValue() {
    Either<String, Integer> error = Either.left("Database connection failed");

    // TODO: Replace null with code that recovers with a default value using fold
    // Hint: error.fold(err -> -1, value -> value)
    Integer recovered = answerRequired();

    assertThat(recovered).isEqualTo(-1);
  }

  /**
   * Exercise 4: Conditional error recovery.
   *
   * <pre>
   *   // Nudge:    Inside the err branch we can branch further on the error value.
   *   // Strategy: NOT_FOUND -&gt; 0; everything else -&gt; -999.
   *   // Spoiler:  return 0 for the if branch, -999 for the else.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: branch inside the error handler")
  void exercise4_conditionalRecovery() {
    Either<String, Integer> error = Either.left("NOT_FOUND");

    // TODO: Replace null with code that:
    // - Returns 0 if error is "NOT_FOUND"
    // - Returns -999 for other errors (to distinguish from success)
    Integer recovered =
        error.fold(
            err -> {
              if (err.equals("NOT_FOUND")) {
                return answerRequired();
              } else {
                return answerRequired();
              }
            },
            value -> value);

    assertThat(recovered).isEqualTo(0);
  }

  /**
   * Exercise 5: Error handling at the end of a chain.
   *
   * <pre>
   *   // Nudge:    The chain can short-circuit at parse or validation; fold handles whatever
   *   //           shape the result lands in.
   *   // Strategy: input.flatMap(parse).flatMap(validatePositive).fold(err -&gt; 1, value -&gt; value)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: fold at the end of a multi-step chain")
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

    // TODO: Replace null with a chain that:
    // 1. Parses the input
    // 2. Validates it's positive (will fail for "0")
    // 3. Uses fold to recover from any error with value 1
    // Hint: input.flatMap(parse).flatMap(validatePositive).fold(err -> 1, value -> value)
    Integer result = answerRequired();

    assertThat(result).isEqualTo(1);
  }

  /**
   * Exercise 6: Fallback Either via fold.
   *
   * <pre>
   *   // Nudge:    err -&gt; fallback Either; success -&gt; rewrap as Either.right.
   *   // Strategy: primary.fold(err -&gt; fallback, value -&gt; Either.right(value))
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: fold returns a fallback Either when primary fails")
  void exercise6_orElseFallback() {
    Either<String, String> primary = Either.left("Primary failed");
    Either<String, String> fallback = Either.right("Fallback value");

    // TODO: Replace null with code that uses fold to return fallback on error
    // If primary is Left, use fallback; if primary is Right, use primary's value wrapped in Right
    // Hint: primary.fold(err -> fallback, value -> Either.right(value))
    Either<String, String> result = answerRequired();

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo("Fallback value");
  }

  /**
   * Exercise 7: Try captures throws as values.
   *
   * <pre>
   *   // Nudge:    Try.of takes a Supplier that may throw; the result is Success or Failure.
   *   // Strategy: Try.of(() -&gt; riskyDivision.apply(0))
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: Try.of captures exceptions as Failure values")
  void exercise7_tryForExceptions() throws Throwable {
    Function<Integer, Integer> riskyDivision =
        n -> {
          if (n == 0) {
            throw new ArithmeticException("Division by zero");
          }
          return 100 / n;
        };

    // TODO: Replace null with code that wraps the risky operation in Try
    // Hint: Try.of(() -> riskyDivision.apply(0))
    Try<Integer> result = answerRequired();

    assertThat(result.isFailure()).isTrue();

    // TODO: Replace null with code that recovers from the exception
    // Hint: result.recover(ex -> -1)
    Try<Integer> recovered = answerRequired();

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
