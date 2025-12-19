// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for RetryPolicy.
 *
 * <p>Tests cover factory methods, configuration methods, delay calculations, and the builder.
 */
@DisplayName("RetryPolicy Test Suite")
class RetryPolicyTest {

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("fixed() creates policy with fixed delay")
    void fixedCreatesFixedDelayPolicy() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(100));

      assertThat(policy.maxAttempts()).isEqualTo(3);
      assertThat(policy.initialDelay()).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.backoffMultiplier()).isEqualTo(1.0);
      assertThat(policy.useJitter()).isFalse();
    }

    @Test
    @DisplayName("fixed() validates arguments")
    void fixedValidatesArguments() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> RetryPolicy.fixed(0, Duration.ofMillis(100)))
          .withMessageContaining("maxAttempts must be at least 1");

      assertThatNullPointerException()
          .isThrownBy(() -> RetryPolicy.fixed(3, null))
          .withMessageContaining("delay must not be null");
    }

    @Test
    @DisplayName("exponentialBackoff() creates policy with exponential delays")
    void exponentialBackoffCreatesExponentialPolicy() {
      RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100));

      assertThat(policy.maxAttempts()).isEqualTo(5);
      assertThat(policy.initialDelay()).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.backoffMultiplier()).isEqualTo(2.0);
      assertThat(policy.useJitter()).isFalse();
    }

    @Test
    @DisplayName("exponentialBackoff() validates arguments")
    void exponentialBackoffValidatesArguments() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> RetryPolicy.exponentialBackoff(0, Duration.ofMillis(100)))
          .withMessageContaining("maxAttempts must be at least 1");

      assertThatNullPointerException()
          .isThrownBy(() -> RetryPolicy.exponentialBackoff(3, null))
          .withMessageContaining("initialDelay must not be null");
    }

    @Test
    @DisplayName("exponentialBackoff() default predicate retries all exceptions")
    void exponentialBackoffDefaultPredicateRetriesAll() {
      // This test covers the default predicate (_ -> true) at line 93
      RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100));

      // Default predicate should return true for any exception
      assertThat(policy.shouldRetry(new RuntimeException())).isTrue();
      assertThat(policy.shouldRetry(new IllegalArgumentException())).isTrue();
      assertThat(policy.shouldRetry(new Exception())).isTrue();
    }

    @Test
    @DisplayName("exponentialBackoffWithJitter() creates policy with jitter")
    void exponentialBackoffWithJitterCreatesJitterPolicy() {
      RetryPolicy policy = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100));

      assertThat(policy.maxAttempts()).isEqualTo(5);
      assertThat(policy.initialDelay()).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.backoffMultiplier()).isEqualTo(2.0);
      assertThat(policy.useJitter()).isTrue();
    }

    @Test
    @DisplayName("noRetry() creates policy that doesn't retry")
    void noRetryCreatesNoRetryPolicy() {
      RetryPolicy policy = RetryPolicy.noRetry();

      assertThat(policy.maxAttempts()).isEqualTo(1);
      assertThat(policy.shouldRetry(new RuntimeException())).isFalse();
    }
  }

  @Nested
  @DisplayName("Configuration Methods")
  class ConfigurationMethodsTests {

    @Test
    @DisplayName("withMaxAttempts() creates new policy with updated attempts")
    void withMaxAttemptsCreatesNewPolicy() {
      RetryPolicy original = RetryPolicy.fixed(3, Duration.ofMillis(100));
      RetryPolicy updated = original.withMaxAttempts(5);

      assertThat(updated.maxAttempts()).isEqualTo(5);
      assertThat(original.maxAttempts()).isEqualTo(3); // Original unchanged
    }

    @Test
    @DisplayName("withMaxAttempts() validates argument")
    void withMaxAttemptsValidatesArgument() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(100));

      assertThatIllegalArgumentException()
          .isThrownBy(() -> policy.withMaxAttempts(0))
          .withMessageContaining("maxAttempts must be at least 1");
    }

    @Test
    @DisplayName("withInitialDelay() creates new policy with updated delay")
    void withInitialDelayCreatesNewPolicy() {
      RetryPolicy original = RetryPolicy.fixed(3, Duration.ofMillis(100));
      RetryPolicy updated = original.withInitialDelay(Duration.ofMillis(200));

      assertThat(updated.initialDelay()).isEqualTo(Duration.ofMillis(200));
      assertThat(original.initialDelay()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("withBackoffMultiplier() creates new policy with updated multiplier")
    void withBackoffMultiplierCreatesNewPolicy() {
      RetryPolicy original = RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100));
      RetryPolicy updated = original.withBackoffMultiplier(3.0);

      assertThat(updated.backoffMultiplier()).isEqualTo(3.0);
      assertThat(original.backoffMultiplier()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("withBackoffMultiplier() validates argument")
    void withBackoffMultiplierValidatesArgument() {
      RetryPolicy policy = RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100));

      assertThatIllegalArgumentException()
          .isThrownBy(() -> policy.withBackoffMultiplier(0.5))
          .withMessageContaining("backoffMultiplier must be at least 1.0");
    }

    @Test
    @DisplayName("withMaxDelay() creates new policy with updated max delay")
    void withMaxDelayCreatesNewPolicy() {
      RetryPolicy original = RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100));
      RetryPolicy updated = original.withMaxDelay(Duration.ofSeconds(30));

      assertThat(updated.maxDelay()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("retryOn() creates policy that only retries specific exception type")
    void retryOnCreatesFilteredPolicy() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(100)).retryOn(IOException.class);

      assertThat(policy.shouldRetry(new IOException())).isTrue();
      assertThat(policy.shouldRetry(new RuntimeException())).isFalse();
    }

    @Test
    @DisplayName("retryIf() creates policy with custom predicate")
    void retryIfCreatesPredicatePolicy() {
      RetryPolicy policy =
          RetryPolicy.fixed(3, Duration.ofMillis(100))
              .retryIf(ex -> ex.getMessage().contains("retry"));

      assertThat(policy.shouldRetry(new RuntimeException("please retry"))).isTrue();
      assertThat(policy.shouldRetry(new RuntimeException("fail"))).isFalse();
    }
  }

  @Nested
  @DisplayName("Delay Calculations")
  class DelayCalculationsTests {

    @Test
    @DisplayName("delayForAttempt() returns initial delay for first attempt")
    void delayForAttemptReturnsInitialDelayForFirst() {
      RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100));

      assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("delayForAttempt() calculates exponential delay")
    void delayForAttemptCalculatesExponentialDelay() {
      RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100));

      // First attempt: 100ms
      // Second attempt: 100 * 2 = 200ms
      // Third attempt: 100 * 4 = 400ms
      assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofMillis(200));
      assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMillis(400));
    }

    @Test
    @DisplayName("delayForAttempt() respects maxDelay")
    void delayForAttemptRespectsMaxDelay() {
      RetryPolicy policy =
          RetryPolicy.exponentialBackoff(10, Duration.ofMillis(100))
              .withMaxDelay(Duration.ofMillis(500));

      // Attempt 4 would be 100 * 8 = 800ms, but capped at 500ms
      assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("delayForAttempt() with jitter returns value within range")
    void delayForAttemptWithJitterReturnsValueInRange() {
      RetryPolicy policy = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100));

      // With jitter, delay should be between 0 and the calculated value
      for (int i = 0; i < 100; i++) {
        Duration delay = policy.delayForAttempt(2);
        assertThat(delay.toMillis()).isBetween(0L, 200L);
      }
    }

    @Test
    @DisplayName("fixed delay doesn't increase")
    void fixedDelayDoesNotIncrease() {
      RetryPolicy policy = RetryPolicy.fixed(5, Duration.ofMillis(100));

      assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("delayForAttempt() with zero initial delay returns zero")
    void delayForAttemptWithZeroDelayReturnsZero() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ZERO);

      assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ZERO);
      assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("delayForAttempt() with zero maxDelay caps to zero")
    void delayForAttemptWithZeroMaxDelayCapsToZero() {
      RetryPolicy policy =
          RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100)).withMaxDelay(Duration.ZERO);

      // Should be capped at 0
      assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("delayForAttempt() with jitter and zero delay returns zero")
    void delayForAttemptWithJitterAndZeroDelayReturnsZero() {
      // Create policy with jitter enabled but zero maxDelay - this tests the delayMillis > 0 branch
      RetryPolicy policy =
          RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100))
              .withMaxDelay(Duration.ZERO);

      // When delayMillis is 0, jitter should not be applied and should return 0
      assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("delayForAttempt() handles attempt number 0 or negative")
    void delayForAttemptHandlesZeroOrNegativeAttempt() {
      RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(100));

      // Attempt 0 or negative should return initial delay (treated as first attempt)
      assertThat(policy.delayForAttempt(0)).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.delayForAttempt(-1)).isEqualTo(Duration.ofMillis(100));
    }
  }

  @Nested
  @DisplayName("Builder Tests")
  class BuilderTests {

    @Test
    @DisplayName("builder() creates policy with all options")
    void builderCreatesCompletePolicy() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .maxAttempts(5)
              .initialDelay(Duration.ofMillis(100))
              .backoffMultiplier(3.0)
              .maxDelay(Duration.ofSeconds(30))
              .useJitter(true)
              .retryOn(IOException.class)
              .build();

      assertThat(policy.maxAttempts()).isEqualTo(5);
      assertThat(policy.initialDelay()).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.backoffMultiplier()).isEqualTo(3.0);
      assertThat(policy.maxDelay()).isEqualTo(Duration.ofSeconds(30));
      assertThat(policy.useJitter()).isTrue();
      assertThat(policy.shouldRetry(new IOException())).isTrue();
      assertThat(policy.shouldRetry(new RuntimeException())).isFalse();
    }

    @Test
    @DisplayName("builder() uses defaults when not specified")
    void builderUsesDefaults() {
      RetryPolicy policy = RetryPolicy.builder().build();

      assertThat(policy.maxAttempts()).isEqualTo(3);
      assertThat(policy.initialDelay()).isEqualTo(Duration.ofMillis(100));
      assertThat(policy.backoffMultiplier()).isEqualTo(2.0);
      assertThat(policy.useJitter()).isFalse();
    }

    @Test
    @DisplayName("builder() default predicate retries all exceptions")
    void builderDefaultPredicateRetriesAll() {
      // This test covers the default predicate (_ -> true) at line 335 in Builder
      RetryPolicy policy = RetryPolicy.builder().build();

      // Default predicate should return true for any exception
      assertThat(policy.shouldRetry(new RuntimeException())).isTrue();
      assertThat(policy.shouldRetry(new IllegalArgumentException())).isTrue();
      assertThat(policy.shouldRetry(new Exception())).isTrue();
    }

    @Test
    @DisplayName("builder validates arguments")
    void builderValidatesArguments() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> RetryPolicy.builder().maxAttempts(0))
          .withMessageContaining("maxAttempts must be at least 1");

      assertThatIllegalArgumentException()
          .isThrownBy(() -> RetryPolicy.builder().backoffMultiplier(0.5))
          .withMessageContaining("backoffMultiplier must be at least 1.0");

      assertThatNullPointerException()
          .isThrownBy(() -> RetryPolicy.builder().initialDelay(null))
          .withMessageContaining("initialDelay must not be null");
    }

    @Test
    @DisplayName("builder retryIf() sets custom predicate")
    void builderRetryIfSetsCustomPredicate() {
      RetryPolicy policy =
          RetryPolicy.builder()
              .retryIf(ex -> ex.getMessage() != null && ex.getMessage().contains("transient"))
              .build();

      assertThat(policy.shouldRetry(new RuntimeException("transient error"))).isTrue();
      assertThat(policy.shouldRetry(new RuntimeException("permanent error"))).isFalse();
    }
  }

  @Nested
  @DisplayName("toString Tests")
  class ToStringTests {

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      RetryPolicy policy = RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100));

      String str = policy.toString();

      assertThat(str).contains("RetryPolicy");
      assertThat(str).contains("maxAttempts=3");
      assertThat(str).contains("backoffMultiplier=2.0");
    }
  }
}
