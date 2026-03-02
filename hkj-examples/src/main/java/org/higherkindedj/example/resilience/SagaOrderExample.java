// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.resilience;

import java.util.function.Consumer;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.Saga;
import org.higherkindedj.hkt.resilience.SagaBuilder;
import org.higherkindedj.hkt.resilience.SagaError;
import org.higherkindedj.hkt.resilience.SagaExecutionException;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Demonstrates the Saga pattern for distributed transaction compensation using an e-commerce order
 * workflow.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Building a multi-step saga with {@link SagaBuilder}
 *   <li>Step 1: Charge payment (compensate: refund)
 *   <li>Step 2: Reserve inventory (compensate: release)
 *   <li>Step 3: Schedule shipping (compensate: cancel shipment)
 *   <li>Successful saga execution
 *   <li>Failed saga with automatic compensation in reverse order
 *   <li>Inspecting {@link SagaError} for compensation results
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.resilience.SagaOrderExample}
 */
public class SagaOrderExample {

  public static void main(String[] args) {
    System.out.println("=== Saga Order Example ===\n");

    successfulOrderSaga();
    failedOrderSagaWithCompensation();
    sagaWithSagaBuilder();
    sagaRunSafeExample();
  }

  // ===== Successful Order Saga =====

  private static void successfulOrderSaga() {
    System.out.println("--- Successful Order Saga ---");

    // Build a saga using the fluent andThen API.
    // Each step has a forward action and a compensation action.
    Saga<String> orderSaga =
        // Step 1: Charge payment -> returns a payment ID
        Saga.of(
                VTask.of(
                    () -> {
                      System.out.println("  [Step 1] Charging payment...");
                      return "PAY-12345";
                    }),
                // Compensation: refund the payment
                (Consumer<String>)
                    paymentId -> System.out.println("  [Compensate] Refunding " + paymentId))
            // Step 2: Reserve inventory -> returns a reservation ID
            .andThen(
                paymentId ->
                    Saga.of(
                        VTask.of(
                            () -> {
                              System.out.println(
                                  "  [Step 2] Reserving inventory (payment: " + paymentId + ")...");
                              return "RES-67890";
                            }),
                        (Consumer<String>)
                            reservationId ->
                                System.out.println(
                                    "  [Compensate] Releasing reservation " + reservationId)))
            // Step 3: Schedule shipping -> returns a tracking ID
            .andThen(
                reservationId ->
                    Saga.of(
                        VTask.of(
                            () -> {
                              System.out.println(
                                  "  [Step 3] Scheduling shipping (reservation: "
                                      + reservationId
                                      + ")...");
                              return "TRACK-11111";
                            }),
                        (Consumer<String>)
                            trackingId ->
                                System.out.println(
                                    "  [Compensate] Cancelling shipment " + trackingId)));

    // Execute the saga -- all steps succeed, no compensation runs
    try {
      String trackingId = orderSaga.run().run();
      System.out.println("  Order completed! Tracking ID: " + trackingId);
    } catch (RuntimeException e) {
      System.out.println("  Order failed: " + e.getMessage());
    }

    System.out.println();
  }

  // ===== Failed Order Saga with Automatic Compensation =====

  private static void failedOrderSagaWithCompensation() {
    System.out.println("--- Failed Order Saga (Shipping Fails) ---");

    // Same saga, but shipping (step 3) will fail.
    // Compensation should run in reverse: release inventory, then refund payment.
    Saga<String> failingOrderSaga =
        Saga.of(
                VTask.of(
                    () -> {
                      System.out.println("  [Step 1] Charging payment...");
                      return "PAY-99999";
                    }),
                (Consumer<String>)
                    paymentId -> System.out.println("  [Compensate] Refunding " + paymentId))
            .andThen(
                paymentId ->
                    Saga.of(
                        VTask.of(
                            () -> {
                              System.out.println("  [Step 2] Reserving inventory...");
                              return "RES-88888";
                            }),
                        (Consumer<String>)
                            reservationId ->
                                System.out.println(
                                    "  [Compensate] Releasing reservation " + reservationId)))
            .andThen(
                reservationId ->
                    Saga.of(
                        VTask.<String>of(
                            () -> {
                              System.out.println("  [Step 3] Scheduling shipping...");
                              // Simulate a failure in the shipping step
                              throw new RuntimeException(
                                  "Shipping service unavailable: no carriers in region");
                            }),
                        (Consumer<String>)
                            trackingId ->
                                System.out.println(
                                    "  [Compensate] Cancelling shipment " + trackingId)));

    // Execute -- step 3 fails, compensation runs for steps 2 and 1 in reverse order
    try {
      failingOrderSaga.run().run();
      System.out.println("  Order completed (unexpected)");
    } catch (SagaExecutionException e) {
      // This is thrown when compensation itself also fails
      SagaError error = e.sagaError();
      System.out.println("  Saga failed with compensation errors!");
      System.out.println("  Failed step: " + error.failedStep());
      System.out.println("  Original error: " + error.originalError().getMessage());
      System.out.println("  Compensation failures: " + error.compensationFailures().size());
    } catch (RuntimeException e) {
      // This is thrown when compensation succeeds (original error re-thrown)
      System.out.println("  Order failed: " + e.getMessage());
      System.out.println(
          "  All compensations ran successfully (payment refunded, inventory released).");
    }

    System.out.println();
  }

