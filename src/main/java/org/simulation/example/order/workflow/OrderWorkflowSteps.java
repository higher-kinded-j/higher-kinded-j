package org.simulation.example.order.workflow;

import org.simulation.hkt.Kind;
import org.simulation.hkt.either.Either;
import org.simulation.hkt.either.EitherKind;
import org.simulation.hkt.either.EitherKindHelper;
import org.simulation.example.order.error.DomainError;
import org.simulation.hkt.future.CompletableFutureKind;
import org.simulation.hkt.future.CompletableFutureKindHelper;

import static org.simulation.example.order.error.DomainError.*;
import static org.simulation.example.order.model.WorkflowModels.*;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Defines the individual steps involved in processing an order.
 * This class demonstrates a mix of synchronous and asynchronous step implementations,
 * using the HKT simulation framework with {@link Either} for error handling and
 * {@link CompletableFuture} for asynchronous operations.
 *
 * Asynchronous steps return results wrapped in {@code Kind<CompletableFutureKind<?>, Either<DomainError, T>>},
 * representing a future computation that will eventually yield either a {@link DomainError} or a successful result {@code T}.
 * Synchronous steps return results wrapped in {@code Kind<EitherKind<DomainError, ?>, T>},
 * representing an immediate computation that resulted in either a {@link DomainError} or a successful result {@code T}.
 */
public class OrderWorkflowSteps {

  private final Random random = new Random();

  // --- Synchronous Step ---

  /**
   * Validates the initial order data synchronously.
   * Checks for positive quantity and the presence of a product ID.
   * Calculates the order amount.
   *
   * @param data The initial {@link OrderData}.
   * @return A {@code Kind<EitherKind<DomainError, ?>, ValidatedOrder>} containing either a {@link ValidatedOrder}
   * on success, or a {@link DomainError.ValidationError} on failure. The result is available immediately.
   */
  public Kind<EitherKind<DomainError, ?>, ValidatedOrder> validateOrder(OrderData data) {
    System.out.println("Step (sync): Validating order " + data.orderId());
    // Simulate immediate validation logic
    if (data.quantity() <= 0) {
      // Wrap the Left (error) result using the EitherKindHelper
      return EitherKindHelper.wrap(Either.left(new ValidationError("Quantity must be positive for order " + data.orderId())));
    }
    if (data.productId() == null || data.productId().isEmpty()) {
      // Wrap the Left (error) result using the EitherKindHelper
      return EitherKindHelper.wrap(Either.left(new ValidationError("Product ID missing for order " + data.orderId())));
    }
    // Simulate calculation
    double amount = data.quantity() * 19.99;
    ValidatedOrder validated = new ValidatedOrder(data.orderId(), data.productId(), data.quantity(),
            data.paymentDetails(), amount, data.shippingAddress(), data.customerId());
    // Wrap the Right (success) result using the EitherKindHelper
    return EitherKindHelper.wrap(Either.right(validated));
  }


  // --- Asynchronous Steps ---

