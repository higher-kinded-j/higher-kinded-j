package org.simulation.hkt.example.order.workflow;

import org.simulation.hkt.Kind;
import org.simulation.hkt.MonadError;
import org.simulation.hkt.either.Either;
import org.simulation.hkt.either.EitherKind; // Result type inside Future OR Sync result
import org.simulation.hkt.either.EitherKindHelper; // Needed to unwrap sync result
import org.simulation.hkt.either.EitherMonad; // Needed for inner Either ops
import org.simulation.hkt.example.order.error.DomainError;
import org.simulation.hkt.example.order.model.WorkflowModels;
import org.simulation.hkt.future.CompletableFutureKind;
import org.simulation.hkt.future.CompletableFutureKindHelper;
import org.simulation.hkt.future.CompletableFutureMonadError;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.simulation.hkt.example.order.model.WorkflowModels.WorkflowContext;
import static org.simulation.hkt.example.order.model.WorkflowModels.*;

/**
 * Orchestrates an order processing workflow using the HKT simulation framework.
 *
 * <p>This runner demonstrates:</p>
 * <ul>
 * <li>Chaining dependent operations using Monads (specifically {@link CompletableFutureMonadError}).</li>
 * <li>Handling potential failures at each step using {@link Either} as the result type within the future.</li>
 * <li>Integrating both synchronous and asynchronous workflow steps within a single monadic chain.</li>
 * <li>Using a {@link WorkflowContext} object to pass state between steps.</li>
 * <li>Basic error recovery using {@link MonadError} principles (applied to the inner {@link Either}).</li>
 * </ul>
 *
 * <p>The primary monad used is {@code MonadError<CompletableFutureKind<?>, Throwable>} which manages the
 * asynchronous execution flow via {@link CompletableFuture}. Each step's result is wrapped within this
 * future, typically as an {@code Either<DomainError, T>}, allowing business errors (`DomainError`) to be
 * handled separately from execution exceptions (`Throwable`).</p>
 */
public class OrderWorkflowRunner {

    private final OrderWorkflowSteps steps;
    /** The MonadError instance for CompletableFuture, handling async flow and Throwables. */
    private final MonadError<CompletableFutureKind<?>, Throwable> futureMonad;
    /** The MonadError instance for Either, used for operations on the results *within* the CompletableFuture. */
    private final MonadError<EitherKind<DomainError, ?>, DomainError> eitherMonad;


    /**
     * Constructs a runner with the necessary workflow step implementations.
     * @param steps An instance providing the logic for each workflow step.
     */
    public OrderWorkflowRunner(OrderWorkflowSteps steps) {
        this.steps = steps;
        this.futureMonad = new CompletableFutureMonadError();
        this.eitherMonad = new EitherMonad<>();
    }

