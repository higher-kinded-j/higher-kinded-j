// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.jspecify.annotations.Nullable;

/**
 * A fluent builder for structured concurrent computations using Java 25's {@link
 * StructuredTaskScope}.
 *
 * <p>Scope provides a functional interface for structured concurrency, wrapping Java 25's preview
 * APIs with HKJ's effect types. It enables forking multiple subtasks, joining their results, and
 * handling errors functionally.
 *
 * <h2>Basic Usage</h2>
 *
 * <pre>{@code
 * // Fork multiple tasks and wait for all to succeed
 * VTask<List<String>> result = Scope.<String>allSucceed()
 *     .fork(VTask.of(() -> fetchUser(id)))
 *     .fork(VTask.of(() -> fetchProfile(id)))
 *     .join();
 *
 * // Race tasks - first to succeed wins
 * VTask<String> fastest = Scope.<String>anySucceed()
 *     .fork(fetchFromServerA())
 *     .fork(fetchFromServerB())
 *     .join();
 *
 * // Accumulate all errors (doesn't fail-fast)
 * VTask<Validated<List<Error>, List<User>>> validated = Scope.<User>accumulating(Error::from)
 *     .fork(validateUser(user1))
 *     .fork(validateUser(user2))
 *     .fork(validateUser(user3))
 *     .join();
 * }</pre>
 *
 * <h2>Timeout Support</h2>
 *
 * <pre>{@code
 * VTask<List<String>> withTimeout = Scope.<String>allSucceed()
 *     .timeout(Duration.ofSeconds(5))
 *     .fork(slowTask1())
 *     .fork(slowTask2())
 *     .join();
 * }</pre>
 *
 * <h2>Running a Scope Again</h2>
 *
 * <p>A {@code Scope} is a description. Each run of the {@link VTask} that {@link #join()} returns
 * opens a {@code StructuredTaskScope} of its own, with a new {@code Joiner} from the {@link
 * ScopeJoiner}. So a {@code Scope} can be run again, retried, or run from several threads at once,
 * and each run joins only the subtasks it forked.
 *
 * <h2>Preview API Notice</h2>
 *
 * <p><b>Note:</b> This class uses Java 25's structured concurrency APIs which are currently in
 * preview (JEP 505/525). The underlying API may change in future Java releases.
 *
 * @param <T> the type of values produced by subtasks
 * @param <R> the type of the final result after joining
 * @see StructuredTaskScope
 * @see ScopeJoiner
 * @see VTask
 */
public final class Scope<T, R> {

  private final ScopeJoiner<T, R> joiner;
  private final List<VTask<? extends T>> tasks;
  private final @Nullable Duration timeout;
  private final @Nullable String name;

  private Scope(
      ScopeJoiner<T, R> joiner,
      List<VTask<? extends T>> tasks,
      @Nullable Duration timeout,
      @Nullable String name) {
    this.joiner = joiner;
    this.tasks = tasks;
    this.timeout = timeout;
    this.name = name;
  }

  // ==================== Factory Methods ====================

  /**
   * Creates a scope that waits for all subtasks to succeed.
   *
   * <p>If any subtask fails, the entire operation fails with that exception and remaining tasks are
   * cancelled.
   *
   * @param <T> the type of values produced by subtasks
   * @return a new Scope builder configured for all-succeed semantics
   */
  public static <T> Scope<T, List<T>> allSucceed() {
    return new Scope<>(ScopeJoiner.allSucceed(), new ArrayList<>(), null, null);
  }

  /**
   * Creates a scope that returns the first successful result.
   *
   * <p>As soon as any subtask succeeds, its result is returned and other tasks are cancelled.
   *
   * @param <T> the type of values produced by subtasks
   * @return a new Scope builder configured for any-succeed semantics
   */
  public static <T> Scope<T, T> anySucceed() {
    return new Scope<>(ScopeJoiner.anySucceed(), new ArrayList<>(), null, null);
  }

  /**
   * Creates a scope that returns the first completed result (success or failure).
   *
   * <p>As soon as one subtask completes, the others are cancelled: those running are interrupted,
   * and those not yet started never start. The run returns once they have stopped.
   *
   * @param <T> the type of values produced by subtasks
   * @return a new Scope builder configured for first-complete semantics
   */
  public static <T> Scope<T, T> firstComplete() {
    return new Scope<>(ScopeJoiner.firstComplete(), new ArrayList<>(), null, null);
  }

  /**
   * Creates a scope that accumulates errors using {@link Validated}.
   *
   * <p>Unlike fail-fast scopes, this waits for all tasks to complete and collects both successes
   * and failures.
   *
   * @param <E> the error type after mapping
   * @param <T> the type of values produced by subtasks
   * @param errorMapper function to convert exceptions to error type E; must not be null
   * @return a new Scope builder configured for error accumulation
   * @throws NullPointerException if errorMapper is null
   */
  public static <E, T> Scope<T, Validated<List<E>, List<T>>> accumulating(
      Function<Throwable, E> errorMapper) {
    Objects.requireNonNull(errorMapper, "errorMapper must not be null");
    return new Scope<>(ScopeJoiner.accumulating(errorMapper), new ArrayList<>(), null, null);
  }

