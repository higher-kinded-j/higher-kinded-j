// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for {@link ResilienceBuilder} and the {@link Resilience} utility class.
 *
 * <p>Tests cover pattern ordering, combined patterns, circuit breaker interaction with retry,
 * fallback behavior, and convenience methods.
 */
@DisplayName("ResilienceBuilder and Resilience Test Suite")
class ResilienceBuilderTest {

  // Shared helpers for creating resilience components with short durations
  private static CircuitBreakerConfig shortCbConfig(int failureThreshold) {
    return CircuitBreakerConfig.builder()
        .failureThreshold(failureThreshold)
        .openDuration(Duration.ofMillis(100))
        .callTimeout(Duration.ofSeconds(5))
        .build();
  }

  private static RetryPolicy shortRetry(int maxAttempts) {
    return RetryPolicy.fixed(maxAttempts, Duration.ofMillis(1));
  }

  @Nested
  @DisplayName("Pattern Ordering Tests")
  class PatternOrderingTests {

    @Test
    @DisplayName("Circuit breaker is innermost - sees each retry attempt individually")
    void circuitBreakerIsInnermostAndSeesEachRetryAttempt() {
      AtomicInteger attempts = new AtomicInteger(0);
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(5));
      RetryPolicy retry = shortRetry(3);

      // Task fails on first two attempts, succeeds on third
      VTask<String> task =
          VTask.of(
              () -> {
                if (attempts.incrementAndGet() < 3) {
                  throw new RuntimeException("transient failure");
                }
                return "success";
              });

      VTask<String> resilient =
          Resilience.<String>builder(task).withCircuitBreaker(cb).withRetry(retry).build();

      String result = resilient.run();

