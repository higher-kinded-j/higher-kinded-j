// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience.solutions;

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
 * Tutorial 04: Path-Based Resilience - SOLUTIONS
 *
 * <p>This file contains the complete solutions for all exercises in Tutorial04_PathResilience.java.
 */
@DisplayName("Tutorial 04: Path-Based Resilience (Solutions)")
public class Tutorial04_PathResilience_Solution {

  @Nested
  @DisplayName("Part 1: VTaskPath Retry")
  class VTaskPathRetry {

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