  /**
   * Creates a scope with a custom joiner.
   *
   * @param <T> the type of values produced by subtasks
   * @param <R> the type of the final result after joining
   * @param joiner the custom joiner to use; must not be null
   * @return a new Scope builder with the custom joiner
   * @throws NullPointerException if joiner is null
   */
  public static <T, R> Scope<T, R> withJoiner(ScopeJoiner<T, R> joiner) {
    Objects.requireNonNull(joiner, "joiner must not be null");
    return new Scope<>(joiner, new ArrayList<>(), null, null);
  }

  // ==================== Configuration Methods ====================

  /**
   * Sets a timeout for the scope.
   *
   * <p>Each run's timeout starts when it opens its scope. When it expires, unless the joiner has
   * decided the result first, the scope is cancelled: the subtasks still running are interrupted,
   * those not yet started never start, and the run fails with a {@link TimeoutException} once they
   * have stopped. A subtask that ignores interruption holds the run back until it finishes, whether
   * the timeout or the joiner cancelled the scope. A timeout of zero or less expires at once.
   *
   * <p>Two timers keep the timeout: the JDK's, which fires on a thread of the common {@code
   * ForkJoinPool}, and one more subtask of the run's scope, which sleeps on a virtual thread. The
   * first to fire cancels the scope, so the timeout is late only while every thread of the common
   * pool and every virtual-thread carrier are busy at once.
   *
   * @param timeout the maximum time to wait; must not be null
   * @return a new Scope with the timeout configured
   * @throws NullPointerException if timeout is null
   */
  public Scope<T, R> timeout(Duration timeout) {
    Objects.requireNonNull(timeout, "timeout must not be null");
    return new Scope<>(joiner, tasks, timeout, name);
  }

  /**
   * Sets a name for the scope, for monitoring and debugging. A thread dump in JSON format, such as
   * {@code jcmd <pid> Thread.dump_to_file -format=json <file>} writes, shows it.
   *
   * @param name the name for this scope; must not be null
   * @return a new Scope with the name configured
   * @throws NullPointerException if name is null
   */
  public Scope<T, R> named(String name) {
    Objects.requireNonNull(name, "name must not be null");
    return new Scope<>(joiner, tasks, timeout, name);
  }

  // ==================== Fork Methods ====================

  /**
   * Forks a VTask to run as a subtask within this scope.
   *
   * @param task the task to fork; must not be null
   * @return a new Scope with the task added
   * @throws NullPointerException if task is null
   */
  public Scope<T, R> fork(VTask<? extends T> task) {
    Objects.requireNonNull(task, "task must not be null");
    List<VTask<? extends T>> newTasks = new ArrayList<>(tasks);
    newTasks.add(task);
    return new Scope<>(joiner, newTasks, timeout, name);
  }

  /**
   * Forks multiple VTasks to run as subtasks within this scope.
   *
   * @param tasksToFork the tasks to fork; must not be null or contain nulls
   * @return a new Scope with all tasks added
   * @throws NullPointerException if tasksToFork is null or contains null
   */
  public Scope<T, R> forkAll(List<? extends VTask<? extends T>> tasksToFork) {
    Objects.requireNonNull(tasksToFork, "tasksToFork must not be null");
    List<VTask<? extends T>> newTasks = new ArrayList<>(tasks);
    for (VTask<? extends T> task : tasksToFork) {
      newTasks.add(Objects.requireNonNull(task, "tasksToFork must not contain null"));
    }
    return new Scope<>(joiner, newTasks, timeout, name);
  }

  // ==================== Join Methods ====================

  /**
   * Joins all forked tasks and returns the result as a VTask.
   *
   * <p>Each run of the returned VTask will:
   *
   * <ol>
   *   <li>Open a StructuredTaskScope with a new Joiner from the configured ScopeJoiner, and the
   *       configured timeout and name
   *   <li>Fork all added tasks, then a subtask that also keeps the timeout, if one is configured
   *   <li>Wait for completion according to the joiner's semantics
   *   <li>Return the joined result
   * </ol>
   *
   * <p>The scope is opened on the thread that runs the VTask, so its subtasks see that thread's
   * {@link ScopedValue} bindings.
   *
   * @return a VTask that executes the scope and returns the result
   */
  @SuppressWarnings("preview")
  public VTask<R> join() {
    return () -> {
      StructuredTaskScope.Joiner<T, R> joining = joiner.joiner();
      @Nullable Deadline<T, R> deadline = timeout == null ? null : new Deadline<>(joining, timeout);
      try (var scope =
          StructuredTaskScope.open(deadline == null ? joining : deadline, this::configure)) {
        for (VTask<? extends T> task : tasks) {
          scope.fork(task.asCallable());
        }
        if (deadline != null) {
          deadline.fork(scope);
        }

        // StructuredTaskScope with custom Joiner returns result directly from join()
        return scope.join();
      } catch (StructuredTaskScope.FailedException e) {
        throw e.getCause();
      } catch (StructuredTaskScope.TimeoutException e) {
        TimeoutException timedOut = new TimeoutException("Scope timed out after " + timeout);
        timedOut.initCause(e);
        throw timedOut;
      }
    };
  }

