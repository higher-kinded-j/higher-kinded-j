// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import static org.higherkindedj.example.order.error.DomainErrorPrisms.*;
import static org.higherkindedj.example.order.model.WorkflowContextLenses.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
// Import the generated optics

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
import org.jspecify.annotations.NonNull;

/**
 * An identical workflow to Workflow1, but refactored to use generated Lenses and Prisms for state
 * updates and error handling, demonstrating a more declarative and functional approach.
 */
public class WorkflowLensAndPrism {

  private final @NonNull Dependencies dependencies;
  private final @NonNull OrderWorkflowSteps steps;

  private final @NonNull MonadError<CompletableFutureKind.Witness, Throwable> futureMonad;
  private final @NonNull
      MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad;

  public WorkflowLensAndPrism(
      @NonNull Dependencies dependencies,
      @NonNull OrderWorkflowSteps steps,
      @NonNull MonadError<CompletableFutureKind.Witness, Throwable> futureMonad,
      @NonNull
          MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
          eitherTMonad) {
    this.dependencies = dependencies;
    this.steps = steps;
    this.futureMonad = futureMonad;
    this.eitherTMonad = eitherTMonad;
  }

  public Kind<CompletableFutureKind.Witness, Either<DomainError, WorkflowModels.FinalResult>> run(
      WorkflowModels.OrderData orderData) {

    dependencies.log("Starting Order Workflow [WorkflowOptics] for Order: " + orderData.orderId());

    var initialContext = WorkflowModels.WorkflowContext.start(orderData);

    var workflow =
        For.from(eitherTMonad, eitherTMonad.of(initialContext))
            .from(
                ctx1 -> {
                  var validatedOrderET =
                      EitherT.fromEither(
                          futureMonad, EITHER.narrow(steps.validateOrder(ctx1.initialData())));
                  // OPTICS REFACTORING:
                  // Original: eitherTMonad.map(ctx1::withValidatedOrder, validatedOrderET)
                  // Using a Lens to perform the state update declaratively.
                  return eitherTMonad.map(vo -> validatedOrder().set(vo, ctx1), validatedOrderET);
                })
            .from(
                t -> {
                  var ctx = t._2();
                  var inventoryCheckET =
                      EitherT.fromKind(
                          steps.checkInventoryAsync(
                              ctx.validatedOrder().productId(), ctx.validatedOrder().quantity()));
                  // OPTICS REFACTORING:
                  // Original: eitherTMonad.map(ignored -> ctx.withInventoryChecked(),
                  // inventoryCheckET)
                  // Using a Lens to set the boolean flag.
                  return eitherTMonad.map(
                      ignored -> inventoryChecked().set(true, ctx), inventoryCheckET);
                })
            .from(
                t -> {
                  var ctx = t._3();
                  var paymentConfirmET =
                      EitherT.fromKind(
                          steps.processPaymentAsync(
                              ctx.validatedOrder().paymentDetails(),
                              ctx.validatedOrder().amount()));
                  // OPTICS REFACTORING:
                  // Original: eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET)
                  return eitherTMonad.map(
                      pc -> paymentConfirmation().set(pc, ctx), paymentConfirmET);
                })
            .from(
                t -> {
                  var ctx = t._4();
                  var shipmentAttemptET =
                      EitherT.fromKind(
                          steps.createShipmentAsync(
                              ctx.validatedOrder().orderId(),
                              ctx.validatedOrder().shippingAddress()));
                  var recoveredShipmentET =
                      eitherTMonad.handleErrorWith(
                          shipmentAttemptET,
                          error ->
                              // OPTICS REFACTORING:
                              // Original: if (error instanceof DomainError.ShippingError(var
                              // reason) && ...)
                              // Using a Prism to pattern-match the error type safely and
                              // functionally.
                              shippingError()
                                  .getOptional(error) // Returns Optional<ShippingError>
                                  .filter(se -> "Temporary Glitch".equals(se.reason()))
                                  .map(
                                      se -> {
                                        dependencies.log(
                                            "WARN: Recovering from temporary shipping glitch for"
                                                + " order "
                                                + ctx.validatedOrder().orderId());
                                        return eitherTMonad.of(
                                            new WorkflowModels.ShipmentInfo(
                                                "DEFAULT_SHIPPING_USED"));
                                      })
                                  .orElse(eitherTMonad.raiseError(error)));
                  // OPTICS REFACTORING:
                  // Original: eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET)
                  return eitherTMonad.map(si -> shipmentInfo().set(si, ctx), recoveredShipmentET);
                })
            .yield(
                t -> {
                  var finalContext = t._5();
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
                                "WARN: Notification failed for order "
                                    + finalResult.orderId()
                                    + ": "
                                    + notifyError.message());
                            return Unit.INSTANCE;
                          });

                  return eitherTMonad.map(ignored -> finalResult, recoveredNotifyET);
                });

    var flattenedFinalResultET = eitherTMonad.flatMap(x -> x, workflow);
    var finalConcreteET = EITHER_T.narrow(flattenedFinalResultET);
    return finalConcreteET.value();
  }
}
