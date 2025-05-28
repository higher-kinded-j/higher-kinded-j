// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.example.order.model.WorkflowModels;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.trans.either_t.EitherT;
import org.higherkindedj.hkt.trans.either_t.EitherTKind;
import org.higherkindedj.hkt.trans.either_t.EitherTKindHelper;
import org.higherkindedj.hkt.unit.Unit;
import org.jspecify.annotations.NonNull;

/**
 * Implements an order processing workflow using {@link EitherT} over {@link CompletableFuture}.
 * This version of the workflow specifically uses an {@link Either} for its initial validation step.
 *
 * <p>The workflow processes an order through several stages:
 *
 * <ol>
 *   <li>Validation: Validates the initial order data.
 *   <li>Inventory Check: Checks if the product is in stock. This step now results in {@link Unit}
 *       on success.
 *   <li>Payment Processing: Processes the payment for the order.
 *   <li>Shipment Creation: Creates a shipment for the order.
 *   <li>Result Mapping: Maps the processed context to a final result.
 *   <li>Customer Notification: Notifies the customer about the order status. This step now results
 *       in {@link Unit} on success.
 * </ol>
 *
 * Each step is a function that takes the current workflow context wrapped in an {@code EitherT} and
 * returns an updated context or final result, also wrapped in {@code EitherT}. Errors are
 * propagated as {@link DomainError} within the {@code EitherT}.
 */
public class Workflow1 {

  private final @NonNull Dependencies dependencies;
  private final @NonNull OrderWorkflowSteps steps;

  private final @NonNull MonadError<CompletableFutureKind.Witness, Throwable> futureMonad;
  private final @NonNull
      MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad;

  /** Function representing the order validation step. */
  private final Function<
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.WorkflowContext>,
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.WorkflowContext>>
      processValidation;

  /** Function representing the payment processing step. */
  private final Function<
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.WorkflowContext>,
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.WorkflowContext>>
      processInventory;

  /** Function representing the payment processing step. */
  private final Function<
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.WorkflowContext>,
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.WorkflowContext>>
      processPayment;

  /** Function representing the shipment creation step. */
  private final Function<
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.WorkflowContext>,
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.WorkflowContext>>
      processShipment;

  private final Function<
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.WorkflowContext>,
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.FinalResult>>
      mapToFinalResult;

  /**
   * Creates a function for the customer notification step. This is a factory method to allow
   * capturing the {@code orderData}.
   *
   * @param orderData The initial order data, used for customer identification.
   * @return A function that takes an {@code EitherT} of {@code FinalResult} and attempts to notify
   *     the customer, returning the original {@code FinalResult}.
   */
  private Function<
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.FinalResult>,
          Kind<
              EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
              WorkflowModels.FinalResult>>
      createNotifyCustomerFunction(WorkflowModels.OrderData orderData) {
    return finalResult -> step6NotifyCustomer(orderData, finalResult);
  }

  /**
   * Constructs a {@code Workflow1} instance.
   *
   * @param dependencies The shared dependencies for workflow steps (e.g., logging).
   * @param steps The concrete implementations of individual workflow steps.
   * @param futureMonad The monad instance for {@link CompletableFutureKind}.
   * @param eitherTMonad The monad instance for {@link EitherTKind} wrapping a {@code
   *     CompletableFuture} and {@code DomainError}.
   */
  public Workflow1(
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

    this.processValidation = this::step1ValidateOrder;
    this.processInventory = this::step2CheckInventory;
    this.processPayment = this::step3ProcessPayment;
    this.processShipment = this::step4CreateShipment;
    this.mapToFinalResult = this::step5FinalResult;
  }

  /**
   * Step 1: Validates the order data. This is a synchronous step that returns an {@code
   * Either<DomainError, ValidatedOrder>}. The result is lifted into the {@code EitherT} context.
   *
   * @param initialET The initial workflow context wrapped in {@code EitherT}.
   * @return An {@code EitherT} containing the updated workflow context with validated order
   *     information, or a {@code DomainError} if validation fails.
   */
  Kind<
          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
          WorkflowModels.WorkflowContext>
      step1ValidateOrder(
          Kind<
                  EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                  WorkflowModels.WorkflowContext>
              initialET) {
    return eitherTMonad.flatMap(
        ctx -> {
          var syncResultEitherKind = steps.validateOrder(ctx.initialData());
          var syncResultEither = EitherKindHelper.unwrap(syncResultEitherKind);
          var validatedOrderET = EitherT.fromEither(futureMonad, syncResultEither);
          return eitherTMonad.map(ctx::withValidatedOrder, validatedOrderET);
        },
        initialET);
  }

