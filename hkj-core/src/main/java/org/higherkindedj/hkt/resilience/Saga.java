// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;

/**
 * A saga represents a sequence of steps where each step has a corresponding compensation action. If
 * any step fails, all previously completed steps are compensated in reverse order.
 *
 * <p><b>Distinction from Resource:</b> {@link org.higherkindedj.hkt.vtask.Resource Resource}
 * manages individual resources with deterministic cleanup (close files, release connections).
 * {@code Saga} orchestrates multi-step distributed operations where compensation may involve
 * complex business logic (refund payment, restore inventory). Resource cleanup is always the same
 * action; Saga compensation depends on what was successfully completed.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Saga<String> orderSaga = Saga.of(
 *         VTask.of(() -> paymentService.charge(order)),
 *         paymentId -> paymentService.refund(paymentId))
 *     .andThen(paymentId -> Saga.of(
 *         VTask.of(() -> inventoryService.reserve(order)),
 *         reservationId -> inventoryService.release(reservationId)))
 *     .andThen(reservationId -> Saga.of(
 *         VTask.of(() -> shippingService.schedule(order)),
 *         trackingId -> shippingService.cancel(trackingId)));
 *
 * // Run the saga
 * Try<String> result = orderSaga.run().runSafe();
 * }</pre>
 *
 * @param <A> the type of the final result
 * @see SagaBuilder
 * @see SagaError
 */
public final class Saga<A> {

  /** A function that executes saga steps and records them in a tracker for compensation. */
  @FunctionalInterface
  interface SagaRunner<A> {
    A execute(List<CompletedStep<?>> tracker) throws Throwable;
  }

  /** A completed step paired with the result it produced (for compensation). */
  record CompletedStep<A>(SagaStep<A> step, A result) {}

  private final SagaRunner<A> runner;

  Saga(SagaRunner<A> runner) {
    this.runner = runner;
  }

  // ===== Factory Methods =====

  /**
   * Creates a saga with a single step and a synchronous compensation action.
   *
   * @param action the forward action
   * @param compensate compensation to run if a later step fails (receives the action's result)
   * @param <A> the result type
   * @return a new Saga
   */
  public static <A> Saga<A> of(VTask<A> action, Consumer<A> compensate) {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");
    return singleStep(SagaStep.of(action, compensate, "step-1"));
  }

  /**
   * Creates a saga with a single step and an asynchronous compensation action.
   *
   * @param action the forward action
   * @param compensate function producing a compensation VTask
   * @param <A> the result type
   * @return a new Saga
   */
  public static <A> Saga<A> of(VTask<A> action, Function<A, VTask<Unit>> compensate) {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");
    return singleStep(SagaStep.ofAsync(action, compensate, "step-1"));
  }

  /**
   * Creates a saga with a single step and no compensation.
   *
   * <p>Use this for the final step in a chain, or for idempotent operations that do not need to be
   * undone.
   *
   * @param action the forward action
   * @param <A> the result type
   * @return a new Saga
   */
  public static <A> Saga<A> noCompensation(VTask<A> action) {
    Objects.requireNonNull(action, "action must not be null");
    return singleStep(SagaStep.ofAsync(action, _ -> VTask.succeed(Unit.INSTANCE), "step-1"));
  }

  /** Creates a named saga step. Package-private, used by {@link SagaBuilder}. */
  @SuppressWarnings("unchecked")
  static <A> Saga<A> namedStep(String name, VTask<A> action, Function<A, VTask<Unit>> compensate) {
    SagaStep<A> step = SagaStep.ofAsync(action, compensate, name);
    return new Saga<>(
        tracker -> {
          try {
            A result = step.action().run();
            tracker.add(new CompletedStep<>((SagaStep<Object>) (SagaStep<?>) step, result));
            return result;
          } catch (Throwable e) {
            throw new SagaStepFailure(name, unwrap(e));
          }
        });
  }

  /** Creates a named saga step with synchronous compensation. Package-private. */
  @SuppressWarnings("unchecked")
  static <A> Saga<A> namedStepSync(String name, VTask<A> action, Consumer<A> compensate) {
    SagaStep<A> step = SagaStep.of(action, compensate, name);
    return new Saga<>(
        tracker -> {
          try {
            A result = step.action().run();
            tracker.add(new CompletedStep<>((SagaStep<Object>) (SagaStep<?>) step, result));
            return result;
          } catch (Throwable e) {
            throw new SagaStepFailure(name, unwrap(e));
          }
        });
  }

  // ===== Composition =====

  /**
   * Transforms the saga's result without adding a new step.
   *
   * @param f the mapping function
   * @param <B> the new result type
   * @return a new Saga with the transformed result
   */
  public <B> Saga<B> map(Function<A, B> f) {
    Objects.requireNonNull(f, "f must not be null");
    SagaRunner<A> selfRunner = this.runner;
    return new Saga<>(tracker -> f.apply(selfRunner.execute(tracker)));
  }

  /**
   * Chains this saga with a function that produces a new saga from the result.
   *
   * @param f function producing the next saga from this saga's result
   * @param <B> the new result type
   * @return a new Saga representing the composed sequence
   */
  public <B> Saga<B> flatMap(Function<A, Saga<B>> f) {
    Objects.requireNonNull(f, "f must not be null");
    return andThen(f);
  }

