// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Represents a lazy computation that, when executed, runs on a Java virtual thread and produces a
 * value of type {@code A}. {@code VTask} is the primary effect type for virtual thread-based
 * concurrency in Higher-Kinded-J.
 *
 * <p>A {@code VTask<A>} instance does not perform any action when it's created. Instead, it acts as
 * a description or "recipe" for a computation that will be executed only when explicitly run via
 * {@link #run()}, {@link #runSafe()}, or {@link #runAsync()}. This deferred execution allows for
 * referential transparency and enables building complex concurrent programs that remain testable
 * and composable.
 *
 * <p><b>Key Characteristics:</b>
 *
 * <ul>
 *   <li><b>Laziness:</b> Effects are not executed upon creation of a {@code VTask} value, but only
 *       when explicitly run.
 *   <li><b>Virtual Threads:</b> Computations execute on virtual threads, enabling millions of
 *       concurrent tasks with minimal memory overhead.
 *   <li><b>Structured Concurrency:</b> Uses Java 25's {@code StructuredTaskScope} for proper
 *       cancellation and error propagation.
 *   <li><b>Composability:</b> {@code VTask} operations can be easily chained using {@link
 *       #map(Function)}, {@link #flatMap(Function)}, and other combinators.
 *   <li><b>HKT Integration:</b> {@code VTask<A>} directly extends {@link VTaskKind VTaskKind<A>},
 *       making it a first-class participant in the Higher-Kinded-J HKT simulation.
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * // Describe a computation that fetches data
 * VTask<String> fetchData = VTask.of(() -> {
 *     // This runs on a virtual thread
 *     return httpClient.get("https://api.example.com/data");
 * });
 *
 * // Chain computations
 * VTask<Integer> processData = fetchData
 *     .map(String::length)
 *     .flatMap(len -> VTask.of(() -> processLength(len)));
 *
 * // Nothing has executed yet. To run:
 * Try<Integer> result = processData.runSafe();
 * }</pre>
 *
 * @param <A> The type of the value produced by the computation when executed.
 * @see VTaskKind
 * @see Par
 */
@FunctionalInterface
public interface VTask<A> extends VTaskKind<A> {

  /**
   * The core operation representing the computation. This method is invoked internally when the
   * task is executed. Implementations should not call this directly; use {@link #run()}, {@link
   * #runSafe()}, or {@link #runAsync()} instead.
   *
   * <p><b>Note:</b> This method declares {@code throws Throwable} to allow lambda implementations
   * to throw checked exceptions. The public execution methods ({@link #run()}, {@link #runSafe()},
   * {@link #runAsync()}) handle exception translation at the boundary.
   *
   * @return The result of the computation of type {@code A}.
   * @throws Throwable If the computation fails.
   */
  @Nullable A execute() throws Throwable;

  /**
   * Returns this VTask as a {@link Callable} for use with {@code StructuredTaskScope.fork()}.
   *
   * <p>This method wraps the {@link #execute()} method to handle the exception type mismatch
   * between {@code execute()} (which throws {@code Throwable}) and {@code Callable.call()} (which
   * throws {@code Exception}).
   *
   * @return A {@link Callable} that invokes this task's execute method. Never null.
   */
  default Callable<A> asCallable() {
    return () -> {
      try {
        return execute();
      } catch (Exception e) {
        throw e;
      } catch (Throwable t) {
        // Error is unchecked and can be thrown directly
        if (t instanceof Error) throw (Error) t;
        // Wrap any other Throwable (rare) in RuntimeException
        throw new RuntimeException(t);
      }
    };
  }

  // ===== FACTORY METHODS =====

  /**
   * Creates a {@code VTask<A>} from a {@link Callable}. The callable will be executed on a virtual
   * thread when the task is run.
   *
   * @param callable The computation to execute. Must not be null.
   * @param <A> The type of the value produced by the callable.
   * @return A new {@code VTask<A>} representing the deferred computation. Never null.
   * @throws NullPointerException if {@code callable} is null.
   */
  static <A> VTask<A> of(Callable<A> callable) {
    Objects.requireNonNull(callable, "callable cannot be null");
    return callable::call;
  }

  /**
   * Creates a {@code VTask<A>} that defers a computation described by the given {@link Supplier}.
   * This is the primary way to lift an arbitrary block of code into a {@code VTask} context.
   *
   * @param thunk A {@link Supplier} that produces a value of type {@code A}. Must not be null.
   * @param <A> The type of the value produced.
   * @return A new {@code VTask<A>} representing the deferred computation. Never null.
   * @throws NullPointerException if {@code thunk} is null.
   */
  static <A> VTask<A> delay(Supplier<A> thunk) {
    Validation.function().requireFunction(thunk, "thunk", VTask.class, DELAY);
    return thunk::get;
  }

  /**
   * Creates a {@code VTask<A>} that immediately succeeds with the given value when executed. This
   * is the "pure" or "return" operation for the VTask monad.
   *
   * @param value The value to wrap. Can be {@code null}.
   * @param <A> The type of the value.
   * @return A new {@code VTask<A>} that succeeds with the given value. Never null.
   */
  static <A> VTask<A> succeed(@Nullable A value) {
    return () -> value;
  }

  /**
   * Creates a {@code VTask<A>} that immediately fails with the given throwable when executed.
   *
   * @param error The throwable representing the failure. Must not be null.
   * @param <A> The phantom type parameter of the value.
   * @return A new {@code VTask<A>} that fails with the given error. Never null.
   * @throws NullPointerException if {@code error} is null.
   */
  static <A> VTask<A> fail(Throwable error) {
    Validation.coreType().requireError(error, VTask.class, RAISE_ERROR);
    return () -> {
      throw error;
    };
  }

  /**
   * Creates a {@code VTask<Unit>} from a {@link Runnable}. The task will execute the runnable and
   * return {@link Unit#INSTANCE}.
   *
   * @param runnable The side effect to execute. Must not be null.
   * @return A new {@code VTask<Unit>} that executes the runnable. Never null.
   * @throws NullPointerException if {@code runnable} is null.
   */
  static VTask<Unit> exec(Runnable runnable) {
    Objects.requireNonNull(runnable, "runnable cannot be null");
    return () -> {
      runnable.run();
      return Unit.INSTANCE;
    };
  }

  /**
   * Creates a {@code VTask<A>} that explicitly marks the computation as blocking. This is a hint
   * that the operation may block for I/O or other external resources.
   *
   * <p>In virtual thread contexts, blocking operations automatically unmount from the carrier
   * thread, so this marker is primarily for documentation and potential future optimisations.
   *
   * @param callable The blocking computation. Must not be null.
   * @param <A> The type of the value produced.
   * @return A new {@code VTask<A>} representing the blocking computation. Never null.
   * @throws NullPointerException if {@code callable} is null.
   */
  static <A> VTask<A> blocking(Callable<A> callable) {
    Objects.requireNonNull(callable, "callable cannot be null");
    // Virtual threads handle blocking automatically, but we mark it for clarity
    return callable::call;
  }

  // ===== EXECUTION METHODS =====

  /**
   * Executes this {@code VTask} synchronously, blocking until completion.
   *
   * <p>This method executes the computation on the current thread. For asynchronous execution on a
   * virtual thread, use {@link #runAsync()}. For parallel execution of multiple tasks, use the
   * combinators in {@link Par}.
   *
   * <p><b>Exception handling:</b> Unchecked exceptions ({@link RuntimeException} and {@link Error})
   * are thrown directly. Checked exceptions are wrapped in {@link VTaskExecutionException}. For
   * functional error handling that preserves the original exception type, use {@link #runSafe()}
   * instead.
   *
   * @return The result of the computation of type {@code A}.
   * @throws VTaskExecutionException if the computation throws a checked exception
   * @throws RuntimeException if the computation throws an unchecked exception
   */
  default @Nullable A run() {
    try {
      return execute();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new VTaskExecutionException(t);
    }
  }

  /**
   * Executes this {@code VTask}, returning the result as a {@link Try}.
   *
   * <p>This is the preferred method for error handling. Unlike {@link #run()}, which wraps checked
   * exceptions in {@link VTaskExecutionException}, this method preserves the original exception
   * type in the {@code Try.Failure}.
   *
   * @return A {@link Try} containing either the successful result or the failure. Never null.
   */
  default Try<A> runSafe() {
    try {
      return Try.success(execute());
    } catch (Throwable t) {
      return Try.failure(t);
    }
  }

  /**
   * Executes this {@code VTask} asynchronously on a virtual thread, returning a {@link
   * CompletableFuture}.
   *
   * <p>The computation starts immediately on a virtual thread. The returned future can be used to
   * wait for the result or combine with other asynchronous operations.
   *
   * @return A {@link CompletableFuture} that will complete with the result. Never null.
   */
  default CompletableFuture<A> runAsync() {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return execute();
          } catch (Throwable t) {
            throw new CompletionException(t);
          }
        },
        r -> Thread.ofVirtual().start(r));
  }

  // ===== COMPOSITION METHODS =====

  /**
   * Transforms the result of this {@code VTask} using the provided mapping function, without
   * altering its effectful nature. This is the Functor {@code map} operation for {@code VTask}.
   *
   * @param f A function to apply to the result. Must not be null.
   * @param <B> The type of the transformed value.
   * @return A new {@code VTask<B>} that applies the function to the result. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> VTask<B> map(Function<? super A, ? extends B> f) {
    Validation.function().requireMapper(f, "f", VTask.class, MAP);
    return () -> f.apply(this.execute());
  }

  /**
   * Composes this {@code VTask} with another {@code VTask}-producing function. This is the Monad
   * {@code flatMap} (or {@code bind}) operation for {@code VTask}.
   *
   * <p>First, this task is executed to get a value. Then, the function {@code f} is applied to that
   * value to get a new {@code VTask}, which is then executed to get the final result.
   *
   * @param f A function that produces a new {@code VTask} from the result. Must not be null.
   * @param <B> The type of the value produced by the resulting task.
   * @return A new {@code VTask<B>} representing the composed computation. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> VTask<B> flatMap(Function<? super A, ? extends VTask<B>> f) {
    Validation.function().requireFlatMapper(f, "f", VTask.class, FLAT_MAP);
    return () -> {
      A a = this.execute();
      VTask<B> next = f.apply(a);
      Validation.function().requireNonNullResult(next, "f", VTask.class, FLAT_MAP, VTask.class);
      return next.execute();
    };
  }

  /**
   * Alias for {@link #flatMap(Function)}. Chains this task with another task-producing function.
   *
   * @param f A function that produces a new {@code VTask} from the result. Must not be null.
   * @param <B> The type of the value produced by the resulting task.
   * @return A new {@code VTask<B>} representing the composed computation. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default <B> VTask<B> via(Function<? super A, ? extends VTask<B>> f) {
    return flatMap(f);
  }

  /**
   * Sequences this task with another task, discarding the result of this task.
   *
   * @param next A supplier that produces the next task. Must not be null.
   * @param <B> The type of the value produced by the next task.
   * @return A new {@code VTask<B>} that executes both tasks in sequence. Never null.
   * @throws NullPointerException if {@code next} is null.
   */
  default <B> VTask<B> then(Supplier<? extends VTask<B>> next) {
    Objects.requireNonNull(next, "next cannot be null");
    return () -> {
      this.execute(); // Execute and discard result
      VTask<B> nextTask = next.get();
      Objects.requireNonNull(nextTask, "next supplier returned null");
      return nextTask.execute();
    };
  }

  /**
   * Performs a side-effect action on the successful result without modifying it.
   *
   * @param action The action to perform on the result. Must not be null.
   * @return A new {@code VTask<A>} that performs the action and returns the original value. Never
   *     null.
   * @throws NullPointerException if {@code action} is null.
   */
  default VTask<A> peek(Consumer<? super A> action) {
    Objects.requireNonNull(action, "action cannot be null");
    return () -> {
      A result = this.execute();
      action.accept(result);
      return result;
    };
  }

  // ===== TIMEOUT AND CANCELLATION =====

  /**
   * Creates a new task that fails if this task does not complete within the specified duration.
   *
   * @param duration The maximum time to wait. Must not be null.
   * @return A new {@code VTask<A>} with timeout behaviour. Never null.
   * @throws NullPointerException if {@code duration} is null.
   */
  default VTask<A> timeout(Duration duration) {
    Objects.requireNonNull(duration, "duration cannot be null");
    return () -> {
      try {
        return runAsync().orTimeout(duration.toMillis(), TimeUnit.MILLISECONDS).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof TimeoutException) {
          throw new TimeoutException("VTask timed out after " + duration);
        }
        throw e.getCause();
      }
    };
  }

  // ===== ERROR HANDLING =====

  /**
   * Recovers from a failure by applying the given function to the exception.
   *
   * @param recoveryFunction A function that produces a recovery value from the exception. Must not
   *     be null.
   * @return A new {@code VTask<A>} that recovers from failures. Never null.
   * @throws NullPointerException if {@code recoveryFunction} is null.
   */
  default VTask<A> recover(Function<? super Throwable, ? extends A> recoveryFunction) {
    Validation.function()
        .requireFunction(recoveryFunction, "recoveryFunction", VTask.class, RECOVER);
    return () -> {
      try {
        return this.execute();
      } catch (Throwable t) {
        return recoveryFunction.apply(t);
      }
    };
  }

  /**
   * Recovers from a failure by applying the given function to produce a new task.
   *
   * @param recoveryFunction A function that produces a recovery task from the exception. Must not
   *     be null.
   * @return A new {@code VTask<A>} that recovers from failures. Never null.
   * @throws NullPointerException if {@code recoveryFunction} is null.
   */
  default VTask<A> recoverWith(Function<? super Throwable, ? extends VTask<A>> recoveryFunction) {
    Validation.function()
        .requireFunction(recoveryFunction, "recoveryFunction", VTask.class, RECOVER_WITH);
    return () -> {
      try {
        return this.execute();
      } catch (Throwable t) {
        VTask<A> recovery = recoveryFunction.apply(t);
        Validation.function()
            .requireNonNullResult(
                recovery, "recoveryFunction", VTask.class, RECOVER_WITH, VTask.class);
        return recovery.execute();
      }
    };
  }

  /**
   * Transforms the error of a failed task using the given function.
   *
   * @param f A function that transforms the exception. Must not be null.
   * @return A new {@code VTask<A>} with the transformed error. Never null.
   * @throws NullPointerException if {@code f} is null.
   */
  default VTask<A> mapError(Function<? super Throwable, ? extends Throwable> f) {
    Validation.function().requireMapper(f, "f", VTask.class, MAP_ERROR);
    return () -> {
      try {
        return this.execute();
      } catch (Throwable t) {
        throw f.apply(t);
      }
    };
  }

  /**
   * Discards the result of this task, replacing it with {@link Unit}.
   *
   * @return A new {@code VTask<Unit>} that performs the same effect but returns {@link
   *     Unit#INSTANCE}. Never null.
   */
  default VTask<Unit> asUnit() {
    return this.map(_ -> Unit.INSTANCE);
  }
}
