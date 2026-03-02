// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Effectful;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * A fluent path wrapper for {@link VTask} values.
 *
 * <p>{@code VTaskPath} provides a chainable API for composing deferred computations that execute on
 * Java virtual threads. It implements {@link Effectful} to provide methods for executing the
 * deferred computation.
 *
 * <h2>Virtual Threads</h2>
 *
 * <p>VTaskPath leverages Java's virtual threads for lightweight concurrency. Virtual threads are
 * managed by the JVM and can scale to millions of concurrent tasks with minimal memory overhead.
 * This makes VTaskPath ideal for I/O-bound workloads.
 *
 * <h2>Creating VTaskPath instances</h2>
 *
 * <p>Use the {@link Path} factory class to create instances:
 *
 * <pre>{@code
 * VTaskPath<String> path = Path.vtask(() -> Files.readString(file));
 * VTaskPath<Unit> action = Path.vtaskExec(() -> System.out.println("Hello"));
 * VTaskPath<Integer> pure = Path.vtaskPure(42);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <p>VTaskPath operations are lazy; they describe a computation but do not execute it until {@link
 * #unsafeRun()} or {@link #runSafe()} is called.
 *
 * <pre>{@code
 * VTaskPath<Config> config = Path.vtask(() -> readConfigFile())
 *     .map(Config::parse)
 *     .via(c -> Path.vtask(() -> validate(c)));
 *
 * // Nothing has happened yet!
 * Config result = config.unsafeRun();  // Now the computation runs
 * }</pre>
 *
 * <h2>Executing the computation</h2>
 *
 * <pre>{@code
 * // Unsafe - exceptions propagate
 * String content = Path.vtask(() -> Files.readString(path)).unsafeRun();
 *
 * // Safe - exceptions are captured
 * Try<String> result = Path.vtask(() -> Files.readString(path)).runSafe();
 *
 * // Async - returns CompletableFuture
 * CompletableFuture<String> future = Path.vtask(() -> Files.readString(path)).runAsync();
 * }</pre>
 *
 * <h2>Comparison with IOPath</h2>
 *
 * <p>Both {@code IOPath} and {@code VTaskPath} represent deferred side-effecting computations. The
 * key differences are:
 *
 * <ul>
 *   <li>{@code VTaskPath} executes on virtual threads, enabling massive concurrency
 *   <li>{@code VTaskPath} provides parallel combinators via {@link org.higherkindedj.hkt.vtask.Par}
 *   <li>{@code IOPath} uses the calling thread for execution
 * </ul>
 *
 * @param <A> the type of the value produced by the computation
 * @see VTask
 * @see IOPath
 * @see org.higherkindedj.hkt.vtask.Par
 */
public sealed interface VTaskPath<A> extends VTaskKind<A>, Effectful<A> permits DefaultVTaskPath {

  /**
   * Returns the underlying VTask value.
   *
   * @return the wrapped VTask
   */
  VTask<A> run();

  // ===== Effectful implementation =====

  @Override
  A unsafeRun();

  // runSafe() uses the default implementation from Effectful interface

  /**
   * Executes this VTaskPath asynchronously on a virtual thread.
   *
   * <p>The computation starts immediately on a virtual thread. The returned future can be used to
   * wait for the result or combine with other asynchronous operations.
   *
   * @return a CompletableFuture that will complete with the result
   */
  CompletableFuture<A> runAsync();

  // ===== Composable implementation =====

  @Override
  <B> VTaskPath<B> map(Function<? super A, ? extends B> mapper);

  @Override
  VTaskPath<A> peek(Consumer<? super A> consumer);

  /**
   * Converts the result of this VTaskPath to Unit, discarding any value.
   *
   * <p>Useful when you only care about the side effect, not the result.
   *
   * @return a VTaskPath that produces Unit
   */
  VTaskPath<Unit> asUnit();

  // ===== Combinable implementation =====

  @Override
  <B, C> VTaskPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner);

  /**
   * Combines this path with two others using a ternary function.
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new path containing the combined result
   */
  <B, C, D> VTaskPath<D> zipWith3(
      VTaskPath<B> second,
      VTaskPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner);

  // ===== Chainable implementation =====

  @Override
  <B> VTaskPath<B> via(Function<? super A, ? extends Chainable<B>> mapper);

  @Override
  <B> VTaskPath<B> then(Supplier<? extends Chainable<B>> supplier);

  /**
   * Alias for {@link #via(Function)} for those who prefer "flatMap" naming.
   *
   * @param mapper the function to apply; must not be null
   * @param <B> the result type
   * @return the result of applying the function
   */
  @Override
  <B> VTaskPath<B> flatMap(Function<? super A, ? extends Chainable<B>> mapper);

  // ===== Error handling =====

  /**
   * Handles exceptions that occur during execution.
   *
   * <p>If an exception is thrown during execution, the recovery function is applied to produce an
   * alternative value.
   *
   * @param recovery the function to apply if an exception occurs; must not be null
   * @return a VTaskPath that will recover from exceptions
   * @throws NullPointerException if recovery is null
   */
  VTaskPath<A> handleError(Function<? super Throwable, ? extends A> recovery);

  /**
   * Handles exceptions that occur during execution with a recovery VTaskPath.
   *
   * <p>If an exception is thrown during execution, the recovery function is applied to produce an
   * alternative VTaskPath.
   *
   * @param recovery the function to apply if an exception occurs; must not be null
   * @return a VTaskPath that will recover from exceptions
   * @throws NullPointerException if recovery is null
   */
  VTaskPath<A> handleErrorWith(Function<? super Throwable, ? extends VTaskPath<A>> recovery);

  // ===== Timeout =====

  /**
   * Creates a new VTaskPath that fails if this task does not complete within the specified
   * duration.
   *
   * @param duration the maximum time to wait; must not be null
   * @return a VTaskPath with timeout behaviour
   * @throws NullPointerException if duration is null
   */
  VTaskPath<A> timeout(Duration duration);

  // ===== Focus Bridge Methods =====

  /**
   * Applies a {@link FocusPath} to navigate within the contained value.
   *
   * <p>This bridges from the effect domain to the optics domain, allowing structural navigation
   * inside a VTask context. The lens operation is deferred along with the VTask computation.
   *
   * @param path the FocusPath to apply; must not be null
   * @param <B> the focused type
   * @return a new VTaskPath containing the focused value
   * @throws NullPointerException if path is null
   */
  <B> VTaskPath<B> focus(FocusPath<A, B> path);

  /**
   * Applies an {@link AffinePath} to navigate within the contained value.
   *
   * <p>This bridges from the effect domain to the optics domain. If the AffinePath does not match,
   * a runtime exception is thrown when the VTask is executed.
   *
   * @param path the AffinePath to apply; must not be null
   * @param exceptionIfAbsent supplies the exception if the path does not match; must not be null
   * @param <B> the focused type
   * @return a new VTaskPath containing the focused value
   * @throws NullPointerException if path or exceptionIfAbsent is null
   */
  <B> VTaskPath<B> focus(
      AffinePath<A, B> path, Supplier<? extends RuntimeException> exceptionIfAbsent);

  // ===== Conversion Methods =====

  // ===== Retry Operations =====

  /**
   * Returns a VTaskPath that retries this computation according to the given policy.
   *
   * @param policy the retry policy; must not be null
   * @return a new VTaskPath with retry behaviour
   */
  VTaskPath<A> withRetry(RetryPolicy policy);

  /**
   * Returns a VTaskPath that retries with exponential backoff and jitter.
   *
   * @param maxAttempts the maximum number of attempts
   * @param initialDelay the initial delay between retries
   * @return a new VTaskPath with retry behaviour
   */
  VTaskPath<A> retry(int maxAttempts, Duration initialDelay);

  // ===== Resilience Operations =====

  /**
   * Returns a VTaskPath protected by the given circuit breaker.
   *
   * @param circuitBreaker the circuit breaker; must not be null
   * @return a new VTaskPath with circuit breaker protection
   */
  VTaskPath<A> withCircuitBreaker(CircuitBreaker circuitBreaker);

  /**
   * Returns a VTaskPath protected by the given bulkhead.
   *
   * @param bulkhead the bulkhead; must not be null
   * @return a new VTaskPath with bulkhead protection
   */
  VTaskPath<A> withBulkhead(Bulkhead bulkhead);

  // ===== Effect Wrapping Methods =====

  /**
   * Wraps the result in an Either, catching exceptions.
   *
   * <p>Success produces {@code Either.right(value)}; failure produces {@code Either.left(mapped)}.
   *
   * @param exceptionMapper function to map exceptions to the left type
   * @param <E> the left (error) type
   * @return a new VTaskPath that always succeeds with an Either
   */
  <E> VTaskPath<Either<E, A>> catching(Function<? super Throwable, ? extends E> exceptionMapper);

  /**
   * Converts exceptions to Nothing, success to Just.
   *
   * @return a new VTaskPath that always succeeds with a Maybe
   */
  VTaskPath<Maybe<A>> asMaybe();

  /**
   * Wraps the result in a Try.
   *
   * @return a new VTaskPath that always succeeds with a Try
   */
  VTaskPath<Try<A>> asTry();

  // ===== Error Transformation =====

  /**
   * Transforms the exception without affecting success values.
   *
   * @param f the exception mapping function; must not be null
   * @return a new VTaskPath with mapped errors
   */
  VTaskPath<A> mapError(Function<? super Throwable, ? extends Throwable> f);

  // ===== Resource Safety =====

  /**
   * Ensures a finalizer runs whether this task succeeds or fails.
   *
   * @param finalizer the finalizer to run; must not be null
   * @return a new VTaskPath with guaranteed finalization
   */
  VTaskPath<A> guarantee(Runnable finalizer);

  // ===== Parallel Combinators =====

  /**
   * Combines this path with another in parallel on virtual threads.
   *
   * @param other the other VTaskPath; must not be null
   * @param combiner function to combine results; must not be null
   * @param <B> the other result type
   * @param <C> the combined result type
   * @return a new VTaskPath combining both results
   */
  <B, C> VTaskPath<C> parZipWith(
      VTaskPath<B> other, BiFunction<? super A, ? super B, ? extends C> combiner);

  /**
   * Races this path against another, returning the first to complete.
   *
   * @param other the other VTaskPath; must not be null
   * @return a new VTaskPath that returns the first result
   */
  VTaskPath<A> race(VTaskPath<A> other);

  /**
   * Converts this VTaskPath to a TryPath by executing it safely.
   *
   * <p><b>Note:</b> This executes the VTask immediately to capture success or failure.
   *
   * @return a TryPath containing the result or exception
   */
  TryPath<A> toTryPath();

  /**
   * Converts this VTaskPath to an IOPath.
   *
   * <p>The returned IOPath wraps this VTask's execution. When the IOPath is run, it will execute
   * the underlying VTask on the calling thread (blocking until completion).
   *
   * <p>This conversion is useful when you need to:
   *
   * <ul>
   *   <li>Integrate VTask-based code with existing IOPath pipelines
   *   <li>Use IOPath-specific features like {@code bracket} or {@code withResource}
   *   <li>Execute VTask computations on the calling thread instead of a virtual thread
   * </ul>
   *
   * <p><b>Note:</b> Unlike VTask which executes on virtual threads, the resulting IOPath will
   * execute on whatever thread calls {@code unsafeRun()}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * VTaskPath<String> vtask = Path.vtask(() -> fetchData());
   * IOPath<String> io = vtask.toIOPath();
   *
   * // Now can use IOPath-specific features
   * IOPath<String> withCleanup = io.guarantee(() -> cleanup());
   * }</pre>
   *
   * @return an IOPath that executes this VTask when run
   */
  IOPath<A> toIOPath();
}
