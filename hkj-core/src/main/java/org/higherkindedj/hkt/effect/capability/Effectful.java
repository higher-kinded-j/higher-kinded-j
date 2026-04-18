// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import java.util.function.Function;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * A capability interface representing types that support deferred side effect execution.
 *
 * <p>This capability is specific to effect types like {@code IOPath} and {@code VTaskPath} that
 * encapsulate side effects. Types implementing this interface provide methods to execute the
 * deferred computation, to recover from exceptions, and to run a finalizer regardless of outcome.
 *
 * <h2>Operations</h2>
 *
 * <ul>
 *   <li>{@link #unsafeRun()} - Execute the effect synchronously (may throw)
 *   <li>{@link #runSafe()} - Execute the effect and wrap the result in a {@code Try}
 *   <li>{@link #handleError(Function)} - Recover from an exception with a pure value
 *   <li>{@link #handleErrorWith(Function)} - Recover from an exception with an alternative effect
 *   <li>{@link #guarantee(Runnable)} - Run a finalizer whether the effect succeeds or fails
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

  /**
   * Recovers from any exception thrown while running this effect by supplying a pure fallback
   * value. The returned effect has the same concrete type as this one.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Effectful<String> greeting = Path.io(() -> riskyGreet())
   *     .handleError(t -> "hello, stranger");
   * }</pre>
   *
   * @param recovery function applied to any thrown {@link Throwable} to produce a fallback value;
   *     must not be {@code null}
   * @return an effect that yields either the original result or the recovery value
   * @throws NullPointerException if {@code recovery} is {@code null}
   */
  Effectful<A> handleError(Function<? super Throwable, ? extends A> recovery);

  /**
   * Recovers from any exception thrown while running this effect by substituting another effect.
   * Unlike {@link #handleError(Function)}, the recovery function returns an {@code Effectful<A>},
   * allowing the fallback to itself perform side effects.
   *
   * <p>The parameter is widened to {@code Function<? super Throwable, ? extends Effectful<A>>} so
   * the recovery can return any concrete effect type (an {@link IOPath} recovering into a {@link
   * VTaskPath} or vice versa). The returned effect has the same concrete type as this one.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Effectful<Config> config = Path.io(() -> loadFromFile())
   *     .handleErrorWith(t -> Path.io(() -> loadFromEnv()));
   * }</pre>
   *
   * @param recovery function applied to any thrown {@link Throwable} to produce a fallback effect;
   *     must not be {@code null} and must not return {@code null}
   * @return an effect that yields either the original result or the recovery effect's result
   * @throws NullPointerException if {@code recovery} is {@code null}
   */
  Effectful<A> handleErrorWith(Function<? super Throwable, ? extends Effectful<A>> recovery);

  /**
   * Runs a finalizer after this effect completes, regardless of whether it succeeded or threw. The
   * finalizer runs even if the effect throws, but any exception thrown by the finalizer itself will
   * mask (or follow, depending on implementation) the original failure. The returned effect has the
   * same concrete type as this one.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Effectful<Data> withCleanup = Path.io(() -> process(resource))
   *     .guarantee(() -> resource.close());
   * }</pre>
   *
   * @param finalizer action to run on completion; must not be {@code null}
   * @return an effect that runs the finalizer after this effect
   * @throws NullPointerException if {@code finalizer} is {@code null}
   */
  Effectful<A> guarantee(Runnable finalizer);
}
