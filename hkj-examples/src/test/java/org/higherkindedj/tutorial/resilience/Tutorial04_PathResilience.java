// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience;

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
 * Tutorial 04: Path-Based Resilience
 *
 * <p>Learn to use resilience patterns through the fluent Path API. VTaskPath, VStreamPath, and
 * VTaskContext provide chainable resilience operations like withRetry(), withCircuitBreaker(),
 * catching(), asMaybe(), and asTry().
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
 * <p>Replace each {@code fail("TODO: ...")} with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 04: Path-Based Resilience")
public class Tutorial04_PathResilience {

  // ===========================================================================
  // Part 1: VTaskPath Retry
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: VTaskPath Retry")
  class VTaskPathRetry {

    /**
     * Exercise 1: VTaskPath withRetry and retry convenience
     *
     * <p>VTaskPath provides withRetry(RetryPolicy) for full control and retry(maxAttempts,
     * initialDelay) as a convenience for exponential backoff with jitter.
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

      // Add retry using withRetry and a fixed policy
      VTaskPath<String> retriedPath =
          unstable.withRetry(RetryPolicy.fixed(3, Duration.ofMillis(10)));

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
     * Exercise 2: VTaskPath catching, asMaybe, asTry
     *
     * <p>These methods convert exceptions into typed values:
     *
     * <ul>
     *   <li>catching(mapper) wraps in Either: Right(value) or Left(mappedError)
     *   <li>asMaybe() wraps in Maybe: Just(value) or Nothing
     *   <li>asTry() wraps in Try: Success(value) or Failure(exception)
     * </ul>
     */
    @Test
    @DisplayName("Exercise 2a: Use catching to convert errors to Either")
    void exercise2a_catching() {
      VTaskPath<String> failing =
          Path.vtask(
              () -> {
                throw new RuntimeException("Connection refused");
              });

      // Use catching to wrap the error as Either<String, String>
      VTaskPath<Either<String, String>> caught = failing.catching(Throwable::getMessage);

      Either<String, String> result = caught.unsafeRun();
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("Exercise 2b: Use asMaybe to convert errors to Nothing")
    void exercise2b_asMaybe() {
      VTaskPath<String> failing =
          Path.vtask(
              () -> {
                throw new RuntimeException("Not found");
              });

      // Use asMaybe to convert failure to Nothing
      VTaskPath<Maybe<String>> maybePath = failing.asMaybe();

      Maybe<String> result = maybePath.unsafeRun();
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Exercise 2c: Use asTry to capture errors in Try")
    void exercise2c_asTry() {
      VTaskPath<Integer> failing =
          Path.vtask(
              () -> {
                throw new ArithmeticException("Division by zero");
              });

      // Use asTry to wrap the result in Try<Integer>
      VTaskPath<Try<Integer>> tryPath = failing.asTry();

      Try<Integer> result = tryPath.unsafeRun();
      assertThat(result.isFailure()).isTrue();
    }
  }

  // ===========================================================================
  // Part 3: VTaskPath with Circuit Breaker
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: VTaskPath with Circuit Breaker")
  class VTaskPathCircuitBreaker {

    /**
     * Exercise 3: VTaskPath withCircuitBreaker in a chain
     *
     * <p>Use withCircuitBreaker() directly in a VTaskPath chain to protect a computation inline.
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

      // Chain map and withCircuitBreaker
      VTaskPath<String> protectedPath =
          Path.vtaskPure(42).map(n -> "Answer: " + n).withCircuitBreaker(breaker);

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
     * Exercise 4: VStreamPath recover and onError
     *
     * <p>VStreamPath.recover() provides a fallback value for elements that fail during processing.
     * VStreamPath.onError() performs a side effect when an error occurs.
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

      // Use recover for both error counting and fallback value
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
     * Exercise 5: VStreamPath mapTask with per-element retry
     *
     * <p>mapTask() applies an effectful function (returning VTask) to each element sequentially.
     * You can combine this with Retry to add per-element retry logic.
     */
    @Test
    @DisplayName("Exercise 5: VStreamPath mapTask with per-element retry")
    void exercise5_vstreamPathMapTask() {
      AtomicInteger callCount = new AtomicInteger(0);

      // Use mapTask to apply a VTask function to each element
      VStreamPath<String> processed =
          Path.vstreamOf("a", "b").mapTask(s -> VTask.succeed(s.toUpperCase()));

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
     * Exercise 6: VTaskContext retry and withCircuitBreaker
     *
     * <p>VTaskContext provides a simplified Layer 2 API with built-in resilience methods. It wraps
     * VTaskPath and provides factory methods like of(), pure(), fail(), and execution methods like
     * run() (returns Try), runOrThrow(), and runOrElse().
     */
    @Test
    @DisplayName("Exercise 6: VTaskContext with retry and circuit breaker")
    void exercise6_vtaskContextResilience() {
      CircuitBreaker breaker = CircuitBreaker.withDefaults();
      AtomicInteger attempts = new AtomicInteger(0);

      // Build VTaskContext with retry, circuit breaker, and recovery
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
