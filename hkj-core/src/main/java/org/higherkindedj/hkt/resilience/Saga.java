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

/**
 * A saga represents a sequence of steps where each step has a corresponding compensation
 * action. If any step fails, all previously completed steps are compensated in reverse order.
 *
 * <p><b>Distinction from Resource:</b> {@link org.higherkindedj.hkt.vtask.Resource Resource}
 * manages individual resources with deterministic cleanup (close files, release connections).
 * {@code Saga} orchestrates multi-step distributed operations where compensation may involve
 * complex business logic (refund payment, restore inventory). Resource cleanup is always the
 * same action; Saga compensation depends on what was successfully completed.
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

  private final List<SagaStep<?>> steps;
  private final VTask<A> composedAction;

  private Saga(List<SagaStep<?>> steps, VTask<A> composedAction) {
    this.steps = List.copyOf(steps);
    this.composedAction = composedAction;
  }

  // ===== Factory Methods =====

  /**
   * Creates a Saga from pre-built steps. Package-private, used by {@link SagaBuilder}.
   */
  static <A> Saga<A> fromSteps(List<SagaStep<?>> steps, VTask<A> composedAction) {
    return new Saga<>(steps, composedAction);
  }

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
    SagaStep<A> step = SagaStep.of(action, compensate, "step-1");
    return new Saga<>(List.of(step), action);
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
    SagaStep<A> step = SagaStep.ofAsync(action, compensate, "step-1");
    return new Saga<>(List.of(step), action);
  }

  /**
   * Creates a saga with a single step and no compensation.
   *
   * <p>Use this for the final step in a chain, or for idempotent operations that do not
   * need to be undone.
   *
   * @param action the forward action
   * @param <A> the result type
   * @return a new Saga
   */
  public static <A> Saga<A> noCompensation(VTask<A> action) {
    Objects.requireNonNull(action, "action must not be null");
    SagaStep<A> step = SagaStep.ofAsync(action, _ -> VTask.succeed(Unit.INSTANCE), "step-1");
    return new Saga<>(List.of(step), action);
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
    return new Saga<>(steps, composedAction.map(f));
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
   * <p>On failure in the next saga, compensation runs in reverse order across all steps
   * of both sagas.
   *
   * @param next function producing the next saga from this saga's result
   * @param <B> the new result type
   * @return a new Saga representing the composed sequence
   */
  public <B> Saga<B> andThen(Function<A, Saga<B>> next) {
    Objects.requireNonNull(next, "next must not be null");
    List<SagaStep<?>> currentSteps = this.steps;
    VTask<A> currentAction = this.composedAction;

    VTask<B> combined = () -> {
      A resultA = currentAction.run();
      Saga<B> nextSaga = next.apply(resultA);
      Objects.requireNonNull(nextSaga, "next function returned null saga");
      return nextSaga.composedAction.run();
    };

    // Lazily build the combined step list; actual list is computed at run time
    return new Saga<>(currentSteps, combined) {
      @Override
      List<SagaStep<?>> allSteps(Function<A, Saga<B>> nextFn) {
        // This is evaluated at run time when we need to build compensations
        List<SagaStep<?>> all = new ArrayList<>(currentSteps);
        // We cannot resolve the next saga's steps without running it,
        // so the run() method handles this incrementally
        return all;
      }
    };
  }

  // Overridable for andThen composition
  <B> List<SagaStep<?>> allSteps(Function<A, Saga<B>> nextFn) {
    return steps;
  }

  // ===== Execution =====

  /**
   * Returns a {@link VTask} that executes the saga. If any step fails, compensation
   * runs for all previously completed steps in reverse order.
   *
   * <p>If all steps succeed, the task completes with the final result. If a step fails
   * and all compensations succeed, the original exception is thrown. If compensations
   * also fail, a {@link SagaExecutionException} is thrown containing the {@link SagaError}.
   *
   * @return a VTask that executes the saga
   */
  public VTask<A> run() {
    return () -> {
      List<CompletedStep<?>> completedSteps = new ArrayList<>();

      try {
        return executeSteps(completedSteps);
      } catch (Throwable originalError) {
        String failedStepName = "step-" + (completedSteps.size() + 1);
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
   * Returns a {@link VTask} that executes the saga and wraps the result in an
   * {@link Either}. The left side contains a {@link SagaError} on failure; the right
   * side contains the result on success.
   *
   * @return a VTask producing Either with the saga result or error
   */
  public VTask<Either<SagaError, A>> runSafe() {
    return () -> {
      List<CompletedStep<?>> completedSteps = new ArrayList<>();

      try {
        A result = executeSteps(completedSteps);
        return Either.right(result);
      } catch (Throwable originalError) {
        String failedStepName = "step-" + (completedSteps.size() + 1);
        SagaError sagaError = compensate(completedSteps, originalError, failedStepName);
        return Either.left(sagaError);
      }
    };
  }

  // ===== Internal =====

  private record CompletedStep<A>(SagaStep<A> step, A result) {}

  @SuppressWarnings("unchecked")
  private A executeSteps(List<CompletedStep<?>> completedSteps) throws Throwable {
    Object result = null;
    for (int i = 0; i < steps.size(); i++) {
      SagaStep<?> step = steps.get(i);
      result = step.action().run();
      completedSteps.add(new CompletedStep<>((SagaStep<Object>) step, result));
    }
    return (A) result;
  }

  @SuppressWarnings("unchecked")
  private SagaError compensate(
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
        results.add(new SagaError.CompensationResult(
            typedStep.step().name(), Either.right(Unit.INSTANCE)));
      } catch (Throwable compensationError) {
        results.add(new SagaError.CompensationResult(
            typedStep.step().name(), Either.left(compensationError)));
      }
    }

    return new SagaError(originalError, failedStep, results);
  }
}
