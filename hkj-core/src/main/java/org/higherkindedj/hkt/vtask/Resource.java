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
import org.higherkindedj.hkt.util.Cleanup;
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
 * <h2>Failures</h2>
 *
 * <p>A Resource never holds null: an acquire, or a function given to {@link #map}, that returns
 * null fails with a {@link NullPointerException}. When a use fails and cleaning up after it fails
 * too, the first failure is the one reported, and every later one is kept among its suppressed
 * exceptions, directly or nested. The same holds between a release and the finalisers added to it.
 *
 * <p>A Resource composed with {@link #and} or {@link #flatMap} runs every one of its releases even
 * when one throws, and reports a failed release as a {@link RuntimeException} whose cause is the
 * outermost release that threw, with any earlier one suppressed onto it.
 *
 * <h2>Cancellation</h2>
 *
 * <p>When used with structured concurrency, Resource respects task cancellation. A cancelled task
 * fails, so its acquired resources are still released. Cleanup after a failed use runs with the
 * thread's interrupt status cleared, so a blocking release is not cut short, and the status is
 * restored afterwards.
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
   * One acquisition: the acquired value and the release for exactly that value. Each use of a
   * Resource that acquires something makes its own, so uses that overlap never share a release.
   */
  private record Allocated<T>(T value, Release<Exit> release) {}

  /**
   * Releases what it is given. It may throw a checked exception, as {@link AutoCloseable#close()}
   * may, which fails the use as a failure of its task does.
   */
  @FunctionalInterface
  private interface Release<T> {
    void accept(T t) throws Exception;
  }

  // ==================== Factory Methods ====================

  /**
   * Creates a Resource from explicit acquire and release functions.
   *
   * <p>This is the fundamental way to create a Resource. The acquire function is called when the
   * resource is needed, and the release function is guaranteed to be called after use.
   *
   * @param <A> the type of the managed resource
   * @param acquire function to acquire the resource; must not be null, and must not return null
   * @param release function to release the resource; must not be null
   * @return a new Resource
   * @throws NullPointerException if acquire or release is null
   */
  public static <A> Resource<A> make(Callable<A> acquire, Consumer<A> release) {
    Validation.function().require(acquire, "acquire", CONSTRUCTION);
    Validation.function().require(release, "release", CONSTRUCTION);
    return acquiring(acquire, release::accept, "make");
  }

  /**
   * Creates a Resource from an AutoCloseable.
   *
   * <p>The resource's close() method is called automatically after use. An exception from close()
   * fails the use as a failure of its task does: {@link VTask#run()} throws a checked one wrapped
   * in a {@link VTaskExecutionException}, and {@link VTask#runSafe()} holds it as close() threw it.
   * When the use has failed already, its failure is the one reported, with the exception from
   * close() suppressed onto it.
   *
   * @param <A> the type of the AutoCloseable resource
   * @param acquire function to acquire the AutoCloseable; must not be null, and must not return
   *     null
   * @return a new Resource that calls close() on release
   * @throws NullPointerException if acquire is null
   */
  public static <A extends AutoCloseable> Resource<A> fromAutoCloseable(Callable<A> acquire) {
    Validation.function().require(acquire, "acquire", CONSTRUCTION);
    return acquiring(acquire, AutoCloseable::close, "fromAutoCloseable");
  }

  /**
   * Creates a Resource that does nothing (unit resource).
   *
   * <p>Useful as an identity element for resource composition.
   *
   * @param <A> the type parameter (arbitrary since no resource is managed)
   * @param value the value to return; must not be null
   * @return a Resource that returns the value without any acquire/release behaviour
   * @throws NullPointerException if value is null
   */
  public static <A> Resource<A> pure(A value) {
    Validation.function().require(value, "value", CONSTRUCTION);
    Allocated<A> allocated = new Allocated<>(value, _ -> {});
    return new Resource<>(() -> allocated);
  }

  /**
   * A Resource that pairs each value {@code acquire} returns with {@code release} applied to it.
   */
  private static <A> Resource<A> acquiring(
      Callable<A> acquire, Release<? super A> release, String factory) {
    String returnedNull = "acquire returned null in Resource." + factory + ", which is not allowed";
    return new Resource<>(
        () -> {
          A value = acquire.call();
          Objects.requireNonNull(value, returnedNull);
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
   *   <li>Release the resource, whether the VTask succeeds or fails; on a failure, any {@link
   *       #onFailure} actions run first
   *   <li>Return the result (or rethrow the exception)
   * </ol>
   *
   * <p>Each run of the returned VTask acquires its own resource and releases exactly that one. If
   * the VTask fails and the release throws too, the VTask's failure is the one reported, with the
   * release's exception suppressed onto it.
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
        releaseAfter(failure, allocated);
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
   * the function throws, or returns null, the original resource is released before the exception
   * propagates.
   *
   * @param <B> the type of the transformed resource
   * @param f the transformation function; must not be null, and must not return null
   * @return a new Resource with transformed value
   * @throws NullPointerException if f is null
   */
  public <B> Resource<B> map(Function<? super A, ? extends B> f) {
    Validation.function().require(f, "f", MAP);

    return new Resource<>(
        () -> {
          Allocated<A> allocated = allocate.call();
          B value;
          try {
            value = f.apply(allocated.value());
            Objects.requireNonNull(value, "f returned null in Resource.map, which is not allowed");
          } catch (Throwable failure) {
            releaseAfter(failure, allocated);
            throw failure;
          }
          return new Allocated<>(value, allocated.release());
        });
  }

  /**
   * Chains resource acquisition.
   *
   * <p>The function is called with the first resource to acquire a second resource. Both resources
   * are released in reverse order (second first, then first), each by the release of the Resource
   * that acquired it. If the function or the second acquire throws, the first resource is released
   * before the exception propagates. A failed release is reported as the class documentation
   * describes.
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
          Allocated<B> inner;
          try {
            Resource<B> next = f.apply(outer.value());
            Objects.requireNonNull(
                next, "f returned null in Resource.flatMap, which is not allowed");
            inner = next.allocate.call();
          } catch (Throwable failure) {
            releaseAfter(failure, outer);
            throw failure;
          }
          return new Allocated<>(
              inner.value(),
              releaseInTurn(INNER_RELEASE_FAILED, OUTER_RELEASE_FAILED, inner, outer));
        });
  }

  /**
   * Combines this resource with another, acquiring both and releasing in reverse order.
   *
   * <p>Both resources are acquired, used together, then released in LIFO order. If acquiring the
   * other resource fails, this one is released before the exception propagates. A failed release is
   * reported as the class documentation describes.
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
          Allocated<B> b;
          try {
            b = other.allocate.call();
          } catch (Throwable failure) {
            releaseAfter(failure, a);
            throw failure;
          }
          return new Allocated<>(
              new Par.Tuple2<>(a.value(), b.value()),
              releaseInTurn(RELEASE_FAILED, RELEASE_FAILED, b, a));
        });
  }

  /**
   * Combines three resources, acquiring all and releasing in reverse order.
   *
   * <p>If acquiring one fails, those already acquired are released, in reverse order, before the
   * exception propagates.
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
          Allocated<B> b;
          try {
            b = second.allocate.call();
          } catch (Throwable failure) {
            releaseAfter(failure, a);
            throw failure;
          }
          Allocated<C> c;
          try {
            c = third.allocate.call();
          } catch (Throwable failure) {
            releaseAfter(failure, b);
            releaseAfter(failure, a);
            throw failure;
          }
          return new Allocated<>(
              new Par.Tuple3<>(a.value(), b.value(), c.value()),
              releaseInTurn(RELEASE_FAILED, RELEASE_FAILED, c, b, a));
        });
  }

  // ==================== Finaliser Support ====================

  /**
   * Adds a finaliser that runs after the primary release.
   *
   * <p>The finaliser is guaranteed to run even if the primary release throws an exception; the
   * release's exception is then the one reported, with any exception from the finaliser suppressed
   * onto it. Finalisers added one after another run in the order they were added.
   *
   * @param finalizer the finaliser to run; must not be null
   * @return a new Resource with the finaliser added
   * @throws NullPointerException if {@code finalizer} is null
   */
  public Resource<A> withFinalizer(Runnable finalizer) {
    Validation.function().require(finalizer, "finalizer", CONSTRUCTION);

    return wrapRelease(
        allocated ->
            exit -> {
              try {
                allocated.release().accept(exit);
              } catch (Throwable failure) {
                Cleanup.afterFailure(failure, finalizer::run);
                throw failure;
              }
              finalizer.run();
            });
  }

  /**
   * Adds an action that runs, before the release, when the use of the acquired resource fails.
   *
   * <p>The use fails when the function given to {@link #use} throws, or the task it returns fails.
   * A cancelled task fails too. A step composed after this one counts as well, when it fails while
   * the resource is held: the function given to {@link #map} or {@link #flatMap} throwing, or the
   * next acquire in {@link #flatMap} or {@link #and} failing. When the use succeeds, only the
   * release runs.
   *
   * <p>The action receives the acquired resource. It runs before this Resource's release and before
   * the finalisers added to it, and actions added one after another run most recently added first.
   * If the action throws, the release still runs, and the use's failure is still the one reported,
   * with the action's exception kept among its suppressed exceptions.
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
              if (exit == Exit.FAILED) {
                try {
                  onFailure.accept(allocated.value());
                } catch (Throwable failure) {
                  Cleanup.afterFailure(failure, () -> allocated.release().accept(exit));
                  throw failure;
                }
              }
              allocated.release().accept(exit);
            });
  }

  // ==================== Release Plumbing ====================

  /** This Resource with each acquisition's release replaced by what {@code wrap} makes of it. */
  private Resource<A> wrapRelease(Function<Allocated<A>, Release<Exit>> wrap) {
    return new Resource<>(
        () -> {
          Allocated<A> allocated = allocate.call();
          return new Allocated<>(allocated.value(), wrap.apply(allocated));
        });
  }

  /**
   * Releases a held resource as failed, after {@code failure}, as {@link Cleanup#afterFailure} runs
   * cleanup.
   */
  private static void releaseAfter(Throwable failure, Allocated<?> held) {
    Cleanup.afterFailure(failure, () -> held.release().accept(Exit.FAILED));
  }

  /**
   * A release of each held resource in turn, innermost first, telling each how the use ended. Every
   * release runs even when an earlier one throws. A failure of the last release is reported with
   * {@code lastFailed}, carrying the earlier failure suppressed; otherwise an earlier failure is
   * reported with {@code earlierFailed}. The loop is the release itself, so a deep composition
   * releases with one stack frame per level.
   */
  private static Release<Exit> releaseInTurn(
      String earlierFailed, String lastFailed, Allocated<?>... held) {
    return exit -> {
      @Nullable Throwable failure = null;
      for (int i = 0; i < held.length; i++) {
        try {
          held[i].release().accept(exit);
        } catch (Throwable t) {
          if (failure != null) {
            Cleanup.suppress(t, failure);
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
    };
  }
}