  @SuppressWarnings("preview")
  private StructuredTaskScope.Configuration configure(StructuredTaskScope.Configuration config) {
    StructuredTaskScope.Configuration configured = config;
    if (timeout != null) {
      configured = configured.withTimeout(timeout);
    }
    if (name != null) {
      configured = configured.withName(name);
    }
    return configured;
  }

  /**
   * Joins all forked tasks and returns the result wrapped in a {@link Try}.
   *
   * @return a VTask that executes the scope and returns a Try containing the result or exception
   */
  public VTask<Try<R>> joinSafe() {
    return join().map(Try::success).recover(Try::failure);
  }

  /**
   * Joins all forked tasks and returns the result wrapped in an {@link Either}.
   *
   * @return a VTask that executes the scope and returns Either.right(result) or
   *     Either.left(exception)
   */
  public VTask<Either<Throwable, R>> joinEither() {
    return join().map(Either::<Throwable, R>right).recover(Either::left);
  }

  /**
   * Joins all forked tasks and returns the result wrapped in a {@link Maybe}.
   *
   * <p>Returns {@code Maybe.just(result)} on success, {@code Maybe.nothing()} on failure.
   *
   * @return a VTask that executes the scope and returns a Maybe
   */
  public VTask<Maybe<R>> joinMaybe() {
    return join().map(Maybe::just).recover(e -> Maybe.nothing());
  }

  // ==================== Utility Methods ====================

  /**
   * Returns the number of tasks currently forked in this scope.
   *
   * @return the number of forked tasks
   */
  public int taskCount() {
    return tasks.size();
  }

  /**
   * Returns whether this scope has a timeout configured.
   *
   * @return true if a timeout is set
   */
  public boolean hasTimeout() {
    return timeout != null;
  }

  /**
   * Returns the configured timeout, if any.
   *
   * @return Maybe containing the timeout, or nothing if not set
   */
  public Maybe<Duration> getTimeout() {
    return timeout != null ? Maybe.just(timeout) : Maybe.nothing();
  }

  /**
   * Keeps a run's timeout as one more subtask of its scope, on a virtual thread, so the timeout
   * fires even when every thread of the common {@code ForkJoinPool}, where the JDK's own timer
   * fires, is blocked. The joiner it wraps never sees the deadline subtask.
   *
   * @param <T> the type of values produced by subtasks
   * @param <R> the type of the final result after joining
   */
  @SuppressWarnings("preview")
  private static final class Deadline<T, R> implements StructuredTaskScope.Joiner<T, R> {

    private final StructuredTaskScope.Joiner<T, R> joining;
    private final Duration timeout;
    // One for each subtask still running, and one held until the deadline is forked
    private final AtomicInteger outstanding = new AtomicInteger(1);
    // Set and read only on the thread that owns the scope
    private boolean forkingDeadline;
    private volatile StructuredTaskScope.@Nullable Subtask<? extends T> deadline;
    private volatile boolean expired;

    Deadline(StructuredTaskScope.Joiner<T, R> joining, Duration timeout) {
      this.joining = joining;
      this.timeout = timeout;
    }

    void fork(StructuredTaskScope<T, R> scope) {
      forkingDeadline = true;
      scope.fork(
          () -> {
            Thread.sleep(timeout);
            return null;
          });
    }

    @Override
    public boolean onFork(StructuredTaskScope.Subtask<? extends T> subtask) {
      if (forkingDeadline) {
        deadline = subtask;
        // When every subtask has already completed, cancel, so the deadline never starts
        return outstanding.decrementAndGet() == 0;
      }
      outstanding.incrementAndGet();
      return joining.onFork(subtask);
    }

    @Override
    public boolean onComplete(StructuredTaskScope.Subtask<? extends T> subtask) {
      if (subtask == deadline) {
        expired = true;
        return true;
      }
      boolean cancel = decide(subtask);
      // The last subtask to complete cancels the deadline
      return outstanding.decrementAndGet() == 0 || cancel;
    }

    // A joiner that throws is reported as the JDK reports it, and still counts the subtask, so
    // the run does not wait out its timeout
    private boolean decide(StructuredTaskScope.Subtask<? extends T> subtask) {
      try {
        return joining.onComplete(subtask);
      } catch (Throwable t) {
        Thread.currentThread()
            .getUncaughtExceptionHandler()
            .uncaughtException(Thread.currentThread(), t);
        return false;
      }
    }

    @Override
    public R result() throws Throwable {
      if (expired) {
        throw new TimeoutException("Scope timed out after " + timeout);
      }
      return joining.result();
    }
  }
}
