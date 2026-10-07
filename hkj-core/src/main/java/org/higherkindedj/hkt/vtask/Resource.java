// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.higherkindedj.hkt.util.validation.Operation.AP;
import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;
import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * A functional resource type that guarantees cleanup via the bracket pattern.
 *
 * <p>Resource provides safe resource management for VTask computations. It ensures that resources
 * are always released, even when exceptions occur or structured concurrency tasks are cancelled.
 *
 * <h2>The Bracket Pattern</h2>
 *
 * <p>Resource implements the bracket pattern (acquire-use-release):
 *
 * <ol>
 *   <li><b>Acquire:</b> The resource is acquired (connection opened, file handle created, etc.)
 *   <li><b>Use:</b> The resource is used to perform some computation
 *   <li><b>Release:</b> The resource is released (connection closed, file handle closed, etc.)
 * </ol>
 *
 * <p>The release step is <b>guaranteed</b> to run regardless of whether the use step succeeds or
 * fails. Resources are released in reverse order of acquisition (LIFO).
 *
 * <h2>Basic Usage</h2>
 *
 * <pre>{@code
 * // Create a resource from AutoCloseable
 * Resource<Connection> connResource = Resource.fromAutoCloseable(
 *     () -> dataSource.getConnection()
 * );
 *
 * // Use the resource
 * VTask<List<User>> users = connResource.use(conn ->
 *     VTask.of(() -> userDao.findAll(conn))
 * );
 *
 * // Resource is automatically closed after use
 * List<User> result = users.run();
 * }</pre>
 *
 * <h2>Composing Resources</h2>
 *
 * <pre>{@code
 * // Combine two resources - both are acquired, used, then released in reverse order
 * Resource<Tuple2<Connection, Statement>> combined =
 *     connResource.and(stmtResource);
 *
 * // Chain resource acquisition
 * Resource<PreparedStatement> chained = connResource.flatMap(conn ->
 *     Resource.make(
 *         () -> conn.prepareStatement(sql),
 *         PreparedStatement::close
 *     )
 * );
 * }</pre>
 *
 * <h2>Each Use Acquires Its Own</h2>
 *
 * <p>A Resource describes how to acquire and release, and holds nothing itself. Each {@link #use}
 * acquires afresh and releases exactly what it acquired. So one Resource, however it was composed,
 * can be used any number of times: one use nested inside another, or several at once on different
 * threads.
 *
 * <h2>Preview API Notice</h2>
 *
 * <p><b>Note:</b> When used with structured concurrency, Resource respects task cancellation. If a
 * task is cancelled, acquired resources are still released.
 *
 * @param <A> the type of the managed resource
 * @see VTask
 * @see Scope
 */
public final class Resource<A> {

  private static final String RELEASE_FAILED = "Failed to release resource";
  private static final String INNER_RELEASE_FAILED = "Failed to release inner resource";
  private static final String OUTER_RELEASE_FAILED = "Failed to release outer resource";

  private final Callable<Allocated<A>> allocate;

  private Resource(Callable<Allocated<A>> allocate) {
    this.allocate = allocate;
  }

  /** How the use of an acquired value ended, as its release is told. */
  private enum Exit {
    SUCCEEDED,
    FAILED
  }

  /**
   * One acquisition: the acquired value and the release for exactly that value. Each use makes its
   * own, so uses that overlap never share one.
   */
  private record Allocated<T>(T value, Consumer<Exit> release) {}

  // ==================== Factory Methods ====================

  /**
   * Creates a Resource from explicit acquire and release functions.
   *
   * <p>This is the fundamental way to create a Resource. The acquire function is called when the
   * resource is needed, and the release function is guaranteed to be called after use.
   *
   * @param <A> the type of the managed resource
   * @param acquire function to acquire the resource; must not be null
   * @param release function to release the resource; must not be null
   * @return a new Resource
   * @throws NullPointerException if acquire or release is null
   */
  public static <A> Resource<A> make(Callable<A> acquire, Consumer<A> release) {
    Validation.function().require(acquire, "acquire", CONSTRUCTION);
    Validation.function().require(release, "release", CONSTRUCTION);
    return acquiring(acquire, release);
  }

  /**
   * Creates a Resource from an AutoCloseable.
   *
   * <p>The resource's close() method is called automatically after use.
   *
   * @param <A> the type of the AutoCloseable resource
   * @param acquire function to acquire the AutoCloseable; must not be null
   * @return a new Resource that calls close() on release
   * @throws NullPointerException if acquire is null
   */
  public static <A extends AutoCloseable> Resource<A> fromAutoCloseable(Callable<A> acquire) {
    Validation.function().require(acquire, "acquire", CONSTRUCTION);
    return acquiring(
        acquire,
        resource -> {
          try {
            resource.close();
          } catch (Exception e) {
            // Silently ignore close exceptions, as is standard with try-with-resources
            // Consider logging in production code
          }
        });
  }

  /**
   * Creates a Resource that does nothing (unit resource).
   *
   * <p>Useful as an identity element for resource composition.
   *
   * @param <A> the type parameter (arbitrary since no resource is managed)
   * @param value the value to return
   * @return a Resource that returns the value without any acquire/release behaviour
   */
  public static <A> Resource<A> pure(A value) {
    Allocated<A> allocated = new Allocated<>(value, _ -> {});
    return new Resource<>(() -> allocated);
  }

  /**
   * A Resource that pairs each value {@code acquire} returns with {@code release} applied to it.
   */
  private static <A> Resource<A> acquiring(Callable<A> acquire, Consumer<? super A> release) {
    return new Resource<>(
        () -> {
          A value = acquire.call();
          return new Allocated<>(value, _ -> release.accept(value));
        });
  }

  // ==================== Core Operations ====================

  /**
   * Uses the resource with the given function, returning a VTask.
   *
   * <p>This is the primary way to use a Resource. The pattern is:
   *
   * <ol>
   *   <li>Acquire the resource
   *   <li>Apply the function to get a VTask
   *   <li>Execute the VTask
   *   <li>Release the resource, even if the VTask fails, after any {@link #onFailure} action when
   *       it does
   *   <li>Return the result (or rethrow the exception)
   * </ol>
   *
   * <p>Each run of the returned VTask acquires its own resource and releases exactly that one.
   *
   * @param <B> the type of the result
   * @param f function that uses the resource; must not be null
   * @return a VTask that manages the resource lifecycle
   * @throws NullPointerException if f is null
   */
  public <B> VTask<B> use(Function<? super A, ? extends VTask<B>> f) {
    Validation.function().require(f, "f", FLAT_MAP);

    return () -> {
      Allocated<A> allocated = allocate.call();
      @Nullable B result;
      try {
        VTask<B> task = f.apply(allocated.value());
        Objects.requireNonNull(task, "f returned null in Resource.use, which is not allowed");
        result = task.execute();
      } catch (Throwable failure) {
        allocated.release().accept(Exit.FAILED);
        throw failure;
      }
      allocated.release().accept(Exit.SUCCEEDED);
      return result;
    };
  }

  /**
   * Uses the resource with a simple function (non-effectful).
   *
   * <p>Convenience method for when the use function doesn't need to return a VTask.
   *
   * @param <B> the type of the result
   * @param f function that uses the resource; must not be null
   * @return a VTask that manages the resource lifecycle
   * @throws NullPointerException if f is null
   */
  public <B> VTask<B> useSync(Function<? super A, ? extends B> f) {
    Validation.function().require(f, "f", MAP);
    return use(a -> VTask.succeed(f.apply(a)));
  }

  // ==================== Composition ====================

  /**
   * Transforms the resource value using the given function.
   *
   * <p>The transformation is applied after acquire, and the original resource is released after
   * use. Note that the release operates on the original resource type, not the transformed type. If
   * the function throws, the original resource is released before the exception propagates.
   *
   * @param <B> the type of the transformed resource
   * @param f the transformation function; must not be null
   * @return a new Resource with transformed value
   * @throws NullPointerException if f is null
   */
  public <B> Resource<B> map(Function<? super A, ? extends B> f) {
    Validation.function().require(f, "f", MAP);

    return new Resource<>(
        () -> {
          Allocated<A> allocated = allocate.call();
          B value = whileHolding(allocated, () -> f.apply(allocated.value()));
          return new Allocated<>(value, allocated.release());
        });
  }

  /**
   * Chains resource acquisition.
   *
   * <p>The function is called with the first resource to acquire a second resource. Both resources
   * are released in reverse order (second first, then first), each by the release of the Resource
   * that acquired it. If the function or the second acquire throws, the first resource is released
   * before the exception propagates.
   *
   * @param <B> the type of the second resource
   * @param f function that creates a second resource from the first; must not be null
   * @return a new Resource that manages both resources
   * @throws NullPointerException if f is null
   */
  public <B> Resource<B> flatMap(Function<? super A, ? extends Resource<B>> f) {
    Validation.function().require(f, "f", FLAT_MAP);

    return new Resource<>(
        () -> {
          Allocated<A> outer = allocate.call();
          Allocated<B> inner =
              whileHolding(
                  outer,
                  () -> {
                    Resource<B> next = f.apply(outer.value());
                    Objects.requireNonNull(
                        next, "f returned null in Resource.flatMap, which is not allowed");
                    return next.allocate.call();
                  });
          return new Allocated<>(
              inner.value(),
              exit ->
                  releaseInTurn(exit, INNER_RELEASE_FAILED, OUTER_RELEASE_FAILED, inner, outer));
        });
  }

  /**
   * Combines this resource with another, acquiring both and releasing in reverse order.
   *
   * <p>Both resources are acquired, used together, then released in LIFO order.
   *
   * @param <B> the type of the other resource
   * @param other the other resource to combine with; must not be null
   * @return a Resource producing a tuple of both values
   * @throws NullPointerException if other is null
   */
  public <B> Resource<Par.Tuple2<A, B>> and(Resource<B> other) {
    Validation.function().require(other, "other", AP);

    return new Resource<>(
        () -> {
          Allocated<A> a = allocate.call();
          Allocated<B> b = whileHolding(a, other.allocate);
          return new Allocated<>(
              new Par.Tuple2<>(a.value(), b.value()),
              exit -> releaseInTurn(exit, RELEASE_FAILED, RELEASE_FAILED, b, a));
        });
  }

  /**
   * Combines three resources, acquiring all and releasing in reverse order.
   *
   * @param <B> the type of the second resource
   * @param <C> the type of the third resource
   * @param second the second resource; must not be null
   * @param third the third resource; must not be null
   * @return a Resource producing a tuple of all three values
   * @throws NullPointerException if second or third is null
   */
  public <B, C> Resource<Par.Tuple3<A, B, C>> and(Resource<B> second, Resource<C> third) {
    Validation.function().require(second, "second", AP);
    Validation.function().require(third, "third", AP);

    return new Resource<>(
        () -> {
          Allocated<A> a = allocate.call();
          Allocated<B> b = whileHolding(a, second.allocate);
          Allocated<C> c = whileHolding(a, () -> whileHolding(b, third.allocate));
          return new Allocated<>(
              new Par.Tuple3<>(a.value(), b.value(), c.value()),
              exit -> releaseInTurn(exit, RELEASE_FAILED, RELEASE_FAILED, c, b, a));
        });
  }

  // ==================== Finaliser Support ====================

  /**
   * Adds a finaliser that runs after the primary release.
   *
   * <p>The finaliser is guaranteed to run even if the primary release throws an exception.
   * Finalisers added one after another run in the order they were added.
   *
   * @param finalizer the finaliser to run; must not be null
   * @return a new Resource with the finaliser added
   * @throws NullPointerException if finaliser is null
   */
  public Resource<A> withFinalizer(Runnable finalizer) {
    Validation.function().require(finalizer, "finalizer", CONSTRUCTION);

    return wrapRelease(
        allocated ->
            exit -> {
              try {
                allocated.release().accept(exit);
              } finally {
                finalizer.run();
              }
            });
  }

  /**
   * Adds an action that runs, before the release, when the use of the acquired resource fails.
   *
   * <p>The use fails when the function given to {@link #use}, or the task it returns, throws. In a
   * composed Resource, a failure while the resource is held counts too: the function given to
   * {@link #map} throwing, or a resource that {@link #flatMap} or {@link #and} acquires after this
   * one failing to acquire. The action receives the acquired resource, and the release runs even if
   * the action throws. When the use succeeds, only the release runs.
   *
   * @param onFailure the action to run with the acquired resource when its use fails; must not be
   *     null
   * @return a Resource that runs the action before its release when the use fails
   * @throws NullPointerException if onFailure is null
   */
  public Resource<A> onFailure(Consumer<? super A> onFailure) {
    Validation.function().require(onFailure, "onFailure", CONSTRUCTION);

    return wrapRelease(
        allocated ->
            exit -> {
              try {
                if (exit == Exit.FAILED) {
                  onFailure.accept(allocated.value());
                }
              } finally {
                allocated.release().accept(exit);
              }
            });
  }

  // ==================== Acquisition Plumbing ====================

  /** This Resource with each acquisition's release replaced by what {@code wrap} makes of it. */
  private Resource<A> wrapRelease(Function<Allocated<A>, Consumer<Exit>> wrap) {
    return new Resource<>(
        () -> {
          Allocated<A> allocated = allocate.call();
          return new Allocated<>(allocated.value(), wrap.apply(allocated));
        });
  }

  /**
   * Runs a step that follows an acquisition. If the step throws, the held resource is released as
   * failed before the step's exception propagates, with any exception from that release suppressed
   * onto it.
   */
  private static <R> R whileHolding(Allocated<?> held, Callable<R> step) throws Exception {
    try {
      return step.call();
    } catch (Throwable failure) {
      try {
        held.release().accept(Exit.FAILED);
      } catch (Exception e) {
        failure.addSuppressed(e);
      }
      throw failure;
    }
  }

  /**
   * Releases each held resource in turn, innermost first, telling each how the use ended. Every
   * release runs even when an earlier one throws. A failure of the last release is reported with
   * {@code lastFailed}, carrying the earlier failure suppressed; otherwise an earlier failure is
   * reported with {@code earlierFailed}.
   */
  private static void releaseInTurn(
      Exit exit, String earlierFailed, String lastFailed, Allocated<?>... held) {
    @Nullable Throwable failure = null;
    for (int i = 0; i < held.length; i++) {
      try {
        held[i].release().accept(exit);
      } catch (Throwable t) {
        if (failure != null) {
          t.addSuppressed(failure);
        }
        if (i == held.length - 1) {
          throw new RuntimeException(lastFailed, t);
        }
        failure = t;
      }
    }
    if (failure != null) {
      throw new RuntimeException(earlierFailed, failure);
    }
  }
}
