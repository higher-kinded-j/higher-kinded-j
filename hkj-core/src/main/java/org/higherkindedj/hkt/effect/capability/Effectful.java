// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * A capability interface representing types that support deferred side effect execution.
 *
 * <p>This capability is specific to effect types like {@code IOPath} that encapsulate side effects.
 * Types implementing this interface provide methods to execute the deferred computation.
 *
 * <h2>Operations</h2>
 *
 * <ul>
 *   <li>{@link #unsafeRun()} - Execute the effect synchronously (may throw)
 *   <li>{@link #runSafe()} - Execute the effect and wrap the result in a {@code Try}
 * </ul>
 *
 * <h2>Safety Considerations</h2>
 *
 * <p>The {@code unsafe} prefix on {@link #unsafeRun()} highlights that:
 *
 * <ul>
 *   <li>Side effects will be performed when called
 *   <li>Exceptions from the underlying computation will propagate
 *   <li>The operation may block the current thread
 * </ul>
 *
 * <p>Prefer {@link #runSafe()} when exception handling is needed, or when composing with other
 * effect types.
 *
 * @param <A> the type of the value produced by the effect
 */
public sealed interface Effectful<A> extends Chainable<A> permits IOPath, VTaskPath {

  /**
   * Executes the deferred effect synchronously and returns the result.
   *
   * <p><b>Warning:</b> This method is "unsafe" because:
   *
   * <ul>
   *   <li>It will perform side effects (I/O, mutations, etc.)
   *   <li>It may throw exceptions from the underlying computation
   *   <li>It may block the current thread for long-running operations
   * </ul>
   *
   * <p>This should typically be called at the "edge" of your program, after building up a pure
   * description of the computation.
   *
   * <p>Example:
   *
   * <pre>{@code
   * String content = Path.io(() -> Files.readString(path))
   *     .map(String::trim)
   *     .unsafeRun();
   * }</pre>
   *
   * @return the result of executing the effect
   * @throws RuntimeException if the underlying computation throws
   */
  A unsafeRun();

  /**
   * Executes the deferred effect and wraps the result in a {@code Try}.
   *
   * <p>This is the safe alternative to {@link #unsafeRun()}. Any exceptions thrown during execution
   * are captured in a {@code Try.Failure} rather than propagating.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try<String> result = Path.io(() -> Files.readString(path))
   *     .runSafe();
   *
   * result.fold(
   *     content -> process(content),
   *     error -> log.error("Failed to read file", error)
   * );
   * }</pre>
   *
   * @return a {@code Try} containing either the result or the exception
   */
  default Try<A> runSafe() {
    return Try.of(this::unsafeRun);
  }
}
