// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

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
 * Tutorial 04: Path-Based Resilience.
 *
 * <p>Pain → Promise. Tutorial 01-03 wrap resilience patterns around individual VTasks. In Path-API
 * style we want the same patterns as fluent chain methods on VTaskPath:
 *
 * <pre>
 *   Path.vtask(() -&gt; service.fetch())
 *       .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100)))
 *       .withCircuitBreaker(breaker)
 *       .catching(IOException.class, e -&gt; defaultResponse)
 *       .asTry();
 * </pre>
 *
 * <p>Same patterns, fluent shape, integrates with the rest of the Effect Path API.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>VTaskPath.withRetry() and retry() for fluent retry composition
 *   <li>VTaskPath.catching(), asMaybe(), asTry() for error wrapping
 *   <li>VTaskPath.withCircuitBreaker() for inline circuit breaker protection
 *   <li>VStreamPath.recover() and onError() for stream error handling
 *   <li>VStreamPath.mapTask() for per-element effectful processing
 *   <li>VTaskContext provides a simplified Layer 2 API with resilience built in
 * </ul>
 *
 * <p>Requirements: Java 25+ (virtual threads and structured concurrency)
 *
 * <p>Replace each {@code answerRequired()} call with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 04: Path-Based Resilience")
public class Tutorial04_PathResilience {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ===========================================================================
  // Part 1: VTaskPath Retry
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: VTaskPath Retry")
  class VTaskPathRetry {

