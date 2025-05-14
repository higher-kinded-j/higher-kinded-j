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
import org.higherkindedj.hkt.trymonad.TryKindHelper;
import org.jspecify.annotations.NonNull;

/**
 * Orchestrates an order processing workflow using the Higher-Kinded-J framework, primarily
 * leveraging the {@link EitherT} monad transformer over {@link CompletableFuture}.
 *
 * <p>This example primarily demonstrates:
 *
 * <ul>
 * <li><b>Dependency Injection:</b> The {@link OrderWorkflowSteps} instance is now created with a
 * {@link Dependencies} object, making dependencies like logging explicit.
 * <li><b>Structured Logging:</b> Workflow steps use the logger provided in {@code Dependencies}
 * instead of direct console output.
 * <li><b>EitherT for Async/Error Flow:</b> Continues to use {@code EitherT} to manage the nested
 * {@code CompletableFuture<Either<DomainError, T>>} structure, handling asynchronicity and
 * domain errors gracefully.
 * <li><b>Integration of Sync/Async Steps:</b> Shows lifting results from synchronous steps
 * (returning {@code Either} or {@code Try}) and asynchronous steps into the unified {@code
 * EitherT} context.
 * <li><b>Error Handling and Recovery:</b> Uses {@code MonadError} capabilities of {@code
 * EitherTMonad} to handle specific {@link DomainError}s (e.g., recoverable shipping errors).
 * <li><b>Usage of Kind Helpers:</b> Demonstrates using {@link EitherTKindHelper} for
 * wrapping/unwrapping {@code EitherT} instances when interacting with the HKT system.
 * <li><b>Use of `var` keyword:</b> Local variables use `var` for conciseness where type is clear.
 * </ul>
 *
 * <h2>Workflow Structure:</h2>
 *
 * <p>The workflow is a sequence of operations chained using {@code eitherTMonad.flatMap}. If any
 * step results in a {@code Left<DomainError>}, subsequent steps are skipped. System-level errors
 * during async execution are caught by the underlying {@code CompletableFuture}. Logging occurs
 * within each step via the injected logger.
 * See `order-walkthrough.md` for a detailed step-by-step explanation.
 */
public class OrderWorkflowRunner {

  private final @NonNull OrderWorkflowSteps steps;
  private final @NonNull Dependencies dependencies;
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
    var appDependencies = new Dependencies(consoleLogger); 
    var runner = new OrderWorkflowRunner(appDependencies); 

