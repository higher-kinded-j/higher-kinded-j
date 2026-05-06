// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.effect.context.VTaskContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial04 PathResilience — teaching-solution format.
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
@DisplayName("Tutorial 04: Path-Based Resilience (Solutions)")
public class Tutorial04_PathResilience_Solution {

  @Nested
  @DisplayName("Part 1: VTaskPath Retry")
  class VTaskPathRetry {

    /**
     * Why this is idiomatic: {@code path.withRetry(policy)} attaches the retry policy to the path
     * itself. The wrapper retries failures up to the policy limit before surfacing them.
     *
     * <p>Alternative: external {@code Retry.retryTask} on the underlying VTask. Equivalent; the
     * path-level wrapper composes with other path combinators.
     *
     * <p>Common wrong attempt: assume the retry runs the path's setup again. Only the body is
     * retried; setup like {@code Path.vtask(...)} runs once to capture the lambda.
     */
    @Test
    @DisplayName("Exercise 1: VTaskPath withRetry and retry")
    void exercise1_vtaskPathRetry() {
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskPath<String> unstable =
          Path.vtask(
              () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                  throw new RuntimeException("Attempt " + attempt + " failed");
                }
                return "success";
              });

      // SOLUTION: Add retry using withRetry and a fixed policy
      VTaskPath<String> retriedPath =
          unstable.withRetry(RetryPolicy.fixed(3, Duration.ofMillis(10)));

