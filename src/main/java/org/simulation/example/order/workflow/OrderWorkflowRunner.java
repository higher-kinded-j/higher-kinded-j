package org.simulation.example.order.workflow;

import static org.simulation.example.order.model.WorkflowModels.*;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;
import org.simulation.example.order.error.DomainError;
import org.simulation.hkt.Kind;
import org.simulation.hkt.MonadError;
import org.simulation.hkt.either.Either;
import org.simulation.hkt.either.EitherKindHelper;
import org.simulation.hkt.future.CompletableFutureKind;
import org.simulation.hkt.future.CompletableFutureKindHelper;
import org.simulation.hkt.future.CompletableFutureMonadError;
import org.simulation.hkt.trans.EitherT;
import org.simulation.hkt.trans.EitherTKind;
import org.simulation.hkt.trans.EitherTMonad;
import org.simulation.hkt.trymonad.Try;
import org.simulation.hkt.trymonad.TryKind;
import org.simulation.hkt.trymonad.TryKindHelper;

/**
 * Orchestrates an order processing workflow using the HKT simulation framework, primarily
 * leveraging the {@link EitherT} monad transformer over {@link CompletableFuture}.
 *
 * <p>This version demonstrates:
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
 * </ul>
 *
 * <h2>Workflow Structure:</h2>
 *
 * The workflow remains a sequence of operations chained using {@code eitherTMonad.flatMap}. If any
 * step results in a {@code Left<DomainError>}, subsequent steps are skipped. System-level errors
 * during async execution are caught by the underlying {@code CompletableFuture}. Logging occurs
 * within each step via the injected logger.
 */
public class OrderWorkflowRunner {

  private final @NonNull OrderWorkflowSteps steps; // Now requires Dependencies
  private final @NonNull Dependencies dependencies; // Store dependencies
  private final @NonNull MonadError<CompletableFutureKind<?>, Throwable> futureMonad;
  private final @NonNull
      MonadError<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, DomainError>
      eitherTMonad;

