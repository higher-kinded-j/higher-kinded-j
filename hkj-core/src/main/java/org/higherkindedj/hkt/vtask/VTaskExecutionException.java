// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

/**
 * Unchecked exception that wraps checked exceptions thrown during {@link VTask} execution.
 *
 * <p>When a {@code VTask} computation throws a checked exception and is executed via {@link
 * VTask#run()}, the checked exception is wrapped in this {@code RuntimeException}. This allows
 * {@code run()} to have a clean signature without {@code throws Throwable}.
 *
 * <p>The original checked exception is always available via {@link #getCause()}.
 *
 * <p><b>Design Rationale:</b> {@code VTask} is a lazy effect type. The primary APIs for error
 * handling are functional: {@link VTask#runSafe()} returns a {@code Try<A>} with the original
 * exception preserved, and {@link Scope#joinSafe()}, {@link Scope#joinEither()}, and {@link
 * Scope#joinMaybe()} provide similar functional alternatives. The throwing {@code run()} method is
 * a convenience escape hatch where checked exception wrapping is acceptable.
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * VTask<String> task = VTask.of(() -> {
 *     throw new IOException("file not found");
 * });
 *
 * // Functional approach — preserves original exception type:
 * Try<String> result = task.runSafe();
 * // result is Try.Failure(IOException("file not found"))
 *
 * // Direct approach — checked exception is wrapped:
 * try {
 *     task.run();
 * } catch (VTaskExecutionException e) {
 *     Throwable cause = e.getCause(); // IOException
 * }
 * }</pre>
 *
 * @see VTask#run()
 * @see VTask#runSafe()
 */
public class VTaskExecutionException extends RuntimeException {

  /**
   * Creates a new {@code VTaskExecutionException} wrapping the given cause.
   *
   * @param cause the checked exception that was thrown during VTask execution; must not be null
   */
  public VTaskExecutionException(Throwable cause) {
    super(cause);
  }

  /**
   * Creates a new {@code VTaskExecutionException} with a message and cause.
   *
   * @param message a description of the context in which the exception occurred
   * @param cause the checked exception that was thrown during VTask execution; must not be null
   */
  public VTaskExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
