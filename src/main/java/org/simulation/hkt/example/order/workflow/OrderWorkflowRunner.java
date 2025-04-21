package org.simulation.hkt.example.order.workflow;

import org.simulation.hkt.Kind;
import org.simulation.hkt.MonadError; // Use MonadError for potential error handling
import org.simulation.hkt.either.Either;
import org.simulation.hkt.either.EitherKind;
import org.simulation.hkt.either.EitherKindHelper;
import org.simulation.hkt.either.EitherMonad;
import org.simulation.hkt.example.order.error.DomainError;
import org.simulation.hkt.example.order.model.WorkflowModels;

// Import the new context
import static org.simulation.hkt.example.order.model.WorkflowModels.WorkflowContext;

import static org.simulation.hkt.example.order.model.WorkflowModels.*;

public class OrderWorkflowRunner {

    private final OrderWorkflowSteps steps;
    // Use MonadError interface for flexibility, cast if specific methods needed
    private final MonadError<EitherKind<DomainError, ?>, DomainError> monad;

    public OrderWorkflowRunner(OrderWorkflowSteps steps) {
        this.steps = steps;
        // Instantiate MonadError for Either<DomainError, ?>
        this.monad = new EitherMonad<>();
    }

    public Kind<EitherKind<DomainError, ?>, FinalResult> runOrderWorkflow(OrderData orderData) {

        // Start with initial context
        WorkflowContext initialContext = WorkflowContext.start(orderData);
        Kind<EitherKind<DomainError, ?>, WorkflowContext> initialKind = monad.of(initialContext);

        // Step 1: Validate Order -> Update context with ValidatedOrder
        Kind<EitherKind<DomainError, ?>, WorkflowContext> contextAfterValidation = monad.flatMap(
                ctx -> monad.map(
                        ctx::withValidatedOrder,
                        steps.validateOrder(ctx.initialData()) // Pass initial data
                ),
                initialKind
        );

        // Step 2: Check Inventory -> Update context flag
        Kind<EitherKind<DomainError, ?>, WorkflowContext> contextAfterInventory = monad.flatMap(
                ctx -> {
                    System.out.println("flatMap Check Inventory: Context has ValidatedOrder: " + ctx.validatedOrder().orderId());
                    return monad.map(
                            ignoredVoid -> ctx.withInventoryChecked(), // Update context flag
                            steps.checkInventory(ctx.validatedOrder().productId(), ctx.validatedOrder().quantity())
                    );
                },
                contextAfterValidation
        );

        // Step 3: Process Payment -> Update context with PaymentConfirmation
        Kind<EitherKind<DomainError, ?>, WorkflowContext> contextAfterPayment = monad.flatMap(
                ctx -> {
                    System.out.println("flatMap Process Payment: Context has ValidatedOrder: " + ctx.validatedOrder().orderId());
                    return monad.map(
                            ctx::withPaymentConfirmation,
                            steps.processPayment(ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount())
                    );
                },
                contextAfterInventory // Depends on inventory check completing successfully
        );

        // Step 4: Create Shipment -> Update context with ShipmentInfo
        Kind<EitherKind<DomainError, ?>, WorkflowContext> contextAfterShipment = monad.flatMap(
                ctx -> {
                    System.out.println("flatMap Create Shipment: Context has PaymentConfirmation: " + ctx.paymentConfirmation().transactionId());
                    return monad.map(
                            ctx::withShipmentInfo,
                            steps.createShipment(ctx.validatedOrder().orderId(), ctx.validatedOrder().shippingAddress())
                    );
                },
                contextAfterPayment // Depends on payment completing successfully
        );

        // Step 5: Map final context to FinalResult
        Kind<EitherKind<DomainError, ?>, FinalResult> finalKind = monad.map(
                ctx -> {
                    System.out.println("Mapping final context to FinalResult for Order: " + ctx.validatedOrder().orderId());
                    return new FinalResult(
                            ctx.validatedOrder().orderId(),
                            ctx.paymentConfirmation().transactionId(),
                            ctx.shipmentInfo().trackingId()
                    );
                },
                contextAfterShipment
        );

        // --- Optional: Notification ---
        // Run as a side-effect after the main flow potentially succeeds.
        // We can inspect the final result and decide whether to notify.
        // This avoids complicating the main monadic flow.
        Either<DomainError, FinalResult> finalResultEither = EitherKindHelper.unwrap(finalKind);
        finalResultEither.ifRight(finalResult -> {
            // Attempt notification only on overall success
            Kind<EitherKind<DomainError, ?>, Void> notifyKind = steps.notifyCustomer(
                    orderData.customerId(), // Get customerId from original data
                    "Order processed: " + finalResult.orderId()
            );
            // Optionally handle/log notification failure without failing the main result
            Either<DomainError, Void> notifyEither = EitherKindHelper.unwrap(notifyKind);
            notifyEither.ifLeft(error -> {
                System.err.println("WARN: Notification failed for successful order "
                        + finalResult.orderId() + ": " + error.message());
            });
        });

        return finalKind;
    }


