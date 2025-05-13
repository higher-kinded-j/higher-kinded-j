package org.higherkindedj.example.order.workflow;

import static org.higherkindedj.example.order.model.WorkflowModels.*;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureMonadError;
import org.higherkindedj.hkt.trans.either_t.EitherT;
import org.higherkindedj.hkt.trans.either_t.EitherTKind;
import org.higherkindedj.hkt.trans.either_t.EitherTKindHelper;
import org.higherkindedj.hkt.trans.either_t.EitherTMonad;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryKindHelper;
import org.jspecify.annotations.NonNull;

/**
 * Orchestrates an order processing workflow using the Higher-Kinded-J framework, primarily
 * leveraging the {@link EitherT} monad transformer over {@link CompletableFuture}.
 *
 * <p>This example primarily demonstrates:
 *
 * <ul>
 *   <li><b>Dependency Injection:</b> The {@link OrderWorkflowSteps} instance is now created with a
 *       {@link Dependencies} object, making dependencies like logging explicit.
 *   <li><b>Structured Logging:</b> Workflow steps use the logger provided in {@code Dependencies}
 *       instead of direct console output.
 *   <li><b>EitherT for Async/Error Flow:</b> Continues to use {@code EitherT} to manage the nested
 *       {@code CompletableFuture<Either<DomainError, T>>} structure, handling asynchronicity and
 *       domain errors gracefully.
 *   <li><b>Integration of Sync/Async Steps:</b> Shows lifting results from synchronous steps
 *       (returning {@code Either} or {@code Try}) and asynchronous steps into the unified {@code
 *       EitherT} context.
 *   <li><b>Error Handling and Recovery:</b> Uses {@code MonadError} capabilities of {@code
 *       EitherTMonad} to handle specific {@link DomainError}s (e.g., recoverable shipping errors).
 *   <li><b>Usage of Kind Helpers:</b> Demonstrates using {@link EitherTKindHelper} for
 *       wrapping/unwrapping {@code EitherT} instances when interacting with the HKT system.
 * </ul>
 *
 * <h2>Workflow Structure:</h2>
 *
 * <p>The workflow is a sequence of operations chained using {@code eitherTMonad.flatMap}. If any
 * step results in a {@code Left<DomainError>}, subsequent steps are skipped. System-level errors
 * during async execution are caught by the underlying {@code CompletableFuture}. Logging occurs
 * within each step via the injected logger.
 */
public class OrderWorkflowRunner {

  private final @NonNull OrderWorkflowSteps steps; // Now requires Dependencies
  private final @NonNull Dependencies dependencies; // Store dependencies
  private final @NonNull MonadError<CompletableFutureKind.Witness, Throwable> futureMonad;
  private final @NonNull
      MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
      eitherTMonad;

