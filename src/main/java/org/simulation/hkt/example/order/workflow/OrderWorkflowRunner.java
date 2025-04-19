package org.simulation.hkt.example.order.workflow;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;
import org.simulation.hkt.either.Either;
import org.simulation.hkt.either.EitherKind;
import org.simulation.hkt.either.EitherKindHelper;
import org.simulation.hkt.either.EitherMonad;
import org.simulation.hkt.example.order.error.DomainError;
import org.simulation.hkt.example.order.model.WorkflowModels;
import static org.simulation.hkt.example.order.error.DomainError.*;


import static org.simulation.hkt.example.order.model.WorkflowModels.*;

public class OrderWorkflowRunner {

  private final OrderWorkflowSteps steps;
  // Instantiate the Monad for Either<DomainError, ?>
  private final Monad<EitherKind<DomainError, ?>> monad;

  public OrderWorkflowRunner(OrderWorkflowSteps steps) {
    this.steps = steps;
    this.monad = new EitherMonad<>(); // Instantiate the Monad for our specific Left type
  }

  public Kind<EitherKind<DomainError, ?>, WorkflowModels.FinalResult> runOrderWorkflow(OrderData orderData) {

    // Step 1: Validate Order -> Returns Kind<..., ValidatedOrder>
    Kind<EitherKind<DomainError, ?>, ValidatedOrder> validatedOrderKind = steps.validateOrder(orderData);

    // Step 2: Check Inventory (depends on ValidatedOrder)
    // We use flatMap to chain. The function inside flatMap takes ValidatedOrder and returns Kind<..., Void>
    Kind<EitherKind<DomainError, ?>, Void> inventoryCheckedKind = monad.flatMap(
        (ValidatedOrder validatedOrder) -> {
          System.out.println("flatMap 1: Input ValidatedOrder: " + validatedOrder.orderId());
          return steps.checkInventory(validatedOrder.productId(), validatedOrder.quantity());
        },
        validatedOrderKind // Input to the first flatMap
    );

    // Step 3: Process Payment (depends on ValidatedOrder, but runs *after* inventory check)
    // We flatMap again. Input comes *implicitly* from the success path of the previous steps.
    // The function needs access to the *original* ValidatedOrder result.
    // This highlights a complexity: flatMap only passes the *immediate* previous result.
    // We need to structure the flatMaps to pass necessary data along.

    // --- Restructuring for data passing ---
    // Instead of returning Void from checkInventory in the chain, let's pass ValidatedOrder through
    Kind<EitherKind<DomainError, ?>, ValidatedOrder> inventoryCheckedKindPassingData = monad.flatMap(
        (ValidatedOrder validatedOrder) -> {
          System.out.println("flatMap 1 (pass data): Input ValidatedOrder: " + validatedOrder.orderId());
          // Check inventory, but if successful, return the original validatedOrder wrapped in the context
          Kind<EitherKind<DomainError, ?>, Void> inventoryResult = steps.checkInventory(validatedOrder.productId(), validatedOrder.quantity());
          // If inventory check succeeds (is Right), return success(validatedOrder), otherwise return the failure
          // This requires checking the result Kind - Monad itself doesn't expose this easily.
          // A better approach might be to use Either's methods inside the step if possible,
          // or use a more advanced MonadError typeclass if available.
          // Let's simulate the success path assuming checkInventory returns success(null).
          // In a real library, you'd have better ways (like map + flatMap, or dedicated >> operator).
          // For this simulation, we'll assume inventory passes and re-wrap validatedOrder.
          // This is awkward and shows limitations of simple Monad without MonadError helpers.

          // A more practical way in this simulation: use map inside flatMap if previous step was just for effect
          return monad.map(ignoredVoid -> validatedOrder, // If inventory check succeeds, map its Void result back to validatedOrder
              steps.checkInventory(validatedOrder.productId(), validatedOrder.quantity())
          );

        },
        validatedOrderKind
    );


    // Step 3: Process Payment (depends on ValidatedOrder)
    Kind<EitherKind<DomainError, ?>, PaymentConfirmation> paymentProcessedKind = monad.flatMap(
        (ValidatedOrder validatedOrder) -> { // Now gets ValidatedOrder from the previous step
          System.out.println("flatMap 2: Input ValidatedOrder: " + validatedOrder.orderId());
          return steps.processPayment(validatedOrder.paymentDetails(), validatedOrder.amount());
        },
        inventoryCheckedKindPassingData // Use the Kind that passes ValidatedOrder
    );

    // Step 4: Create Shipment (depends on ValidatedOrder and PaymentConfirmation)
    // We need both! This requires nesting flatMap calls or combining results using Applicative/mapN if available.
    // Let's nest flatMap for simplicity here, passing necessary data.

    Kind<EitherKind<DomainError, ?>, ShipmentInfo> shipmentCreatedKind = monad.flatMap(
        (ValidatedOrder validatedOrder) -> // Outer flatMap gets ValidatedOrder
            monad.flatMap(
                (PaymentConfirmation paymentConf) -> { // Inner flatMap gets PaymentConfirmation
                  System.out.println("flatMap 3 (nested): Order: " + validatedOrder.orderId() + ", Payment: " + paymentConf.transactionId());
                  // Now we have both validatedOrder and paymentConf
                  return steps.createShipment(validatedOrder.orderId(), validatedOrder.shippingAddress());
                },
                steps.processPayment(validatedOrder.paymentDetails(), validatedOrder.amount()) // Re-call payment within this scope - inefficient!
                // *** This nesting shows the simulation's awkwardness for complex data dependencies. ***
                // A better way needs Applicative/mapN or passing tuples/records through flatMap.
                // Let's simplify for the demo: Assume createShipment only needs orderId and address from validatedOrder
            ),
        inventoryCheckedKindPassingData // Start nesting from here
    );

    // --- Simpler approach: Passing a Tuple/Record through ---
    // Let's redefine the flow slightly passing necessary data

    // flatMap 1: validate -> checkInventory -> return ValidatedOrder
    Kind<EitherKind<DomainError, ?>, ValidatedOrder> step1_2Result = monad.flatMap(
        (ValidatedOrder vo) -> monad.map(ignoredVoid -> vo, steps.checkInventory(vo.productId(), vo.quantity())),
        steps.validateOrder(orderData)
    );

    // flatMap 2: processPayment -> return Pair<ValidatedOrder, PaymentConfirmation>
    Kind<EitherKind<DomainError, ?>, Pair<ValidatedOrder, PaymentConfirmation>> step3Result = monad.flatMap(
        (ValidatedOrder vo) -> monad.map(pc -> new Pair<>(vo, pc), steps.processPayment(vo.paymentDetails(), vo.amount())),
        step1_2Result
    );

    // flatMap 3: createShipment -> return FinalResult
    Kind<EitherKind<DomainError, ?>, FinalResult> finalKind = monad.flatMap(
        (Pair<ValidatedOrder, PaymentConfirmation> pair) -> {
          ValidatedOrder vo = pair.left();
          PaymentConfirmation pc = pair.right();
          // Now call createShipment
          Kind<EitherKind<DomainError, ?>, ShipmentInfo> shipmentResult = steps.createShipment(vo.orderId(), vo.shippingAddress());
          // Map the ShipmentInfo to FinalResult
          return monad.map(si -> new FinalResult(vo.orderId(), pc.transactionId(), si.trackingId()), shipmentResult);
        },
        step3Result
    );


    // Optional: Notify customer (run separately, perhaps ignore failure)
    monad.flatMap(
        (FinalResult result) -> steps.notifyCustomer(orderData.customerId(), "Order processed: " + result.orderId()),
        finalKind // Run only if the main workflow succeeded
    ); // We ignore the result of notify

    return finalKind;
  }

  // Simple Pair utility (or use a library like Vavr/Jool)
  record Pair<L, R>(L left, R right) {}


  // --- Main method for testing ---
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


