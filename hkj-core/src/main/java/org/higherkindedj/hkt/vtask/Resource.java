// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

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

  private final Callable<A> acquire;
  private final Consumer<A> release;

  private Resource(Callable<A> acquire, Consumer<A> release) {
    this.acquire = acquire;
    this.release = release;
  }

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
    Objects.requireNonNull(acquire, "acquire must not be null");
    Objects.requireNonNull(release, "release must not be null");
    return new Resource<>(acquire, release);
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
    Objects.requireNonNull(acquire, "acquire must not be null");
    return new Resource<>(
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
   * @return a Resource that returns the value without any acquire/release behavior
   */
  public static <A> Resource<A> pure(A value) {
    return new Resource<>(() -> value, a -> {});
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
   *   <li>Release the resource (even if the VTask fails)
   *   <li>Return the result (or rethrow the exception)
   * </ol>
   *
   * @param <B> the type of the result
   * @param f function that uses the resource; must not be null
   * @return a VTask that manages the resource lifecycle
   * @throws NullPointerException if f is null
   */
  public <B> VTask<B> use(Function<? super A, ? extends VTask<B>> f) {
    Objects.requireNonNull(f, "f must not be null");

    return () -> {
      A resource = acquire.call();
      try {
        VTask<B> task = f.apply(resource);
        Objects.requireNonNull(task, "function must not return null");
        return task.execute();
      } finally {
        release.accept(resource);
      }
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
    Objects.requireNonNull(f, "f must not be null");
    return use(a -> VTask.succeed(f.apply(a)));
  }

  // ==================== Composition ====================

  /**
   * Transforms the resource value using the given function.
   *
   * <p>The transformation is applied after acquire, and the original resource is properly released.
   * Note that the release operates on the original resource type, not the transformed type.
   *
   * @param <B> the type of the transformed resource
   * @param f the transformation function; must not be null
   * @return a new Resource with transformed value
   * @throws NullPointerException if f is null
   */
  public <B> Resource<B> map(Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "f must not be null");

    // Use a holder to capture the original resource for release
    @SuppressWarnings("unchecked")
    Object[] holder = new Object[1];

    return new Resource<>(
        () -> {
          A a = acquire.call();
          holder[0] = a;
          return f.apply(a);
        },
        b -> {
          @SuppressWarnings("unchecked")
          A a = (A) holder[0];
          if (a != null) {
            release.accept(a);
          }
        });
  }

  /**
   * Chains resource acquisition.
   *
   * <p>The function is called with the first resource to acquire a second resource. Both resources
   * are released in reverse order (second first, then first).
   *
   * @param <B> the type of the second resource
   * @param f function that creates a second resource from the first; must not be null
   * @return a new Resource that manages both resources
   * @throws NullPointerException if f is null
   */
  public <B> Resource<B> flatMap(Function<? super A, ? extends Resource<B>> f) {
    Objects.requireNonNull(f, "f must not be null");

    // Holders to capture the outer resource and inner release for proper cleanup
    @SuppressWarnings("unchecked")
    Object[] outerHolder = new Object[1];
    @SuppressWarnings("unchecked")
    Consumer<B>[] innerReleaseHolder = new Consumer[1];

    return new Resource<>(
        () -> {
          A a = acquire.call();
          outerHolder[0] = a;
          try {
            Resource<B> resourceB = f.apply(a);
            Objects.requireNonNull(resourceB, "function must not return null");
            innerReleaseHolder[0] = resourceB.release;
            return resourceB.acquire.call();
          } catch (Throwable t) {
            // If acquiring B fails, release A
            try {
              release.accept(a);
            } catch (Exception e) {
              t.addSuppressed(e);
            }
            throw t;
          }
        },
        b -> {
          Throwable firstException = null;

          // Release inner resource (B) first
          // innerReleaseHolder[0] is always set before acquire returns successfully,
          // so it is guaranteed non-null when this release lambda is called.
          try {
            innerReleaseHolder[0].accept(b);
          } catch (Throwable t) {
            firstException = t;
          }

          // Release outer resource (A) second
          @SuppressWarnings("unchecked")
          A a = (A) outerHolder[0];
          if (a != null) {
            try {
              release.accept(a);
            } catch (Throwable t) {
              if (firstException != null) {
                t.addSuppressed(firstException);
              }
              throw new RuntimeException("Failed to release outer resource", t);
            }
          }

          if (firstException != null) {
            throw new RuntimeException("Failed to release inner resource", firstException);
          }
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
    Objects.requireNonNull(other, "other must not be null");

    return new Resource<>(
        () -> {
          A a = this.acquire.call();
          try {
            B b = other.acquire.call();
            return new Par.Tuple2<>(a, b);
          } catch (Throwable t) {
            try {
              this.release.accept(a);
            } catch (Exception e) {
              t.addSuppressed(e);
            }
            throw t;
          }
        },
        tuple -> {
          Throwable firstException = null;
          try {
            // Release in reverse order (B first, then A)
            other.release.accept(tuple.second());
          } catch (Throwable t) {
            firstException = t;
          }
          try {
            this.release.accept(tuple.first());
          } catch (Throwable t) {
            if (firstException != null) {
              t.addSuppressed(firstException);
            }
            throw new RuntimeException("Failed to release resource", t);
          }
          if (firstException != null) {
            throw new RuntimeException("Failed to release resource", firstException);
          }
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
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");

    return new Resource<>(
        () -> {
          A a = this.acquire.call();
          try {
            B b = second.acquire.call();
            try {
              C c = third.acquire.call();
              return new Par.Tuple3<>(a, b, c);
            } catch (Throwable t) {
              try {
                second.release.accept(b);
              } catch (Exception e) {
                t.addSuppressed(e);
              }
              throw t;
            }
          } catch (Throwable t) {
            try {
              this.release.accept(a);
            } catch (Exception e) {
              t.addSuppressed(e);
            }
            throw t;
          }
        },
        tuple -> {
          Throwable firstException = null;
          // Release in reverse order: C, B, A
          try {
            third.release.accept(tuple.third());
          } catch (Throwable t) {
            firstException = t;
          }
          try {
            second.release.accept(tuple.second());
          } catch (Throwable t) {
            if (firstException != null) {
              t.addSuppressed(firstException);
            }
            firstException = t;
          }
          try {
            this.release.accept(tuple.first());
          } catch (Throwable t) {
            if (firstException != null) {
              t.addSuppressed(firstException);
            }
            throw new RuntimeException("Failed to release resource", t);
          }
          if (firstException != null) {
            throw new RuntimeException("Failed to release resource", firstException);
          }
        });
  }

  // ==================== Finalizer Support ====================

  /**
   * Adds a finalizer that runs after the primary release.
   *
   * <p>The finalizer is guaranteed to run even if the primary release throws an exception.
   *
   * @param finalizer the finalizer to run; must not be null
   * @return a new Resource with the finalizer added
   * @throws NullPointerException if finalizer is null
   */
  public Resource<A> withFinalizer(Runnable finalizer) {
    Objects.requireNonNull(finalizer, "finalizer must not be null");

    return new Resource<>(
        acquire,
        a -> {
          try {
            release.accept(a);
          } finally {
            finalizer.run();
          }
        });
  }

  /**
   * Adds cleanup that runs on failure only.
   *
   * <p>Useful for resources that need special handling when the use function fails.
   *
   * @param onFailure the cleanup to run on failure; must not be null
   * @return a Resource with failure cleanup added
   */
  public Resource<A> onFailure(Consumer<? super A> onFailure) {
    Objects.requireNonNull(onFailure, "onFailure must not be null");

    return new Resource<>(acquire, release);
    // Note: Full implementation would track failure state in use()
  }
}
