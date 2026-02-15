// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.function.Function3;

/**
 * Utility class providing parallel combinators for {@link VTask} computations.
 *
 * <p>{@code Par} provides operations for executing multiple VTasks concurrently using Java 25's
 * {@code StructuredTaskScope}. All operations in this class leverage virtual threads for
 * lightweight concurrent execution with proper cancellation semantics.
 *
 * <p><b>Key operations:</b>
 *
 * <ul>
 *   <li>{@link #zip(VTask, VTask)} - Execute two tasks in parallel, combine results
 *   <li>{@link #zip3(VTask, VTask, VTask)} - Execute three tasks in parallel, combine results
 *   <li>{@link #map2(VTask, VTask, BiFunction)} - Execute two tasks in parallel, apply function
 *   <li>{@link #race(List)} - Return the first task to complete
 *   <li>{@link #all(List)} - Wait for all tasks to complete
 *   <li>{@link #traverse(List, Function)} - Apply function to list, execute results in parallel
 * </ul>
 *
 * <p><b>Error handling:</b> Most operations use fail-fast semantics - if any task fails, the entire
 * operation fails and other tasks are cancelled. The {@link #race(List)} operation returns the
 * first successful result or fails if all tasks fail.
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * VTask<String> fetchUser = VTask.of(() -> userService.getUser(id));
 * VTask<String> fetchProfile = VTask.of(() -> profileService.getProfile(id));
 *
 * // Execute both in parallel and combine results
 * VTask<UserProfile> combined = Par.map2(
 *     fetchUser,
 *     fetchProfile,
 *     (user, profile) -> new UserProfile(user, profile)
 * );
 *
 * UserProfile result = combined.run();
 * }</pre>
 *
 * @see VTask
 * @see StructuredTaskScope
 */
public final class Par {

  private Par() {
    // Utility class - prevent instantiation
  }

  /**
   * Executes two tasks in parallel and combines their results into a tuple-like record.
   *
   * <p>Both tasks are forked simultaneously using {@code StructuredTaskScope}. If either task
   * fails, the entire operation fails and the other task is cancelled.
   *
   * @param <A> The type of the first task's result.
   * @param <B> The type of the second task's result.
   * @param taskA The first task. Must not be null.
   * @param taskB The second task. Must not be null.
   * @return A {@code VTask} that produces a {@link Tuple2} containing both results. Never null.
   * @throws NullPointerException if either task is null.
   */
  @SuppressWarnings("preview")
  public static <A, B> VTask<Tuple2<A, B>> zip(VTask<A> taskA, VTask<B> taskB) {
    Objects.requireNonNull(taskA, "taskA cannot be null");
    Objects.requireNonNull(taskB, "taskB cannot be null");

    return () -> {
      try (var scope = StructuredTaskScope.open()) {
        var subtaskA = scope.fork(taskA.asCallable());
        var subtaskB = scope.fork(taskB.asCallable());

        scope.join();

        return new Tuple2<>(subtaskA.get(), subtaskB.get());
      } catch (StructuredTaskScope.FailedException e) {
        throw e.getCause();
      }
    };
  }

  /**
   * Executes three tasks in parallel and combines their results into a tuple-like record.
   *
   * <p>All tasks are forked simultaneously using {@code StructuredTaskScope}. If any task fails,
   * the entire operation fails and the other tasks are cancelled.
   *
   * @param <A> The type of the first task's result.
   * @param <B> The type of the second task's result.
   * @param <C> The type of the third task's result.
   * @param taskA The first task. Must not be null.
   * @param taskB The second task. Must not be null.
   * @param taskC The third task. Must not be null.
   * @return A {@code VTask} that produces a {@link Tuple3} containing all results. Never null.
   * @throws NullPointerException if any task is null.
   */
  @SuppressWarnings("preview")
  public static <A, B, C> VTask<Tuple3<A, B, C>> zip3(
      VTask<A> taskA, VTask<B> taskB, VTask<C> taskC) {
    Objects.requireNonNull(taskA, "taskA cannot be null");
    Objects.requireNonNull(taskB, "taskB cannot be null");
    Objects.requireNonNull(taskC, "taskC cannot be null");

    return () -> {
      try (var scope = StructuredTaskScope.open()) {
        var subtaskA = scope.fork(taskA.asCallable());
        var subtaskB = scope.fork(taskB.asCallable());
        var subtaskC = scope.fork(taskC.asCallable());

        scope.join();

        return new Tuple3<>(subtaskA.get(), subtaskB.get(), subtaskC.get());
      } catch (StructuredTaskScope.FailedException e) {
        throw e.getCause();
      }
    };
  }

