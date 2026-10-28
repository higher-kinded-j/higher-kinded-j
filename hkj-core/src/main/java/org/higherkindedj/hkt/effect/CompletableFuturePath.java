// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Recoverable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.tuple.Tuple2;

/**
 * A fluent path wrapper for {@link CompletableFuture} async computations.
 *
 * <p>{@code CompletableFuturePath} represents asynchronous computations that may complete with a
 * value or fail with an exception. It provides error recovery and timeout handling capabilities.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Async API calls
 *   <li>Parallel computation
 *   <li>Non-blocking I/O
 *   <li>Timeout handling
 * </ul>
 *
 * <h2>Creating CompletableFuturePath instances</h2>
 *
 * <pre>{@code
 * // From existing future
 * CompletableFuturePath<User> userPath = CompletableFuturePath.fromFuture(
 *     userService.findByIdAsync(userId));
 *
 * // Already completed
 * CompletableFuturePath<Integer> completed = CompletableFuturePath.completed(42);
 *
 * // Failed
 * CompletableFuturePath<Integer> failed = CompletableFuturePath.failed(new IOException("..."));
 *
 * // Async supplier
 * CompletableFuturePath<Data> async = CompletableFuturePath.supplyAsync(() -> loadData());
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * CompletableFuturePath<Order> orderPath = CompletableFuturePath.fromFuture(
 *         userService.findByIdAsync(userId))
 *     .via(user -> CompletableFuturePath.fromFuture(
 *         orderService.getOrdersAsync(user.id())))
 *     .map(orders -> orders.get(0))
 *     .withTimeout(Duration.ofSeconds(5))
 *     .recover(ex -> Order.empty());
 *
 * Order order = orderPath.join();
 * }</pre>
 *
 * @param <A> the type of the computed value
 */
