// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;

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
  private final Duration timeout;
  private final String name;

  private Scope(
      ScopeJoiner<T, R> joiner, List<VTask<? extends T>> tasks, Duration timeout, String name) {
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
   * <p>If the tasks don't complete within the timeout, a {@link TimeoutException} is thrown.
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
   * Sets a name for the scope (useful for debugging).
   *
   * @param name the name for this scope
   * @return a new Scope with the name configured
   */
  public Scope<T, R> named(String name) {
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
   * @param tasksToFork the tasks to fork; must not be null
   * @return a new Scope with all tasks added
   * @throws NullPointerException if tasksToFork is null
   */
  public Scope<T, R> forkAll(List<? extends VTask<? extends T>> tasksToFork) {
    Objects.requireNonNull(tasksToFork, "tasksToFork must not be null");
    List<VTask<? extends T>> newTasks = new ArrayList<>(tasks);
    newTasks.addAll(tasksToFork);
    return new Scope<>(joiner, newTasks, timeout, name);
  }

  // ==================== Join Methods ====================

  /**
   * Joins all forked tasks and returns the result as a VTask.
   *
   * <p>The returned VTask, when executed, will:
   *
   * <ol>
   *   <li>Open a StructuredTaskScope with the configured joiner
   *   <li>Fork all added tasks
   *   <li>Wait for completion according to the joiner's semantics
   *   <li>Return the joined result
   * </ol>
   *
   * @return a VTask that executes the scope and returns the result
   */
  @SuppressWarnings("preview")
  public VTask<R> join() {
    VTask<R> joinTask =
        () -> {
          try (var scope = StructuredTaskScope.open(joiner.joiner())) {
            for (VTask<? extends T> task : tasks) {
              scope.fork(task.asCallable());
            }

            // StructuredTaskScope with custom Joiner returns result directly from join()
            return scope.join();
          } catch (StructuredTaskScope.FailedException e) {
            throw e.getCause();
          }
        };

    // Apply timeout if configured
    return timeout != null ? joinTask.timeout(timeout) : joinTask;
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
}
