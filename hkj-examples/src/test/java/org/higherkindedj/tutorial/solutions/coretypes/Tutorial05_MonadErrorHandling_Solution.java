// Copyright (c) 2025 - 2026 Magnus Smith
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
 * Solution for Tutorial05 MonadErrorHandling — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial05_MonadErrorHandling_Solution {

  /**
   * Why this is idiomatic: {@code monad.raiseError(e)} produces a {@code Kind} that carries the
   * error in the typeclass's error channel — same shape as a successful {@code of(value)}, just the
   * failing branch. Code that only sees {@code Kind} can chain it without ever knowing it holds a
   * Left.
   *
   * <p>Alternative: build an {@code Either.left(...)} directly and {@code EITHER.widen} it. Same
   * runtime value; loses the polymorphism — switching to {@code Validated} or {@code Try} later
   * means rewriting every site, while {@code raiseError} is the same call against any {@code
   * MonadError}.
   *
   * <p>Common wrong attempt: calling {@code monad.of("Invalid input")}. {@code of} is the
   * <em>success</em> constructor; the result is {@code Right("Invalid input")}, which the test for
   * {@code isLeft()} catches immediately.
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
   * Why this is idiomatic: {@code fold(onError, onValue)} forces both sides at the type level — the
   * compiler will not let us forget the failure branch. Each handler returns the same target type,
   * so the combined result is unambiguous.
   *
   * <p>Alternative: {@code failed.getOrElse(0)}. Shorter for "recover with a constant", but
   * silently throws away the error; reach for {@code fold} as soon as the recovery depends on the
   * error value or you want to log it.
   *
   * <p>Common wrong attempt: {@code failed.getRight()} guarded by an {@code if (failed.isRight())}.
   * That works at the call site but spreads the branching across two statements; {@code fold} makes
   * the total handling a single expression.
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
   * Why this is idiomatic: ignoring the error binding ({@code err -> -1}) is honest about the
   * intent — "any failure becomes the sentinel". The reader sees one constant on the failure branch
   * and {@code identity} on the success branch.
   *
   * <p>Alternative: {@code error.getOrElse(-1)}. Equivalent for this exact case; prefer it when the
   * recovery is a pure constant and there is nothing to learn from the error value.
   *
   * <p>Common wrong attempt: defaulting to {@code null} ({@code err -> null}). The signature still
   * type-checks, but the {@code Integer} unboxing at the call site will NPE — pick a real sentinel
   * or change the return type to {@code Optional<Integer>}.
   */
  @Test
  void exercise3_recoverWithValue() {
    Either<String, Integer> error = Either.left("Database connection failed");

    // Solution: Use fold to recover with -1 for any error
    Integer recovered = error.fold(err -> -1, value -> value);

    assertThat(recovered).isEqualTo(-1);
  }

  /**
   * Why this is idiomatic: the error branch is a real function of the error value; pattern-style
   * branching ({@code if/else} on the tag) lives inside that function, where it can grow without
   * polluting the success path.
   *
   * <p>Alternative: a {@code switch} expression on the error enum/string returning the recovery
   * value. Cleaner once there are more than two cases; the {@code if} chain shown is the smallest
   * step from the previous exercise.
   *
   * <p>Common wrong attempt: throwing on the unrecognised error to "promote" it. That converts a
   * typed error back into an exception — exactly the pain {@code Either} was introduced to avoid.
   * Return a sentinel or re-raise into a wider error type instead.
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
   * Why this is idiomatic: each {@code flatMap} short-circuits on Left, so neither the parser nor
   * the validator needs to know about the other. The single {@code fold} at the end is the only
   * place that knows what "failure" means for the whole pipeline.
   *
   * <p>Alternative: {@code fold} after each step, threading a {@code Result} through. Works, but
   * pushes the recovery decision up into every stage and makes adding a new step a multi-line
   * change instead of a one-line {@code .flatMap(...)}.
   *
   * <p>Common wrong attempt: {@code .map(parse)} instead of {@code .flatMap(parse)}. {@code parse}
   * already returns {@code Either<String, Integer>}, so {@code map} produces {@code Either<String,
   * Either<String, Integer>>} — the chain compiles but the inner failure is invisible to the outer
   * fold.
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
   * Why this is idiomatic: the failure branch returns the fallback {@code Either} as-is and the
   * success branch re-wraps the value with {@code Either.right} — the result is a single {@code
   * Either<E, A>} the caller can keep chaining against.
   *
   * <p>Alternative: in libraries that ship one, {@code primary.orElse(fallback)} reads as exactly
   * the intent. Where it is missing, this {@code fold} spelling is the canonical reconstruction.
   *
   * <p>Common wrong attempt: returning {@code value} from the success branch rather than {@code
   * Either.right(value)}. The lambdas would now have different result types, so the call fails to
   * type-check; both branches must agree on the return type.
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
   * Why this is idiomatic: {@code Try.of(supplier)} captures any thrown {@link Throwable} as a
   * {@code Failure}, and {@code recover(fn)} turns that failure back into a {@code Success} — the
   * exception never leaks into the calling code.
   *
   * <p>Alternative: {@code try { ... } catch (Exception e) { return -1; }} around the call site.
   * Same outcome locally; loses the value-as-data shape, so combinators like {@code map} and {@code
   * flatMap} cannot be reused on the result.
   *
   * <p>Common wrong attempt: {@code Try.of(riskyDivision.apply(0))} (no lambda). That evaluates the
   * call eagerly — the exception is thrown <em>before</em> {@code Try.of} ever sees it. {@code
   * Try.of} expects a {@code Supplier}, so always pass {@code () -> ...}.
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