    // Test Data
    var goodData = 
        new OrderData("Order-Good-001", "PROD-123", 2, "VALID_CARD", "123 Main St", "cust-good");
    var badQtyData = 
        new OrderData("Order-BadQty-002", "PROD-456", 0, "VALID_CARD", "456 Oak Ave", "cust-ok");
    var stockData = 
        new OrderData(
            "Order-Stock-003", "OUT_OF_STOCK", 1, "VALID_CARD", "789 Pine Ln", "cust-stock");
    var paymentData = 
        new OrderData("Order-Pay-004", "PROD-789", 1, "INVALID_CARD", "101 Maple Dr", "cust-pay");
    var recoverableShippingData = 
        new OrderData(
            "FAIL_SHIPMENT", "PROD-SHIP-REC", 1, "VALID_CARD", "1 Recovery Lane", "cust-ship-rec");
    var nonRecoverableShippingData = 
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
    // Add more test cases for Try validation if desired
  }

  private static void runAndPrintResult(
      String label,
      java.util.function.Function<
          OrderData, Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>>
          runnerMethod,
      OrderData data) {
    System.out.println("=== Executing Workflow: " + label + " for Order: " + data.orderId() + " ===");
    var resultKind = runnerMethod.apply(data); 
    var future = CompletableFutureKindHelper.unwrap(resultKind); 

    try {
      var resultEither = future.join(); 
      System.out.println("Final Result (" + label + "): " + resultEither);
    } catch (CompletionException e) {
      var cause = e.getCause(); 
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

  // See order-walkthrough.md for detailed explanation of this method
  public Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>
  runOrderWorkflowEitherT(OrderData orderData) {

    dependencies.log(
        "Starting Order Workflow [runOrderWorkflowEitherT] for Order: " + orderData.orderId());

    // --- Initialisation (Corresponds to "Initial State" in order-walkthrough.md) ---
    var initialContext = WorkflowContext.start(orderData); 
    var initialET = eitherTMonad.of(initialContext); 

    // --- Step 1: Validate Order (Corresponds to Step 1 in order-walkthrough.md) ---
    var validatedET = 
        eitherTMonad.flatMap(
            ctx -> {
              // Synchronous step returning Kind<EitherKind.Witness<DomainError>, ValidatedOrder>
              var syncResultEitherKind = steps.validateOrder(ctx.initialData()); 
              var syncResultEither = EitherKindHelper.unwrap(syncResultEitherKind); 

              // Lift sync Either into EitherT<CompletableFuture, DomainError, ValidatedOrder>
              var validatedOrderET = EitherT.fromEither(futureMonad, syncResultEither); 

              // If validatedOrderET is Right(vo), map to update context
              return eitherTMonad.map(ctx::withValidatedOrder, validatedOrderET);
            },
            initialET);

    // --- Step 2: Check Inventory (Corresponds to Step 2 in order-walkthrough.md) ---
    var inventoryET = 
        eitherTMonad.flatMap(
            ctx -> {
              // Asynchronous step returning Kind<CompletableFutureKind.Witness, Either<DomainError, Void>>
              var inventoryCheckFutureKind = 
                  steps.checkInventoryAsync(
                      ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());
              // Lift async result directly into EitherT
              var inventoryCheckET = EitherT.fromKind(inventoryCheckFutureKind); 

              // If inventoryCheckET is Right(null), map to update context
              return eitherTMonad.map(
                  ignored -> ctx.withInventoryChecked(), inventoryCheckET);
            },
            validatedET);

    // --- Step 3: Process Payment (Corresponds to Step 3 in order-walkthrough.md) ---
    var paymentET = 
        eitherTMonad.flatMap(
            ctx -> {
              // Asynchronous step
              var paymentFutureKind = 
                  steps.processPaymentAsync(
                      ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
              var paymentConfirmET = EitherT.fromKind(paymentFutureKind); 
              return eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET);
            },
            inventoryET);

    // --- Step 4: Create Shipment (Corresponds to Step 4 in order-walkthrough.md) ---
    var shipmentET = 
        eitherTMonad.flatMap(
            ctx -> {
              // Asynchronous step
              var shipmentAttemptFutureKind = 
                  steps.createShipmentAsync(
                      ctx.validatedOrder().orderId(),
                      ctx.validatedOrder().shippingAddress());
              var shipmentAttemptET = EitherT.fromKind(shipmentAttemptFutureKind); 

              // Error Handling & Recovery
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
                          return eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"));
                        } else {
                          return eitherTMonad.raiseError(error);
                        }
                      });
              return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
            },
            paymentET);

    // --- Step 5: Map to Final Result (Corresponds to Step 5 in order-walkthrough.md) ---
    var finalResultET = 
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

    // --- Step 6: Attempt Notification (Corresponds to Step 6 in order-walkthrough.md) ---
    var finalResultWithNotificationET = 
        eitherTMonad.flatMap(
            finalResult -> {
              var notifyFutureKind = 
                  steps.notifyCustomerAsync(
                      orderData.customerId(), "Order processed: " + finalResult.orderId());
              var notifyET = EitherT.fromKind(notifyFutureKind); 

              // Handle notification error, but always recover (non-critical failure)
              return eitherTMonad.map(
                  ignored -> finalResult, // Return original FinalResult
                  eitherTMonad.handleError(
                      notifyET,
                      notifyError -> {
                        dependencies.log(
                            "WARN (EitherT): Notification failed for successful order "
                                + finalResult.orderId()
                                + ": "
                                + notifyError.message());
                        return null; // Recover with Void (represented by null)
                      }));
            },
            finalResultET);

    // --- Final Unwrapping (Corresponds to "Final Unwrapping" in order-walkthrough.md) ---
    var finalConcreteET = 
        EitherTKindHelper.<CompletableFutureKind.Witness, DomainError, FinalResult>unwrap(
            finalResultWithNotificationET);
    return finalConcreteET.value();
  }

  // See order-walkthrough.md for explanation of this method and Try integration
  public Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>
  runOrderWorkflowEitherTWithTryValidation(OrderData orderData) {
    dependencies.log(
        "Starting runOrderWorkflowEitherTWithTryValidation for Order: " + orderData.orderId());

    var initialContext = WorkflowContext.start(orderData);
    // Explicit type for the start of the chain
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext> initialET =
        eitherTMonad.of(initialContext);

    // --- Step 1: Validate Order (using Try) ---
    var validatedET = 
        eitherTMonad.flatMap(
            ctx -> {
              // Synchronous step returning Kind<TryKind.Witness, ValidatedOrder>
              var tryResultKind = steps.validateOrderWithTry(ctx.initialData()); 
              var tryResult = TryKindHelper.<ValidatedOrder>unwrap(tryResultKind);

              // Convert Try<ValidatedOrder> to Either<DomainError, ValidatedOrder> using the new method
              var eitherResult = tryResult.toEither(
                  throwable -> {
                    dependencies.log(
                        "Converting Try.Failure to DomainError.ValidationError: "
                            + throwable.getMessage());
                    // The type L (DomainError) is inferred from this lambda's return type
                    return (DomainError)new DomainError.ValidationError(throwable.getMessage());
                  });

              var validatedOrderET_Concrete =
                  EitherT.<CompletableFutureKind.Witness, DomainError, ValidatedOrder>fromEither(futureMonad, eitherResult);
              var validatedOrderET_Kind = EitherTKindHelper.wrap(validatedOrderET_Concrete);

              return eitherTMonad.map(
                  ctx::withValidatedOrder,
                  validatedOrderET_Kind
              );
            },
            initialET);

    // --- Subsequent steps are similar to runOrderWorkflowEitherT ---

    // Step 2: Check Inventory
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext> inventoryET = 
        eitherTMonad.flatMap(
             ctx -> {
              var inventoryCheckFutureKind = 
                  steps.checkInventoryAsync(
                      ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());
              var inventoryCheckET = EitherT.fromKind(inventoryCheckFutureKind); 
              return eitherTMonad.map(
                  ignored -> ctx.withInventoryChecked(), inventoryCheckET);
            },
            validatedET);

    // Step 3: Process Payment
    var paymentET = 
        eitherTMonad.flatMap(
            ctx -> {
              var paymentFutureKind = 
                  steps.processPaymentAsync(
                      ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
              var paymentConfirmET = EitherT.fromKind(paymentFutureKind); 
              return eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET);
            },
            inventoryET);

    // Step 4: Create Shipment (with recovery using switch for pattern matching)
    var shipmentET = 
        eitherTMonad.flatMap(
            ctx -> {
              var shipmentAttemptFutureKind = 
                  steps.createShipmentAsync(
                      ctx.validatedOrder().orderId(),
                      ctx.validatedOrder().shippingAddress());
              var shipmentAttemptET = EitherT.fromKind(shipmentAttemptFutureKind); 

              var recoveredShipmentET = 
                  eitherTMonad.handleErrorWith(
                      shipmentAttemptET,
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

    // Step 5: Map to Final Result
    var finalResultET = 
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

    // Step 6: Attempt Notification
    var finalResultWithNotificationET = 
        eitherTMonad.flatMap(
            finalResult -> {
              var notifyFutureKind = 
                  steps.notifyCustomerAsync(
                      orderData.customerId(), "Order processed: " + finalResult.orderId());
              var notifyET = EitherT.fromKind(notifyFutureKind); 

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

    var finalConcreteET = 
        EitherTKindHelper.unwrap(
            finalResultWithNotificationET);
    return finalConcreteET.value();
  }
}