  /**
   * Constructor accepting dependencies.
   *
   * @param dependencies The external dependencies for the workflow. (NonNull)
   */
  public OrderWorkflowRunner(@NonNull Dependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "Dependencies cannot be null");
    // Pass dependencies to the steps
    this.steps = new OrderWorkflowSteps(dependencies);
    this.futureMonad = new CompletableFutureMonadError();
    this.eitherTMonad = new EitherTMonad<>(this.futureMonad);
  }

  /**
   * Runs the order processing workflow using EitherT, with the validation step returning {@code
   * Either<DomainError, ValidatedOrder>}.
   *
   * @param orderData The initial data for the order.
   * @return A {@code Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>} representing
   *     the final outcome.
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>> runOrderWorkflowEitherT(
      OrderData orderData) {

    // Log workflow start
    dependencies.log("Starting runOrderWorkflowEitherT for Order: " + orderData.orderId());

    WorkflowContext initialContext = WorkflowContext.start(orderData);
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> initialET =
        eitherTMonad.of(initialContext);

    // Step 1: Validate Order (Synchronous - returns Either)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> validatedET =
        eitherTMonad.flatMap(
            ctx -> {
              // Logging is now done inside steps.validateOrder
              Either<DomainError, ValidatedOrder> syncResultEither =
                  EitherKindHelper.unwrap(steps.validateOrder(ctx.initialData()));
              // Lift Either into EitherT<Future, ...>
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, ValidatedOrder>
                  validatedOrderET = EitherT.fromEither(futureMonad, syncResultEither);
              return eitherTMonad.map(ctx::withValidatedOrder, validatedOrderET);
            },
            initialET);

    // Step 2: Check Inventory (Asynchronous)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> inventoryET =
        eitherTMonad.flatMap(
            ctx -> {
              // Logging done inside steps.checkInventoryAsync
              Kind<CompletableFutureKind<?>, Either<DomainError, Void>> inventoryCheckFutureKind =
                  steps.checkInventoryAsync(
                      ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, Void> inventoryCheckET =
                  EitherT.fromKind(inventoryCheckFutureKind);
              return eitherTMonad.map(ignored -> ctx.withInventoryChecked(), inventoryCheckET);
            },
            validatedET);

    // Step 3: Process Payment (Asynchronous)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> paymentET =
        eitherTMonad.flatMap(
            ctx -> {
              // Logging done inside steps.processPaymentAsync
              Kind<CompletableFutureKind<?>, Either<DomainError, PaymentConfirmation>>
                  paymentFutureKind =
                      steps.processPaymentAsync(
                          ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, PaymentConfirmation>
                  paymentConfirmET = EitherT.fromKind(paymentFutureKind);
              return eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET);
            },
            inventoryET);

    // Step 4: Create Shipment (Asynchronous with Recovery)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> shipmentET =
        eitherTMonad.flatMap(
            ctx -> {
              // Logging done inside steps.createShipmentAsync
              Kind<CompletableFutureKind<?>, Either<DomainError, ShipmentInfo>>
                  shipmentAttemptFutureKind =
                      steps.createShipmentAsync(
                          ctx.validatedOrder().orderId(), ctx.validatedOrder().shippingAddress());
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, ShipmentInfo>
                  shipmentAttemptET = EitherT.fromKind(shipmentAttemptFutureKind);

              // Attempt recovery for specific shipping errors
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, ShipmentInfo>
                  recoveredShipmentET =
                      eitherTMonad.handleErrorWith(
                          shipmentAttemptET,
                          error -> { // Handles DomainError
                            if (error instanceof DomainError.ShippingError(String reason)
                                && "Temporary Glitch".equals(reason)) {
                              // Log recovery attempt
                              dependencies.log(
                                  "WARN (EitherT): Recovering from temporary shipping glitch with"
                                      + " default for order "
                                      + ctx.validatedOrder().orderId());
                              // Recover by returning a successful EitherT with default info
                              return eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"));
                            } else {
                              // Re-raise other errors
                              return eitherTMonad.raiseError(error);
                            }
                          });
              // Map the (potentially recovered) ShipmentInfo back into the context
              return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
            },
            paymentET);

    // Step 5: Map final context to FinalResult
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, FinalResult> finalResultET =
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
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, FinalResult>
        finalResultWithNotificationET =
            eitherTMonad.flatMap(
                finalResult -> {
                  // Logging done inside steps.notifyCustomerAsync
                  Kind<CompletableFutureKind<?>, Either<DomainError, Void>> notifyFutureKind =
                      steps.notifyCustomerAsync(
                          orderData.customerId(), "Order processed: " + finalResult.orderId());
                  Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, Void> notifyET =
                      EitherT.fromKind(notifyFutureKind);

                  // Handle potential notification failure without failing the whole workflow
                  return eitherTMonad.map(
                      ignored -> finalResult, // Return the original FinalResult
                      eitherTMonad.handleError(
                          notifyET,
                          notifyError -> {
                            // Log the notification error but don't fail the order
                            dependencies.log(
                                "WARN (EitherT): Notification failed for successful order "
                                    + finalResult.orderId()
                                    + ": "
                                    + notifyError.message());
                            return null; // Recover with Void (null)
                          }));
                },
                finalResultET);

    // Unwrap the final EitherT to get the underlying CompletableFuture<Either<...>>
    EitherT<CompletableFutureKind<?>, DomainError, FinalResult> finalET =
        (EitherT<CompletableFutureKind<?>, DomainError, FinalResult>) finalResultWithNotificationET;
    return finalET.value();
  }

  /**
   * Runs the order processing workflow using EitherT, with the validation step returning {@code
   * Try<ValidatedOrder>}.
   *
   * @param orderData The initial data for the order.
   * @return A {@code Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>} representing
   *     the final outcome.
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>
      runOrderWorkflowEitherTWithTryValidation(OrderData orderData) {

    dependencies.log(
        "Starting runOrderWorkflowEitherTWithTryValidation for Order: " + orderData.orderId());

    WorkflowContext initialContext = WorkflowContext.start(orderData);
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> initialET =
        eitherTMonad.of(initialContext);

    // Step 1: Validate Order (Synchronous - returns Try)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> validatedET =
        eitherTMonad.flatMap(
            ctx -> {
              // Logging is now done inside steps.validateOrderWithTry
              Kind<TryKind<?>, ValidatedOrder> tryResultKind =
                  steps.validateOrderWithTry(ctx.initialData());
              Try<ValidatedOrder> tryResult = TryKindHelper.unwrap(tryResultKind);

              // Convert Try<ValidatedOrder> to Either<DomainError, ValidatedOrder>
              Either<DomainError, ValidatedOrder> eitherResult =
                  tryResult.fold(
                      Either::right, // Success(v) -> Right(v)
                      throwable -> {
                        // Failure(t) -> Left(DomainError)
                        // Log the conversion
                        dependencies.log(
                            "Converting Try.Failure to DomainError.ValidationError: "
                                + throwable.getMessage());
                        return Either.left(new DomainError.ValidationError(throwable.getMessage()));
                      });

              // Lift the converted Either result into EitherT<Future, ..., ValidatedOrder>
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, ValidatedOrder>
                  validatedOrderET = EitherT.fromEither(futureMonad, eitherResult);
              // Map the success case to update the context
              return eitherTMonad.map(ctx::withValidatedOrder, validatedOrderET);
            },
            initialET);

    // --- Subsequent steps are identical to the runOrderWorkflowEitherT method ---

    // Step 2: Check Inventory (Asynchronous)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> inventoryET =
        eitherTMonad.flatMap(
            ctx -> {
              Kind<CompletableFutureKind<?>, Either<DomainError, Void>> inventoryCheckFutureKind =
                  steps.checkInventoryAsync(
                      ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, Void> inventoryCheckET =
                  EitherT.fromKind(inventoryCheckFutureKind);
              return eitherTMonad.map(ignored -> ctx.withInventoryChecked(), inventoryCheckET);
            },
            validatedET);

    // Step 3: Process Payment (Asynchronous)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> paymentET =
        eitherTMonad.flatMap(
            ctx -> {
              Kind<CompletableFutureKind<?>, Either<DomainError, PaymentConfirmation>>
                  paymentFutureKind =
                      steps.processPaymentAsync(
                          ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, PaymentConfirmation>
                  paymentConfirmET = EitherT.fromKind(paymentFutureKind);
              return eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET);
            },
            inventoryET);

    // Step 4: Create Shipment (Asynchronous with Recovery)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> shipmentET =
        eitherTMonad.flatMap(
            ctx -> {
              Kind<CompletableFutureKind<?>, Either<DomainError, ShipmentInfo>>
                  shipmentAttemptFutureKind =
                      steps.createShipmentAsync(
                          ctx.validatedOrder().orderId(), ctx.validatedOrder().shippingAddress());
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, ShipmentInfo>
                  shipmentAttemptET = EitherT.fromKind(shipmentAttemptFutureKind);

              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, ShipmentInfo>
                  recoveredShipmentET =
                      eitherTMonad.handleErrorWith(
                          shipmentAttemptET,
                          error ->
                              switch (error) {
                                case DomainError.ShippingError(String reason)
                                    when "Temporary Glitch".equals(reason) -> {
                                  dependencies.log(
                                      "WARN (Try Validation): Recovering from temporary shipping"
                                          + " glitch with default for order "
                                          + ctx.validatedOrder().orderId());
                                  yield eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"));
                                }
                                default -> eitherTMonad.raiseError(error);
                              });
              return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
            },
            paymentET);

    // Step 5: Map final context to FinalResult
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, FinalResult> finalResultET =
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

    // Step 6: Attempt Notification (Asynchronous) - Optional, non-critical failure
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, FinalResult>
        finalResultWithNotificationET =
            eitherTMonad.flatMap(
                finalResult -> {
                  Kind<CompletableFutureKind<?>, Either<DomainError, Void>> notifyFutureKind =
                      steps.notifyCustomerAsync(
                          orderData.customerId(), "Order processed: " + finalResult.orderId());
                  Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, Void> notifyET =
                      EitherT.fromKind(notifyFutureKind);

                  return eitherTMonad.map(
                      ignored -> finalResult,
                      eitherTMonad.handleError(
                          notifyET,
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

    // Unwrap the final EitherT
    EitherT<CompletableFutureKind<?>, DomainError, FinalResult> finalET =
        (EitherT<CompletableFutureKind<?>, DomainError, FinalResult>) finalResultWithNotificationET;
    return finalET.value();
  }

  /** Main method demonstrating the refactored workflow runners. */
  public static void main(String[] args) {
    // --- Setup Dependencies ---
    // Create a simple logger that prints to System.out
    Consumer<String> consoleLogger = System.out::println;
    // You could add other dependencies here (config, db connections, etc.)
    Dependencies appDependencies = new Dependencies(consoleLogger);

    // Create the runner, injecting dependencies
    OrderWorkflowRunner runner = new OrderWorkflowRunner(appDependencies);

    // --- Test Data (same as before) ---
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

    // --- Run Workflows ---
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

  // Generic helper method to run a workflow and print the outcome
  private static void runAndPrintResult(
      String label,
      java.util.function.Function<
              OrderData, Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>>
          runnerMethod,
      OrderData data) {
    System.out.println("=== Executing Workflow: " + label + " ===");
    Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>> resultKind =
        runnerMethod.apply(data);
    CompletableFuture<Either<DomainError, FinalResult>> future =
        CompletableFutureKindHelper.unwrap(resultKind);

    try {
      // Block and wait for the future to complete
      Either<DomainError, FinalResult> resultEither = future.join();
      // Print the final Either result (Left or Right)
      System.out.println("Final Result (" + label + "): " + resultEither);
    } catch (CompletionException e) {
      // Handle exceptions thrown *during* the CompletableFuture execution
      Throwable cause = e.getCause();
      System.err.println(
          "ERROR ("
              + label
              + "): Workflow execution failed unexpectedly: "
              + (cause != null ? cause : e));
      // Optionally log stack trace for debugging
      // e.printStackTrace();
    } catch (Exception e) {
      // Catch other potential exceptions (less likely with CompletableFuture.join)
      System.err.println("ERROR (" + label + "): Unexpected error during result processing: " + e);
    }
    System.out.println("========================================");
  }
}