  public OrderWorkflowRunner(@NonNull Dependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "Dependencies cannot be null");
    this.steps = new OrderWorkflowSteps(dependencies);
    this.futureMonad = new CompletableFutureMonadError();
    this.eitherTMonad = new EitherTMonad<>(this.futureMonad);
  }

  public static void main(String[] args) {
    Consumer<String> consoleLogger = System.out::println;
    Dependencies appDependencies = new Dependencies(consoleLogger);
    OrderWorkflowRunner runner = new OrderWorkflowRunner(appDependencies);

    OrderData goodData =
        new OrderData("Order-Good-001", "PROD-123", 2, "VALID_CARD", "123 Main St", "cust-good");
    OrderData badQtyData =
        new OrderData("Order-BadQty-002", "PROD-456", 0, "VALID_CARD", "456 Oak Ave", "cust-ok");
    OrderData stockData =
        new OrderData(
            "Order-Stock-003", "OUT_OF_STOCK", 1, "VALID_CARD", "789 Pine Ln", "cust-stock");
    OrderData paymentData =
        new OrderData("Order-Pay-004", "PROD-789", 1, "INVALID_CARD", "101 Maple Dr", "cust-pay");
    OrderData recoverableShippingData =
        new OrderData(
            "FAIL_SHIPMENT", "PROD-SHIP-REC", 1, "VALID_CARD", "1 Recovery Lane", "cust-ship-rec");
    OrderData nonRecoverableShippingData =
        new OrderData("Order-ShipFail-005", "PROD-SHIP", 1, "VALID_CARD", "", "cust-ship");

    System.out.println("\n--- Running Workflow with EitherT (Validate using Either) ---");
    runAndPrintResult("Good (Either Valid)", runner::runOrderWorkflowEitherT, goodData);
    runAndPrintResult("Bad Qty (Either Valid)", runner::runOrderWorkflowEitherT, badQtyData);
    runAndPrintResult("Stock Error (Either Valid)", runner::runOrderWorkflowEitherT, stockData);
    runAndPrintResult("Payment Error (Either Valid)", runner::runOrderWorkflowEitherT, paymentData);
    runAndPrintResult(
        "Recoverable Shipping (Either Valid)",
        runner::runOrderWorkflowEitherT,
        recoverableShippingData);
    runAndPrintResult(
        "Non-Recoverable Shipping (Either Valid)",
        runner::runOrderWorkflowEitherT,
        nonRecoverableShippingData);

    System.out.println("\n--- Running Workflow with EitherT (Validate using Try) ---");
    runAndPrintResult(
        "Good (Try Valid)", runner::runOrderWorkflowEitherTWithTryValidation, goodData);
    runAndPrintResult(
        "Bad Qty (Try Valid)", runner::runOrderWorkflowEitherTWithTryValidation, badQtyData);
    runAndPrintResult(
        "Stock Error (Try Valid)", runner::runOrderWorkflowEitherTWithTryValidation, stockData);
    runAndPrintResult(
        "Payment Error (Try Valid)", runner::runOrderWorkflowEitherTWithTryValidation, paymentData);
    runAndPrintResult(
        "Recoverable Shipping (Try Valid)",
        runner::runOrderWorkflowEitherTWithTryValidation,
        recoverableShippingData);
    runAndPrintResult(
        "Non-Recoverable Shipping (Try Valid)",
        runner::runOrderWorkflowEitherTWithTryValidation,
        nonRecoverableShippingData);
  }

  private static void runAndPrintResult(
      String label,
      java.util.function.Function<
              OrderData, Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>>
          runnerMethod,
      OrderData data) {
    System.out.println("=== Executing Workflow: " + label + " ===");
    Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>> resultKind =
        runnerMethod.apply(data);
    CompletableFuture<Either<DomainError, FinalResult>> future =
        CompletableFutureKindHelper.unwrap(resultKind);

    try {
      Either<DomainError, FinalResult> resultEither = future.join();
      System.out.println("Final Result (" + label + "): " + resultEither);
    } catch (CompletionException e) {
      Throwable cause = e.getCause();
      System.err.println(
          "ERROR ("
              + label
              + "): Workflow execution failed unexpectedly: "
              + (cause != null ? cause : e));
    } catch (Exception e) {
      System.err.println("ERROR (" + label + "): Unexpected error during result processing: " + e);
    }
    System.out.println("========================================");
  }

  public Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>
      runOrderWorkflowEitherT(OrderData orderData) {

    dependencies.log(
        "Starting Order Workflow [runOrderWorkflowEitherT] for Order: " + orderData.orderId());

    WorkflowContext initialContext = WorkflowContext.start(orderData);

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        initialET = eitherTMonad.of(initialContext);

    // Step 1: Validate Order

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        validatedET =
            eitherTMonad.flatMap(
                ctx -> {
                  Either<DomainError, ValidatedOrder> syncResultEither =
                      EitherKindHelper.unwrap(steps.validateOrder(ctx.initialData()));
                  EitherT<CompletableFutureKind.Witness, DomainError, ValidatedOrder>
                      concreteValidatedOrderET = EitherT.fromEither(futureMonad, syncResultEither);

                  Kind<
                          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                          ValidatedOrder>
                      wrappedValidatedOrderET = EitherTKindHelper.wrap(concreteValidatedOrderET);
                  return eitherTMonad.map(ctx::withValidatedOrder, wrappedValidatedOrderET);
                },
                initialET);

    // Step 2: Check Inventory

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        inventoryET =
            eitherTMonad.flatMap(
                ctx -> {
                  Kind<CompletableFutureKind.Witness, Either<DomainError, Void>>
                      inventoryCheckFutureKind =
                          steps.checkInventoryAsync(
                              ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());
                  EitherT<CompletableFutureKind.Witness, DomainError, Void>
                      concreteInventoryCheckET = EitherT.fromKind(inventoryCheckFutureKind);

                  Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Void>
                      wrappedInventoryCheckET = EitherTKindHelper.wrap(concreteInventoryCheckET);
                  return eitherTMonad.map(
                      ignored -> ctx.withInventoryChecked(), wrappedInventoryCheckET);
                },
                validatedET);

    // Step 3: Process Payment (Asynchronous)

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        paymentET =
            eitherTMonad.flatMap(
                ctx -> {
                  Kind<CompletableFutureKind.Witness, Either<DomainError, PaymentConfirmation>>
                      paymentFutureKind =
                          steps.processPaymentAsync(
                              ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
                  EitherT<CompletableFutureKind.Witness, DomainError, PaymentConfirmation>
                      concretePaymentConfirmET = EitherT.fromKind(paymentFutureKind);

                  Kind<
                          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                          PaymentConfirmation>
                      wrappedPaymentConfirmET = EitherTKindHelper.wrap(concretePaymentConfirmET);
                  return eitherTMonad.map(ctx::withPaymentConfirmation, wrappedPaymentConfirmET);
                },
                inventoryET);

    // Step 4: Create Shipment (Asynchronous with Recovery)

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        shipmentET =
            eitherTMonad.flatMap(
                ctx -> {
                  Kind<CompletableFutureKind.Witness, Either<DomainError, ShipmentInfo>>
                      shipmentAttemptFutureKind =
                          steps.createShipmentAsync(
                              ctx.validatedOrder().orderId(),
                              ctx.validatedOrder().shippingAddress());
                  EitherT<CompletableFutureKind.Witness, DomainError, ShipmentInfo>
                      concreteShipmentAttemptET = EitherT.fromKind(shipmentAttemptFutureKind);

                  Kind<
                          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                          ShipmentInfo>
                      wrappedShipmentAttemptET = EitherTKindHelper.wrap(concreteShipmentAttemptET);

                  Kind<
                          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                          ShipmentInfo>
                      recoveredShipmentET =
                          eitherTMonad.handleErrorWith(
                              wrappedShipmentAttemptET,
                              error -> {
                                if (error instanceof DomainError.ShippingError(String reason)
                                    && "Temporary Glitch".equals(reason)) {
                                  dependencies.log(
                                      "WARN (EitherT): Recovering from temporary shipping glitch"
                                          + " with default for order "
                                          + ctx.validatedOrder().orderId());
                                  return eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"));
                                } else {
                                  return eitherTMonad.raiseError(error);
                                }
                              });
                  return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
                },
                paymentET);

    // Step 5: Map final context to FinalResult

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, FinalResult>
        finalResultET =
            eitherTMonad.map(
                ctx -> {
                  dependencies.log(
                      "Mapping final context to FinalResult (EitherT) for Order: "
                          + ctx.validatedOrder().orderId());
                  return new FinalResult(
                      ctx.validatedOrder().orderId(),
                      ctx.paymentConfirmation().transactionId(),
                      ctx.shipmentInfo().trackingId());
                },
                shipmentET);

    // Step 6: Attempt Notification (Asynchronous) - Optional, non-critical failure

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, FinalResult>
        finalResultWithNotificationET =
            eitherTMonad.flatMap(
                finalResult -> {
                  Kind<CompletableFutureKind.Witness, Either<DomainError, Void>> notifyFutureKind =
                      steps.notifyCustomerAsync(
                          orderData.customerId(), "Order processed: " + finalResult.orderId());
                  EitherT<CompletableFutureKind.Witness, DomainError, Void> concreteNotifyET =
                      EitherT.fromKind(notifyFutureKind);

                  Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Void>
                      wrappedNotifyET = EitherTKindHelper.wrap(concreteNotifyET);

                  return eitherTMonad.map(
                      ignored -> finalResult,
                      eitherTMonad.handleError(
                          wrappedNotifyET,
                          notifyError -> {
                            dependencies.log(
                                "WARN (EitherT): Notification failed for successful order "
                                    + finalResult.orderId()
                                    + ": "
                                    + notifyError.message());
                            return null;
                          }));
                },
                finalResultET);

    EitherT<CompletableFutureKind.Witness, DomainError, FinalResult> finalConcreteET =
        EitherTKindHelper.unwrap(finalResultWithNotificationET);
    return finalConcreteET.value();
  }

  public Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>
      runOrderWorkflowEitherTWithTryValidation(OrderData orderData) {
    dependencies.log(
        "Starting runOrderWorkflowEitherTWithTryValidation for Order: " + orderData.orderId());

    WorkflowContext initialContext = WorkflowContext.start(orderData);

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        initialET = eitherTMonad.of(initialContext);

    // Step 1: Validate Order (Synchronous - returns Try)

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        validatedET =
            eitherTMonad.flatMap(
                ctx -> {
                  Kind<TryKind.Witness, ValidatedOrder> tryResultKind =
                      steps.validateOrderWithTry(ctx.initialData());
                  Either<DomainError, ValidatedOrder> eitherResult =
                      TryKindHelper.unwrap(tryResultKind)
                          .fold(
                              Either::right,
                              throwable -> {
                                dependencies.log(
                                    "Converting Try.Failure to DomainError.ValidationError: "
                                        + throwable.getMessage());
                                return Either.left(
                                    new DomainError.ValidationError(throwable.getMessage()));
                              });
                  EitherT<CompletableFutureKind.Witness, DomainError, ValidatedOrder>
                      concreteValidatedOrderET = EitherT.fromEither(futureMonad, eitherResult);

                  Kind<
                          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                          ValidatedOrder>
                      wrappedValidatedOrderET = EitherTKindHelper.wrap(concreteValidatedOrderET);
                  return eitherTMonad.map(ctx::withValidatedOrder, wrappedValidatedOrderET);
                },
                initialET);

    // Inventory Check

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        inventoryET =
            eitherTMonad.flatMap(
                ctx -> {
                  Kind<CompletableFutureKind.Witness, Either<DomainError, Void>>
                      inventoryCheckFutureKind =
                          steps.checkInventoryAsync(
                              ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());
                  EitherT<CompletableFutureKind.Witness, DomainError, Void>
                      concreteInventoryCheckET = EitherT.fromKind(inventoryCheckFutureKind);

                  Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Void>
                      wrappedInventoryCheckET = EitherTKindHelper.wrap(concreteInventoryCheckET);
                  return eitherTMonad.map(
                      ignored -> ctx.withInventoryChecked(), wrappedInventoryCheckET);
                },
                validatedET);

    // Payment

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        paymentET =
            eitherTMonad.flatMap(
                ctx -> {
                  Kind<CompletableFutureKind.Witness, Either<DomainError, PaymentConfirmation>>
                      paymentFutureKind =
                          steps.processPaymentAsync(
                              ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
                  EitherT<CompletableFutureKind.Witness, DomainError, PaymentConfirmation>
                      concretePaymentConfirmET = EitherT.fromKind(paymentFutureKind);

                  Kind<
                          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                          PaymentConfirmation>
                      wrappedPaymentConfirmET = EitherTKindHelper.wrap(concretePaymentConfirmET);
                  return eitherTMonad.map(ctx::withPaymentConfirmation, wrappedPaymentConfirmET);
                },
                inventoryET);
    // Shipment

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext>
        shipmentET =
            eitherTMonad.flatMap(
                ctx -> {
                  Kind<CompletableFutureKind.Witness, Either<DomainError, ShipmentInfo>>
                      shipmentAttemptFutureKind =
                          steps.createShipmentAsync(
                              ctx.validatedOrder().orderId(),
                              ctx.validatedOrder().shippingAddress());
                  EitherT<CompletableFutureKind.Witness, DomainError, ShipmentInfo>
                      concreteShipmentAttemptET = EitherT.fromKind(shipmentAttemptFutureKind);

                  Kind<
                          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                          ShipmentInfo>
                      wrappedShipmentAttemptET = EitherTKindHelper.wrap(concreteShipmentAttemptET);

                  Kind<
                          EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
                          ShipmentInfo>
                      recoveredShipmentET =
                          eitherTMonad.handleErrorWith(
                              wrappedShipmentAttemptET,
                              error ->
                                  switch (error) {
                                    case DomainError.ShippingError(String reason)
                                        when "Temporary Glitch".equals(reason) -> {
                                      dependencies.log(
                                          "WARN (Try Validation): Recovering from temporary"
                                              + " shipping glitch with default for order "
                                              + ctx.validatedOrder().orderId());
                                      yield eitherTMonad.of(
                                          new ShipmentInfo("DEFAULT_SHIPPING_USED"));
                                    }
                                    default -> eitherTMonad.raiseError(error);
                                  });
                  return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
                },
                paymentET);
    // Final Result Mapping

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, FinalResult>
        finalResultET =
            eitherTMonad.map(
                ctx -> {
                  dependencies.log(
                      "Mapping final context to FinalResult (Try Validation) for Order: "
                          + ctx.validatedOrder().orderId());
                  return new FinalResult(
                      ctx.validatedOrder().orderId(),
                      ctx.paymentConfirmation().transactionId(),
                      ctx.shipmentInfo().trackingId());
                },
                shipmentET);
    // Notification

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, FinalResult>
        finalResultWithNotificationET =
            eitherTMonad.flatMap(
                finalResult -> {
                  Kind<CompletableFutureKind.Witness, Either<DomainError, Void>> notifyFutureKind =
                      steps.notifyCustomerAsync(
                          orderData.customerId(), "Order processed: " + finalResult.orderId());
                  EitherT<CompletableFutureKind.Witness, DomainError, Void> concreteNotifyET =
                      EitherT.fromKind(notifyFutureKind);

                  Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Void>
                      wrappedNotifyET = EitherTKindHelper.wrap(concreteNotifyET);

                  return eitherTMonad.map(
                      ignored -> finalResult,
                      eitherTMonad.handleError(
                          wrappedNotifyET,
                          notifyError -> {
                            dependencies.log(
                                "WARN (Try Validation): Notification failed for successful order "
                                    + finalResult.orderId()
                                    + ": "
                                    + notifyError.message());
                            return null; // Recover with Void
                          }));
                },
                finalResultET);

    EitherT<CompletableFutureKind.Witness, DomainError, FinalResult> finalConcreteET =
        EitherTKindHelper.unwrap(finalResultWithNotificationET);
    return finalConcreteET.value();
  }
}
