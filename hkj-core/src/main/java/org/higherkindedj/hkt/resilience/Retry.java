// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

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
 * <p>The {@code VTask} methods see a failed attempt's exception as thrown, so a policy that retries
 * on {@code IOException} retries a task that throws one, and {@link RetryExhaustedException} holds
 * it as its cause.
 *
 * <p>An interrupt stops the retry, whatever the policy's predicate. An attempt that throws {@code
 * InterruptedException} is not run again. The {@code VTask} methods throw it as is, as they would
 * with no retry; {@link #execute(RetryPolicy, Supplier)}, whose {@code Supplier} declares no
 * checked exception, wraps it in a {@code RuntimeException} and sets the interrupt status. A thread
 * interrupted before the next attempt starts stops the retry too: the interrupt status is set, and
 * {@link RetryExhaustedException} is thrown with the last attempt's exception as its cause. {@link
 * #retryTaskWithFallback} still falls back from that, with the status set, and {@link
 * #retryTaskWithRecovery} throws it rather than start its recovery task: a cancelled retry starts
 * no new work.
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
   * @throws RetryExhaustedException if all attempts fail, or an interrupt stops the retry
   * @throws NullPointerException if policy or supplier is null
   */
  public static <A> A execute(RetryPolicy policy, Supplier<A> supplier) {
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(supplier, "supplier must not be null");
    try {
      return retrying(policy, supplier::get);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      // Only a sneaky throw gets here, since a Supplier declares no checked exception
      if (t instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new RuntimeException(t);
    }
  }

  /**
   * Executes a void operation with retry according to the given policy.
   *
   * @param policy the retry policy; must not be null
   * @param runnable the operation to execute; must not be null
   * @throws RetryExhaustedException if all attempts fail, or an interrupt stops the retry
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
   * @throws RetryExhaustedException if all attempts fail, or an interrupt stops the retry
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
   * @throws RetryExhaustedException if all attempts fail, or an interrupt stops the retry
   * @throws NullPointerException if delay or supplier is null
   * @throws IllegalArgumentException if maxAttempts is less than 1
   */
  public static <A> A withFixedDelay(int maxAttempts, Duration delay, Supplier<A> supplier) {
    return execute(RetryPolicy.fixed(maxAttempts, delay), supplier);
  }

  // ===== VTask-Native Retry =====

  /**
   * Returns a lazy {@link VTask} that retries the given task according to the policy.
   *
   * <p>The returned task is lazy: nothing executes until {@code run()}, {@code runSafe()}, or
   * {@code runAsync()} is called. Each execution attempt runs the original task; on failure, the
   * policy determines whether to retry.
   *
   * @param task the task to retry; must not be null
   * @param policy the retry policy; must not be null
   * @param <A> the result type
   * @return a new VTask that retries according to the policy
   * @throws NullPointerException if task or policy is null
   */
  public static <A> VTask<A> retryTask(VTask<A> task, RetryPolicy policy) {
    Objects.requireNonNull(task, "task must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    return () -> retrying(policy, task);
  }

  /**
   * Returns a lazy {@link VTask} that retries the given task with default exponential backoff and
   * jitter.
   *
   * @param task the task to retry; must not be null
   * @param maxAttempts the maximum number of attempts
   * @param <A> the result type
   * @return a new VTask that retries with exponential backoff
   * @throws NullPointerException if task is null
   * @throws IllegalArgumentException if maxAttempts is less than 1
   */
  public static <A> VTask<A> retryTask(VTask<A> task, int maxAttempts) {
    return retryTask(
        task, RetryPolicy.exponentialBackoffWithJitter(maxAttempts, Duration.ofMillis(100)));
  }

  /**
   * Returns a lazy {@link VTask} that retries the given task and falls back to a value on
   * exhaustion.
   *
   * @param task the task to retry; must not be null
   * @param policy the retry policy; must not be null
   * @param fallback a function that produces a fallback value from the last exception; must not be
   *     null, and must not return null: a null result fails the task with a {@link
   *     NullPointerException}
   * @param <A> the result type
   * @return a new VTask that retries, then falls back on exhaustion
   * @throws NullPointerException if any argument is null
   */
  public static <A> VTask<A> retryTaskWithFallback(
      VTask<A> task, RetryPolicy policy, Function<Throwable, A> fallback) {
    Objects.requireNonNull(task, "task must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(fallback, "fallback must not be null");
    return () -> {
      try {
        return retrying(policy, task);
      } catch (RetryExhaustedException e) {
        return Objects.requireNonNull(
            fallback.apply(e.getCause()), "fallback must not return null");
      }
    };
  }

  /**
   * Returns a lazy {@link VTask} that retries the given task and runs a recovery task on
   * exhaustion.
   *
   * @param task the task to retry; must not be null
   * @param policy the retry policy; must not be null
   * @param recovery a function that produces a recovery task from the last exception; must not be
   *     null
   * @param <A> the result type
   * @return a new VTask that retries, then runs the recovery task on exhaustion
   * @throws NullPointerException if any argument is null
   */
  public static <A> VTask<A> retryTaskWithRecovery(
      VTask<A> task, RetryPolicy policy, Function<Throwable, VTask<A>> recovery) {
    Objects.requireNonNull(task, "task must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(recovery, "recovery must not be null");
    return () -> {
      try {
        return retrying(policy, task);
      } catch (RetryExhaustedException e) {
        // A cancelled retry starts no new work
        if (Thread.currentThread().isInterrupted()) {
          throw e;
        }
        VTask<A> recoveryTask = recovery.apply(e.getCause());
        Objects.requireNonNull(recoveryTask, "recovery function returned null");
        return recoveryTask.execute();
      }
    };
  }

  // The one retry loop. Each attempt runs with execute(), so its exception reaches the policy's
  // predicate as thrown: a VTask's checked exception arrives unwrapped
  private static <A> A retrying(RetryPolicy policy, VTask<A> attempt) throws Throwable {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        return attempt.execute();
      } catch (Throwable t) {
        // A cancelled attempt is not run again, and its InterruptedException is thrown as is
        if (t instanceof InterruptedException || !policy.shouldRetry(t)) {
          throw t;
        }
        if (attempts >= policy.maxAttempts()) {
          throw RetryExhaustedException.of(t, policy.maxAttempts());
        }
        // An interrupted thread stops here, before a retry is announced or waited for
        if (Thread.currentThread().isInterrupted()) {
          throw retryInterrupted(t, attempts);
        }

        Duration delay = policy.delayForAttempt(attempts);
        @Nullable Consumer<RetryEvent> listener = policy.retryListener();
        if (listener != null) {
          listener.accept(RetryEvent.of(attempts, t, delay));
        }

        // Wait before the next attempt; an interrupt while waiting stops the retry, and so does
        // one that arrived while the listener ran when there is no wait
        if (!delay.isZero() && !delay.isNegative()) {
          try {
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            throw retryInterrupted(t, attempts);
          }
        } else if (Thread.currentThread().isInterrupted()) {
          throw retryInterrupted(t, attempts);
        }
      }
    }
  }

  // Sets the interrupt status again, and reports the retry as stopped by it
  private static RetryExhaustedException retryInterrupted(Throwable last, int attempts) {
    Thread.currentThread().interrupt();
    return new RetryExhaustedException(
        "Retry interrupted after " + attempts + " attempts", last, attempts);
  }
}
