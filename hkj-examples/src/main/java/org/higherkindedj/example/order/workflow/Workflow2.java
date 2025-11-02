// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.example.order.model.WorkflowModels;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;

/**
 * Implements an order processing workflow using {@link EitherT} over {@link CompletableFuture}.
 * This version of the workflow specifically uses a {@link org.higherkindedj.hkt.trymonad.Try} for
 * its initial validation step, which is then converted to an {@link Either} to fit into the {@code
 * EitherT} pipeline.
 *
 * <p>The workflow processes an order through several stages:
 *
 * <ol>
 *   <li>Validation (using Try): Validates the initial order data. The Try result is converted to
 *       Either.
 *   <li>Inventory Check: Checks if the product is in stock. This step now results in {@link Unit}
 *       on success.
 *   <li>Payment Processing: Processes the payment for the order.
 *   <li>Shipment Creation: Creates a shipment for the order, with recovery for temporary glitches.
 *   <li>Result Mapping: Maps the processed context to a final result.
 *   <li>Customer Notification: Attempts to notify the customer, recovering from notification
 *       failures. This step now results in {@link Unit} on success.
 * </ol>
 *
 * Each step is a function that takes the current workflow context wrapped in an {@code EitherT} and
 * returns an updated context or final result, also wrapped in {@code EitherT}. Errors are
 * propagated as {@link DomainError} within the {@code EitherT}.
 */
public class Workflow2 {

  private final Dependencies dependencies;
  private final OrderWorkflowSteps steps;

  private final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad;
  private final MonadError<
          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad;

  /**
   * Constructs a {@code Workflow2} instance.
   *
   * @param dependencies The shared dependencies for workflow steps (e.g., logging).
   * @param steps The concrete implementations of individual workflow steps.
   * @param futureMonad The monad instance for {@link CompletableFutureKind}.
   * @param eitherTMonad The monad instance for {@link EitherTKind} wrapping a {@code
   *     CompletableFuture} and {@code DomainError}.
   */
  public Workflow2(
      Dependencies dependencies,
      OrderWorkflowSteps steps,
      MonadError<CompletableFutureKind.Witness, Throwable> futureMonad,
      MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
          eitherTMonad) {
    this.dependencies = dependencies;
    this.steps = steps;
    this.futureMonad = futureMonad;
    this.eitherTMonad = eitherTMonad;
  }

  /**
   * Runs the complete order processing workflow for the given order data. The workflow is composed
   * using a {@code For} comprehension to sequentially chain the validation, inventory check,
   * payment, shipment, and notification steps.
   *
   * @param orderData The initial data for the order to be processed.
   * @return A {@code Kind} representing the asynchronous result of the workflow, which will resolve
   *     to an {@code Either} containing a {@code FinalResult} on success or a {@code DomainError}
   *     on failure.
   */
  public Kind<CompletableFutureKind.Witness, Either<DomainError, WorkflowModels.FinalResult>> run(
      WorkflowModels.OrderData orderData) {

    dependencies.log(
        "Starting runOrderWorkflowEitherTWithTryValidation for Order: " + orderData.orderId());

    var initialContext = WorkflowModels.WorkflowContext.start(orderData);

    // The For-comprehension expresses the workflow sequentially.
    // Each '.from(...)' corresponds to a flatMap operation, chaining the next step.
    var workflow =
        For.from(eitherTMonad, eitherTMonad.of(initialContext))
            // Step 1: Validation (using Try and converting to Either)
            .from(
                ctx1 -> {
                  var tryResult = TRY.narrow(steps.validateOrderWithTry(ctx1.initialData()));
                  var eitherResult =
                      tryResult.toEither(
                          throwable ->
                              (DomainError)
                                  new DomainError.ValidationError(throwable.getMessage()));
                  var validatedOrderET = EitherT.fromEither(futureMonad, eitherResult);
                  // The cast here helps the compiler with type inference for the EitherT
                  return eitherTMonad.map(ctx1::withValidatedOrder, validatedOrderET);
                })
            // Step 2: Inventory Check
            .from(
                t2 -> {
                  var ctx = t2._2(); // Get the context from the previous step
                  var inventoryCheckET =
                      EitherT.fromKind(
                          steps.checkInventoryAsync(
                              ctx.validatedOrder().productId(), ctx.validatedOrder().quantity()));
                  return eitherTMonad.map(ignored -> ctx.withInventoryChecked(), inventoryCheckET);
                })
            // Step 3: Payment
            .from(
                t3 -> {
                  var ctx = t3._3(); // Get the context from the previous step
                  var paymentConfirmET =
                      EitherT.fromKind(
                          steps.processPaymentAsync(
                              ctx.validatedOrder().paymentDetails(),
                              ctx.validatedOrder().amount()));
                  return eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET);
                })
            // Step 4: Shipment (with error handling)
            .from(
                t4 -> {
                  var ctx = t4._4(); // Get the context from the previous step
                  var shipmentAttemptET =
                      EitherT.fromKind(
                          steps.createShipmentAsync(
                              ctx.validatedOrder().orderId(),
                              ctx.validatedOrder().shippingAddress()));
                  var recoveredShipmentET =
                      eitherTMonad.handleErrorWith(
                          shipmentAttemptET,
                          error -> {
                            if (error instanceof DomainError.ShippingError(var reason)
                                && "Temporary Glitch".equals(reason)) {
                              dependencies.log(
                                  "WARN (Try Validation): Recovering from temporary shipping glitch"
                                      + " with default for order "
                                      + ctx.validatedOrder().orderId());
                              return eitherTMonad.of(
                                  new WorkflowModels.ShipmentInfo("DEFAULT_SHIPPING_USED"));
                            }
                            return eitherTMonad.raiseError(error);
                          });
                  return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
                })
            // Step 5 & 6: Yield the final result after attempting notification.
            .yield(
                t5 -> {
                  // Explicitly type the tuple to guide the compiler
                  var finalContext = t5._5();
                  var finalResult =
                      new WorkflowModels.FinalResult(
                          finalContext.validatedOrder().orderId(),
                          finalContext.paymentConfirmation().transactionId(),
                          finalContext.shipmentInfo().trackingId());

                  var notifyET =
                      EitherT.fromKind(
                          steps.notifyCustomerAsync(
                              finalContext.initialData().customerId(),
                              "Order processed: " + finalResult.orderId()));
                  var recoveredNotifyET =
                      eitherTMonad.handleError(
                          notifyET,
                          notifyError -> {
                            dependencies.log(
                                "WARN (Try Validation): Notification failed for successful order "
                                    + finalResult.orderId()
                                    + ": "
                                    + notifyError.message());
                            return Unit.INSTANCE;
                          });

                  return eitherTMonad.map(ignored -> finalResult, recoveredNotifyET);
                });

    // The yield returns a Kind<M, Kind<M, R>>, so we must flatten it one last time.
    var flattenedFinalResultET = eitherTMonad.flatMap(x -> x, workflow);

    var finalConcreteET = EITHER_T.narrow(flattenedFinalResultET);
    return finalConcreteET.value();
  }
}