  /**
   * Chains this saga with a new saga step, passing the result of this saga to the next.
   *
   * <p>On failure in the next saga, compensation runs in reverse order across all steps of both
   * sagas. The shared completion tracker ensures that all steps, regardless of which saga they
   * belong to, are properly compensated.
   *
   * @param next function producing the next saga from this saga's result
   * @param <B> the new result type
   * @return a new Saga representing the composed sequence
   */
  public <B> Saga<B> andThen(Function<A, Saga<B>> next) {
    Objects.requireNonNull(next, "next must not be null");
    SagaRunner<A> selfRunner = this.runner;
    return new Saga<>(
        tracker -> {
          A resultA = selfRunner.execute(tracker);
          Saga<B> nextSaga = next.apply(resultA);
          Objects.requireNonNull(nextSaga, "next function returned null saga");
          return nextSaga.runner.execute(tracker);
        });
  }

  // ===== Execution =====

  /**
   * Returns a {@link VTask} that executes the saga. If any step fails, compensation runs for all
   * previously completed steps in reverse order.
   *
   * <p>If all steps succeed, the task completes with the final result. If a step fails and all
   * compensations succeed, the original exception is thrown. If compensations also fail, a {@link
   * SagaExecutionException} is thrown containing the {@link SagaError}.
   *
   * @return a VTask that executes the saga
   */
  public VTask<A> run() {
    return () -> {
      List<CompletedStep<?>> completedSteps = new ArrayList<>();

      try {
        return runner.execute(completedSteps);
      } catch (Throwable caught) {
        Throwable originalError;
        String failedStepName;
        if (caught instanceof SagaStepFailure stepFailure) {
          originalError = stepFailure.getCause();
          failedStepName = stepFailure.stepName;
        } else {
          originalError = unwrap(caught);
          failedStepName = "step-" + (completedSteps.size() + 1);
        }
        SagaError sagaError = compensate(completedSteps, originalError, failedStepName);

        if (sagaError.allCompensationsSucceeded()) {
          throw originalError;
        } else {
          throw new SagaExecutionException(sagaError);
        }
      }
    };
  }

  /**
   * Returns a {@link VTask} that executes the saga and wraps the result in an {@link Either}. The
   * left side contains a {@link SagaError} on failure; the right side contains the result on
   * success.
   *
   * @return a VTask producing Either with the saga result or error
   */
  public VTask<Either<SagaError, A>> runSafe() {
    return () -> {
      List<CompletedStep<?>> completedSteps = new ArrayList<>();

      try {
        A result = runner.execute(completedSteps);
        return Either.right(result);
      } catch (Throwable caught) {
        Throwable originalError;
        String failedStepName;
        if (caught instanceof SagaStepFailure stepFailure) {
          originalError = stepFailure.getCause();
          failedStepName = stepFailure.stepName;
        } else {
          originalError = unwrap(caught);
          failedStepName = "step-" + (completedSteps.size() + 1);
        }
        SagaError sagaError = compensate(completedSteps, originalError, failedStepName);
        return Either.left(sagaError);
      }
    };
  }

  // ===== Internal =====

  /**
   * Wraps an exception thrown by a saga step to preserve the step name for error reporting. This is
   * caught in {@link #run()} and {@link #runSafe()} to extract the actual failed step name.
   */
  private static final class SagaStepFailure extends RuntimeException {
    private final String stepName;

    SagaStepFailure(String stepName, Throwable cause) {
      super(cause);
      this.stepName = stepName;
    }
  }

  /** Unwraps a {@link VTaskExecutionException} to expose the original checked exception. */
  private static Throwable unwrap(Throwable e) {
    return (e instanceof VTaskExecutionException vte) ? vte.getCause() : e;
  }

  @SuppressWarnings("unchecked")
  private static <A> Saga<A> singleStep(SagaStep<A> step) {
    return new Saga<>(
        tracker -> {
          try {
            A result = step.action().run();
            tracker.add(new CompletedStep<>((SagaStep<Object>) (SagaStep<?>) step, result));
            return result;
          } catch (Throwable e) {
            throw new SagaStepFailure(step.name(), unwrap(e));
          }
        });
  }

  @SuppressWarnings("unchecked")
  private static SagaError compensate(
      List<CompletedStep<?>> completedSteps, Throwable originalError, String failedStep) {
    List<SagaError.CompensationResult> results = new ArrayList<>();

    // Compensate in reverse order
    List<CompletedStep<?>> reversed = new ArrayList<>(completedSteps);
    Collections.reverse(reversed);

    for (CompletedStep<?> completed : reversed) {
      CompletedStep<Object> typedStep = (CompletedStep<Object>) completed;
      try {
        VTask<Unit> compensationTask = typedStep.step().compensate().apply(typedStep.result());
        compensationTask.run();
        results.add(
            new SagaError.CompensationResult(typedStep.step().name(), Either.right(Unit.INSTANCE)));
      } catch (Throwable compensationError) {
        results.add(
            new SagaError.CompensationResult(
                typedStep.step().name(), Either.left(compensationError)));
      }
    }

    return new SagaError(originalError, failedStep, results);
  }
}
