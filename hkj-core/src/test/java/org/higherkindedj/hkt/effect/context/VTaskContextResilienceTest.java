// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.CircuitOpenException;
import org.higherkindedj.hkt.resilience.RetryExhaustedException;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for VTaskContext resilience methods.
 *
 * <p>Tests cover retry, circuit breaker, bulkhead, and combined resilience patterns.
 */
@DisplayName("VTaskContext Resilience Tests")
class VTaskContextResilienceTest {

  @Nested
  @DisplayName("withRetry()")
  class WithRetryTests {

    @Test
    @DisplayName("returns result on first success without retrying")
    void returnsResultOnFirstSuccess() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      VTaskContext<String> context = VTaskContext.pure("success").withRetry(policy);

      Try<String> result = context.run();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo("success");
    }

    @Test
    @DisplayName("retries on failure and eventually succeeds")
    void retriesAndSucceeds() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    if (attempts.incrementAndGet() < 3) {
                      throw new RuntimeException("transient failure");
                    }
                    return "recovered";
                  })
              .withRetry(policy);

      Try<String> result = context.run();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo("recovered");
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("returns failure with RetryExhaustedException when all attempts fail")
    void returnsFailureWhenAllAttemptsFail() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    throw new RuntimeException("persistent failure");
                  })
              .withRetry(policy);

      Try<String> result = context.run();
      assertThat(result.isFailure()).isTrue();
      assertThat(((Try.Failure<String>) result).cause())
          .isInstanceOf(RetryExhaustedException.class)
          .hasMessageContaining("3 attempts");
    }

    @Test
    @DisplayName("runOrThrow() throws RetryExhaustedException when all attempts fail")
    void runOrThrowThrowsOnExhaustion() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));

      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .withRetry(policy);

      assertThatThrownBy(context::runOrThrow)
          .isInstanceOf(RetryExhaustedException.class)
          .hasMessageContaining("2 attempts");
    }

    @Test
    @DisplayName("convenience retry() method works")
    void convenienceRetryMethod() {
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("retry");
                    }
                    return "ok";
                  })
              .retry(3, Duration.ofMillis(1));

      assertThat(context.runOrThrow()).isEqualTo("ok");
      assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("withRetry() is lazy - does not execute until run")
    void withRetryIsLazy() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    executed.set(true);
                    return "value";
                  })
              .withRetry(policy);

      assertThat(executed).isFalse();
      context.run();
      assertThat(executed).isTrue();
    }
  }

  @Nested
  @DisplayName("withCircuitBreaker()")
  class WithCircuitBreakerTests {

    @Test
    @DisplayName("allows calls when circuit is closed")
    void allowsCallsWhenClosed() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(2)
              .openDuration(Duration.ofMillis(50))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      VTaskContext<String> context = VTaskContext.pure("hello").withCircuitBreaker(cb);

      Try<String> result = context.run();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo("hello");
    }

    @Test
    @DisplayName("rejects calls when circuit is open")
    void rejectsCallsWhenOpen() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(2)
              .openDuration(Duration.ofSeconds(10))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      // Trip the circuit breaker
      for (int i = 0; i < 2; i++) {
        VTaskContext.<String>of(
                () -> {
                  throw new RuntimeException("fail");
                })
            .withCircuitBreaker(cb)
            .run();
      }

      // Circuit should now be open
      VTaskContext<String> context = VTaskContext.pure("should not reach").withCircuitBreaker(cb);

      Try<String> result = context.run();
      assertThat(result.isFailure()).isTrue();
      assertThat(((Try.Failure<String>) result).cause()).isInstanceOf(CircuitOpenException.class);
    }

    @Test
    @DisplayName("runOrThrow() throws CircuitOpenException when circuit is open")
    void runOrThrowThrowsWhenOpen() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(1)
              .openDuration(Duration.ofSeconds(10))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      // Trip the circuit
      VTaskContext.<String>of(
              () -> {
                throw new RuntimeException("trip");
              })
          .withCircuitBreaker(cb)
          .run();

      VTaskContext<String> context = VTaskContext.pure("value").withCircuitBreaker(cb);

      assertThatThrownBy(context::runOrThrow).isInstanceOf(CircuitOpenException.class);
    }

    @Test
    @DisplayName("records successful calls keeping circuit closed")
    void recordsSuccessfulCalls() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(5)
              .openDuration(Duration.ofMillis(50))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      VTaskContext.pure("ok").withCircuitBreaker(cb).run();

      assertThat(cb.currentStatus()).isEqualTo(CircuitBreaker.Status.CLOSED);
    }
  }

  @Nested
  @DisplayName("withBulkhead()")
  class WithBulkheadTests {

    @Test
    @DisplayName("allows calls within concurrency limit")
    void allowsCallsWithinLimit() {
      Bulkhead bh = Bulkhead.withMaxConcurrent(2);

      VTaskContext<String> context = VTaskContext.pure("ok").withBulkhead(bh);

      Try<String> result = context.run();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo("ok");
    }

    @Test
    @DisplayName("permits are released after execution")
    void permitsReleasedAfterExecution() {
      Bulkhead bh = Bulkhead.withMaxConcurrent(1);

      VTaskContext<String> context1 = VTaskContext.pure("first").withBulkhead(bh);
      assertThat(context1.runOrThrow()).isEqualTo("first");

      // Should be able to run again
      VTaskContext<String> context2 = VTaskContext.pure("second").withBulkhead(bh);
      assertThat(context2.runOrThrow()).isEqualTo("second");
    }

    @Test
    @DisplayName("permits are released even on failure")
    void permitsReleasedOnFailure() {
      Bulkhead bh = Bulkhead.withMaxConcurrent(1);

      VTaskContext<String> failing =
          VTaskContext.<String>of(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .withBulkhead(bh);

      Try<String> failResult = failing.run();
      assertThat(failResult.isFailure()).isTrue();

      // Permit should still be available
      VTaskContext<String> context = VTaskContext.pure("after failure").withBulkhead(bh);
      assertThat(context.runOrThrow()).isEqualTo("after failure");
    }

    @Test
    @DisplayName("runOrThrow() works with bulkhead")
    void runOrThrowWorksWithBulkhead() {
      Bulkhead bh = Bulkhead.withMaxConcurrent(5);

      VTaskContext<Integer> context = VTaskContext.pure(42).withBulkhead(bh);

      assertThat(context.runOrThrow()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Combined Resilience Patterns")
  class CombinedResilienceTests {

    @Test
    @DisplayName("withRetry() and withCircuitBreaker() can be combined")
    void retryWithCircuitBreaker() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(10)
              .openDuration(Duration.ofMillis(50))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      AtomicInteger attempts = new AtomicInteger(0);

      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("transient");
                    }
                    return "success";
                  })
              .withRetry(policy)
              .withCircuitBreaker(cb);

      Try<String> result = context.run();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo("success");
    }

    @Test
    @DisplayName("withRetry() and withBulkhead() can be combined")
    void retryWithBulkhead() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      Bulkhead bh = Bulkhead.withMaxConcurrent(2);

      AtomicInteger attempts = new AtomicInteger(0);

      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("retry");
                    }
                    return "ok";
                  })
              .withRetry(policy)
              .withBulkhead(bh);

      assertThat(context.runOrThrow()).isEqualTo("ok");
      assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("withRetry() chains with map()")
    void retryChainedWithMap() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskContext<String> context =
          VTaskContext.<Integer>of(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("fail");
                    }
                    return 42;
                  })
              .withRetry(policy)
              .map(i -> "value: " + i);

      assertThat(context.runOrThrow()).isEqualTo("value: 42");
    }

    @Test
    @DisplayName("withRetry() chains with recover()")
    void retryChainedWithRecover() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));

      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .withRetry(policy)
              .recover(ex -> "recovered: " + ex.getClass().getSimpleName());

      assertThat(context.runOrThrow()).isEqualTo("recovered: RetryExhaustedException");
    }

    @Test
    @DisplayName("fail() creates a failed context that can be recovered")
    void failCreatesRecoverableContext() {
      VTaskContext<String> context =
          VTaskContext.<String>fail(new RuntimeException("error"))
              .recover(ex -> "recovered: " + ex.getMessage());

      assertThat(context.runOrThrow()).isEqualTo("recovered: error");
    }

    @Test
    @DisplayName("all three resilience methods combined")
    void allThreeCombined() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(10)
              .openDuration(Duration.ofMillis(50))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);
      Bulkhead bh = Bulkhead.withMaxConcurrent(5);

      VTaskContext<String> context =
          VTaskContext.pure("resilient").withRetry(policy).withCircuitBreaker(cb).withBulkhead(bh);

      Try<String> result = context.run();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo("resilient");
    }

    @Test
    @DisplayName("failed context with retry and recover")
    void failedContextWithRetryAndRecover() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));

      VTaskContext<String> context =
          VTaskContext.<String>fail(new RuntimeException("initial failure"))
              .withRetry(policy)
              .recover(ex -> "fallback");

      assertThat(context.runOrThrow()).isEqualTo("fallback");
    }
  }
}
