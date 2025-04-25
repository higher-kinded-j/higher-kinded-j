// src/main/java/org/simulation/example/order/workflow/OrderWorkflowRunner.java
package org.simulation.example.order.workflow;

import static org.simulation.example.order.model.WorkflowModels.*;
import static org.simulation.example.order.model.WorkflowModels.WorkflowContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.simulation.example.order.error.DomainError;
import org.simulation.hkt.Kind;
import org.simulation.hkt.MonadError;
import org.simulation.hkt.either.Either;
import org.simulation.hkt.either.EitherKindHelper; // Needed for unwrapping sync step result
import org.simulation.hkt.future.CompletableFutureKind;
import org.simulation.hkt.future.CompletableFutureKindHelper;
import org.simulation.hkt.future.CompletableFutureMonadError;
import org.simulation.hkt.trans.EitherT;
import org.simulation.hkt.trans.EitherTKind;
import org.simulation.hkt.trans.EitherTMonad;
// Import Try related classes
import org.simulation.hkt.trymonad.Try;
import org.simulation.hkt.trymonad.TryKind;
import org.simulation.hkt.trymonad.TryKindHelper;

/**
 * Orchestrates an order processing workflow using the HKT simulation framework, primarily
 * leveraging the {@link EitherT} monad transformer over {@link CompletableFuture}.
 *
 * <h2>Core Concepts Demonstrated:</h2>
 *
 * <ul>
 *   <li><b>Monad Transformers (EitherT):</b> Simplifies working with nested monadic structures like
 *       {@code CompletableFuture<Either<DomainError, T>>}. It allows chaining operations within the
 *       nested context using standard {@code flatMap} and {@code map}, automatically handling the
 *       short-circuiting logic of the inner {@link Either}.
 *   <li><b>Asynchronous Flow (CompletableFuture):</b> The outer monad {@code F} in {@code
 *       EitherT<F, L, R>} is {@code CompletableFutureKind<?>}, managed by {@link
 *       CompletableFutureMonadError}. This handles the asynchronous execution of steps and manages
 *       system-level exceptions (`Throwable`).
 *   <li><b>Domain Error Handling (Either):</b> The inner structure is {@code Either<DomainError,
 *       T>}. {@link DomainError} represents specific, known business rule violations or expected
 *       failure modes (e.g., validation error, out of stock). The {@code Left} side of the {@code
 *       Either} carries these domain errors.
 *   <li><b>Exception Handling (Try):</b> Demonstrates an alternative for synchronous steps where
 *       failures might manifest as thrown exceptions rather than specific domain errors. {@link
 *       Try} captures these exceptions, which can then be converted to an appropriate {@link
 *       DomainError}.
 *   <li><b>MonadError Abstraction:</b> Uses the {@link MonadError} interface for both the outer
 *       {@code CompletableFuture} (handling {@code Throwable}) and the combined {@code EitherT}
 *       (handling {@code DomainError}). This allows for consistent error handling and recovery
 *       patterns.
 *   <li><b>Integration of Sync/Async Steps:</b> Shows how to lift the results of both synchronous
 *       (returning {@code Either} or {@code Try}) and asynchronous (returning {@code
 *       CompletableFuture<Either>}) steps into the unified {@code EitherT} context for seamless
 *       composition.
 * </ul>
 *
 * <h2>Workflow Structure:</h2>
 *
 * The workflow is defined as a sequence of operations chained using {@code eitherTMonad.flatMap}.
 * If any step results in a {@code Left<DomainError>} (either directly or converted from a {@code
 * Try.Failure}), subsequent steps in the main chain are automatically skipped, and the {@code Left}
 * value is propagated. System-level errors during async execution (e.g., network issues simulated
 * by exceptions in steps) are caught by the underlying {@code CompletableFuture} mechanism.
 */
public class OrderWorkflowRunner {

  private final OrderWorkflowSteps steps;
  private final MonadError<CompletableFutureKind<?>, Throwable> futureMonad;
  private final MonadError<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, DomainError>
      eitherTMonad;

  public OrderWorkflowRunner(OrderWorkflowSteps steps) {
    this.steps = steps;
    this.futureMonad = new CompletableFutureMonadError();
    this.eitherTMonad = new EitherTMonad<>(this.futureMonad);
  }