      assertThat(result).isEqualTo("success");
      // All 3 attempts went through the circuit breaker
      assertThat(attempts.get()).isEqualTo(3);
      // Circuit breaker saw the 2 failures but threshold (5) was not reached, so still CLOSED
      assertThat(cb.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("Circuit breaker failures are counted per attempt, not per overall call")
    void circuitBreakerCountsFailuresPerAttempt() {
      AtomicInteger attempts = new AtomicInteger(0);
      // Threshold of 3 means circuit opens after 3 consecutive failures
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(3));
      // Allow up to 5 retries
      RetryPolicy retry = shortRetry(5);

      VTask<String> task =
          VTask.of(
              () -> {
                attempts.incrementAndGet();
                throw new RuntimeException("always fails");
              });

      VTask<String> resilient =
          Resilience.<String>builder(task).withCircuitBreaker(cb).withRetry(retry).build();

      assertThatThrownBy(resilient::run).isInstanceOf(RuntimeException.class);

      // The circuit breaker opens after 3 failures. Subsequent attempts see CircuitOpenException.
      // Since the default retry policy retries all exceptions, it retries CircuitOpenException too.
      // All 5 attempts are made (3 real + 2 rejected by open circuit).
      assertThat(attempts.get()).isEqualTo(3);
      assertThat(cb.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName("Retry wraps circuit breaker - order is independent of builder call order")
    void builderOrderDoesNotAffectPatternOrder() {
      AtomicInteger attempts = new AtomicInteger(0);
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(5));
      RetryPolicy retry = shortRetry(3);

      VTask<String> task =
          VTask.of(
              () -> {
                if (attempts.incrementAndGet() < 3) {
                  throw new RuntimeException("transient");
                }
                return "ok";
              });

      // Specify retry before circuit breaker - ordering should still be the same
      VTask<String> resilient =
          Resilience.<String>builder(task).withRetry(retry).withCircuitBreaker(cb).build();

      String result = resilient.run();

      assertThat(result).isEqualTo("ok");
      assertThat(attempts.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Combined Patterns Tests")
  class CombinedPatternsTests {

    @Test
    @DisplayName("All patterns compose successfully for a passing task")
    void allPatternsComposeForSuccess() {
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(3));
      RetryPolicy retry = shortRetry(3);
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(5);

      VTask<String> task = VTask.succeed("hello");

      VTask<String> resilient =
          Resilience.<String>builder(task)
              .withCircuitBreaker(cb)
              .withRetry(retry)
              .withBulkhead(bulkhead)
              .withTimeout(Duration.ofSeconds(5))
              .build();

      String result = resilient.run();

      assertThat(result).isEqualTo("hello");
      assertThat(cb.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("Retry and circuit breaker work together for transient failures")
    void retryAndCircuitBreakerForTransientFailures() {
      AtomicInteger attempts = new AtomicInteger(0);
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(5));
      RetryPolicy retry = shortRetry(4);

      VTask<Integer> task =
          VTask.of(
              () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt <= 2) {
                  throw new RuntimeException("transient failure " + attempt);
                }
                return attempt;
              });

      VTask<Integer> resilient =
          Resilience.<Integer>builder(task).withCircuitBreaker(cb).withRetry(retry).build();

      Integer result = resilient.run();

      assertThat(result).isEqualTo(3);
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Bulkhead limits concurrency while allowing sequential calls")
    void bulkheadAllowsSequentialCalls() {
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(2);
      AtomicInteger counter = new AtomicInteger(0);

      VTask<Integer> task = VTask.of(counter::incrementAndGet);

      VTask<Integer> resilient = Resilience.<Integer>builder(task).withBulkhead(bulkhead).build();

      Integer first = resilient.run();
      Integer second = resilient.run();

      assertThat(first).isEqualTo(1);
      assertThat(second).isEqualTo(2);
    }

    @Test
    @DisplayName("Builder with CircuitBreakerConfig overload creates circuit breaker from config")
    void builderWithCircuitBreakerConfig() {
      AtomicInteger attempts = new AtomicInteger(0);
      CircuitBreakerConfig config = shortCbConfig(5);
      RetryPolicy retry = shortRetry(3);

      VTask<String> task =
          VTask.of(
              () -> {
                if (attempts.incrementAndGet() < 3) {
                  throw new RuntimeException("fail");
                }
                return "done";
              });

      VTask<String> resilient =
          Resilience.<String>builder(task).withCircuitBreaker(config).withRetry(retry).build();

      String result = resilient.run();

      assertThat(result).isEqualTo("done");
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Builder with BulkheadConfig overload creates bulkhead from config")
    void builderWithBulkheadConfig() {
      BulkheadConfig config = BulkheadConfig.builder().maxConcurrent(5).build();

      VTask<String> task = VTask.succeed("ok");

      VTask<String> resilient = Resilience.<String>builder(task).withBulkhead(config).build();

      String result = resilient.run();

      assertThat(result).isEqualTo("ok");
    }
  }

  @Nested
  @DisplayName("Circuit Breaker Stops Retry Tests")
  class CircuitBreakerStopsRetryTests {

    @Test
    @DisplayName(
        "CircuitOpenException is thrown when circuit is open and retry predicate excludes it")
    void circuitOpenExceptionNotRetriedWhenExcluded() {
      AtomicInteger attempts = new AtomicInteger(0);
      // Threshold of 2 means circuit opens after 2 failures
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(2));
      // Retry only RuntimeException (not CircuitOpenException subtype check won't help here
      // since CircuitOpenException extends RuntimeException, so we use a custom predicate)
      RetryPolicy retry =
          RetryPolicy.fixed(5, Duration.ofMillis(1))
              .retryIf(ex -> !(ex instanceof CircuitOpenException));

      VTask<String> task =
          VTask.of(
              () -> {
                attempts.incrementAndGet();
                throw new RuntimeException("service down");
              });

      VTask<String> resilient =
          Resilience.<String>builder(task).withCircuitBreaker(cb).withRetry(retry).build();

      // The circuit opens after 2 real failures. The 3rd attempt gets CircuitOpenException
      // which is not retryable, so it stops immediately.
      assertThatThrownBy(resilient::run).isInstanceOf(CircuitOpenException.class);

      // Only 2 real task attempts + 1 circuit-rejected attempt (which throws CircuitOpenException)
      assertThat(attempts.get()).isEqualTo(2);
      assertThat(cb.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);
    }

    @Test
    @DisplayName("Open circuit rejects immediately without executing the task")
    void openCircuitRejectsImmediately() {
      AtomicInteger attempts = new AtomicInteger(0);
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(3));
      // Manually trip the circuit open
      cb.tripOpen();

      VTask<String> task =
          VTask.of(
              () -> {
                attempts.incrementAndGet();
                return "should not reach here";
              });

      VTask<String> resilient = Resilience.<String>builder(task).withCircuitBreaker(cb).build();

      assertThatThrownBy(resilient::run).isInstanceOf(CircuitOpenException.class);

      // Task was never executed because circuit was already open
      assertThat(attempts.get()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("Fallback Tests")
  class FallbackTests {

    @Test
    @DisplayName("Fallback provides value when task fails")
    void fallbackProvidesValueOnFailure() {
      VTask<String> task = VTask.fail(new RuntimeException("boom"));

      VTask<String> resilient =
          Resilience.<String>builder(task).withFallback(ex -> "fallback value").build();

      String result = resilient.run();

      assertThat(result).isEqualTo("fallback value");
    }

    @Test
    @DisplayName("Fallback receives the causing exception")
    void fallbackReceivesCausingException() {
      RuntimeException cause = new RuntimeException("original error");
      VTask<String> task = VTask.fail(cause);

      VTask<String> resilient =
          Resilience.<String>builder(task)
              .withFallback(ex -> "recovered from: " + ex.getMessage())
              .build();

      String result = resilient.run();

      assertThat(result).isEqualTo("recovered from: original error");
    }

    @Test
    @DisplayName("Fallback is not invoked on success")
    void fallbackNotInvokedOnSuccess() {
      AtomicInteger fallbackCalls = new AtomicInteger(0);

      VTask<String> task = VTask.succeed("success");

      VTask<String> resilient =
          Resilience.<String>builder(task)
              .withFallback(
                  ex -> {
                    fallbackCalls.incrementAndGet();
                    return "fallback";
                  })
              .build();

      String result = resilient.run();

      assertThat(result).isEqualTo("success");
      assertThat(fallbackCalls.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Fallback activates after retry exhaustion")
    void fallbackAfterRetryExhaustion() {
      AtomicInteger attempts = new AtomicInteger(0);
      RetryPolicy retry = shortRetry(3);

      VTask<String> task =
          VTask.of(
              () -> {
                attempts.incrementAndGet();
                throw new RuntimeException("persistent failure");
              });

      VTask<String> resilient =
          Resilience.<String>builder(task)
              .withRetry(retry)
              .withFallback(ex -> "fallback after retries")
              .build();

      String result = resilient.run();

      assertThat(result).isEqualTo("fallback after retries");
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Fallback activates after circuit breaker opens")
    void fallbackAfterCircuitBreakerOpens() {
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(1));
      cb.tripOpen();

      VTask<String> task = VTask.of(() -> "should not run");

      VTask<String> resilient =
          Resilience.<String>builder(task)
              .withCircuitBreaker(cb)
              .withFallback(ex -> "circuit open fallback")
              .build();

      String result = resilient.run();

      assertThat(result).isEqualTo("circuit open fallback");
    }
  }

  @Nested
  @DisplayName("Convenience Method Tests")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("Resilience.builder() returns a working builder")
    void builderReturnsWorkingBuilder() {
      VTask<String> task = VTask.succeed("built");

      VTask<String> resilient = Resilience.<String>builder(task).build();

      assertThat(resilient.run()).isEqualTo("built");
    }

    @Test
    @DisplayName("withCircuitBreakerAndRetry() composes circuit breaker and retry")
    void withCircuitBreakerAndRetryComposes() {
      AtomicInteger attempts = new AtomicInteger(0);
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(5));
      RetryPolicy retry = shortRetry(3);

      VTask<String> task =
          VTask.of(
              () -> {
                if (attempts.incrementAndGet() < 3) {
                  throw new RuntimeException("transient");
                }
                return "recovered";
              });

      VTask<String> resilient = Resilience.withCircuitBreakerAndRetry(task, cb, retry);

      String result = resilient.run();

      assertThat(result).isEqualTo("recovered");
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("protect() composes circuit breaker, retry, and bulkhead")
    void protectComposesAllThreePatterns() {
      AtomicInteger attempts = new AtomicInteger(0);
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(5));
      RetryPolicy retry = shortRetry(3);
      Bulkhead bulkhead = Bulkhead.withMaxConcurrent(5);

      VTask<String> task =
          VTask.of(
              () -> {
                if (attempts.incrementAndGet() < 2) {
                  throw new RuntimeException("transient");
                }
                return "protected";
              });

      VTask<String> resilient = Resilience.protect(task, cb, retry, bulkhead);

      String result = resilient.run();

      assertThat(result).isEqualTo("protected");
      assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("withRetryPerElement() wraps per-element function with retry")
    void withRetryPerElementWrapsFunction() {
      AtomicInteger callCount = new AtomicInteger(0);
      RetryPolicy retry = shortRetry(3);

      Function<Integer, VTask<String>> original =
          n ->
              VTask.of(
                  () -> {
                    int count = callCount.incrementAndGet();
                    // Fail on first call for each element, succeed on second
                    if (count % 2 == 1) {
                      throw new RuntimeException("transient for " + n);
                    }
                    return "result-" + n;
                  });

      Function<Integer, VTask<String>> wrapped = Resilience.withRetryPerElement(original, retry);

      String result1 = wrapped.apply(1).run();
      String result2 = wrapped.apply(2).run();

      assertThat(result1).isEqualTo("result-1");
      assertThat(result2).isEqualTo("result-2");
      // Each element required 2 attempts (1 failure + 1 success), so 4 total calls
      assertThat(callCount.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("withCircuitBreakerPerElement() wraps per-element function with circuit breaker")
    void withCircuitBreakerPerElementWrapsFunction() {
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(5));

      Function<Integer, VTask<String>> original = n -> VTask.succeed("element-" + n);

      Function<Integer, VTask<String>> wrapped =
          Resilience.withCircuitBreakerPerElement(original, cb);

      String result1 = wrapped.apply(1).run();
      String result2 = wrapped.apply(2).run();

      assertThat(result1).isEqualTo("element-1");
      assertThat(result2).isEqualTo("element-2");
      assertThat(cb.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }

    @Test
    @DisplayName("withCircuitBreakerPerElement() opens circuit after enough failures")
    void withCircuitBreakerPerElementOpensCircuit() {
      CircuitBreaker cb = CircuitBreaker.create(shortCbConfig(2));

      Function<Integer, VTask<String>> original =
          n -> VTask.fail(new RuntimeException("fail-" + n));

      Function<Integer, VTask<String>> wrapped =
          Resilience.withCircuitBreakerPerElement(original, cb);

      // First 2 calls fail and count towards circuit breaker threshold
      assertThatThrownBy(() -> wrapped.apply(1).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("fail-1");

      assertThatThrownBy(() -> wrapped.apply(2).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("fail-2");

      // Circuit should now be open; 3rd call is rejected
      assertThat(cb.currentStatus()).isEqualTo(CircuitBreaker.Status.OPEN);

      assertThatThrownBy(() -> wrapped.apply(3).run()).isInstanceOf(CircuitOpenException.class);
    }
  }
}