      String result = retriedPath.unsafeRun();
      assertThat(result).isEqualTo("success");
      assertThat(attempts.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Part 2: VTaskPath Error Wrapping")
  class VTaskPathErrorWrapping {

    /**
     * Why this is idiomatic: {@code path.catching(mapper)} turns an exception into an {@code
     * Either<L, R>} where {@code L} comes from the mapper. The path now carries the error as a
     * value.
     *
     * <p>Alternative: try/catch around {@code unsafeRun}. Loses the path's combinator surface; the
     * wrapped Either keeps the work composable.
     *
     * <p>Common wrong attempt: catch a specific exception type and assume the mapper sees it. The
     * mapper receives a {@code Throwable}; cast inside if a richer type is needed.
     */
    @Test
    @DisplayName("Exercise 2a: Use catching to convert errors to Either")
    void exercise2a_catching() {
      VTaskPath<String> failing =
          Path.vtask(
              () -> {
                throw new RuntimeException("Connection refused");
              });

      // SOLUTION: Use catching to wrap the error as Either<String, String>
      VTaskPath<Either<String, String>> caught = failing.catching(Throwable::getMessage);

      Either<String, String> result = caught.unsafeRun();
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("Connection refused");
    }

    /**
     * Why this is idiomatic: {@code path.asMaybe()} converts a failing path to {@code
     * Maybe.nothing()} and a successful path to {@code Maybe.just}. The exception is dropped in
     * favour of a typed absence.
     *
     * <p>Alternative: {@code catching} into {@code Either} and project to Maybe. Same outcome;
     * {@code asMaybe} is the named shorthand.
     *
     * <p>Common wrong attempt: assume the original exception is preserved. It is dropped; use
     * {@code asTry} or {@code catching} when the error details matter.
     */
    @Test
    @DisplayName("Exercise 2b: Use asMaybe to convert errors to Nothing")
    void exercise2b_asMaybe() {
      VTaskPath<String> failing =
          Path.vtask(
              () -> {
                throw new RuntimeException("Not found");
              });

      // SOLUTION: Use asMaybe to convert failure to Nothing
      VTaskPath<Maybe<String>> maybePath = failing.asMaybe();

      Maybe<String> result = maybePath.unsafeRun();
      assertThat(result.isNothing()).isTrue();
    }

    /**
     * Why this is idiomatic: {@code path.asTry()} captures the result in a {@code Try<A>},
     * preserving both the value (on success) and the exception (on failure). The richest of the
     * three error wrappers.
     *
     * <p>Alternative: {@code catching} for typed errors or {@code asMaybe} for presence/absence.
     * Pick by how much detail the caller needs.
     *
     * <p>Common wrong attempt: use {@code asTry} when only presence matters. The Try carries the
     * entire {@code Throwable}; reach for {@code asMaybe} when the error details are not used.
     */
    @Test
    @DisplayName("Exercise 2c: Use asTry to capture errors in Try")
    void exercise2c_asTry() {
      VTaskPath<Integer> failing =
          Path.vtask(
              () -> {
                throw new ArithmeticException("Division by zero");
              });

      // SOLUTION: Use asTry to wrap the result in Try<Integer>
      VTaskPath<Try<Integer>> tryPath = failing.asTry();

      Try<Integer> result = tryPath.unsafeRun();
      assertThat(result.isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Part 3: VTaskPath with Circuit Breaker")
  class VTaskPathCircuitBreaker {

    /**
     * Why this is idiomatic: {@code path.withCircuitBreaker(breaker)} attaches the breaker
     * mid-chain. Pure transformations stay outside the breaker; downstream calls go through it.
     *
     * <p>Alternative: wrap the whole path with the breaker. Includes the pure transforms; usually
     * wrong because the breaker only protects the risky boundary.
     *
     * <p>Common wrong attempt: place {@code withCircuitBreaker} before the effectful step. Order
     * matters — it must come after the call that may fail.
     */
    @Test
    @DisplayName("Exercise 3: VTaskPath withCircuitBreaker in a chain")
    void exercise3_vtaskPathCircuitBreaker() {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(5)
                  .openDuration(Duration.ofSeconds(30))
                  .build());

      // SOLUTION: Chain map and withCircuitBreaker
      VTaskPath<String> protectedPath =
          Path.vtaskPure(42).map(n -> "Answer: " + n).withCircuitBreaker(breaker);

      String result = protectedPath.unsafeRun();
      assertThat(result).isEqualTo("Answer: 42");
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }
  }

  @Nested
  @DisplayName("Part 4: VStreamPath Error Handling")
  class VStreamPathErrors {

    /**
     * Why this is idiomatic: {@code stream.recover(handler)} replaces a failing element with the
     * handler's result. The stream continues with subsequent elements; the failure is logged and a
     * fallback substituted.
     *
     * <p>Alternative: filter out failing elements with a try inside {@code mapTask}. Loses
     * positions in the stream; recover preserves order with placeholders.
     *
     * <p>Common wrong attempt: assume {@code recover} aborts the stream on the first error. It
     * applies per-element; the stream continues.
     */
    @Test
    @DisplayName("Exercise 4: VStreamPath recover from element errors")
    void exercise4_vstreamPathRecover() {
      AtomicInteger errorCount = new AtomicInteger(0);

      VStreamPath<String> stream =
          Path.vstreamOf(1, 2, 3, 4, 5)
              .mapTask(
                  n ->
                      VTask.of(
                          () -> {
                            if (n == 3) {
                              throw new RuntimeException("Bad element: 3");
                            }
                            return "item-" + n;
                          }));

      // SOLUTION: Use recover for both error counting and fallback value
      VStreamPath<String> safeStream =
          stream.recover(
              error -> {
                errorCount.incrementAndGet();
                return "recovered";
              });

      List<String> result = safeStream.toList().unsafeRun();
      assertThat(result).containsExactly("item-1", "item-2", "recovered", "item-4", "item-5");
      assertThat(errorCount.get()).isEqualTo(1);
    }

    /**
     * Why this is idiomatic: {@code stream.mapTask(fn)} applies an effectful function to each
     * element. Each element runs in its own VTask; combine with retry/breaker per element if
     * needed.
     *
     * <p>Alternative: {@code stream.map(fn)} when the function is pure. The task variant earns its
     * keep when each element calls a remote service.
     *
     * <p>Common wrong attempt: assume failures abort the stream. Pair with {@code recover} or
     * {@code asMaybe} to keep the stream flowing past failed elements.
     */
    @Test
    @DisplayName("Exercise 5: VStreamPath mapTask with per-element retry")
    void exercise5_vstreamPathMapTask() {
      AtomicInteger callCount = new AtomicInteger(0);

      // SOLUTION: Use mapTask to apply a VTask function to each element
      VStreamPath<String> processed =
          Path.vstreamOf("a", "b").mapTask(s -> VTask.succeed(s.toUpperCase()));

      List<String> result = processed.toList().unsafeRun();
      assertThat(result).containsExactly("A", "B");
    }
  }

  @Nested
  @DisplayName("Part 5: VTaskContext Resilience")
  class VTaskContextResilience {

    /**
     * Why this is idiomatic: {@code VTaskContext.of(supplier).retry(n,
     * delay).withCircuitBreaker(breaker).recover(fn)} composes the resilience stack at the Layer-2
     * ergonomic API. Returns a {@code Try<A>} from {@code run()}.
     *
     * <p>Alternative: drop to {@code VTask} with the {@code Resilience} builder. Equivalent stack;
     * the Context API stays cleaner.
     *
     * <p>Common wrong attempt: forget that {@code .run()} returns {@code Try}, not the raw value.
     * Inspect or unwrap explicitly.
     */
    @Test
    @DisplayName("Exercise 6: VTaskContext with retry and circuit breaker")
    void exercise6_vtaskContextResilience() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      AtomicInteger attempts = new AtomicInteger(0);

      // SOLUTION: Build VTaskContext with retry, circuit breaker, and recovery
      VTaskContext<String> ctx =
          VTaskContext.<String>of(
                  () -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 2) {
                      throw new RuntimeException("fail");
                    }
                    return "context-result";
                  })
              .retry(3, Duration.ofMillis(10))
              .withCircuitBreaker(breaker)
              .recover(error -> "fallback");

      // VTaskContext.run() returns Try<String>
      Try<String> result = ctx.run();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse("oops")).isEqualTo("context-result");
    }
  }

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * Why this is idiomatic: a complete resilience chain on both VTaskPath and VTaskContext sides.
     * Each layer composes naturally with the rest of the path/context vocabulary.
     *
     * <p>Alternative: Layer-1 {@code Resilience} builder for explicit control. The path/context
     * surfaces are the ergonomic faces of the same stack.
     *
     * <p>Common wrong attempt: mix VTaskPath and VTaskContext stacks in the same pipeline. Stay in
     * one layer per pipeline; convert at the boundary.
     */
    @Test
    @DisplayName("Complete path-based resilience example")
    void completePathResilience() {
      CircuitBreaker breaker =
          CircuitBreaker.create(
              CircuitBreakerConfig.builder()
                  .failureThreshold(5)
                  .openDuration(Duration.ofSeconds(30))
                  .build());

      // VTaskPath with full resilience chain
      VTaskPath<String> resilientPath =
          Path.vtaskPure("raw-data")
              .map(s -> s.toUpperCase())
              .withCircuitBreaker(breaker)
              .withRetry(RetryPolicy.fixed(3, Duration.ofMillis(10)))
              .handleError(error -> "default-data");

      String pathResult = resilientPath.unsafeRun();
      assertThat(pathResult).isEqualTo("RAW-DATA");

      // VTaskContext with retry and recovery
      Try<String> contextResult =
          VTaskContext.pure("context-value")
              .map(String::toUpperCase)
              .retry(2, Duration.ofMillis(10))
              .recover(error -> "RECOVERED")
              .run();

      assertThat(contextResult.isSuccess()).isTrue();
      assertThat(contextResult.orElse("fail")).isEqualTo("CONTEXT-VALUE");

      // VStreamPath with error recovery
      List<String> streamResult =
          Path.vstreamOf(1, 2, 3)
              .map(n -> "item-" + n)
              .recover(error -> "recovered")
              .toList()
              .unsafeRun();

      assertThat(streamResult).containsExactly("item-1", "item-2", "item-3");
    }
  }
}
