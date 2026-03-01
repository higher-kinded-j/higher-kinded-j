// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.util.List;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;

/**
 * Represents the error state of a failed {@link Saga}, including the original error and the
 * results of compensation attempts.
 *
 * <p>When a saga step fails, all previously completed steps are compensated in reverse order.
 * Each compensation may itself succeed or fail. {@code SagaError} captures the complete picture:
 * what failed, which step it was, and what happened during compensation.
 *
 * @param originalError the exception that caused the saga to fail
 * @param failedStep the name of the step that failed
 * @param compensationResults the results of each compensation attempt
 * @see Saga
 */
public record SagaError(
    Throwable originalError,
    String failedStep,
    List<CompensationResult> compensationResults
) {

  /**
   * Represents the result of a single compensation attempt.
   *
   * @param stepName the name of the step being compensated
   * @param result {@code Either.right(Unit)} on success, {@code Either.left(Throwable)} on failure
   */
  public record CompensationResult(
      String stepName,
      Either<Throwable, Unit> result
  ) {}

  /**
   * Returns {@code true} if all compensation actions completed successfully.
   *
   * @return true if no compensation failures occurred
   */
  public boolean allCompensationsSucceeded() {
    return compensationResults.stream().allMatch(cr -> cr.result().isRight());
  }

  /**
   * Returns the list of exceptions from failed compensations.
   *
   * @return a list of compensation failure exceptions (empty if all succeeded)
   */
  public List<Throwable> compensationFailures() {
    return compensationResults.stream()
        .filter(cr -> cr.result().isLeft())
        .map(cr -> cr.result().getLeft())
        .toList();
  }
}