  /**
   * Executes two tasks in parallel and applies a combining function to their results.
   *
   * @param <A> The type of the first task's result.
   * @param <B> The type of the second task's result.
   * @param <R> The type of the combined result.
   * @param taskA The first task. Must not be null.
   * @param taskB The second task. Must not be null.
   * @param combiner The function to combine the results. Must not be null.
   * @return A {@code VTask} that produces the combined result. Never null.
   * @throws NullPointerException if any argument is null.
   */
  @SuppressWarnings("preview")
  public static <A, B, R> VTask<R> map2(
      VTask<A> taskA, VTask<B> taskB, BiFunction<? super A, ? super B, ? extends R> combiner) {
    Objects.requireNonNull(taskA, "taskA cannot be null");
    Objects.requireNonNull(taskB, "taskB cannot be null");
    Objects.requireNonNull(combiner, "combiner cannot be null");

    return () -> {
      try (var scope = StructuredTaskScope.open()) {
        var subtaskA = scope.fork(taskA.asCallable());
        var subtaskB = scope.fork(taskB.asCallable());

        scope.join();

        return combiner.apply(subtaskA.get(), subtaskB.get());
      } catch (StructuredTaskScope.FailedException e) {
        throw e.getCause();
      }
    };
  }

  /**
   * Executes three tasks in parallel and applies a combining function to their results.
   *
   * @param <A> The type of the first task's result.
   * @param <B> The type of the second task's result.
   * @param <C> The type of the third task's result.
   * @param <R> The type of the combined result.
   * @param taskA The first task. Must not be null.
   * @param taskB The second task. Must not be null.
   * @param taskC The third task. Must not be null.
   * @param combiner The function to combine the results. Must not be null.
   * @return A {@code VTask} that produces the combined result. Never null.
   * @throws NullPointerException if any argument is null.
   */
  @SuppressWarnings("preview")
  public static <A, B, C, R> VTask<R> map3(
      VTask<A> taskA, VTask<B> taskB, VTask<C> taskC, Function3<A, B, C, R> combiner) {
    Objects.requireNonNull(taskA, "taskA cannot be null");
    Objects.requireNonNull(taskB, "taskB cannot be null");
    Objects.requireNonNull(taskC, "taskC cannot be null");
    Objects.requireNonNull(combiner, "combiner cannot be null");

    return () -> {
      try (var scope = StructuredTaskScope.open()) {
        var subtaskA = scope.fork(taskA.asCallable());
        var subtaskB = scope.fork(taskB.asCallable());
        var subtaskC = scope.fork(taskC.asCallable());

        scope.join();

        return combiner.apply(subtaskA.get(), subtaskB.get(), subtaskC.get());
      } catch (StructuredTaskScope.FailedException e) {
        throw e.getCause();
      }
    };
  }

  /**
   * Races multiple tasks, returning the result of the first one to complete successfully.
   *
   * <p>All tasks are started in parallel. As soon as one task completes successfully, its result is
   * returned and other tasks are cancelled. If all tasks fail, the exception from the last task to
   * fail is thrown.
   *
   * @param <A> The type of the tasks' results.
   * @param tasks The list of tasks to race. Must not be null or empty.
   * @return A {@code VTask} that produces the first successful result. Never null.
   * @throws NullPointerException if {@code tasks} is null.
   * @throws IllegalArgumentException if {@code tasks} is empty.
   */
  @SuppressWarnings("preview")
  public static <A> VTask<A> race(List<VTask<A>> tasks) {
    Objects.requireNonNull(tasks, "tasks cannot be null");
    if (tasks.isEmpty()) {
      throw new IllegalArgumentException("tasks cannot be empty");
    }

    return () -> {
      try (var scope =
          StructuredTaskScope.open(StructuredTaskScope.Joiner.<A>anySuccessfulResultOrThrow())) {
        for (VTask<A> task : tasks) {
          scope.fork(task.asCallable());
        }

        return scope.join();
      } catch (StructuredTaskScope.FailedException e) {
        throw e.getCause();
      }
    };
  }