  /**
   * Step 2: Checks product inventory. This is an asynchronous step. The result {@code
   * Kind<CompletableFutureKind.Witness, Either<DomainError, Unit>>} is lifted directly into {@code
   * EitherT}.
   *
   * @param validatedET The workflow context after order validation, wrapped in {@code EitherT}.
   * @return An {@code EitherT} containing the updated workflow context indicating inventory check
   *     completion, or a {@code DomainError} if inventory check fails.
   */
  Kind<
          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
          WorkflowModels.WorkflowContext>
      step2CheckInventory(
          Kind<
                  EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                  WorkflowModels.WorkflowContext>
              validatedET) {
    return eitherTMonad.flatMap(
        ctx -> {
          var inventoryCheckFutureKind =
              steps.checkInventoryAsync(
                  ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());
          var inventoryCheckET =
              EitherT.<CompletableFutureKind.Witness, DomainError, Unit>fromKind(
                  inventoryCheckFutureKind); // Type hint for Unit
          return eitherTMonad.map(ignored -> ctx.withInventoryChecked(), inventoryCheckET);
        },
        validatedET);
  }

  /**
   * Step 3: Processes the payment. This is an asynchronous step. The result containing payment
   * confirmation is lifted into {@code EitherT}.
   *
   * @param inventoryET The workflow context after inventory check, wrapped in {@code EitherT}.
   * @return An {@code EitherT} containing the updated workflow context with payment confirmation,
   *     or a {@code DomainError} if payment processing fails.
   */
  Kind<
          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
          WorkflowModels.WorkflowContext>
      step3ProcessPayment(
          Kind<
                  EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                  WorkflowModels.WorkflowContext>
              inventoryET) {
    return eitherTMonad.flatMap(
        ctx -> {
          var paymentFutureKind =
              steps.processPaymentAsync(
                  ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
          var paymentConfirmET = EitherT.fromKind(paymentFutureKind);
          return eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET);
        },
        inventoryET);
  }

  /**
   * Step 4: Creates the shipment. This is an asynchronous step. It includes error handling to
   * recover from specific temporary shipping errors by using a default shipment info.
   *
   * @param paymentET The workflow context after payment processing, wrapped in {@code EitherT}.
   * @return An {@code EitherT} containing the updated workflow context with shipment information,
   *     or a {@code DomainError} if shipment creation fails and is not recoverable.
   */
  Kind<
          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
          WorkflowModels.WorkflowContext>
      step4CreateShipment(
          Kind<
                  EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                  WorkflowModels.WorkflowContext>
              paymentET) {
    return eitherTMonad.flatMap(
        ctx -> {
          var shipmentAttemptFutureKind =
              steps.createShipmentAsync(
                  ctx.validatedOrder().orderId(), ctx.validatedOrder().shippingAddress());
          var shipmentAttemptET = EitherT.fromKind(shipmentAttemptFutureKind);

          var recoveredShipmentET =
              eitherTMonad.handleErrorWith(
                  shipmentAttemptET,
                  error -> {
                    if (error instanceof DomainError.ShippingError(String reason)
                        && "Temporary Glitch".equals(reason)) {
                      dependencies.log(
                          "WARN (EitherT): Recovering from temporary shipping glitch"
                              + " with default for order "
                              + ctx.validatedOrder().orderId());
                      return eitherTMonad.of(
                          new WorkflowModels.ShipmentInfo("DEFAULT_SHIPPING_USED"));
                    } else {
                      return eitherTMonad.raiseError(error);
                    }
                  });
          return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
        },
        paymentET);
  }