  /**
   * Runs the order processing workflow using EitherT, with the validation step returning {@code
   * Either<DomainError, ValidatedOrder>}.
   *
   * <p>This approach is suitable when validation rules explicitly define different types of domain
   * errors that should be propagated directly. See {@link
   * OrderWorkflowSteps#validateOrder(OrderData)}.
   *
   * @param orderData The initial data for the order.
   * @return A {@code Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>} representing
   *     the final outcome.
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>> runOrderWorkflowEitherT(
      OrderData orderData) {

    WorkflowContext initialContext = WorkflowContext.start(orderData);
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> initialET =
        eitherTMonad.of(initialContext);

    // Step 1: Validate Order (Synchronous - returns Either)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> validatedET =
        eitherTMonad.flatMap(
            ctx -> {
              System.out.println(
                  "flatMap Validate Order (sync - Either): Processing Order "
                      + ctx.initialData().orderId());
              Either<DomainError, ValidatedOrder> syncResultEither =
                  EitherKindHelper.unwrap(
                      steps.validateOrder(ctx.initialData())); // Unwrap Kind<EitherKind, ...>
              // Lift Either into EitherT<Future, ...>
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, ValidatedOrder>
                  validatedOrderET = EitherT.fromEither(futureMonad, syncResultEither);
              return eitherTMonad.map(ctx::withValidatedOrder, validatedOrderET);
            },
            initialET);

    // --- Subsequent steps are the same as before ---

    // Step 2: Check Inventory (Asynchronous)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> inventoryET =
        eitherTMonad.flatMap(
            ctx -> {
              System.out.println(
                  "flatMap Check Inventory (async): Context has ValidatedOrder: "
                      + ctx.validatedOrder().orderId());
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
              System.out.println(
                  "flatMap Process Payment (async): Context has ValidatedOrder: "
                      + ctx.validatedOrder().orderId());
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
              System.out.println(
                  "flatMap Create Shipment (async): Context has PaymentConfirmation: "
                      + ctx.paymentConfirmation().transactionId());
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
                          error -> { // Handles DomainError
                            if (error instanceof DomainError.ShippingError(String reason)
                                && "Temporary Glitch".equals(reason)) {
                              System.out.println(
                                  "WARN (EitherT): Recovering from temporary shipping glitch with"
                                      + " default.");
                              return eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"));
                            } else {
                              return eitherTMonad.raiseError(error);
                            }
                          });
              return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
            },
            paymentET);

    // Step 5: Map final context to FinalResult
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, FinalResult> finalResultET =
        eitherTMonad.map(
            ctx -> {
              System.out.println(
                  "Mapping final context to FinalResult (EitherT) for Order: "
                      + ctx.validatedOrder().orderId());
              return new FinalResult(
                  ctx.validatedOrder().orderId(),
                  ctx.paymentConfirmation().transactionId(),
                  ctx.shipmentInfo().trackingId());
            },
            shipmentET);

    // Optional: Attempt Notification (Asynchronous) as a final step
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
                            System.err.println(
                                "WARN (EitherT): Notification failed for successful order "
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

  /**
   * Runs the order processing workflow using EitherT, with the validation step returning {@code
   * Try<ValidatedOrder>}.
   *
   * <p>This approach is suitable when validation logic might throw runtime exceptions that need to
   * be captured and converted into a {@link DomainError}. See {@link
   * OrderWorkflowSteps#validateOrderWithTry(OrderData)}.
   *
   * @param orderData The initial data for the order.
   * @return A {@code Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>} representing
   *     the final outcome.
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>
      runOrderWorkflowEitherTWithTryValidation(OrderData orderData) {

    WorkflowContext initialContext = WorkflowContext.start(orderData);
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> initialET =
        eitherTMonad.of(initialContext);

    // Step 1: Validate Order (Synchronous - returns Try)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> validatedET =
        eitherTMonad.flatMap(
            ctx -> {
              System.out.println(
                  "flatMap Validate Order (sync - Try): Processing Order "
                      + ctx.initialData().orderId());
              // Synchronous step returns Kind<TryKind<?>, ValidatedOrder>
              Kind<TryKind<?>, ValidatedOrder> tryResultKind =
                  steps.validateOrderWithTry(ctx.initialData());
              // Unwrap the Kind<TryKind, ...>
              Try<ValidatedOrder> tryResult = TryKindHelper.unwrap(tryResultKind);

              Either<DomainError, ValidatedOrder> eitherResult =
                  tryResult.fold(
                      Either::right, // Success(v) -> Right(v)
                      // Failure(t) -> Left(DomainError). Convert generic Throwable to a specific
                      // DomainError.
                      throwable ->
                          Either.left(new DomainError.ValidationError(throwable.getMessage())));

              // Lift the converted Either result into EitherT<Future, ..., ValidatedOrder>
              Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, ValidatedOrder>
                  validatedOrderET = EitherT.fromEither(futureMonad, eitherResult);
              // Map the success case to update the context
              return eitherTMonad.map(ctx::withValidatedOrder, validatedOrderET);
            },
            initialET);

    // Step 2: Check Inventory (Asynchronous)
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> inventoryET =
        eitherTMonad.flatMap(
            ctx -> {
              System.out.println(
                  "flatMap Check Inventory (async): Context has ValidatedOrder: "
                      + ctx.validatedOrder().orderId());
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
              System.out.println(
                  "flatMap Process Payment (async): Context has ValidatedOrder: "
                      + ctx.validatedOrder().orderId());
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
              System.out.println(
                  "flatMap Create Shipment (async): Context has PaymentConfirmation: "
                      + ctx.paymentConfirmation().transactionId());
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
                                  System.out.printf(
                                      "WARN (%s Validation): Recovering from temporary shipping"
                                          + " glitch with default.%n",
                                      // Determine context based on which method we are in -
                                      // slightly hacky
                                      ctx.validatedOrder() != null ? "Either/Try" : "Unknown");
                                  // Recover by returning a successful EitherT with default info
                                  yield eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"));
                                }
                                // Add more cases for other specific DomainError subtypes if needed
                                // case DomainError.ValidationError ve -> { /* Handle differently */
                                // yield eitherTMonad.raiseError(ve); }

