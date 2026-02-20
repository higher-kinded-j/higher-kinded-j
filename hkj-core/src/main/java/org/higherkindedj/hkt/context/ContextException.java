// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

/**
 * A {@link RuntimeException} subclass that wraps checked exceptions thrown within {@link Context}
 * computations.
 *
 * <p>When a {@link Context} is created via {@link Context#fail(Throwable)} with a checked
 * exception, the exception is wrapped in a {@code ContextException} so that {@link Context#run()}
 * can remain unchecked. This follows the same pattern used by {@link
 * java.util.concurrent.CompletionException} and {@link java.io.UncheckedIOException} in the
 * standard library.
 *
 * <p><b>Automatic unwrapping:</b> Recovery methods ({@link
 * Context#recover(java.util.function.Function)}, {@link
 * Context#recoverWith(java.util.function.Function)}) automatically unwrap {@code ContextException}
 * so that recovery functions receive the original cause, not this wrapper.
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * // Checked exception is automatically wrapped
 * Context<Env, String> ctx = Context.fail(new IOException("disk full"));
 *
 * // Recovery sees the original IOException, not ContextException
 * Context<Env, String> recovered = ctx.recover(e -> {
 *     assert e instanceof IOException; // true — auto-unwrapped
 *     return "fallback";
 * });
 *
 * // At the run() call site, ContextException is thrown if unrecovered
 * try {
 *     ctx.run();
 * } catch (ContextException e) {
 *     IOException cause = (IOException) e.getCause(); // original exception
 * }
 * }</pre>
 *
 * @see Context#fail(Throwable)
 * @see Context#recover(java.util.function.Function)
 * @see Context#recoverWith(java.util.function.Function)
 */
public class ContextException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new {@code ContextException} with the specified message.
   *
   * @param message the detail message.
   */
  public ContextException(String message) {
    super(message);
  }

  /**
   * Creates a new {@code ContextException} wrapping the specified cause.
   *
   * @param cause the checked exception to wrap. Must not be null.
   */
  public ContextException(Throwable cause) {
    super(cause);
  }

  /**
   * Creates a new {@code ContextException} with the specified message and cause.
   *
   * @param message the detail message.
   * @param cause the checked exception to wrap.
   */
  public ContextException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Unwraps a throwable, returning the cause if it is a {@code ContextException}, otherwise
   * returning the throwable itself.
   *
   * <p>This is used internally by recovery methods to present the original exception to recovery
   * functions.
   *
   * @param t the throwable to unwrap.
   * @return the cause if {@code t} is a {@code ContextException} with a non-null cause, otherwise
   *     {@code t} itself.
   */
  public static Throwable unwrap(Throwable t) {
    if (t instanceof ContextException ce && ce.getCause() != null) {
      return ce.getCause();
    }
    return t;
  }

  /**
   * Wraps a throwable in a {@code RuntimeException} suitable for rethrowing.
   *
   * <p>If the throwable is already a {@code RuntimeException}, it is returned as-is. If it is an
   * {@code Error}, it is rethrown directly. Otherwise, it is wrapped in a {@code ContextException}.
   *
   * @param t the throwable to wrap.
   * @return a {@code RuntimeException} that can be thrown without a checked exception declaration.
   * @throws Error if {@code t} is an {@code Error}.
   */
  public static RuntimeException wrap(Throwable t) {
    if (t instanceof RuntimeException re) return re;
    if (t instanceof Error e) throw e;
    return new ContextException(t);
  }
}
