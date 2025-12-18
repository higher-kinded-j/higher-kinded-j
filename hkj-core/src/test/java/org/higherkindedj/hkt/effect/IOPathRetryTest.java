// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.resilience.RetryExhaustedException;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for IOPath retry integration.
 *
 * <p>Tests cover the withRetry() and retry() methods on IOPath.
 */
@DisplayName("IOPath Retry Integration Tests")
class IOPathRetryTest {

  @Nested
  @DisplayName("withRetry() Method")
  class WithRetryTests {

    @Test
    @DisplayName("withRetry() returns result on success")
    void withRetryReturnsResultOnSuccess() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      IOPath<String> path = Path.ioPure("success").withRetry(policy);

      assertThat(path.unsafeRun()).isEqualTo("success");
    }

    @Test
    @DisplayName("withRetry() retries on failure and succeeds")
    void withRetryRetriesAndSucceeds() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    if (attempts.incrementAndGet() < 3) {
                      throw new RuntimeException("Failed");
                    }
                    return "success";
                  })
              .withRetry(policy);

      assertThat(path.unsafeRun()).isEqualTo("success");
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("withRetry() throws RetryExhaustedException when all attempts fail")
    void withRetryThrowsRetryExhaustedExceptionWhenAllFail() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    throw new RuntimeException("Always fails");
                  })
              .withRetry(policy);

      assertThatThrownBy(path::unsafeRun)
          .isInstanceOf(RetryExhaustedException.class)
          .hasMessageContaining("3 attempts");
    }

    @Test
    @DisplayName("withRetry() respects retry predicate")
    void withRetryRespectsRetryPredicate() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1)).retryOn(IOException.class);
      AtomicInteger attempts = new AtomicInteger(0);

      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("Not retryable");
                  })
              .withRetry(policy);

      // Non-retryable exceptions are thrown directly without wrapping
      assertThatThrownBy(path::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Not retryable");

      // Only one attempt because RuntimeException is not retryable
      assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("withRetry() validates null policy")
    void withRetryValidatesNullPolicy() {
      IOPath<String> path = Path.ioPure("value");

      assertThatNullPointerException()
          .isThrownBy(() -> path.withRetry(null))
          .withMessageContaining("policy must not be null");
    }

    @Test
    @DisplayName("withRetry() is lazy - doesn't execute until run")
    void withRetryIsLazy() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    attempts.incrementAndGet();
                    return "result";
                  })
              .withRetry(policy);

      // Not executed yet
      assertThat(attempts.get()).isEqualTo(0);

      // Now execute
      path.unsafeRun();
      assertThat(attempts.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("retry() Convenience Method")
  class RetryConvenienceTests {

    @Test
    @DisplayName("retry() uses exponential backoff with jitter")
    void retryUsesExponentialBackoff() {
      AtomicInteger attempts = new AtomicInteger(0);

      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("Retry");
                    }
                    return "success";
                  })
              .retry(3, Duration.ofMillis(1));

      assertThat(path.unsafeRun()).isEqualTo("success");
      assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("retry() throws when all attempts fail")
    void retryThrowsWhenAllFail() {
      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    throw new RuntimeException("Fail");
                  })
              .retry(3, Duration.ofMillis(1));

      assertThatThrownBy(path::unsafeRun)
          .isInstanceOf(RetryExhaustedException.class)
          .hasMessageContaining("3 attempts");
    }
  }

  @Nested
  @DisplayName("Integration with other IOPath methods")
  class IntegrationTests {

    @Test
    @DisplayName("withRetry() can be chained with map()")
    void withRetryCanBeChainedWithMap() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      IOPath<String> path =
          Path.<Integer>io(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("Retry");
                    }
                    return 42;
                  })
              .withRetry(policy)
              .map(i -> "value: " + i);

      assertThat(path.unsafeRun()).isEqualTo("value: 42");
    }

    @Test
    @DisplayName("withRetry() can be chained with via()")
    void withRetryCanBeChainedWithVia() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      IOPath<String> path =
          Path.<Integer>io(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("Retry");
                    }
                    return 42;
                  })
              .withRetry(policy)
              .via(i -> Path.ioPure("value: " + i));

      assertThat(path.unsafeRun()).isEqualTo("value: 42");
    }

    @Test
    @DisplayName("withRetry() works with handleError()")
    void withRetryWorksWithHandleError() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));

      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    throw new RuntimeException("Fail");
                  })
              .withRetry(policy)
              .handleError(ex -> "recovered: " + ex.getMessage());

      assertThat(path.unsafeRun()).isEqualTo("recovered: Retry exhausted after 2 attempts");
    }

    @Test
    @DisplayName("retry can be applied after map")
    void retryCanBeAppliedAfterMap() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      IOPath<String> path =
          Path.ioPure(10)
              .<String>map(
                  _ -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("Retry");
                    }
                    return "mapped";
                  })
              .withRetry(policy);

      assertThat(path.unsafeRun()).isEqualTo("mapped");
      assertThat(attempts.get()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("runSafe() with retry")
  class RunSafeWithRetryTests {

    @Test
    @DisplayName("runSafe() captures RetryExhaustedException")
    void runSafeCapturesRetryExhaustedException() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));

      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    throw new RuntimeException("Fail");
                  })
              .withRetry(policy);

      var result = path.runSafe();

      assertThat(result.isFailure()).isTrue();
      assertThat(result).isInstanceOf(Try.Failure.class);
      assertThat(((Try.Failure<?>) result).cause()).isInstanceOf(RetryExhaustedException.class);
    }

    @Test
    @DisplayName("runSafe() returns success after retry")
    void runSafeReturnsSuccessAfterRetry() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("Retry");
                    }
                    return "success";
                  })
              .withRetry(policy);

      var result = path.runSafe();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo("success");
    }
  }
}