  /**
   * Step 5: Maps the final workflow context to a {@link WorkflowModels.FinalResult}.
   *
   * @param shipmentET The workflow context after shipment creation, wrapped in {@code EitherT}.
   * @return An {@code EitherT} containing the {@link WorkflowModels.FinalResult}, or a {@code
   *     DomainError} if any preceding step failed.
   */
  Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowModels.FinalResult>
      step5FinalResult(
          Kind<
                  EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                  WorkflowModels.WorkflowContext>
              shipmentET) {
    return eitherTMonad.map(
        ctx -> {
          dependencies.log(
              "Mapping final context to FinalResult (EitherT) for Order: "
                  + ctx.validatedOrder().orderId());
          return new WorkflowModels.FinalResult(
              ctx.validatedOrder().orderId(),
              ctx.paymentConfirmation().transactionId(),
              ctx.shipmentInfo().trackingId());
        },
        shipmentET);
  }

  /**
   * Step 6: Attempts to notify the customer about the order processing outcome. This step handles
   * notification errors by logging them but always recovers by returning {@link Unit#INSTANCE},
   * effectively making the notification's success value {@link Unit}. The original {@code
   * FinalResult} from previous steps is preserved.
   *
   * @param orderData The initial order data, used for customer identification.
   * @param finalResultET The {@code EitherT} containing the {@code FinalResult} from previous
   *     steps.
   * @return An {@code EitherT} containing the original {@link WorkflowModels.FinalResult},
   *     regardless of notification success or failure.
   */
  Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowModels.FinalResult>
      step6NotifyCustomer(
          WorkflowModels.OrderData orderData,
          Kind<
                  EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                  WorkflowModels.FinalResult>
              finalResultET) {
    return eitherTMonad.flatMap(
        finalResult -> {
          var notifyFutureKind =
              steps.notifyCustomerAsync(
                  orderData.customerId(), "Order processed: " + finalResult.orderId());
          var notifyET =
              EitherT.<CompletableFutureKind.Witness, DomainError, Unit>fromKind(notifyFutureKind);
          var recoveredNotifyET =
              eitherTMonad.handleError(
                  notifyET,
                  notifyError -> {
                    dependencies.log(
                        "WARN (EitherT): Notification failed for successful order "
                            + finalResult.orderId()
                            + ": "
                            + notifyError.message());
                    return Unit.INSTANCE;
                  });
          return eitherTMonad.map(ignored -> finalResult, recoveredNotifyET);
        },
        finalResultET);
  }

  /**
   * Runs the complete order processing workflow for the given order data. The workflow composes a
   * series of steps (validation, inventory check, payment, shipment, final mapping, and
   * notification) using {@code EitherT} to manage asynchronous operations and error handling.
   * Validation in this workflow is performed using {@link
   * OrderWorkflowSteps#validateOrder(WorkflowModels.OrderData)} which returns an {@code Either}.
   *
   * @param orderData The initial data for the order to be processed.
   * @return A {@code Kind<CompletableFutureKind.Witness, Either<DomainError,
   *     WorkflowModels.FinalResult>>} representing the asynchronous result of the workflow. This
   *     will be a {@code CompletableFuture} containing an {@code Either} which is a {@code
   *     Right<FinalResult>} on success, or a {@code Left<DomainError>} if any step in the workflow
   *     fails.
   */
  public Kind<CompletableFutureKind.Witness, Either<DomainError, WorkflowModels.FinalResult>> run(
      WorkflowModels.OrderData orderData) {

    dependencies.log(
        "Starting Order Workflow [runOrderWorkflowEitherT] for Order: " + orderData.orderId());

    var initialContext = WorkflowModels.WorkflowContext.start(orderData);

    Function<
            Kind<
                EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                WorkflowModels.WorkflowContext>,
            Kind<
                EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                WorkflowModels.FinalResult>>
        completeWorkflow =
            processValidation
                .andThen(processInventory)
                .andThen(processPayment)
                .andThen(processShipment)
                .andThen(mapToFinalResult)
                .andThen(createNotifyCustomerFunction(orderData));

    Kind<
            EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
            WorkflowModels.WorkflowContext>
        initialET = eitherTMonad.of(initialContext);
    Kind<
            EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
            WorkflowModels.FinalResult>
        finalResultWithNotificationET = completeWorkflow.apply(initialET);

    var finalConcreteET = EitherTKindHelper.unwrap(finalResultWithNotificationET);
    return finalConcreteET.value();
  }
}
