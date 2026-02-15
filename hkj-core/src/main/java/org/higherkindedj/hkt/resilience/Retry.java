// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Utility class for executing operations with retry logic.
 *
 * <p>{@code Retry} provides static methods to execute operations with configurable retry policies.
 * It handles delays between attempts, respects the retry predicate, and throws {@link
 * RetryExhaustedException} when all attempts are exhausted.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100))
 *     .retryOn(IOException.class);
 *
 * // Execute with retry
 * String response = Retry.execute(policy, () -> httpClient.get(url));
 *
 * // Or for void operations
 * Retry.execute(policy, () -> httpClient.post(url, body));
 * }</pre>
 *
 * @see RetryPolicy
 * @see RetryExhaustedException
 */
public final class Retry {

  private Retry() {
    // Utility class - no instantiation
  }

  /**
   * Executes an operation with retry according to the given policy.
   *
   * <p>The operation is attempted up to {@code policy.maxAttempts()} times. If an attempt fails
   * with an exception that matches the retry predicate, the next attempt is made after the
   * appropriate delay.
   *
   * @param policy the retry policy; must not be null
   * @param supplier the operation to execute; must not be null
   * @param <A> the result type
   * @return the result of the operation
   * @throws RetryExhaustedException if all attempts fail
   * @throws NullPointerException if policy or supplier is null
   */
  public static <A> A execute(RetryPolicy policy, Supplier<A> supplier) {
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(supplier, "supplier must not be null");

    Throwable lastException = null;

    for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
      try {
        return supplier.get();
      } catch (Throwable t) {
        lastException = t;

        // Check if we should retry this exception type
        if (!policy.shouldRetry(t)) {
          // Exception doesn't match retry predicate - re-throw immediately
          if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
          } else if (t instanceof Error) {
            throw (Error) t;
          } else {
            throw new RuntimeException(t);
          }
        }

        // Check if we've exhausted attempts
        if (attempt >= policy.maxAttempts()) {
          break;
        }

        // Wait before next attempt
        Duration delay = policy.delayForAttempt(attempt);
        if (!delay.isZero() && !delay.isNegative()) {
          try {
            Thread.sleep(delay.toMillis());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryExhaustedException(
                "Retry interrupted after " + attempt + " attempts", lastException, attempt);
          }
        }
      }
    }

    throw RetryExhaustedException.of(lastException, policy.maxAttempts());
  }

  /**
   * Executes a void operation with retry according to the given policy.
   *
   * @param policy the retry policy; must not be null
   * @param runnable the operation to execute; must not be null
   * @throws RetryExhaustedException if all attempts fail
   * @throws NullPointerException if policy or runnable is null
   */
  public static void execute(RetryPolicy policy, Runnable runnable) {
    Objects.requireNonNull(runnable, "runnable must not be null");
    execute(
        policy,
        () -> {
          runnable.run();
          return null;
        });
  }

  /**
   * Executes an operation with simple exponential backoff retry.
   *
   * <p>This is a convenience method that uses exponential backoff with jitter.
   *
   * @param maxAttempts maximum number of attempts
   * @param initialDelay initial delay between attempts
   * @param supplier the operation to execute; must not be null
   * @param <A> the result type
   * @return the result of the operation
   * @throws RetryExhaustedException if all attempts fail
   * @throws NullPointerException if initialDelay or supplier is null
   * @throws IllegalArgumentException if maxAttempts is less than 1
   */
  public static <A> A withExponentialBackoff(
      int maxAttempts, Duration initialDelay, Supplier<A> supplier) {
    return execute(RetryPolicy.exponentialBackoffWithJitter(maxAttempts, initialDelay), supplier);
  }

  /**
   * Executes an operation with fixed delay retry.
   *
   * <p>This is a convenience method that uses fixed delay between attempts.
   *
   * @param maxAttempts maximum number of attempts
   * @param delay delay between attempts
   * @param supplier the operation to execute; must not be null
   * @param <A> the result type
   * @return the result of the operation
   * @throws RetryExhaustedException if all attempts fail
   * @throws NullPointerException if delay or supplier is null
   * @throws IllegalArgumentException if maxAttempts is less than 1
   */
  public static <A> A withFixedDelay(int maxAttempts, Duration delay, Supplier<A> supplier) {
    return execute(RetryPolicy.fixed(maxAttempts, delay), supplier);
  }
}