    // Main method remains the same as it tests the overall runOrderWorkflow outcome
    public static void main(String[] args) {
        OrderWorkflowSteps steps = new OrderWorkflowSteps();
        OrderWorkflowRunner runner = new OrderWorkflowRunner(steps);

        // --- Test Case 1: Success ---
        System.out.println("\n--- Running Good Order ---");
        OrderData goodData = new OrderData("Order-001", "PROD-123", 2, "VALID_CARD", "123 Main St", "cust-good");
        Kind<EitherKind<DomainError, ?>, FinalResult> resultGoodKind = runner.runOrderWorkflow(goodData);
        Either<DomainError, FinalResult> resultGood = EitherKindHelper.unwrap(resultGoodKind);
        System.out.println("Final Result (Good): " + resultGood);
        // Expected: Right(FinalResult[orderId=Order-001, transactionId=txn-..., trackingId=track-...])

        // --- Test Case 2: Validation Error ---
        System.out.println("\n--- Running Bad Quantity Order ---");
        OrderData badQtyData = new OrderData("Order-002", "PROD-456", 0, "VALID_CARD", "456 Oak Ave", "cust-ok");
        Kind<EitherKind<DomainError, ?>, FinalResult> resultBadQtyKind = runner.runOrderWorkflow(badQtyData);
        Either<DomainError, FinalResult> resultBadQty = EitherKindHelper.unwrap(resultBadQtyKind);
        System.out.println("Final Result (Bad Qty): " + resultBadQty);
        // Expected: Left(ValidationError[message=Quantity must be positive for order Order-002])


        // --- Test Case 3: Stock Error ---
        System.out.println("\n--- Running Stock Error Order ---");
        OrderData stockData = new OrderData("Order-003", "OUT_OF_STOCK", 1, "VALID_CARD", "789 Pine Ln", "cust-stock");
        Kind<EitherKind<DomainError, ?>, FinalResult> resultStockKind = runner.runOrderWorkflow(stockData);
        Either<DomainError, FinalResult> resultStock = EitherKindHelper.unwrap(resultStockKind);
        System.out.println("Final Result (Stock Error): " + resultStock);
        // Expected: Left(StockError[productId=OUT_OF_STOCK])

        // --- Test Case 4: Payment Error ---
        System.out.println("\n--- Running Payment Error Order ---");
        OrderData paymentData = new OrderData("Order-004", "PROD-789", 1, "INVALID_CARD", "101 Maple Dr", "cust-pay");
        Kind<EitherKind<DomainError, ?>, FinalResult> resultPaymentKind = runner.runOrderWorkflow(paymentData);
        Either<DomainError, FinalResult> resultPayment = EitherKindHelper.unwrap(resultPaymentKind);
        System.out.println("Final Result (Payment Error): " + resultPayment);
        // Expected: Left(PaymentError[reason=Card declined])

        // --- Test Case 5: Shipping Error ---
        System.out.println("\n--- Running Shipping Error Order ---");
        OrderData shippingData = new OrderData("Order-005", "PROD-SHIP", 1, "VALID_CARD", "", "cust-ship"); // Empty address
        Kind<EitherKind<DomainError, ?>, FinalResult> resultShippingKind = runner.runOrderWorkflow(shippingData);
        Either<DomainError, FinalResult> resultShipping = EitherKindHelper.unwrap(resultShippingKind);
        System.out.println("Final Result (Shipping Error): " + resultShipping);
        // Expected: Left(ShippingError[reason=Address invalid for order Order-005]) OR the random failure
    }
}