  /**
   * Executes all tasks in parallel and collects their results into a list.
   *
   * <p>All tasks are forked simultaneously. If any task fails, the entire operation fails and other
   * tasks are cancelled. Results are collected in the same order as the input tasks.
   *
   * @param <A> The type of the tasks' results.
   * @param tasks The list of tasks to execute. Must not be null.
   * @return A {@code VTask} that produces a list of all results. Never null.
   * @throws NullPointerException if {@code tasks} is null.
   */
  @SuppressWarnings("preview")
  public static <A> VTask<List<A>> all(List<VTask<A>> tasks) {
    Objects.requireNonNull(tasks, "tasks cannot be null");

    if (tasks.isEmpty()) {
      return VTask.succeed(List.of());
    }

    return () -> {
      try (var scope = StructuredTaskScope.open()) {
        List<StructuredTaskScope.Subtask<A>> subtasks = new ArrayList<>(tasks.size());

        for (VTask<A> task : tasks) {
          subtasks.add(scope.fork(task.asCallable()));
        }

        scope.join();

        List<A> results = new ArrayList<>(subtasks.size());
        for (var subtask : subtasks) {
          results.add(subtask.get());
        }
        return results;
      } catch (StructuredTaskScope.FailedException e) {
        throw e.getCause();
      }
    };
  }

  /**
   * Applies a function to each element in a list and executes the resulting tasks in parallel.
   *
   * <p>This is equivalent to mapping a function over a list to produce tasks, then executing all
   * tasks in parallel with {@link #all(List)}.
   *
   * @param <A> The type of elements in the input list.
   * @param <B> The type of results from the tasks.
   * @param items The list of items to process. Must not be null.
   * @param f The function that creates a task for each item. Must not be null.
   * @return A {@code VTask} that produces a list of all results. Never null.
   * @throws NullPointerException if any argument is null.
   */
  @SuppressWarnings("preview")
  public static <A, B> VTask<List<B>> traverse(List<A> items, Function<A, VTask<B>> f) {
    Objects.requireNonNull(items, "items cannot be null");
    Objects.requireNonNull(f, "f cannot be null");

    if (items.isEmpty()) {
      return VTask.succeed(List.of());
    }

    return () -> {
      try (var scope = StructuredTaskScope.open()) {
        List<StructuredTaskScope.Subtask<B>> subtasks = new ArrayList<>(items.size());

        for (A item : items) {
          VTask<B> task = f.apply(item);
          Objects.requireNonNull(task, "function returned null task for item: " + item);
          subtasks.add(scope.fork(task.asCallable()));
        }

        scope.join();

        List<B> results = new ArrayList<>(subtasks.size());
        for (var subtask : subtasks) {
          results.add(subtask.get());
        }
        return results;
      } catch (StructuredTaskScope.FailedException e) {
        throw e.getCause();
      }
    };
  }

  /**
   * A simple tuple type for holding two values.
   *
   * @param <A> The type of the first value.
   * @param <B> The type of the second value.
   * @param first The first value.
   * @param second The second value.
   */
  public record Tuple2<A, B>(A first, B second) {}

  /**
   * A simple tuple type for holding three values.
   *
   * @param <A> The type of the first value.
   * @param <B> The type of the second value.
   * @param <C> The type of the third value.
   * @param first The first value.
   * @param second The second value.
   * @param third The third value.
   */
  public record Tuple3<A, B, C>(A first, B second, C third) {}
}
