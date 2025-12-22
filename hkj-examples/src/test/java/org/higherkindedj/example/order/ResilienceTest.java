// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.resilience.Resilience;
import org.higherkindedj.example.order.resilience.RetryPolicy;
import org.higherkindedj.hkt.effect.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for resilience utilities. */
@DisplayName("Resilience")
class ResilienceTest {

  @Nested
  @DisplayName("RetryPolicy")
  class RetryPolicyTests {

    @Test
    @DisplayName("defaults returns sensible default values")
    void defaultsReturnsSensibleValues() {
      var policy = RetryPolicy.defaults();

      assertThat(policy.maxAttempts()).isEqualTo(3);
      assertThat(policy.initialDelay()).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.backoffMultiplier()).isEqualTo(2.0);
      assertThat(policy.maxDelay()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("delayForAttempt calculates exponential backoff")
    void delayForAttemptCalculatesExponentialBackoff() {
      var policy = RetryPolicy.of(5, Duration.ofMillis(100), Duration.ofSeconds(10), 2.0);

      assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ZERO);
      assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMillis(200));
      assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMillis(400));
    }

    @Test
    @DisplayName("delayForAttempt respects maxDelay cap")
    void delayForAttemptRespectsMaxDelay() {
      var policy = RetryPolicy.of(10, Duration.ofSeconds(1), Duration.ofSeconds(5), 3.0);

      // After several attempts, delay should be capped at maxDelay
      assertThat(policy.delayForAttempt(10).toMillis())
          .isLessThanOrEqualTo(policy.maxDelay().toMillis());
    }

    @Test
    @DisplayName("noRetry creates policy with single attempt")
    void noRetryCreatesSingleAttemptPolicy() {
      var policy = RetryPolicy.noRetry();

      assertThat(policy.maxAttempts()).isEqualTo(1);
      assertThat(policy.retryOn().test(new IOException())).isFalse();
    }

    @Test
    @DisplayName("alsoRetryOn extends retry conditions")
    void alsoRetryOnExtendsConditions() {
      var policy = RetryPolicy.defaults().alsoRetryOn(IllegalStateException.class);

      assertThat(policy.retryOn().test(new IOException())).isTrue();
      assertThat(policy.retryOn().test(new IllegalStateException())).isTrue();
      assertThat(policy.retryOn().test(new NullPointerException())).isFalse();
    }
  }

  @Nested
  @DisplayName("Retry Operations")
  class RetryOperationTests {

    @Test
    @DisplayName("withRetry succeeds on first attempt when no error")
    void withRetrySucceedsOnFirstAttempt() {
      var policy = RetryPolicy.forTesting();
      var operation = Path.io(() -> "success");

      var result = Resilience.withRetry(operation, policy);

      assertThat(result.unsafeRun()).isEqualTo("success");
    }

    @Test
    @DisplayName("withRetry retries on transient failure")
    void withRetryRetriesOnTransientFailure() {
      var policy = RetryPolicy.forTesting();
      var attemptCounter = new AtomicInteger(0);

      var operation =
          Path.io(
              () -> {
                int attempt = attemptCounter.incrementAndGet();
                if (attempt < 2) {
                  throw new UncheckedIOException(new IOException("Transient failure"));
                }
                return "success";
              });

      var result = Resilience.withRetry(operation, policy);

      assertThat(result.unsafeRun()).isEqualTo("success");
      assertThat(attemptCounter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("withRetry throws after max attempts exceeded")
    void withRetryThrowsAfterMaxAttempts() {
      var policy = RetryPolicy.forTesting(); // max 2 attempts

      var operation =
          Path.<String>io(
              () -> {
                throw new UncheckedIOException(new IOException("Persistent failure"));
              });

      var retried = Resilience.withRetry(operation, policy);

      assertThatThrownBy(retried::unsafeRun)
          .isInstanceOf(UncheckedIOException.class)
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Nested
  @DisplayName("Timeout Operations")
  class TimeoutOperationTests {

    @Test
    @DisplayName("withTimeout succeeds for fast operations")
    void withTimeoutSucceedsForFastOperations() {
      var operation = Path.io(() -> "quick result");

      var result = Resilience.withTimeout(operation, Duration.ofSeconds(1), "quickOperation");

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo("quick result");
    }

    @Test
    @DisplayName("withTimeout returns error for slow operations")
    void withTimeoutReturnsErrorForSlowOperations() {
      var slowOperation =
          Path.io(
              () -> {
                try {
                  Thread.sleep(500);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "slow result";
              });

      var result = Resilience.withTimeout(slowOperation, Duration.ofMillis(50), "slowOperation");

      assertThat(result.run().isLeft()).isTrue();
      var error = result.run().getLeft();
      assertThat(error).isInstanceOf(OrderError.SystemError.class);
      assertThat(error.code()).isEqualTo("TIMEOUT");
    }
  }

  @Nested
  @DisplayName("Resilient Operations")
  class ResilientOperationTests {

    @Test
    @DisplayName("resilient combines retry and timeout")
    void resilientCombinesRetryAndTimeout() {
      var attemptCounter = new AtomicInteger(0);
      var policy = RetryPolicy.forTesting();

      var operation =
          Path.io(
              () -> {
                int attempt = attemptCounter.incrementAndGet();
                if (attempt < 2) {
                  throw new UncheckedIOException(new IOException("First attempt fails"));
                }
                return "success after retry";
              });

      var result =
          Resilience.resilient(operation, policy, Duration.ofSeconds(5), "retryableOperation");

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo("success after retry");
    }
  }
}