    /**
     * Runs the order processing workflow, combining synchronous validation with asynchronous processing steps.
     *
     * <p>The workflow proceeds as follows:</p>
     * <ol>
     * <li>Validate Order (Sync): Checks initial data. Result is lifted into the async context.</li>
     * <li>Check Inventory (Async): Proceeds if validation succeeded.</li>
     * <li>Process Payment (Async): Proceeds if inventory check succeeded.</li>
     * <li>Create Shipment (Async): Proceeds if payment succeeded. Includes potential error recovery.</li>
     * <li>Map to Final Result: Transforms the final context into {@link FinalResult} if all steps succeeded.</li>
     * <li>Notify Customer (Async): Attempted as a side effect if the main workflow succeeded.</li>
     * </ol>
     * Errors (returned as {@code Either.Left<DomainError>}) halt the main flow, propagating the error to the final result.
     * Execution exceptions (Throwables) are handled by the {@code CompletableFutureMonadError}.
     *
     * @param orderData The initial data for the order.
     * @return A {@code Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>}. This represents an asynchronous
     * computation that will eventually complete. The contained {@link CompletableFuture} holds an {@link Either}
     * which will be a {@code Right(FinalResult)} on success, or a {@code Left(DomainError)} if any step failed.
     */
    public Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>> runOrderWorkflowMixed(OrderData orderData) {

        // Start with initial context wrapped in a successful Future<Either<DomainError, WorkflowContext>>
        WorkflowContext initialContext = WorkflowContext.start(orderData);
        Kind<CompletableFutureKind<?>, Either<DomainError, WorkflowContext>> initialKind =
                futureMonad.of(Either.right(initialContext));

        // --- Chain Steps using futureMonad.flatMap ---
        // Each lambda receives the result of the previous step: Either<DomainError, WorkflowContext>
        // It must return a Kind<CompletableFutureKind<?>, Either<DomainError, WorkflowContext>> for the next step.

        // Step 1: Validate Order (Synchronous)
        Kind<CompletableFutureKind<?>, Either<DomainError, WorkflowContext>> contextAfterValidation = futureMonad.flatMap(
                eitherCtx -> eitherCtx.fold(
                        // Case 1: Previous step (initial) resulted in an error. Propagate it.
                        // (This path shouldn't be hit here as initialKind is always Right)
                        error -> futureMonad.of(Either.<DomainError, WorkflowContext>left(error)), // Lift Either.left into Future

                        // Case 2: Previous step (initial) succeeded. Run the sync validation.
                        ctx -> {
                            // Call the synchronous step. It returns Kind<EitherKind<...>, ValidatedOrder>
                            Kind<EitherKind<DomainError, ?>, ValidatedOrder> syncResultKind = steps.validateOrder(ctx.initialData());
                            // Unwrap the Kind wrapper to get the raw Either result
                            Either<DomainError, ValidatedOrder> syncResultEither = EitherKindHelper.unwrap(syncResultKind);
                            // Use Either's map to update the context if validation was successful (Right)
                            Either<DomainError, WorkflowContext> contextEither = syncResultEither.map(ctx::withValidatedOrder);
                            // Lift the resulting Either (success or validation error) back into the async Future context
                            return futureMonad.of(contextEither);
                        }
                ),
                initialKind
        );

        // Step 2: Check Inventory (Asynchronous)
        Kind<CompletableFutureKind<?>, Either<DomainError, WorkflowContext>> contextAfterInventory = futureMonad.flatMap(
                eitherCtx -> eitherCtx.fold(
                        // Case 1: Previous step (validation) resulted in Left(error). Propagate it.
                        error -> futureMonad.of(Either.<DomainError, WorkflowContext>left(error)), // Lift Either.left into Future

                        // Case 2: Previous step (validation) succeeded. Run async inventory check.
                        ctx -> {
                            System.out.println("flatMap Check Inventory (async): Context has ValidatedOrder: " + ctx.validatedOrder().orderId());
                            // Call the async step. Returns Kind<CompletableFutureKind<?>, Either<DomainError, Void>>
                            Kind<CompletableFutureKind<?>, Either<DomainError, Void>> inventoryCheckFutureKind =
                                    steps.checkInventoryAsync(ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());
                            // Use futureMonad.map to process the result *when the future completes*.
                            // The lambda inside map operates on the Either<DomainError, Void>.
                            return futureMonad.map(
                                    eitherVoid -> eitherVoid.map(ignored -> ctx.withInventoryChecked()), // If inventory check is Right, update context.
                                    inventoryCheckFutureKind
                            );
                        }
                ),
                contextAfterValidation // Input is the Future result from Step 1
        );

        // Step 3: Process Payment (Asynchronous)
        Kind<CompletableFutureKind<?>, Either<DomainError, WorkflowContext>> contextAfterPayment = futureMonad.flatMap(
                eitherCtx -> eitherCtx.fold(
                        // Case 1: Previous step resulted in Left(error). Propagate it.
                        error -> futureMonad.of(Either.<DomainError, WorkflowContext>left(error)), // Lift Either.left into Future

                        // Case 2: Previous step succeeded. Run async payment processing.
                        ctx -> {
                            System.out.println("flatMap Process Payment (async): Context has ValidatedOrder: " + ctx.validatedOrder().orderId());
                            // Call async step. Returns Kind<CompletableFutureKind<?>, Either<DomainError, PaymentConfirmation>>
                            Kind<CompletableFutureKind<?>, Either<DomainError, PaymentConfirmation>> paymentFutureKind =
                                    steps.processPaymentAsync(ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
                            // Use futureMonad.map to process the result when the future completes.
                            return futureMonad.map(
                                    eitherPc -> eitherPc.map(ctx::withPaymentConfirmation), // If payment is Right, update context.
                                    paymentFutureKind
                            );
                        }
                ),
                contextAfterInventory // Input is the Future result from Step 2
        );

        // Step 4: Create Shipment (Asynchronous with Recovery)
        Kind<CompletableFutureKind<?>, Either<DomainError, WorkflowContext>> contextAfterShipment = futureMonad.flatMap(
                eitherCtx -> eitherCtx.fold(
                        // Case 1: Previous step resulted in Left(error). Propagate it.
                        error -> futureMonad.of(Either.<DomainError, WorkflowContext>left(error)), // Lift Either.left into Future

                        // Case 2: Previous step succeeded. Run async shipment creation.
                        ctx -> {
                            System.out.println("flatMap Create Shipment (async): Context has PaymentConfirmation: " + ctx.paymentConfirmation().transactionId());
                            // Call async step. Returns Kind<CompletableFutureKind<?>, Either<DomainError, ShipmentInfo>>
                            Kind<CompletableFutureKind<?>, Either<DomainError, ShipmentInfo>> shipmentAttemptKind =
                                    steps.createShipmentAsync(ctx.validatedOrder().orderId(), ctx.validatedOrder().shippingAddress());

                            // Apply recovery logic directly to the *result* of the async step (the Either).
                            // We use futureMonad.map to operate on the value inside the future.
                            Kind<CompletableFutureKind<?>, Either<DomainError, ShipmentInfo>> shipmentResultFutureKind =
                                    futureMonad.map(
                                            eitherShipment -> // eitherShipment is Either<DomainError, ShipmentInfo>
                                                    eitherShipment.fold( // Use Either's fold for error handling/recovery
                                                            error -> { // Handle Left(error)
                                                                if (error instanceof DomainError.ShippingError se && "Temporary Glitch".equals(se.reason())) {
                                                                    System.out.println("WARN (mixed): Recovering from temporary shipping glitch with default.");
                                                                    // Recover by returning a new Right value
                                                                    return Either.<DomainError, ShipmentInfo>right(new ShipmentInfo("DEFAULT_SHIPPING_USED"));
                                                                } else {
                                                                    // Non-recoverable error, return the original Left
                                                                    return Either.<DomainError, ShipmentInfo>left(error);
                                                                }
                                                            },
                                                            shipmentInfo -> // Handle Right(shipmentInfo)
                                                                    Either.<DomainError, ShipmentInfo>right(shipmentInfo) // Return original Right
                                                    )
                                            , shipmentAttemptKind); // Apply this mapping/folding to the shipment attempt future


                            // Map the potentially recovered shipment result (Future<Either<...>>) to update the context.
                            return futureMonad.map(
                                    eitherSi -> eitherSi.map(ctx::withShipmentInfo), // Map the inner Either
                                    shipmentResultFutureKind // Pass the Future containing the final recovered/original Either
                            );
                        }
                ),
                contextAfterPayment // Input is the Future result from Step 3
        );


        // Step 5: Map final context to FinalResult -> Future<Either<DomainError, FinalResult>>
        Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>> finalKind = futureMonad.map(
                eitherCtx -> eitherCtx.map(ctx -> { // Map the inner Either
                    System.out.println("Mapping final context to FinalResult (mixed) for Order: " + ctx.validatedOrder().orderId());
                    // Assuming successful path, context fields are non-null
                    return new FinalResult(
                            ctx.validatedOrder().orderId(),
                            ctx.paymentConfirmation().transactionId(),
                            ctx.shipmentInfo().trackingId()
                    );
                }),
                contextAfterShipment // Input is the Future result from Step 4
        );

        // Optional: Attempt Notification (Asynchronous) as a final step
        // This chains onto the finalKind future.
        Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>> finalKindWithNotificationAttempt =
                futureMonad.flatMap(eitherResult -> eitherResult.fold(
                        // Case 1: Main workflow failed. Propagate the error.
                        error -> futureMonad.of(Either.<DomainError, FinalResult>left(error)), // Lift error back into Future

                        // Case 2: Main workflow succeeded. Attempt notification.
                        finalResult -> {
                            Kind<CompletableFutureKind<?>, Either<DomainError, Void>> notifyKind =
                                    steps.notifyCustomerAsync(orderData.customerId(), "Order processed: " + finalResult.orderId());
                            // Process notification result but return the original FinalResult
                            return futureMonad.map(eitherNotify -> {
                                        eitherNotify.ifLeft(notifyError ->
                                                System.err.println("WARN (mixed): Notification failed for successful order "
                                                        + finalResult.orderId() + ": " + notifyError.message())
                                        );
                                        // Always return the original success result of the main workflow
                                        return Either.<DomainError, FinalResult>right(finalResult);
                                    },
                                    notifyKind // Map the notification future's result
                            );
                        }
                ), finalKind); // Apply this final step to the result of Step 5


        return finalKindWithNotificationAttempt;
    }


    /**
     * Main method for demonstrating the mixed sync/async workflow runner.
     * Runs several test cases and prints the final result or error.
     * Uses {@code .join()} to block and wait for the asynchronous results for simplicity in this example.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        OrderWorkflowSteps steps = new OrderWorkflowSteps();
        OrderWorkflowRunner runner = new OrderWorkflowRunner(steps);

        System.out.println("\n--- Running Mixed Sync/Async Workflow ---");

        // Test cases remain the same, but now run through the mixed workflow
        System.out.println("\n--- Running Good Order (Mixed) ---");
        OrderData goodData = new OrderData("Order-Mixed-001", "PROD-123", 2, "VALID_CARD", "123 Main St", "cust-good");
        runAndPrintResult("Good (Mixed)", runner, goodData);

        System.out.println("\n--- Running Bad Quantity Order (Mixed) ---");
        OrderData badQtyData = new OrderData("Order-Mixed-002", "PROD-456", 0, "VALID_CARD", "456 Oak Ave", "cust-ok");
        runAndPrintResult("Bad Qty (Mixed)", runner, badQtyData); // Fails on sync validation

        System.out.println("\n--- Running Stock Error Order (Mixed) ---");
        OrderData stockData = new OrderData("Order-Mixed-003", "OUT_OF_STOCK", 1, "VALID_CARD", "789 Pine Ln", "cust-stock");
        runAndPrintResult("Stock Error (Mixed)", runner, stockData); // Fails on async inventory check

        System.out.println("\n--- Running Payment Error Order (Mixed) ---");
        OrderData paymentData = new OrderData("Order-Mixed-004", "PROD-789", 1, "INVALID_CARD", "101 Maple Dr", "cust-pay");
        runAndPrintResult("Payment Error (Mixed)", runner, paymentData); // Fails on async payment

        System.out.println("\n--- Running Recoverable Shipping Error Order (Mixed) ---");
        OrderData recoverableShippingData = new OrderData("FAIL_SHIPMENT", "PROD-SHIP-REC", 1, "VALID_CARD", "1 Recovery Lane", "cust-ship-rec");
        runAndPrintResult("Recoverable Shipping (Mixed)", runner, recoverableShippingData);

        System.out.println("\n--- Running Non-Recoverable Shipping Error Order (Mixed) ---");
        OrderData nonRecoverableShippingData = new OrderData("Order-Mixed-005", "PROD-SHIP", 1, "VALID_CARD", "", "cust-ship");
        runAndPrintResult("Non-Recoverable Shipping (Mixed)", runner, nonRecoverableShippingData);

    }

    /**
     * Helper method to execute the workflow for given data, block for the result, and print it.
     * @param label A label for the test case.
     * @param runner The workflow runner instance.
     * @param data The input order data.
     */
    private static void runAndPrintResult(String label, OrderWorkflowRunner runner, OrderData data) {
        System.out.println("Starting workflow for: " + label);
        // Call the mixed workflow method
        Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>> resultKind = runner.runOrderWorkflowMixed(data);
        // Unwrap the Kind to get the CompletableFuture
        CompletableFuture<Either<DomainError, FinalResult>> future = CompletableFutureKindHelper.unwrap(resultKind);

        try {
            // Block and wait for the future to complete.
            // join() throws CompletionException if the future completed exceptionally.
            Either<DomainError, FinalResult> resultEither = future.join();
            // If join() succeeds, the future completed normally (though the Either might be Left).
            System.out.println("Final Result (" + label + "): " + resultEither);
        } catch (CompletionException e) {
            // Handle exceptions from the CompletableFuture itself (e.g., infrastructure issues, bugs)
            Throwable cause = e.getCause();
            System.err.println("Workflow execution failed unexpectedly for " + label + ": " + (cause != null ? cause : e));
        } catch (Exception e) {
            // Catch any other unexpected errors during join/processing
            System.err.println("Unexpected error for " + label + ": " + e);
        }
        System.out.println("----------------------------------------"); // Separator
    }
}