                                // Default case: Re-raise any other DomainError
                                default -> eitherTMonad.raiseError(error);
                              }); // End of handleErrorWith
              // Map the (potentially recovered) ShipmentInfo back into the context
              return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
            },
            paymentET);

    // Step 5: Map final context to FinalResult
    Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, FinalResult> finalResultET =
        eitherTMonad.map(
            ctx -> {
              System.out.println(
                  "Mapping final context to FinalResult (Try Validation) for Order: "
                      + ctx.validatedOrder().orderId());
              return new FinalResult(
                  ctx.validatedOrder().orderId(),
                  ctx.paymentConfirmation().transactionId(),
                  ctx.shipmentInfo().trackingId());
            },
            shipmentET);

    // Optional: Attempt Notification (Asynchronous) as a final step
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
                            System.err.println(
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

  /** Main method demonstrating both workflow runners. */
  public static void main(String[] args) {
    OrderWorkflowSteps steps = new OrderWorkflowSteps();
    OrderWorkflowRunner runner = new OrderWorkflowRunner(steps);

    // --- Test Data ---
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
    // For Bad Qty, validateOrderWithTry throws IllegalArgumentException, which Try catches,
    // and the runner converts it to Left(ValidationError("Quantity must be positive..."))
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

  // Generic helper method using a Function to pass the runner method
  private static void runAndPrintResult(
      String label,
      java.util.function.Function<
              OrderData, Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>>
          runnerMethod,
      OrderData data) {
    System.out.println("Starting workflow for: " + label);
    Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>> resultKind =
        runnerMethod.apply(data);
    CompletableFuture<Either<DomainError, FinalResult>> future =
        CompletableFutureKindHelper.unwrap(resultKind);

    try {
      Either<DomainError, FinalResult> resultEither = future.join();
      System.out.println("Final Result (" + label + "): " + resultEither);
    } catch (CompletionException e) {
      Throwable cause = e.getCause();
      // Default handling for unexpected execution errors
      System.err.println(
          "Workflow execution failed unexpectedly for "
              + label
              + ": "
              + (cause != null ? cause : e));
      // Optionally, you could add specific checks for other RuntimeExceptions if needed
      // e.g., if (cause instanceof SomeSpecificRuntimeException) { ... }
    } catch (Exception e) {
      // Catch other potential exceptions during join/processing (like CancellationException if not
      // caught above)
      System.err.println("Unexpected error for " + label + ": " + e);
    }
    System.out.println("----------------------------------------");
  }
}