  // ===== Using SagaBuilder =====

  private static void sagaWithSagaBuilder() {
    System.out.println("--- Saga with SagaBuilder ---");

    // SagaBuilder provides named steps for better error reporting
    // and a cleaner builder pattern.
    Saga<String> orderSaga =
        SagaBuilder.<Unit>start()
            // Step 1: Charge payment (standalone action, does not depend on previous result)
            .step(
                "charge-payment",
                VTask.of(
                    () -> {
                      System.out.println("  [charge-payment] Processing $49.99...");
                      return "PAY-55555";
                    }),
                paymentId -> System.out.println("  [charge-payment] Refunding " + paymentId))
            // Step 2: Reserve inventory (depends on payment ID from previous step)
            .step(
                "reserve-inventory",
                paymentId ->
                    VTask.of(
                        () -> {
                          System.out.println(
                              "  [reserve-inventory] Reserving items for " + paymentId + "...");
                          return "RES-44444";
                        }),
                reservationId ->
                    System.out.println("  [reserve-inventory] Releasing " + reservationId))
            // Step 3: Schedule shipping (depends on reservation ID)
            .step(
                "schedule-shipping",
                reservationId ->
                    VTask.of(
                        () -> {
                          System.out.println(
                              "  [schedule-shipping] Scheduling for " + reservationId + "...");
                          return "TRACK-33333";
                        }),
                trackingId -> System.out.println("  [schedule-shipping] Cancelling " + trackingId))
            .build();

    try {
      String trackingId = orderSaga.run().run();
      System.out.println("  Order completed! Tracking: " + trackingId);
    } catch (RuntimeException e) {
      System.out.println("  Order failed: " + e.getMessage());
    }

    System.out.println();
  }

  // ===== runSafe with Either =====

  private static void sagaRunSafeExample() {
    System.out.println("--- Saga runSafe (Either Result) ---");

    // runSafe returns Either<SagaError, A> instead of throwing exceptions.
    // This allows functional-style error handling.
    Saga<String> saga =
        SagaBuilder.<Unit>start()
            .step(
                "validate-order",
                VTask.of(
                    () -> {
                      System.out.println("  [validate-order] Validating...");
                      return "ORDER-77777";
                    }),
                orderId -> System.out.println("  [validate-order] Rolling back " + orderId))
            .step(
                "charge-payment",
                orderId ->
                    VTask.<String>of(
                        () -> {
                          System.out.println("  [charge-payment] Charging for " + orderId + "...");
                          // Simulate payment failure
                          throw new RuntimeException("Insufficient funds");
                        }),
                paymentId -> System.out.println("  [charge-payment] Refunding " + paymentId))
            .build();

    // runSafe wraps the result in Either<SagaError, A>
    Either<SagaError, String> result = saga.runSafe().run();

    // Pattern match on the Either
    if (result.isRight()) {
      System.out.println("  Success: " + result.getRight());
    } else {
      SagaError error = result.getLeft();
      System.out.println("  Saga failed at step: " + error.failedStep());
      System.out.println("  Cause: " + error.originalError().getMessage());
      System.out.println("  All compensations succeeded: " + error.allCompensationsSucceeded());

      // Print individual compensation results
      for (SagaError.CompensationResult cr : error.compensationResults()) {
        String status = cr.result().isRight() ? "OK" : "FAILED";
        System.out.println("    Compensated '" + cr.stepName() + "': " + status);
      }
    }

    System.out.println();
  }
}
