// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
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
 * Tests for VTaskPath resilience methods.
 *
 * <p>Tests cover retry, circuit breaker, bulkhead, error wrapping, error transformation, resource
 * safety, race, and parallel combinators.
 */
@DisplayName("VTaskPath Resilience Tests")
class VTaskPathResilienceTest {

  @Nested
  @DisplayName("withRetry()")
  class WithRetryTests {

    @Test
    @DisplayName("returns result on first success without retrying")
    void returnsResultOnFirstSuccess() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      VTaskPath<String> path = Path.vtaskPure("success").withRetry(policy);

      assertThat(path.unsafeRun()).isEqualTo("success");
    }

    @Test
    @DisplayName("retries on failure and eventually succeeds")
    void retriesAndSucceeds() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    if (attempts.incrementAndGet() < 3) {
                      throw new RuntimeException("transient failure");
                    }
                    return "recovered";
                  })
              .withRetry(policy);

      assertThat(path.unsafeRun()).isEqualTo("recovered");
      assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("throws RetryExhaustedException when all attempts fail")
    void throwsWhenAllAttemptsFail() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));

      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("persistent failure");
                  })
              .withRetry(policy);

      assertThatThrownBy(path::unsafeRun)
          .isInstanceOf(RetryExhaustedException.class)
          .hasMessageContaining("3 attempts");
    }

    @Test
    @DisplayName("convenience retry() method uses exponential backoff")
    void convenienceRetryMethod() {
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("retry");
                    }
                    return "ok";
                  })
              .retry(3, Duration.ofMillis(1));

      assertThat(path.unsafeRun()).isEqualTo("ok");
      assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("withRetry() is lazy - does not execute until run")
    void withRetryIsLazy() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    executed.set(true);
                    return "value";
                  })
              .withRetry(policy);

      assertThat(executed).isFalse();
      path.unsafeRun();
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

      VTaskPath<String> path = Path.vtaskPure("hello").withCircuitBreaker(cb);

      assertThat(path.unsafeRun()).isEqualTo("hello");
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

      // Trip the circuit breaker by causing failures
      for (int i = 0; i < 2; i++) {
        try {
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .withCircuitBreaker(cb)
              .unsafeRun();
        } catch (RuntimeException ignored) {
          // expected
        }
      }

      // Now the circuit should be open
      VTaskPath<String> path = Path.vtaskPure("should not reach").withCircuitBreaker(cb);

      assertThatThrownBy(path::unsafeRun).isInstanceOf(CircuitOpenException.class);
    }

    @Test
    @DisplayName("records successful calls")
    void recordsSuccessfulCalls() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(5)
              .openDuration(Duration.ofMillis(50))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      Path.vtaskPure("ok").withCircuitBreaker(cb).unsafeRun();

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

      VTaskPath<String> path = Path.vtaskPure("ok").withBulkhead(bh);

      assertThat(path.unsafeRun()).isEqualTo("ok");
    }

    @Test
    @DisplayName("permits are released after execution")
    void permitsReleasedAfterExecution() {
      Bulkhead bh = Bulkhead.withMaxConcurrent(1);

      VTaskPath<String> path = Path.vtaskPure("first").withBulkhead(bh);
      assertThat(path.unsafeRun()).isEqualTo("first");

      // Should be able to run again since the permit was released
      VTaskPath<String> path2 = Path.vtaskPure("second").withBulkhead(bh);
      assertThat(path2.unsafeRun()).isEqualTo("second");
    }

    @Test
    @DisplayName("permits are released even on failure")
    void permitsReleasedOnFailure() {
      Bulkhead bh = Bulkhead.withMaxConcurrent(1);

      VTaskPath<String> failing =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .withBulkhead(bh);

      assertThatThrownBy(failing::unsafeRun).isInstanceOf(RuntimeException.class);

      // Permit should still be available
      VTaskPath<String> path = Path.vtaskPure("after failure").withBulkhead(bh);
      assertThat(path.unsafeRun()).isEqualTo("after failure");
    }
  }

  @Nested
  @DisplayName("catching()")
  class CatchingTests {

    @Test
    @DisplayName("wraps success in Either.right")
    void wrapsSuccessInRight() {
      VTaskPath<Either<String, Integer>> path = Path.vtaskPure(42).catching(Throwable::getMessage);

      Either<String, Integer> result = path.unsafeRun();

      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("wraps failure in Either.left with mapped exception")
    void wrapsFailureInLeft() {
      VTaskPath<Either<String, Integer>> path =
          Path.<Integer>vtask(
                  () -> {
                    throw new RuntimeException("boom");
                  })
              .catching(Throwable::getMessage);

      Either<String, Integer> result = path.unsafeRun();

      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("boom");
    }

    @Test
    @DisplayName("catching() always succeeds regardless of original outcome")
    void alwaysSucceeds() {
      VTaskPath<Either<String, Integer>> path =
          Path.<Integer>vtask(
                  () -> {
                    throw new RuntimeException("error");
                  })
              .catching(Throwable::getMessage);

      // This should not throw
      Either<String, Integer> result = path.unsafeRun();
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("asMaybe()")
  class AsMaybeTests {

    @Test
    @DisplayName("wraps success in Just")
    void wrapsSuccessInJust() {
      VTaskPath<Maybe<String>> path = Path.vtaskPure("hello").asMaybe();

      Maybe<String> result = path.unsafeRun();

      assertThat(result.isJust()).isTrue();
      assertThat(result.orElse("missing")).isEqualTo("hello");
    }

    @Test
    @DisplayName("wraps failure in Nothing")
    void wrapsFailureInNothing() {
      VTaskPath<Maybe<String>> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("error");
                  })
              .asMaybe();

      Maybe<String> result = path.unsafeRun();

      assertThat(result).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("asMaybe() always succeeds")
    void alwaysSucceeds() {
      VTaskPath<Maybe<Integer>> path =
          Path.<Integer>vtask(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .asMaybe();

      // Should not throw
      Maybe<Integer> result = path.unsafeRun();
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("asTry()")
  class AsTryTests {

    @Test
    @DisplayName("wraps success in Try.Success")
    void wrapsSuccessInTrySuccess() {
      VTaskPath<Try<String>> path = Path.vtaskPure("value").asTry();

      Try<String> result = path.unsafeRun();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo("value");
    }

    @Test
    @DisplayName("wraps failure in Try.Failure")
    void wrapsFailureInTryFailure() {
      VTaskPath<Try<String>> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("oops");
                  })
              .asTry();

      Try<String> result = path.unsafeRun();

      assertThat(result.isFailure()).isTrue();
      assertThat(((Try.Failure<String>) result).cause())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("oops");
    }

    @Test
    @DisplayName("asTry() always succeeds")
    void alwaysSucceeds() {
      VTaskPath<Try<Integer>> path =
          Path.<Integer>vtask(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .asTry();

      // Should not throw
      Try<Integer> result = path.unsafeRun();
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("mapError()")
  class MapErrorTests {

    @Test
    @DisplayName("transforms exception without affecting success")
    void doesNotAffectSuccess() {
      VTaskPath<String> path =
          Path.vtaskPure("ok")
              .mapError(ex -> new IllegalStateException("mapped: " + ex.getMessage()));

      assertThat(path.unsafeRun()).isEqualTo("ok");
    }

    @Test
    @DisplayName("transforms the exception on failure")
    void transformsExceptionOnFailure() {
      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("original");
                  })
              .mapError(ex -> new IllegalStateException("mapped: " + ex.getMessage()));

      assertThatThrownBy(path::unsafeRun)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("mapped: original");
    }

    @Test
    @DisplayName("mapError() chains with other operations")
    void chainsWithOtherOperations() {
      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("original");
                  })
              .mapError(ex -> new IllegalStateException("mapped"))
              .handleError(ex -> "recovered: " + ex.getClass().getSimpleName());

      assertThat(path.unsafeRun()).isEqualTo("recovered: IllegalStateException");
    }
  }

  @Nested
  @DisplayName("guarantee()")
  class GuaranteeTests {

    @Test
    @DisplayName("runs finalizer on success")
    void runsFinalizerOnSuccess() {
      AtomicBoolean finalizerRan = new AtomicBoolean(false);

      VTaskPath<String> path = Path.vtaskPure("ok").guarantee(() -> finalizerRan.set(true));

      assertThat(finalizerRan).isFalse();
      assertThat(path.unsafeRun()).isEqualTo("ok");
      assertThat(finalizerRan).isTrue();
    }

    @Test
    @DisplayName("runs finalizer on failure")
    void runsFinalizerOnFailure() {
      AtomicBoolean finalizerRan = new AtomicBoolean(false);

      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .guarantee(() -> finalizerRan.set(true));

      assertThatThrownBy(path::unsafeRun).isInstanceOf(RuntimeException.class);
      assertThat(finalizerRan).isTrue();
    }

    @Test
    @DisplayName("preserves original exception when finalizer runs")
    void preservesOriginalException() {
      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("original error");
                  })
              .guarantee(() -> {});

      assertThatThrownBy(path::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("original error");
    }
  }

  @Nested
  @DisplayName("race()")
  class RaceTests {

    @Test
    @DisplayName("returns first to complete")
    void returnsFirstToComplete() {
      VTaskPath<String> fast = Path.vtaskPure("fast");
      VTaskPath<String> slow =
          Path.vtask(
              () -> {
                Thread.sleep(500);
                return "slow";
              });

      VTaskPath<String> result = fast.race(slow);

      assertThat(result.unsafeRun()).isEqualTo("fast");
    }

    @Test
    @DisplayName("race between two pure values returns one of them")
    void raceBetweenPureValues() {
      VTaskPath<String> first = Path.vtaskPure("a");
      VTaskPath<String> second = Path.vtaskPure("b");

      VTaskPath<String> result = first.race(second);

      assertThat(result.unsafeRun()).isIn("a", "b");
    }
  }

  @Nested
  @DisplayName("Chained Resilience Patterns")
  class ChainedResilienceTests {

    @Test
    @DisplayName("withRetry() chains with map()")
    void withRetryChainedWithMap() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskPath<String> path =
          Path.<Integer>vtask(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("fail");
                    }
                    return 42;
                  })
              .withRetry(policy)
              .map(i -> "value: " + i);

      assertThat(path.unsafeRun()).isEqualTo("value: 42");
    }

    @Test
    @DisplayName("withRetry() chains with handleError()")
    void withRetryChainedWithHandleError() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));

      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .withRetry(policy)
              .handleError(ex -> "recovered: " + ex.getClass().getSimpleName());

      assertThat(path.unsafeRun()).isEqualTo("recovered: RetryExhaustedException");
    }

    @Test
    @DisplayName("withCircuitBreaker() chains with catching()")
    void withCircuitBreakerChainedWithCatching() {
      CircuitBreakerConfig config =
          CircuitBreakerConfig.builder()
              .failureThreshold(1)
              .openDuration(Duration.ofSeconds(10))
              .build();
      CircuitBreaker cb = CircuitBreaker.create(config);

      // Trip the circuit breaker
      try {
        Path.<String>vtask(
                () -> {
                  throw new RuntimeException("trip");
                })
            .withCircuitBreaker(cb)
            .unsafeRun();
      } catch (RuntimeException ignored) {
        // expected
      }

      // Now use catching() to capture the CircuitOpenException
      VTaskPath<Either<String, String>> path =
          Path.<String>vtaskPure("value").withCircuitBreaker(cb).catching(Throwable::getMessage);

      Either<String, String> result = path.unsafeRun();

      assertThat(result.isLeft()).isTrue();
    }

    @Test
    @DisplayName("guarantee() with retry ensures finalizer runs")
    void guaranteeWithRetry() {
      RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(1));
      AtomicBoolean finalizerRan = new AtomicBoolean(false);

      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("fail");
                  })
              .withRetry(policy)
              .guarantee(() -> finalizerRan.set(true));

      assertThatThrownBy(path::unsafeRun).isInstanceOf(RetryExhaustedException.class);
      assertThat(finalizerRan).isTrue();
    }

    @Test
    @DisplayName("parZipWith() combines two VTaskPaths in parallel")
    void parZipWithCombinesTwoPaths() {
      VTaskPath<String> first = Path.vtaskPure("hello");
      VTaskPath<Integer> second = Path.vtaskPure(3);

      VTaskPath<String> result = first.parZipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.unsafeRun()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("parZipWith() with retry on one side")
    void parZipWithWithRetry() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(1));
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskPath<Integer> retriedPath =
          Path.<Integer>vtask(
                  () -> {
                    if (attempts.incrementAndGet() < 2) {
                      throw new RuntimeException("fail");
                    }
                    return 10;
                  })
              .withRetry(policy);

      VTaskPath<Integer> purePath = Path.vtaskPure(5);

      VTaskPath<Integer> result = retriedPath.parZipWith(purePath, Integer::sum);

      assertThat(result.unsafeRun()).isEqualTo(15);
    }
  }
}