  /**
   * Helper method to simulate an asynchronous operation with a delay.
   * @param action The action to perform asynchronously.
   * @param delayMillis The simulated delay in milliseconds.
   * @param <T> The result type of the action.
   * @return A CompletableFuture that will complete with the result of the action after the delay.
   */
  private <T> CompletableFuture<T> simulateAsync(java.util.function.Supplier<T> action, long delayMillis) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        System.out.printf("... simulating %dms delay ...%n", delayMillis);
        TimeUnit.MILLISECONDS.sleep(delayMillis);
        return action.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CompletionException(e); // Wrap interruption
      } catch (Exception e) {
        // Wrap other exceptions thrown by the action
        throw new CompletionException(e);
      }
    });
  }

  /**
   * Checks inventory asynchronously.
   *
   * @param productId The ID of the product to check.
   * @param quantity The quantity required.
   * @return A {@code Kind<CompletableFutureKind<?>, Either<DomainError, Void>>} representing the future result.
   * The inner {@link Either} contains {@code Void} on success or a {@link DomainError.StockError} if out of stock.
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, Void>> checkInventoryAsync(String productId, int quantity) {
    System.out.println("Step (async): Checking inventory for " + quantity + " of " + productId);
    CompletableFuture<Either<DomainError, Void>> future = simulateAsync(() -> {
      if ("OUT_OF_STOCK".equalsIgnoreCase(productId) && quantity > 0) {
        return Either.<DomainError, Void>left(new StockError(productId));
      }
      return Either.<DomainError, Void>right(null); // Use Void for success
    }, 50); // Simulate 50ms delay
    // Wrap the CompletableFuture<Either<...>> using the CompletableFutureKindHelper
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Processes payment asynchronously.
   *
   * @param paymentDetails Details for processing the payment (e.g., card info).
   * @param amount The amount to charge.
   * @return A {@code Kind<CompletableFutureKind<?>, Either<DomainError, PaymentConfirmation>>} representing the future result.
   * The inner {@link Either} contains a {@link PaymentConfirmation} on success or a {@link DomainError.PaymentError} on failure.
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, PaymentConfirmation>> processPaymentAsync(String paymentDetails, double amount) {
    System.out.println("Step (async): Processing payment of " + amount + " using " + paymentDetails);
    CompletableFuture<Either<DomainError, PaymentConfirmation>> future = simulateAsync(() -> {
      if ("INVALID_CARD".equalsIgnoreCase(paymentDetails)) {
        return Either.<DomainError, PaymentConfirmation>left(new PaymentError("Card declined"));
      }
      return Either.<DomainError, PaymentConfirmation>right(new PaymentConfirmation("async-txn-" + System.nanoTime()));
    }, 80); // Simulate 80ms delay
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Creates a shipment asynchronously.
   * Includes simulated random failures and a specific failure case for recovery demonstration.
   *
   * @param orderId The ID of the order being shipped.
   * @param shippingAddress The destination address.
   * @return A {@code Kind<CompletableFutureKind<?>, Either<DomainError, ShipmentInfo>>} representing the future result.
   * The inner {@link Either} contains {@link ShipmentInfo} on success or a {@link DomainError.ShippingError} on failure.
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, ShipmentInfo>> createShipmentAsync(String orderId, String shippingAddress) {
    System.out.println("Step (async): Creating shipment for order " + orderId + " to " + shippingAddress);
    CompletableFuture<Either<DomainError, ShipmentInfo>> future = simulateAsync(() -> {
      if (shippingAddress == null || shippingAddress.isBlank()) {
        return Either.<DomainError, ShipmentInfo>left(new ShippingError("Address invalid for order " + orderId));
      }
      // Simulate some random shipment failure or a specific recoverable failure
      if ("FAIL_SHIPMENT".equalsIgnoreCase(orderId) || random.nextInt(10) == 0) {
        System.out.println("!!! Simulating shipment failure for " + orderId);
        // Use a specific reason for the recovery example in the runner
        String reason = "FAIL_SHIPMENT".equalsIgnoreCase(orderId) ? "Temporary Glitch" : "Simulated random shipment service failure for " + orderId;
        return Either.<DomainError, ShipmentInfo>left(new ShippingError(reason));
      }
      return Either.<DomainError, ShipmentInfo>right(new ShipmentInfo("async-track-" + System.nanoTime()));
    }, 60); // Simulate 60ms delay
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Notifies the customer asynchronously (optional step).
   * Simulates sending a notification, potentially failing but treated as non-critical.
   *
   * @param customerId The ID of the customer to notify.
   * @param message The notification message.
   * @return A {@code Kind<CompletableFutureKind<?>, Either<DomainError, Void>>} representing the future result.
   * The inner {@link Either} usually contains {@code Void} (success), but could contain a {@code NotificationError} if designed to fail.
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, Void>> notifyCustomerAsync(String customerId, String message) {
    System.out.println("Notify (async): Customer " + customerId + ": " + message);
    CompletableFuture<Either<DomainError, Void>> future = simulateAsync(() -> {
      if ("UNREACHABLE".equalsIgnoreCase(customerId)) {
        System.err.println("WARN (async): Failed to notify unreachable customer " + customerId);
        // Decide if this is a workflow failure or just a logged issue
        // return Either.<DomainError, Void>left(new NotificationError("Email bounced for " + customerId));
      }
      return Either.<DomainError, Void>right(null);
    }, 20); // Simulate 20ms delay
    return CompletableFutureKindHelper.wrap(future);
  }
}