    /**
     * Exercise 1: VTaskPath retry via {@code withRetry}.
     *
     * <pre>
     *   // Nudge:    Same RetryPolicy.fixed shape as Tutorial 03; .withRetry on the path.
     *   // Strategy: unstable.withRetry(RetryPolicy.fixed(3, Duration.ofMillis(10)))
     *   // Spoiler:  exactly that.
     * </pre>
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

      // TODO: Add retry to `unstable` using withRetry(...) and a fixed RetryPolicy
      //       Hint: unstable.withRetry(RetryPolicy.fixed(3, Duration.ofMillis(10)))
      VTaskPath<String> retriedPath = answerRequired();

      String result = retriedPath.unsafeRun();
      assertThat(result).isEqualTo("success");
      assertThat(attempts.get()).isEqualTo(3);
    }
  }

  // ===========================================================================
  // Part 2: VTaskPath Error Wrapping
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: VTaskPath Error Wrapping")
  class VTaskPathErrorWrapping {

    /**
     * Exercise 2: convert exceptions into typed values.
     *
     * <pre>
     *   // Nudge:    Three different conversions: catching, asMaybe, asTry.
     *   // Strategy: 2a: failing.catching(Throwable::getMessage)
     *   //           2b: failing.asMaybe()
     *   //           2c: failing.asTry()
     *   // Spoiler:  exactly that for each.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2a: Use catching to convert errors to Either")
    void exercise2a_catching() {
      VTaskPath<String> failing =
          Path.vtask(
              () -> {
                throw new RuntimeException("Connection refused");
              });

      // TODO: Wrap the failure as Either<String, String> using
      //       failing.catching(Throwable::getMessage)
      VTaskPath<Either<String, String>> caught = answerRequired();

      Either<String, String> result = caught.unsafeRun();
      assertThatEither(result).isLeft().hasLeft("Connection refused");
    }

    @Test
    @DisplayName("Exercise 2b: Use asMaybe to convert errors to Nothing")
    void exercise2b_asMaybe() {
      VTaskPath<String> failing =
          Path.vtask(
              () -> {
                throw new RuntimeException("Not found");
              });

      // TODO: Convert the failure to Nothing using failing.asMaybe()
      VTaskPath<Maybe<String>> maybePath = answerRequired();

      Maybe<String> result = maybePath.unsafeRun();
      assertThatMaybe(result).isNothing();
    }

    @Test
    @DisplayName("Exercise 2c: Use asTry to capture errors in Try")
    void exercise2c_asTry() {
      VTaskPath<Integer> failing =
          Path.vtask(
              () -> {
                throw new ArithmeticException("Division by zero");
              });

      // TODO: Wrap the result in Try<Integer> using failing.asTry()
      VTaskPath<Try<Integer>> tryPath = answerRequired();

      Try<Integer> result = tryPath.unsafeRun();
      assertThatTry(result).isFailure();
    }
  }

  // ===========================================================================
  // Part 3: VTaskPath with Circuit Breaker
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: VTaskPath with Circuit Breaker")
  class VTaskPathCircuitBreaker {

    /**
     * Exercise 3: VTaskPath chain with inline circuit breaker.
     *
     * <pre>
     *   // Nudge:    pure value -&gt; map to format -&gt; protect with breaker.
     *   // Strategy: Path.vtaskPure(42).map(n -&gt; "Answer: " + n).withCircuitBreaker(breaker)
     *   // Spoiler:  exactly that.
     * </pre>
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

      // TODO: Build a VTaskPath<String> by:
      //       starting with Path.vtaskPure(42)
      //       mapping it to "Answer: " + n
      //       protecting with withCircuitBreaker(breaker)
      VTaskPath<String> protectedPath = answerRequired();

      String result = protectedPath.unsafeRun();
      assertThat(result).isEqualTo("Answer: 42");
      assertThat(breaker.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }
  }

  // ===========================================================================
  // Part 4: VStreamPath Error Handling
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: VStreamPath Error Handling")
  class VStreamPathErrors {

    /**
     * Exercise 4: VStreamPath element recovery.
     *
     * <pre>
     *   // Nudge:    recover takes a function from the error to a fallback value.
     *   // Strategy: stream.recover(error -&gt; { errorCount.incrementAndGet(); return "recovered"; })
     *   // Spoiler:  exactly that.
     * </pre>
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

      // TODO: Use stream.recover(error -> ...) to:
      //       - increment errorCount on each failure
      //       - return "recovered" as the fallback value
      VStreamPath<String> safeStream = answerRequired();

      List<String> result = safeStream.toList().unsafeRun();
      assertThat(result).containsExactly("item-1", "item-2", "recovered", "item-4", "item-5");
      assertThat(errorCount.get()).isEqualTo(1);
    }

    /**
     * Exercise 5: VStreamPath per-element {@code mapTask}.
     *
     * <pre>
     *   // Nudge:    mapTask takes a function returning VTask; per-element retry comes from Retry.
     *   // Strategy: Path.vstreamOf("a", "b").mapTask(s -&gt; VTask.succeed(s.toUpperCase()))
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: VStreamPath mapTask with per-element retry")
    void exercise5_vstreamPathMapTask() {
      // TODO: Build a VStreamPath<String> from "a", "b" and apply
      //       mapTask(s -> VTask.succeed(s.toUpperCase()))
      //       Hint: Path.vstreamOf("a", "b").mapTask(...)
      VStreamPath<String> processed = answerRequired();

      List<String> result = processed.toList().unsafeRun();
      assertThat(result).containsExactly("A", "B");
    }
  }

  // ===========================================================================
  // Part 5: VTaskContext Resilience
  // ===========================================================================

  @Nested
  @DisplayName("Part 5: VTaskContext Resilience")
  class VTaskContextResilience {

    /**
     * Exercise 6: VTaskContext layered resilience.
     *
     * <pre>
     *   // Nudge:    VTaskContext.of -&gt; .retry -&gt; .withCircuitBreaker -&gt; .recover.
     *   // Strategy: VTaskContext.&lt;String&gt;of(() -&gt; {
     *   //               int n = attempts.incrementAndGet();
     *   //               if (n &lt; 2) throw new RuntimeException("flaky");
     *   //               return "context-result";
     *   //           })
     *   //               .retry(3, Duration.ofMillis(10))
     *   //               .withCircuitBreaker(breaker)
     *   //               .recover(error -&gt; "fallback")
     *   // Spoiler:  see hint above.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 6: VTaskContext with retry and circuit breaker")
    void exercise6_vtaskContextResilience() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      AtomicInteger attempts = new AtomicInteger(0);

      // TODO: Build a VTaskContext<String> by chaining:
      //       VTaskContext.<String>of(() -> { ...attempt logic... })
      //         .retry(3, Duration.ofMillis(10))
      //         .withCircuitBreaker(breaker)
      //         .recover(error -> "fallback")
      //
      //       The supplier should increment attempts; throw on attempt < 2;
      //       otherwise return "context-result".
      VTaskContext<String> ctx = answerRequired();

      // VTaskContext.run() returns Try<String>
      Try<String> result = ctx.run();
      assertThatTry(result).isSuccess();
      assertThat(result.orElse("oops")).isEqualTo("context-result");
    }
  }

  // ===========================================================================
  // Bonus: Complete Example
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * This test demonstrates a complete path-based resilience workflow:
     *
     * <ol>
     *   <li>VTaskPath with retry and circuit breaker
     *   <li>Chain of transformations with error wrapping
     *   <li>VTaskContext for simplified execution
     * </ol>
     *
     * <p>This is provided as a reference - no exercise to complete.
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

      assertThatTry(contextResult).isSuccess();
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

  /**
   * Congratulations! You've completed Tutorial 04: Path-Based Resilience
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to use VTaskPath.withRetry() and retry() for fluent retry composition
   *   <li>How to use catching(), asMaybe(), asTry() to wrap errors in typed values
   *   <li>How to add withCircuitBreaker() inline in a VTaskPath chain
   *   <li>How to use VStreamPath.recover() and onError() for stream error handling
   *   <li>How to use VStreamPath.mapTask() for per-element effectful processing
   *   <li>How VTaskContext provides a simplified API with built-in resilience
   * </ul>
   *
   * <p>You have completed all resilience tutorials! You are now equipped to build robust,
   * fault-tolerant applications using Higher-Kinded-J's resilience patterns.
   */
}
