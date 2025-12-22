// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.resilience;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;

/**
 * Resilience utilities for fault-tolerant workflow execution.
 *
 * <p>Provides retry with exponential backoff, timeout handling, and composition of resilience
 * patterns with the Effect API.
 */
public final class Resilience {

  private Resilience() {}

  /**
   * Wraps an IO operation with retry logic using exponential backoff.
   *
   * @param operation the operation to retry
   * @param policy the retry policy
   * @param <A> the result type
   * @return an IOPath that will retry on failure
   */
  public static <A> IOPath<A> withRetry(IOPath<A> operation, RetryPolicy policy) {
    return Path.io(() -> executeWithRetry(operation, policy, 1));
  }

  /**
   * Wraps a supplier with retry logic.
   *
   * @param supplier the supplier to retry
   * @param policy the retry policy
   * @param <A> the result type
   * @return an IOPath that will retry on failure
   */
  public static <A> IOPath<A> retrying(Supplier<A> supplier, RetryPolicy policy) {
    return withRetry(Path.io(supplier), policy);
  }

  private static <A> A executeWithRetry(IOPath<A> operation, RetryPolicy policy, int attempt) {
    try {
      return operation.unsafeRun();
    } catch (Throwable t) {
      if (attempt >= policy.maxAttempts() || !policy.retryOn().test(t)) {
        throw t instanceof RuntimeException rt ? rt : new RuntimeException(t);
      }

      var delay = policy.delayForAttempt(attempt + 1);
      sleep(delay);

      return executeWithRetry(operation, policy, attempt + 1);
    }
  }

  /**
   * Wraps an IO operation with a timeout.
   *
   * @param operation the operation to timeout
   * @param timeout the timeout duration
   * @param operationName name for error reporting
   * @param <A> the result type
   * @return an EitherPath with timeout error handling
   */
  public static <A> EitherPath<OrderError, A> withTimeout(
      IOPath<A> operation, Duration timeout, String operationName) {
    return Path.either(executeWithTimeout(operation, timeout, operationName));
  }

  /**
   * Wraps a supplier with a timeout.
   *
   * @param supplier the supplier to timeout
   * @param timeout the timeout duration
   * @param operationName name for error reporting
   * @param <A> the result type
   * @return an EitherPath with timeout error handling
   */
  public static <A> EitherPath<OrderError, A> withTimeout(
      Supplier<A> supplier, Duration timeout, String operationName) {
    return withTimeout(Path.io(supplier), timeout, operationName);
  }

  private static <A> Either<OrderError, A> executeWithTimeout(
      IOPath<A> operation, Duration timeout, String operationName) {
    var executor = Executors.newSingleThreadExecutor();
    var future = executor.submit(operation::unsafeRun);

    try {
      var result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      return Either.right(result);
    } catch (TimeoutException e) {
      future.cancel(true);
      return Either.left(OrderError.SystemError.timeout(operationName, timeout));
    } catch (ExecutionException e) {
      var cause = e.getCause();
      return Either.left(
          OrderError.SystemError.unexpected(
              "Operation failed: " + operationName, cause != null ? cause : e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Either.left(
          OrderError.SystemError.unexpected("Operation interrupted: " + operationName, e));
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Combines retry and timeout into a single resilient operation.
   *
   * @param operation the operation to make resilient
   * @param retryPolicy the retry policy
   * @param timeout the timeout per attempt
   * @param operationName name for error reporting
   * @param <A> the result type
   * @return an EitherPath with both retry and timeout handling
   */
  public static <A> EitherPath<OrderError, A> resilient(
      IOPath<A> operation, RetryPolicy retryPolicy, Duration timeout, String operationName) {
    var retriedOperation = withRetry(operation, retryPolicy);
    return withTimeout(retriedOperation, timeout, operationName);
  }

  /**
   * Combines retry and timeout using workflow configuration.
   *
   * @param operation the operation to make resilient
   * @param maxRetries maximum retries
   * @param retryDelay initial retry delay
   * @param timeout timeout per attempt
   * @param operationName name for error reporting
   * @param <A> the result type
   * @return an EitherPath with resilience handling
   */
  public static <A> EitherPath<OrderError, A> resilient(
      Supplier<A> operation,
      int maxRetries,
      Duration retryDelay,
      Duration timeout,
      String operationName) {
    var policy = RetryPolicy.of(maxRetries, retryDelay, retryDelay.multipliedBy(10), 2.0);
    return resilient(Path.io(operation), policy, timeout, operationName);
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Retry sleep interrupted", e);
    }
  }
}