public final class CompletableFuturePath<A>
    implements Recoverable<CompletableFuturePath<?>, Exception, A> {

  private final CompletableFuture<A> future;

  /**
   * Creates a new CompletableFuturePath wrapping the given future.
   *
   * @param future the CompletableFuture to wrap; must not be null
   */
  CompletableFuturePath(CompletableFuture<A> future) {
    this.future = Objects.requireNonNull(future, "future must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a CompletableFuturePath from an existing future.
   *
   * <p>A CompletableFuturePath always gives a value, so a future that completes with null fails the
   * path with a {@link NullPointerException}. For a future with nothing to return, such as one from
   * {@link CompletableFuture#runAsync(Runnable)}, map its result to {@link
   * org.higherkindedj.hkt.Unit#INSTANCE} first. The path holds a future that mirrors the one given:
   * it completes, fails or is cancelled as the given one does, and cancelling it cancels the given
   * one too.
   *
   * @param future the CompletableFuture to wrap; must not be null
   * @param <A> the value type
   * @return a CompletableFuturePath wrapping the future
   * @throws NullPointerException if future is null
   */
  public static <A> CompletableFuturePath<A> fromFuture(CompletableFuture<A> future) {
    Objects.requireNonNull(future, "future must not be null");
    CompletableFuture<A> checked = new CompletableFuture<>();
    // A mirror rather than thenApply, so a cancelled future still reads as cancelled
    future.whenComplete(
        (a, failure) -> {
          if (failure != null) {
            checked.completeExceptionally(failure);
          } else if (a == null) {
            checked.completeExceptionally(
                new NullPointerException(
                    "the future completed with null: map a future with nothing to return to Unit,"
                        + " with thenApply(_ -> Unit.INSTANCE), before wrapping it"));
          } else {
            checked.complete(a);
          }
        });
    // Cancelling the path's future reaches the given one, as when they were the same future
    checked.whenComplete(
        (_, _) -> {
          if (checked.isCancelled()) {
            future.cancel(true);
          }
        });
    return new CompletableFuturePath<>(checked);
  }

  /**
   * Creates an already-completed CompletableFuturePath with the given value.
   *
   * @param value the completed value; must not be null
   * @param <A> the value type
   * @return a completed CompletableFuturePath
   * @throws NullPointerException if value is null
   */
  public static <A> CompletableFuturePath<A> completed(A value) {
    Objects.requireNonNull(value, "value must not be null");
    return new CompletableFuturePath<>(CompletableFuture.completedFuture(value));
  }

  /**
   * Creates a failed CompletableFuturePath with the given exception.
   *
   * @param exception the exception; must not be null
   * @param <A> the value type
   * @return a failed CompletableFuturePath
   * @throws NullPointerException if exception is null
   */
  public static <A> CompletableFuturePath<A> failed(Exception exception) {
    Objects.requireNonNull(exception, "exception must not be null");
    return new CompletableFuturePath<>(CompletableFuture.failedFuture(exception));
  }

  /**
   * Creates a CompletableFuturePath from a supplier, running async on the common fork-join pool.
   *
   * @param supplier the supplier for the value; must not be null, and must not return null
   * @param <A> the value type
   * @return a CompletableFuturePath running asynchronously
   * @throws NullPointerException if supplier is null
   */
  public static <A> CompletableFuturePath<A> supplyAsync(Supplier<A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new CompletableFuturePath<>(CompletableFuture.supplyAsync(nonNullResult(supplier)));
  }

  /**
   * Creates a CompletableFuturePath from a supplier, running on the given executor.
   *
   * @param supplier the supplier for the value; must not be null, and must not return null
   * @param executor the executor to run on; must not be null
   * @param <A> the value type
   * @return a CompletableFuturePath running on the executor
   * @throws NullPointerException if supplier or executor is null
   */
  public static <A> CompletableFuturePath<A> supplyAsync(Supplier<A> supplier, Executor executor) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    Objects.requireNonNull(executor, "executor must not be null");
    return new CompletableFuturePath<>(
        CompletableFuture.supplyAsync(nonNullResult(supplier), executor));
  }

  // ===== Terminal Operations =====

  /**
   * Returns the underlying CompletableFuture.
   *
   * @return the wrapped CompletableFuture
   */
  public CompletableFuture<A> run() {
    return future;
  }

  /**
   * Returns the underlying CompletableFuture.
   *
   * <p>Alias for {@link #run()} for compatibility with standard CompletableFuture APIs.
   *
   * @return the wrapped CompletableFuture
   */
  public CompletableFuture<A> toCompletableFuture() {
    return future;
  }

  /**
   * Blocks and returns the result when complete.
   *
   * @return the computed value
   * @throws CompletionException if the computation failed
   */
  public A join() {
    return future.join();
  }

  /**
   * Blocks and returns the result, with timeout.
   *
   * @param timeout the maximum time to wait; must not be null
   * @return the computed value
   * @throws TimeoutException if the timeout is exceeded
   * @throws CompletionException if the computation failed
   * @throws NullPointerException if timeout is null
   */
  public A join(Duration timeout) throws TimeoutException {
    Objects.requireNonNull(timeout, "timeout must not be null");
    try {
      return future.get(TimeUnit.NANOSECONDS.convert(timeout), TimeUnit.NANOSECONDS);
    } catch (ExecutionException e) {
      throw new CompletionException(e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CompletionException(e);
    }
  }

  /**
   * Returns whether this future is done (completed normally, exceptionally, or cancelled).
   *
   * @return true if done
   */
  public boolean isDone() {
    return future.isDone();
  }

  /**
   * Returns whether this future completed exceptionally.
   *
   * @return true if completed exceptionally
   */
  public boolean isCompletedExceptionally() {
    return future.isCompletedExceptionally();
  }

  // ===== Composable implementation =====

  @Override
  public <B> CompletableFuturePath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new CompletableFuturePath<>(
        future.thenApply(
            a -> Objects.requireNonNull(mapper.apply(a), "mapper must not return null")));
  }

  @Override
  public CompletableFuturePath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new CompletableFuturePath<>(
        future.thenApply(
            a -> {
              consumer.accept(a);
              return a;
            }));
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> CompletableFuturePath<C> zipWith(
      Combinable<? extends CompletableFuturePath<?>, B> other,
      BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof CompletableFuturePath<?> otherFuture)) {
      throw new IllegalArgumentException(
          "Cannot zipWith non-CompletableFuturePath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    CompletableFuturePath<B> typedOther = (CompletableFuturePath<B>) otherFuture;

    return new CompletableFuturePath<>(
        future.thenCombine(
            typedOther.future,
            (a, b) ->
                Objects.requireNonNull(combiner.apply(a, b), "combiner must not return null")));
  }

  /**
   * Combines this path with two others using a ternary function.
   *
   * <p>All three futures are combined in parallel.
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null, and must not return null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new path containing the combined result
   */
  public <B, C, D> CompletableFuturePath<D> zipWith3(
      CompletableFuturePath<B> second,
      CompletableFuturePath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    CompletableFuture<D> combined =
        future
            .thenCombine(second.future, Tuple2<A, B>::new)
            .thenCombine(
                third.future,
                (pair, c) ->
                    Objects.requireNonNull(
                        combiner.apply(pair._1(), pair._2(), c), "combiner must not return null"));

    return new CompletableFuturePath<>(combined);
  }

  // ===== Chainable implementation =====

  /**
   * {@inheritDoc}
   *
   * <p>The mapper must return a {@code CompletableFuturePath}; a step of another kind does not
   * compile, so convert it first.
   */
  @Override
  public <B> CompletableFuturePath<B> via(
      Function<? super A, ? extends Chainable<? extends CompletableFuturePath<?>, B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    CompletableFuture<B> composed =
        future.thenCompose(
            a -> {
              Chainable<? extends CompletableFuturePath<?>, B> result = mapper.apply(a);
              Objects.requireNonNull(result, "mapper must not return null");

              if (!(result instanceof CompletableFuturePath<?> futurePath)) {
                throw new IllegalArgumentException(
                    "via mapper must return CompletableFuturePath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              CompletableFuturePath<B> typedResult = (CompletableFuturePath<B>) futurePath;
              return typedResult.future;
            });

    return new CompletableFuturePath<>(composed);
  }

  @Override
  public <B> CompletableFuturePath<B> then(
      Supplier<? extends Chainable<? extends CompletableFuturePath<?>, B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    CompletableFuture<B> sequenced =
        future.thenCompose(
            ignored -> {
              Chainable<? extends CompletableFuturePath<?>, B> result = supplier.get();
              Objects.requireNonNull(result, "supplier must not return null");

              if (!(result instanceof CompletableFuturePath<?> futurePath)) {
                throw new IllegalArgumentException(
                    "then supplier must return CompletableFuturePath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              CompletableFuturePath<B> typedResult = (CompletableFuturePath<B>) futurePath;
              return typedResult.future;
            });

    return new CompletableFuturePath<>(sequenced);
  }

  // ===== Recoverable implementation =====

  @Override
  public CompletableFuturePath<A> recover(Function<? super Exception, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new CompletableFuturePath<>(
        future.exceptionally(
            ex -> {
              Exception exception = unwrapException(ex);
              return Objects.requireNonNull(
                  recovery.apply(exception), "recovery must not return null");
            }));
  }

  @Override
  public CompletableFuturePath<A> recoverWith(
      Function<
              ? super Exception,
              ? extends Recoverable<? extends CompletableFuturePath<?>, ? extends Exception, A>>
          recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");

    CompletableFuture<A> recovered =
        future.exceptionallyCompose(
            ex -> {
              Exception exception = unwrapException(ex);
              Recoverable<? extends CompletableFuturePath<?>, ? extends Exception, A> result =
                  recovery.apply(exception);
              Objects.requireNonNull(result, "recovery must not return null");

              if (!(result instanceof CompletableFuturePath<?> futurePath)) {
                throw new IllegalArgumentException(
                    "recoverWith must return CompletableFuturePath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              CompletableFuturePath<A> typedResult = (CompletableFuturePath<A>) futurePath;
              return typedResult.future;
            });

    return new CompletableFuturePath<>(recovered);
  }

  @Override
  public CompletableFuturePath<A> orElse(
      Supplier<? extends Recoverable<? extends CompletableFuturePath<?>, ? extends Exception, A>>
          alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    return recoverWith(ignored -> alternative.get());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <E2> Recoverable<CompletableFuturePath<?>, E2, A> mapError(
      Function<? super Exception, ? extends E2> mapper) {
    // CompletableFuturePath uses Exception as a fixed error type. mapError transforms the error
    // type,
    // but for CompletableFuturePath we can't actually change the underlying Exception.
    // We return this cast to the new error type, which is a limitation of the type system.
    Objects.requireNonNull(mapper, "mapper must not be null");
    return (Recoverable<CompletableFuturePath<?>, E2, A>) this;
  }

  // ===== Async-Specific Operations =====

  /**
   * Adds a timeout to this computation.
   *
   * <p>If the timeout is exceeded, the new path's future completes exceptionally with a
   * TimeoutException. This path is left as it is: its future mirrors into the new path's, as {@link
   * #fromFuture} describes, so the timeout completes only the new path.
   *
   * @param timeout the maximum duration; must not be null
   * @return a new CompletableFuturePath with timeout
   * @throws NullPointerException if timeout is null
   */
  public CompletableFuturePath<A> withTimeout(Duration timeout) {
    Objects.requireNonNull(timeout, "timeout must not be null");
    return new CompletableFuturePath<>(
        fromFuture(future)
            .future
            .orTimeout(TimeUnit.NANOSECONDS.convert(timeout), TimeUnit.NANOSECONDS));
  }

  /**
   * Returns a default value if this computation times out.
   *
   * <p>This path is left as it is: the default completes only the new path, as {@link #withTimeout}
   * describes.
   *
   * @param defaultValue the value to return on timeout; must not be null
   * @param timeout the timeout duration; must not be null
   * @return a new CompletableFuturePath that completes with default on timeout
   * @throws NullPointerException if defaultValue or timeout is null
   */
  public CompletableFuturePath<A> completeOnTimeout(A defaultValue, Duration timeout) {
    Objects.requireNonNull(defaultValue, "defaultValue must not be null");
    Objects.requireNonNull(timeout, "timeout must not be null");
    return new CompletableFuturePath<>(
        fromFuture(future)
            .future
            .completeOnTimeout(
                defaultValue, TimeUnit.NANOSECONDS.convert(timeout), TimeUnit.NANOSECONDS));
  }

  /**
   * Runs the subsequent processing on a different executor.
   *
   * @param executor the executor for subsequent operations; must not be null
   * @return a new CompletableFuturePath that runs subsequent operations on the executor
   * @throws NullPointerException if executor is null
   */
  public CompletableFuturePath<A> onExecutor(Executor executor) {
    Objects.requireNonNull(executor, "executor must not be null");
    return new CompletableFuturePath<>(future.thenApplyAsync(Function.identity(), executor));
  }

  // ===== Parallel Execution =====

  /**
   * Combines this CompletableFuturePath with another in parallel.
   *
   * <p>Both futures are already running concurrently, and this method combines their results when
   * both complete. This is semantically similar to {@link #zipWith} but makes the parallel intent
   * explicit.
   *
   * <p>Example:
   *
   * <pre>{@code
   * CompletableFuturePath<UserProfile> profile =
   *     fetchUser.parZipWith(fetchOrders, UserProfile::new);
   * }</pre>
   *
   * @param other the other path to combine with; must not be null
   * @param combiner the function to combine results; must not be null, and must not return null
   * @param <B> the type of the other value
   * @param <C> the type of the combined result
   * @return a CompletableFuturePath containing the combined result
   * @throws NullPointerException if other or combiner is null
   */
  public <B, C> CompletableFuturePath<C> parZipWith(
      CompletableFuturePath<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");
    return zipWith(other, combiner);
  }

  /**
   * Races this CompletableFuturePath against another, returning the first successful result.
   *
   * <p>Both futures race, and the result of whichever completes successfully first is returned. If
   * one fails but the other succeeds, the successful result is returned. Only if both fail is the
   * exception from the last failure propagated.
   *
   * <p>This "first success" semantic is useful for redundant data sources:
   *
   * <pre>{@code
   * CompletableFuturePath<Config> config = loadFromCache.race(loadFromRemote);
   * // Returns whichever succeeds first; only fails if both fail
   * }</pre>
   *
   * @param other the other path to race against; must not be null
   * @return a CompletableFuturePath that completes with the first successful result
   * @throws NullPointerException if other is null
   */
  public CompletableFuturePath<A> race(CompletableFuturePath<A> other) {
    Objects.requireNonNull(other, "other must not be null");

    CompletableFuture<A> result = new CompletableFuture<>();
    AtomicInteger failureCount = new AtomicInteger(0);
    AtomicReference<Throwable> lastFailure = new AtomicReference<>();

    BiConsumer<A, Throwable> handler =
        (value, ex) -> {
          if (ex == null) {
            result.complete(value);
          } else {
            lastFailure.set(ex);
            if (failureCount.incrementAndGet() == 2) {
              result.completeExceptionally(lastFailure.get());
            }
          }
        };

    future.whenComplete(handler);
    other.future.whenComplete(handler);

    return new CompletableFuturePath<>(result);
  }

  // ===== Retry Operations =====

  /**
   * Creates a CompletableFuturePath that executes the supplier with retry support.
   *
   * <p>Each retry attempt calls the supplier again, allowing the operation to be retried properly.
   * The retry logic runs asynchronously on the common fork-join pool.
   *
   * <p>Example:
   *
   * <pre>{@code
   * RetryPolicy policy = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100))
   *     .retryOn(IOException.class);
   *
   * CompletableFuturePath<String> resilient =
   *     CompletableFuturePath.supplyAsyncWithRetry(() -> httpClient.get(url), policy);
   * }</pre>
   *
   * @param supplier the supplier for the value; called on each retry attempt; must not be null, and
   *     must not return null
   * @param policy the retry policy; must not be null
   * @param <A> the value type
   * @return a CompletableFuturePath that retries the supplier on failure
   * @throws NullPointerException if supplier or policy is null
   */
  public static <A> CompletableFuturePath<A> supplyAsyncWithRetry(
      Supplier<A> supplier, RetryPolicy policy) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    return new CompletableFuturePath<>(
        CompletableFuture.supplyAsync(nonNullResult(() -> Retry.execute(policy, supplier))));
  }

  /**
   * Creates a CompletableFuturePath that executes the supplier with exponential backoff retry.
   *
   * <p>This is a convenience method that uses exponential backoff with jitter.
   *
   * <p>Example:
   *
   * <pre>{@code
   * CompletableFuturePath<String> resilient =
   *     CompletableFuturePath.supplyAsyncWithRetry(() -> httpClient.get(url), 3, Duration.ofMillis(100));
   * }</pre>
   *
   * @param supplier the supplier for the value; called on each retry attempt; must not be null
   * @param maxAttempts maximum number of attempts (must be at least 1)
   * @param initialDelay initial delay between attempts; must not be null
   * @param <A> the value type
   * @return a CompletableFuturePath that retries the supplier on failure
   * @throws NullPointerException if supplier or initialDelay is null
   * @throws IllegalArgumentException if maxAttempts is less than 1
   */
  public static <A> CompletableFuturePath<A> supplyAsyncWithRetry(
      Supplier<A> supplier, int maxAttempts, Duration initialDelay) {
    return supplyAsyncWithRetry(
        supplier, RetryPolicy.exponentialBackoffWithJitter(maxAttempts, initialDelay));
  }

  /**
   * Returns a CompletableFuturePath that retries reading this computation's result.
   *
   * <p><strong>Note:</strong> This method retries calling {@code join()} on the existing future. If
   * the future has already failed, retrying will not help because CompletableFuture caches its
   * result. For retrying an operation that may fail, use {@link #supplyAsyncWithRetry(Supplier,
   * RetryPolicy)} instead.
   *
   * @param policy the retry policy; must not be null
   * @return a CompletableFuturePath that retries on failure
   * @throws NullPointerException if policy is null
   * @deprecated Use {@link #supplyAsyncWithRetry(Supplier, RetryPolicy)} for proper retry
   *     semantics. This method only retries reading from an already-completed future.
   */
  @Deprecated
  public CompletableFuturePath<A> withRetry(RetryPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return new CompletableFuturePath<>(
        CompletableFuture.supplyAsync(() -> Retry.execute(policy, () -> this.join())));
  }

  /**
   * Returns a CompletableFuturePath that retries reading this computation's result with exponential
   * backoff.
   *
   * <p><strong>Note:</strong> This method retries calling {@code join()} on the existing future. If
   * the future has already failed, retrying will not help because CompletableFuture caches its
   * result. For retrying an operation that may fail, use {@link #supplyAsyncWithRetry(Supplier,
   * int, Duration)} instead.
   *
   * @param maxAttempts maximum number of attempts (must be at least 1)
   * @param initialDelay initial delay between attempts; must not be null
   * @return a CompletableFuturePath that retries on failure
   * @throws NullPointerException if initialDelay is null
   * @throws IllegalArgumentException if maxAttempts is less than 1
   * @deprecated Use {@link #supplyAsyncWithRetry(Supplier, int, Duration)} for proper retry
   *     semantics. This method only retries reading from an already-completed future.
   */
  @Deprecated
  public CompletableFuturePath<A> retry(int maxAttempts, Duration initialDelay) {
    return withRetry(RetryPolicy.exponentialBackoffWithJitter(maxAttempts, initialDelay));
  }

  // ===== Conversions =====

  /**
   * Converts to an IOPath (blocking).
   *
   * <p>The IOPath will block on the future when run.
   *
   * @return an IOPath that blocks on this future
   */
  public IOPath<A> toIOPath() {
    return new IOPath<>(this::join);
  }

  /**
   * Converts to a TryPath (blocking).
   *
   * <p>Blocks until the future completes, capturing any exception.
   *
   * @return a TryPath containing the result or exception
   */
  public TryPath<A> toTryPath() {
    return new TryPath<>(Try.of(this::join));
  }

  /**
   * Converts to an EitherPath (blocking).
   *
   * <p>Blocks until the future completes. Exceptions become Left values.
   *
   * @return an EitherPath with Exception as Left
   */
  public EitherPath<Exception, A> toEitherPath() {
    A value;
    try {
      value = join();
    } catch (CompletionException e) {
      Exception cause =
          e.getCause() instanceof Exception ex ? ex : new RuntimeException(e.getCause());
      return new EitherPath<>(Either.left(cause));
    }
    return new EitherPath<>(Either.right(value));
  }

  /**
   * Converts to a MaybePath (blocking).
   *
   * <p>Blocks until the future completes. Returns Nothing if the future fails.
   *
   * @return a MaybePath containing the result if successful
   */
  public MaybePath<A> toMaybePath() {
    try {
      return new MaybePath<>(Maybe.just(join()));
    } catch (CompletionException e) {
      return new MaybePath<>(Maybe.nothing());
    }
  }

  // ===== Helper Methods =====

  /** Wraps a supplier so a null result fails the future that runs it. */
  private static <A> Supplier<A> nonNullResult(Supplier<A> supplier) {
    return () -> Objects.requireNonNull(supplier.get(), "supplier must not return null");
  }

  /** Unwraps CompletionException to get the underlying exception. */
  private static Exception unwrapException(Throwable ex) {
    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
    if (cause instanceof Exception e) {
      return e;
    }
    // Wrap Errors in RuntimeException
    return new RuntimeException(cause);
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CompletableFuturePath<?> other)) return false;
    return future.equals(other.future);
  }

  @Override
  public int hashCode() {
    return future.hashCode();
  }

  @Override
  public String toString() {
    if (future.isDone()) {
      if (future.isCompletedExceptionally()) {
        return "CompletableFuturePath(" + PathToString.FAILED + ")";
      }
      return "CompletableFuturePath(" + future.join() + ")";
    }
    return "CompletableFuturePath(" + PathToString.PENDING + ")";
  }
}
