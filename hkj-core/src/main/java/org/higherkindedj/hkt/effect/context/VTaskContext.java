// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Effect context for computations that execute on virtual threads.
 *
 * <p>VTaskContext provides a user-friendly Layer 2 API for working with {@link VTaskPath}. It wraps
 * VTask computations with convenient factory methods and chainable operations, hiding the
 * complexity of the underlying HKT machinery.
 *
 * <h2>Key Features</h2>
 *
 * <ul>
 *   <li><b>Virtual Thread Execution:</b> Computations run on lightweight virtual threads
 *   <li><b>Lazy Evaluation:</b> Computations are deferred until explicitly executed
 *   <li><b>Error Recovery:</b> Built-in error handling with {@link #recover} and {@link
 *       #recoverWith}
 *   <li><b>Timeout Support:</b> Set time limits on computations with {@link #timeout}
 * </ul>
 *
 * <h2>Factory Methods</h2>
 *
 * <ul>
 *   <li>{@link #of(Callable)} - Create from a callable computation
 *   <li>{@link #exec(Runnable)} - Create from a side-effecting runnable
 *   <li>{@link #pure(Object)} - Create a context with an immediate value
 *   <li>{@link #fail(Throwable)} - Create a failed context
 * </ul>
 *
 * <h2>Execution Methods</h2>
 *
 * <ul>
 *   <li>{@link #run()} - Execute eagerly and return {@code Try<A>}
 *   <li>{@link #runAsync()} - Execute asynchronously, returning a CompletableFuture
 *   <li>{@link #runOrThrow()} - Execute and throw on failure
 *   <li>{@link #runOrElse(Object)} - Execute with a default value on failure
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Try<Profile> result = VTaskContext
 *     .<User>of(() -> userService.fetch(userId))
 *     .via(user -> VTaskContext.of(() -> profileService.fetch(user.profileId())))
 *     .timeout(Duration.ofSeconds(5))
 *     .recover(err -> Profile.defaultProfile())
 *     .run();
 * }</pre>
 *
 * @param <A> the value type
 * @see VTaskPath
 * @see VTask
 */
public final class VTaskContext<A> {

  private final VTaskPath<A> path;

  private VTaskContext(VTaskPath<A> path) {
    this.path = Objects.requireNonNull(path, "path must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a VTaskContext from a callable computation.
   *
   * <p>The computation is not executed until {@link #run()} or similar methods are called.
   *
   * @param callable the computation to execute; must not be null
   * @param <A> the result type
   * @return a new VTaskContext wrapping the computation
   * @throws NullPointerException if callable is null
   */
  public static <A> VTaskContext<A> of(Callable<A> callable) {
    Objects.requireNonNull(callable, "callable must not be null");
    return new VTaskContext<>(Path.vtask(callable));
  }

  /**
   * Creates a VTaskContext from a side-effecting runnable.
   *
   * <p>The runnable is not executed until {@link #run()} or similar methods are called.
   *
   * @param runnable the side effect to execute; must not be null
   * @return a new VTaskContext that produces Unit when run
   * @throws NullPointerException if runnable is null
   */
  public static VTaskContext<Unit> exec(Runnable runnable) {
    Objects.requireNonNull(runnable, "runnable must not be null");
    return new VTaskContext<>(Path.vtaskExec(runnable));
  }

  /**
   * Creates a VTaskContext containing a pure value.
   *
   * <p>The value is already computed; no side effects occur when this context is run.
   *
   * @param value the value to wrap
   * @param <A> the value type
   * @return a new VTaskContext that immediately produces the value when run
   */
  public static <A> VTaskContext<A> pure(A value) {
    return new VTaskContext<>(Path.vtaskPure(value));
  }

  /**
   * Creates a failed VTaskContext containing the given exception.
   *
   * @param error the exception; must not be null
   * @param <A> the phantom type of the success value
   * @return a failed VTaskContext
   * @throws NullPointerException if error is null
   */
  public static <A> VTaskContext<A> fail(Throwable error) {
    Objects.requireNonNull(error, "error must not be null");
    return new VTaskContext<>(Path.vtaskFail(error));
  }

  /**
   * Creates a VTaskContext from an existing VTaskPath.
   *
   * @param path the VTaskPath to wrap; must not be null
   * @param <A> the value type
   * @return a new VTaskContext wrapping the path
   * @throws NullPointerException if path is null
   */
  public static <A> VTaskContext<A> fromPath(VTaskPath<A> path) {
    Objects.requireNonNull(path, "path must not be null");
    return new VTaskContext<>(path);
  }

  /**
   * Creates a VTaskContext from an existing VTask.
   *
   * @param vtask the VTask to wrap; must not be null
   * @param <A> the value type
   * @return a new VTaskContext wrapping the VTask
   * @throws NullPointerException if vtask is null
   */
  public static <A> VTaskContext<A> fromVTask(VTask<A> vtask) {
    Objects.requireNonNull(vtask, "vtask must not be null");
    return new VTaskContext<>(Path.vtaskPath(vtask));
  }

  // ===== Transformation Operations =====

  /**
   * Transforms the contained value using the provided function.
   *
   * <p>If this context fails, the function is not applied and the failure is preserved.
   *
   * @param mapper the function to apply to the value; must not be null
   * @param <B> the type of the transformed value
   * @return a new context with the transformed value
   * @throws NullPointerException if mapper is null
   */
  public <B> VTaskContext<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new VTaskContext<>(path.map(mapper));
  }

  /**
   * Chains a dependent computation that returns a VTaskContext.
   *
   * <p>This is the monadic bind operation, named {@code via} to match the Effect Path API
   * vocabulary. The function is applied to the contained value, and the resulting context becomes
   * the new context.
   *
   * <p>If this context fails, the function is not applied and the failure is propagated.
   *
   * @param fn the function to apply, returning a new context; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context returned by the function, or a failed context
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> VTaskContext<B> via(Function<? super A, ? extends VTaskContext<B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");
    return new VTaskContext<>(
        path.via(
            a -> {
              VTaskContext<B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              return next.path;
            }));
  }

  /**
   * Chains a dependent computation using flatMap.
   *
   * <p>This is an alias for {@link #via(Function)} that matches traditional monad terminology.
   *
   * @param fn the function to apply, returning a new context; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context returned by the function, or a failed context
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> VTaskContext<B> flatMap(Function<? super A, ? extends VTaskContext<B>> fn) {
    return via(fn);
  }

  /**
   * Sequences an independent computation, discarding this context's value.
   *
   * <p>This is useful for sequencing effects where only the final result matters.
   *
   * @param supplier provides the next context; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context from the supplier
   * @throws NullPointerException if supplier is null or returns null
   */
  public <B> VTaskContext<B> then(Supplier<? extends VTaskContext<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(ignored -> supplier.get());
  }

  // ===== Error Recovery Operations =====

  /**
   * Recovers from an error by providing a fallback value.
   *
   * <p>If this context fails, the recovery function is applied to produce a success value. If this
   * context succeeds, it is returned unchanged.
   *
   * @param recovery the function to apply to the error to produce a value; must not be null
   * @return a context containing either the original value or the recovered value
   * @throws NullPointerException if recovery is null
   */
  public VTaskContext<A> recover(Function<? super Throwable, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new VTaskContext<>(path.handleError(recovery));
  }

  /**
   * Recovers from an error by providing a fallback VTaskContext.
   *
   * <p>If this context fails, the recovery function is applied to produce an alternative context.
   * If this context succeeds, it is returned unchanged.
   *
   * @param recovery the function to apply to the error to produce a fallback context; must not be
   *     null
   * @return either this context (if successful) or the fallback context
   * @throws NullPointerException if recovery is null or returns null
   */
  public VTaskContext<A> recoverWith(
      Function<? super Throwable, ? extends VTaskContext<A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new VTaskContext<>(
        path.handleErrorWith(
            error -> {
              VTaskContext<A> next = recovery.apply(error);
              Objects.requireNonNull(next, "recovery must not return null");
              return next.path;
            }));
  }

  /**
   * Provides an alternative context if this one fails.
   *
   * <p>This is a convenience method that ignores the specific error and provides a fallback.
   *
   * @param alternative provides the fallback context; must not be null
   * @return either this context (if successful) or the alternative
   * @throws NullPointerException if alternative is null or returns null
   */
  public VTaskContext<A> orElse(Supplier<? extends VTaskContext<A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    return recoverWith(ignored -> alternative.get());
  }

  // ===== Timeout =====

  /**
   * Creates a new VTaskContext that fails if this computation does not complete within the
   * specified duration.
   *
   * @param duration the maximum time to wait; must not be null
   * @return a VTaskContext with timeout behaviour
   * @throws NullPointerException if duration is null
   */
  public VTaskContext<A> timeout(Duration duration) {
    Objects.requireNonNull(duration, "duration must not be null");
    return new VTaskContext<>(path.timeout(duration));
  }

  // ===== Execution Methods =====

  /**
   * Executes the computation eagerly and returns the result wrapped in a Try.
   *
   * <p>This is the primary execution method. It runs the VTask immediately and captures the result
   * or any exception in a Try.
   *
   * @return a Try containing the result or exception
   */
  public Try<A> run() {
    return path.runSafe();
  }

  /**
   * Executes the computation asynchronously on a virtual thread.
   *
   * <p>The computation starts immediately on a virtual thread. The returned future can be used to
   * wait for the result or combine with other asynchronous operations.
   *
   * @return a CompletableFuture that will complete with the result
   */
  public CompletableFuture<A> runAsync() {
    return path.runAsync();
  }

  /**
   * Executes the computation and returns the result, throwing on failure.
   *
   * <p>If the computation fails, the exception is wrapped in a RuntimeException and thrown.
   *
   * @return the success value
   * @throws RuntimeException if the computation fails
   */
  public A runOrThrow() {
    return path.unsafeRun();
  }

  /**
   * Executes the computation and returns the result, or a default on failure.
   *
   * @param defaultValue the value to return if the computation fails
   * @return the success value or the default
   */
  public A runOrElse(A defaultValue) {
    return run().orElse(defaultValue);
  }

  /**
   * Executes the computation and returns the result, or applies a handler on failure.
   *
   * @param errorHandler the function to apply to the error to produce a value; must not be null
   * @return the success value or the result of the error handler
   * @throws NullPointerException if errorHandler is null
   */
  public A runOrElseGet(Function<? super Throwable, ? extends A> errorHandler) {
    Objects.requireNonNull(errorHandler, "errorHandler must not be null");
    return run().fold(a -> a, errorHandler);
  }

  // ===== Access to Underlying Path =====

  /**
   * Returns the underlying VTaskPath.
   *
   * <p>This is an escape hatch to Layer 1 for users who need full control over the VTaskPath
   * operations.
   *
   * @return the underlying VTaskPath
   */
  public VTaskPath<A> toPath() {
    return path;
  }

  /**
   * Returns the underlying VTask.
   *
   * <p>This is an escape hatch for users who need direct access to the VTask.
   *
   * @return the underlying VTask
   */
  public VTask<A> toVTask() {
    return path.run();
  }

  @Override
  public String toString() {
    return "VTaskContext(<deferred>)";
  }
}
