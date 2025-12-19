// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Effectful;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * A fluent path wrapper for {@link IO} values.
 *
 * <p>{@code IOPath} provides a chainable API for composing deferred side-effecting computations. It
 * implements {@link Effectful} to provide methods for executing the deferred computation.
 *
 * <h2>Creating IOPath instances</h2>
 *
 * <p>Use the {@link Path} factory class to create instances:
 *
 * <pre>{@code
 * IOPath<String> path = Path.io(() -> Files.readString(file));
 * IOPath<Unit> action = Path.ioRunnable(() -> System.out.println("Hello"));
 * IOPath<Integer> pure = Path.ioPure(42);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <p>IOPath operations are lazy - they describe a computation but don't execute it until {@link
 * #unsafeRun()} or {@link #runSafe()} is called.
 *
 * <pre>{@code
 * IOPath<Config> config = Path.io(() -> readConfigFile())
 *     .map(Config::parse)
 *     .via(c -> Path.io(() -> validate(c)));
 *
 * // Nothing has happened yet!
 * Config result = config.unsafeRun();  // Now the computation runs
 * }</pre>
 *
 * <h2>Executing the computation</h2>
 *
 * <pre>{@code
 * // Unsafe - exceptions propagate
 * String content = Path.io(() -> Files.readString(path)).unsafeRun();
 *
 * // Safe - exceptions are captured
 * Try<String> result = Path.io(() -> Files.readString(path)).runSafe();
 * }</pre>
 *
 * @param <A> the type of the value produced by the computation
 */
public final class IOPath<A> implements Effectful<A> {

  private final IO<A> value;

  /**
   * Creates a new IOPath wrapping the given IO.
   *
   * @param value the IO to wrap; must not be null
   */
  IOPath(IO<A> value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
  }

  /**
   * Returns the underlying IO value.
   *
   * @return the wrapped IO
   */
  public IO<A> run() {
    return value;
  }

  @Override
  public A unsafeRun() {
    return value.unsafeRunSync();
  }

  // runSafe() uses the default implementation from Effectful interface

  /**
   * Converts the result of this IOPath to Unit, discarding any value.
   *
   * <p>Useful when you only care about the side effect, not the result.
   *
   * @return an IOPath that produces Unit
   */
  public IOPath<Unit> asUnit() {
    return new IOPath<>(value.asUnit());
  }

  /**
   * Converts this IOPath to a TryPath by executing it safely.
   *
   * <p><b>Note:</b> This executes the IO immediately to capture success or failure.
   *
   * @return a TryPath containing the result or exception
   */
  public TryPath<A> toTryPath() {
    return new TryPath<>(runSafe());
  }

  // ===== Composable implementation =====

  @Override
  public <B> IOPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new IOPath<>(value.map(mapper));
  }

  @Override
  public IOPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new IOPath<>(
        value.map(
            a -> {
              consumer.accept(a);
              return a;
            }));
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> IOPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof IOPath<?> otherIO)) {
      throw new IllegalArgumentException("Cannot zipWith non-IOPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    IOPath<B> typedOther = (IOPath<B>) otherIO;

    return new IOPath<>(
        IO.delay(
            () -> {
              A a = this.value.unsafeRunSync();
              B b = typedOther.value.unsafeRunSync();
              return combiner.apply(a, b);
            }));
  }

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
  public <B, C, D> IOPath<D> zipWith3(
      IOPath<B> second,
      IOPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              A a = this.value.unsafeRunSync();
              B b = second.value.unsafeRunSync();
              C c = third.value.unsafeRunSync();
              return combiner.apply(a, b, c);
            }));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> IOPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              A a = this.value.unsafeRunSync();
              Chainable<B> result = mapper.apply(a);
              Objects.requireNonNull(result, "mapper must not return null");

              if (!(result instanceof IOPath<?> ioPath)) {
                throw new IllegalArgumentException(
                    "via mapper must return IOPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              IOPath<B> typedResult = (IOPath<B>) ioPath;
              return typedResult.unsafeRun();
            }));
  }

  @Override
  public <B> IOPath<B> flatMap(Function<? super A, ? extends Chainable<B>> mapper) {
    return via(mapper);
  }

  @Override
  public <B> IOPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              // Execute this IO for its side effects
              this.value.unsafeRunSync();

              Chainable<B> result = supplier.get();
              Objects.requireNonNull(result, "supplier must not return null");

              if (!(result instanceof IOPath<?> ioPath)) {
                throw new IllegalArgumentException(
                    "then supplier must return IOPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              IOPath<B> typedResult = (IOPath<B>) ioPath;
              return typedResult.unsafeRun();
            }));
  }

  /**
   * Handles exceptions that occur during execution.
   *
   * <p>If an exception is thrown during execution, the recovery function is applied to produce an
   * alternative value.
   *
   * @param recovery the function to apply if an exception occurs; must not be null
   * @return an IOPath that will recover from exceptions
   * @throws NullPointerException if recovery is null
   */
  public IOPath<A> handleError(Function<? super Throwable, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new IOPath<>(
        IO.delay(
            () -> {
              try {
                return this.value.unsafeRunSync();
              } catch (Throwable t) {
                return recovery.apply(t);
              }
            }));
  }

  /**
   * Handles exceptions that occur during execution with a recovery IO.
   *
   * <p>If an exception is thrown during execution, the recovery function is applied to produce an
   * alternative IOPath.
   *
   * @param recovery the function to apply if an exception occurs; must not be null
   * @return an IOPath that will recover from exceptions
   * @throws NullPointerException if recovery is null
   */
  public IOPath<A> handleErrorWith(Function<? super Throwable, ? extends IOPath<A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new IOPath<>(
        IO.delay(
            () -> {
              try {
                return this.value.unsafeRunSync();
              } catch (Throwable t) {
                IOPath<A> fallback = recovery.apply(t);
                Objects.requireNonNull(fallback, "recovery must not return null");
                return fallback.unsafeRun();
              }
            }));
  }

  // ===== Resource Management =====

  /**
   * Bracket pattern: acquire a resource, use it, and guarantee cleanup.
   *
   * <p>This is the fundamental pattern for safe resource management. The release function is
   * guaranteed to be called even if the use function throws an exception.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IOPath<String> content = IOPath.bracket(
   *     () -> Files.newInputStream(path),      // acquire
   *     in -> new String(in.readAllBytes()),   // use
   *     in -> { try { in.close(); } catch (IOException e) { } }  // release
   * );
   * }</pre>
   *
   * @param acquire supplies the resource; must not be null
   * @param use function that uses the resource; must not be null
   * @param release function that releases the resource; must not be null
   * @param <R> the resource type
   * @param <A> the result type
   * @return an IOPath that acquires, uses, and releases the resource
   * @throws NullPointerException if any argument is null
   */
  public static <R, A> IOPath<A> bracket(
      Supplier<? extends R> acquire,
      Function<? super R, ? extends A> use,
      Consumer<? super R> release) {
    Objects.requireNonNull(acquire, "acquire must not be null");
    Objects.requireNonNull(use, "use must not be null");
    Objects.requireNonNull(release, "release must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              R resource = acquire.get();
              try {
                return use.apply(resource);
              } finally {
                release.accept(resource);
              }
            }));
  }

  /**
   * Bracket pattern where the use function returns an IOPath.
   *
   * <p>Similar to {@link #bracket} but the use function returns an IOPath instead of a plain value.
   * This is useful when the use operation itself is effectful.
   *
   * @param acquire supplies the resource; must not be null
   * @param useIO function that uses the resource and returns an IOPath; must not be null
   * @param release function that releases the resource; must not be null
   * @param <R> the resource type
   * @param <A> the result type
   * @return an IOPath that acquires, uses, and releases the resource
   * @throws NullPointerException if any argument is null
   */
  public static <R, A> IOPath<A> bracketIO(
      Supplier<? extends R> acquire,
      Function<? super R, ? extends IOPath<A>> useIO,
      Consumer<? super R> release) {
    Objects.requireNonNull(acquire, "acquire must not be null");
    Objects.requireNonNull(useIO, "useIO must not be null");
    Objects.requireNonNull(release, "release must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              R resource = acquire.get();
              try {
                IOPath<A> result = useIO.apply(resource);
                Objects.requireNonNull(result, "useIO must not return null");
                return result.unsafeRun();
              } finally {
                release.accept(resource);
              }
            }));
  }

  /**
   * Convenience method for AutoCloseable resources.
   *
   * <p>The resource is automatically closed after use, even if an exception is thrown.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IOPath<String> content = IOPath.withResource(
   *     () -> Files.newBufferedReader(path),
   *     reader -> reader.lines().collect(Collectors.joining("\n"))
   * );
   * }</pre>
   *
   * @param resourceSupplier supplies the AutoCloseable resource; must not be null
   * @param use function that uses the resource; must not be null
   * @param <R> the resource type (must be AutoCloseable)
   * @param <A> the result type
   * @return an IOPath that manages the resource lifecycle
   * @throws NullPointerException if any argument is null
   */
  public static <R extends AutoCloseable, A> IOPath<A> withResource(
      Supplier<? extends R> resourceSupplier, Function<? super R, ? extends A> use) {
    Objects.requireNonNull(resourceSupplier, "resourceSupplier must not be null");
    Objects.requireNonNull(use, "use must not be null");

    return bracket(
        resourceSupplier,
        use,
        resource -> {
          try {
            resource.close();
          } catch (Exception e) {
            // Silently ignore close exceptions, as is standard with try-with-resources
          }
        });
  }

  /**
   * Convenience method for AutoCloseable resources where the use function returns an IOPath.
   *
   * @param resourceSupplier supplies the AutoCloseable resource; must not be null
   * @param useIO function that uses the resource and returns an IOPath; must not be null
   * @param <R> the resource type (must be AutoCloseable)
   * @param <A> the result type
   * @return an IOPath that manages the resource lifecycle
   * @throws NullPointerException if any argument is null
   */
  public static <R extends AutoCloseable, A> IOPath<A> withResourceIO(
      Supplier<? extends R> resourceSupplier, Function<? super R, ? extends IOPath<A>> useIO) {
    Objects.requireNonNull(resourceSupplier, "resourceSupplier must not be null");
    Objects.requireNonNull(useIO, "useIO must not be null");

    return bracketIO(
        resourceSupplier,
        useIO,
        resource -> {
          try {
            resource.close();
          } catch (Exception e) {
            // Silently ignore close exceptions, as is standard with try-with-resources
          }
        });
  }

  /**
   * Ensures a finalizer runs regardless of success or failure.
   *
   * <p>The finalizer is guaranteed to run even if this IOPath throws an exception. The original
   * exception (if any) is preserved and rethrown after the finalizer runs.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IOPath<String> operation = Path.io(() -> doSomething())
   *     .guarantee(() -> cleanup());
   * }</pre>
   *
   * @param finalizer the action to run; must not be null
   * @return an IOPath that runs the finalizer after this computation
   * @throws NullPointerException if finalizer is null
   */
  public IOPath<A> guarantee(Runnable finalizer) {
    Objects.requireNonNull(finalizer, "finalizer must not be null");
    return new IOPath<>(
        IO.delay(
            () -> {
              try {
                return this.value.unsafeRunSync();
              } finally {
                finalizer.run();
              }
            }));
  }

  /**
   * Ensures an IOPath finalizer runs regardless of success or failure.
   *
   * <p>Similar to {@link #guarantee} but the finalizer is itself an IOPath.
   *
   * @param finalizerIO the IOPath to run as finalizer; must not be null
   * @return an IOPath that runs the finalizer after this computation
   * @throws NullPointerException if finalizerIO is null
   */
  public IOPath<A> guaranteeIO(IOPath<?> finalizerIO) {
    Objects.requireNonNull(finalizerIO, "finalizerIO must not be null");
    return new IOPath<>(
        IO.delay(
            () -> {
              try {
                return this.value.unsafeRunSync();
              } finally {
                finalizerIO.unsafeRun();
              }
            }));
  }

  // ===== Parallel Execution =====

  /**
   * Combines this IOPath with another in parallel.
   *
   * <p>Both IOPaths are executed concurrently using CompletableFuture, and their results are
   * combined using the provided function.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IOPath<UserProfile> profile = fetchUser.parZipWith(
   *     fetchOrders,
   *     UserProfile::new
   * );
   * }</pre>
   *
   * @param other the other IOPath to execute in parallel; must not be null
   * @param combiner the function to combine results; must not be null
   * @param <B> the type of the other value
   * @param <C> the type of the combined result
   * @return an IOPath that runs both computations in parallel and combines results
   * @throws NullPointerException if other or combiner is null
   */
  public <B, C> IOPath<C> parZipWith(
      IOPath<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              CompletableFuture<A> futureA = CompletableFuture.supplyAsync(this::unsafeRun);
              CompletableFuture<B> futureB = CompletableFuture.supplyAsync(other::unsafeRun);

              try {
                A a = futureA.get();
                B b = futureB.get();
                return combiner.apply(a, b);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Parallel execution interrupted", e);
              } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                  throw re;
                }
                throw new RuntimeException(cause);
              }
            }));
  }

  /**
   * Races this IOPath against another, returning the first to complete.
   *
   * <p>Both IOPaths are executed concurrently, and the result of whichever completes first is
   * returned. The other computation is cancelled if possible.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IOPath<Config> config = loadFromCache.race(loadFromDisk);
   * }</pre>
   *
   * @param other the other IOPath to race against; must not be null
   * @return an IOPath that returns the first result
   * @throws NullPointerException if other is null
   */
  public IOPath<A> race(IOPath<A> other) {
    Objects.requireNonNull(other, "other must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              CompletableFuture<A> futureA = CompletableFuture.supplyAsync(this::unsafeRun);
              CompletableFuture<A> futureB = CompletableFuture.supplyAsync(other::unsafeRun);

              try {
                // anyOf returns when any future completes
                @SuppressWarnings("unchecked")
                CompletableFuture<A> winner =
                    (CompletableFuture<A>)
                        CompletableFuture.anyOf(futureA, futureB).thenApply(result -> (A) result);

                A result = winner.get();

                // Cancel the loser (best effort)
                futureA.cancel(true);
                futureB.cancel(true);

                return result;
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Race interrupted", e);
              } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                  throw re;
                }
                throw new RuntimeException(cause);
              }
            }));
  }

  // ===== Retry Operations =====

  /**
   * Returns an IOPath that retries this computation according to the given policy.
   *
   * <p>If the computation fails, it will be retried according to the policy's configuration (number
   * of attempts, delays, retry predicate).
   *
   * <p>Example:
   *
   * <pre>{@code
   * RetryPolicy policy = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100))
   *     .retryOn(IOException.class);
   *
   * IOPath<String> resilient = Path.io(() -> httpClient.get(url))
   *     .withRetry(policy);
   * }</pre>
   *
   * @param policy the retry policy; must not be null
   * @return an IOPath that retries on failure
   * @throws NullPointerException if policy is null
   */
  public IOPath<A> withRetry(RetryPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return new IOPath<>(IO.delay(() -> Retry.execute(policy, this::unsafeRun)));
  }

  /**
   * Returns an IOPath that retries this computation with exponential backoff.
   *
   * <p>This is a convenience method that uses exponential backoff with jitter.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IOPath<String> resilient = Path.io(() -> httpClient.get(url))
   *     .retry(3, Duration.ofMillis(100));
   * }</pre>
   *
   * @param maxAttempts maximum number of attempts (must be at least 1)
   * @param initialDelay initial delay between attempts; must not be null
   * @return an IOPath that retries on failure
   * @throws NullPointerException if initialDelay is null
   * @throws IllegalArgumentException if maxAttempts is less than 1
   */
  public IOPath<A> retry(int maxAttempts, Duration initialDelay) {
    return withRetry(RetryPolicy.exponentialBackoffWithJitter(maxAttempts, initialDelay));
  }

  // ===== Focus Bridge Methods =====

  /**
   * Applies a {@link FocusPath} to navigate within the contained value.
   *
   * <p>This bridges from the effect domain to the optics domain, allowing structural navigation
   * inside an IO context. The lens operation is deferred along with the IO computation.
   *
   * @param path the FocusPath to apply; must not be null
   * @param <B> the focused type
   * @return a new IOPath containing the focused value
   * @throws NullPointerException if path is null
   */
  public <B> IOPath<B> focus(FocusPath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return map(path::get);
  }

  /**
   * Applies an {@link AffinePath} to navigate within the contained value.
   *
   * <p>This bridges from the effect domain to the optics domain. If the AffinePath doesn't match, a
   * runtime exception is thrown when the IO is executed. For safer handling, consider using {@code
   * toTryPath()} first.
   *
   * @param path the AffinePath to apply; must not be null
   * @param exceptionIfAbsent supplies the exception if the path doesn't match; must not be null
   * @param <B> the focused type
   * @return a new IOPath containing the focused value
   * @throws NullPointerException if path or exceptionIfAbsent is null
   */
  public <B> IOPath<B> focus(
      AffinePath<A, B> path, Supplier<? extends RuntimeException> exceptionIfAbsent) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(exceptionIfAbsent, "exceptionIfAbsent must not be null");
    return via(
        a ->
            path.getOptional(a)
                .<IOPath<B>>map(Path::ioPure)
                .orElseGet(
                    () ->
                        Path.io(
                            () -> {
                              throw exceptionIfAbsent.get();
                            })));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    // IO equality is based on reference since IO represents a computation
    return this == obj;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return "IOPath(<deferred>)";
  }
}
