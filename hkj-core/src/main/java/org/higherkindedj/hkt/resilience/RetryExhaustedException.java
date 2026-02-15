// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

/**
 * Exception thrown when all retry attempts have been exhausted.
 *
 * <p>This exception wraps the last exception encountered during retry attempts and includes
 * information about the number of attempts made.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * try {
 *     Retry.execute(policy, () -> unreliableOperation());
 * } catch (RetryExhaustedException e) {
 *     log.error("Failed after {} attempts: {}", e.getAttempts(), e.getMessage());
 *     throw e.getCause();  // Re-throw the underlying exception
 * }
 * }</pre>
 *
 * @see Retry
 * @see RetryPolicy
 */
public final class RetryExhaustedException extends RuntimeException {

  private final int attempts;

  /**
   * Creates a new RetryExhaustedException.
   *
   * @param message a descriptive message
   * @param cause the last exception encountered
   * @param attempts the number of attempts made
   */
  public RetryExhaustedException(String message, Throwable cause, int attempts) {
    super(message, cause);
    this.attempts = attempts;
  }

  /**
   * Creates a RetryExhaustedException with a default message.
   *
   * @param cause the last exception encountered
   * @param attempts the number of attempts made
   * @return a new RetryExhaustedException
   */
  public static RetryExhaustedException of(Throwable cause, int attempts) {
    return new RetryExhaustedException(
        "Retry exhausted after " + attempts + " attempts", cause, attempts);
  }

  /**
   * Returns the number of attempts made before giving up.
   *
   * @return the number of attempts
   */
  public int getAttempts() {
    return attempts;
  }
}
