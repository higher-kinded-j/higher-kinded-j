// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.traversal.either;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/** A runnable example demonstrating traversing the 'Right' side of an {@link Either}. */
public class EitherTraversalExample {

  /**
   * A record representing a computation that can either succeed (Right) or fail (Left).
   * {@code @GenerateTraversals} generates optics to focus on the result field.
   */
  @GenerateTraversals
  public record Computation(String name, Either<String, Integer> result) {}

  public static void main(String[] args) {
    // 1. Get the generated traversal for the 'result' field.
    var resultTraversal = ComputationTraversals.result();

    System.out.println("--- Running Traversal Scenarios for Either ---");

    // --- Scenario 1: A successful computation (Right) ---
    var success = new Computation("Task A", Either.right(42));
    System.out.println("\nInput: " + success);

    // Use the traversal to modify the successful result. We use the Id monad for a simple update.
    var updatedSuccess =
        ID.narrow(resultTraversal.modifyF(value -> Id.of(value * 2), success, IdMonad.instance()))
            .value();

    System.out.println("Result: " + updatedSuccess);
    // Expected: Computation[name=Task A, result=Right(84)]

    // --- Scenario 2: A failed computation (Left) ---
    var failure = new Computation("Task B", Either.left("Fatal Error"));
    System.out.println("\nInput: " + failure);

    // The traversal does nothing because the Either is a Left.
    var updatedFailure =
        ID.narrow(resultTraversal.modifyF(value -> Id.of(value * 2), failure, IdMonad.instance()))
            .value();

    System.out.println("Result: " + updatedFailure);
    // Expected: Computation[name=Task B, result=Left(Fatal Error)]
  }
}
