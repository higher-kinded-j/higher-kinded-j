// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import static java.lang.String.format;
import static org.higherkindedj.example.order.error.DomainError.*;
import static org.higherkindedj.example.order.model.WorkflowModels.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.unit.Unit;
import org.jspecify.annotations.NonNull;

/**
 * Defines the individual steps involved in processing an order, now accepting dependencies. This
 * class demonstrates a mix of synchronous and asynchronous step implementations, using the HKT
 * Higher-Kinded-J framework with {@link Either} for error handling and {@link CompletableFuture}
 * for asynchronous operations. Logging is now performed via the injected {@link Dependencies}.
 *
 * <p>Asynchronous steps return results wrapped in {@code Kind<CompletableFutureKind<?>,
 * Either<DomainError, T>>}. Synchronous steps return results wrapped in {@code
 * Kind<EitherKind<DomainError, ?>, T>} or {@code Kind<TryKind<?>, T>}. Local variables use `var`
 * for conciseness where type is clear.
 */
public class OrderWorkflowSteps {

  private final @NonNull Dependencies dependencies;
  private final Random random = new Random();

  /**
   * Constructor accepting dependencies.
   *
   * @param dependencies The external dependencies needed by the steps. (NonNull)
   */
  public OrderWorkflowSteps(@NonNull Dependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "Dependencies cannot be null");
  }

  // --- Synchronous Steps ---

  /**
   * Validates the initial order data synchronously using {@link Either} for explicit domain errors.
   * Checks for positive quantity and the presence of a product ID. Calculates the order amount.
   * Logs progress using the injected logger.
   *
   * @param data The initial {@link OrderData}.
   * @return A {@code Kind<EitherKind.Witness<DomainError>, ValidatedOrder>} containing either a
   *     {@link ValidatedOrder} on success, or a {@link DomainError.ValidationError} on failure.
   */
  public Kind<EitherKind.Witness<DomainError>, ValidatedOrder> validateOrder(OrderData data) {
    dependencies.log("Step (sync - Either): Validating order " + data.orderId());
    if (data.quantity() <= 0) {
      var msg = "Quantity must be positive for order " + data.orderId();
      dependencies.log("Validation Failed (Either): " + msg);
      // Need to specify types for Either.left with var if not fully inferable or for clarity
      Either<DomainError, ValidatedOrder> errorResult = Either.left(new ValidationError(msg));
      return EITHER.widen(errorResult);
    }
    if (data.productId().isEmpty()) {
      var msg = "Product ID missing for order " + data.orderId();
      dependencies.log("Validation Failed (Either): " + msg);
      return EITHER.widen(Either.<DomainError, ValidatedOrder>left(new ValidationError(msg)));
    }
    var amount = data.quantity() * 19.99;
    var validated =
        new ValidatedOrder(
            data.orderId(),
            data.productId(),
            data.quantity(),
            data.paymentDetails(),
            amount,
            data.shippingAddress(),
            data.customerId());
    dependencies.log("Validation Succeeded (Either) for order " + data.orderId());
    return EITHER.widen(Either.right(validated));
  }

  /**
   * Validates the initial order data synchronously using {@link Try} to capture potential
   * exceptions. Checks for positive quantity and the presence of a product ID by throwing
   * exceptions on failure. Calculates the order amount. Logs progress.
   *
   * @param data The initial {@link OrderData}.
   * @return A {@code Kind<TryKind.Witness, ValidatedOrder>} containing either a {@link
   *     Try.Success<ValidatedOrder>} or a {@link Try.Failure} wrapping the thrown exception.
   */
  public Kind<TryKind.Witness, ValidatedOrder> validateOrderWithTry(OrderData data) {
    return TRY.tryOf(
        () -> {
          dependencies.log("Step (sync - Try): Validating order " + data.orderId());
          if (data.quantity() <= 0) {
            var msg = "Quantity must be positive for order " + data.orderId();
            dependencies.log("Validation Failed (Try - Exception): " + msg);
            throw new IllegalArgumentException(msg);
          }
          if (data.productId().isEmpty()) {
            var msg = "Product ID missing for order " + data.orderId();
            dependencies.log("Validation Failed (Try - Exception): " + msg);
            throw new IllegalArgumentException(msg);
          }
          var amount = data.quantity() * 19.99;
          var validated =
              new ValidatedOrder(
                  data.orderId(),
                  data.productId(),
                  data.quantity(),
                  data.paymentDetails(),
                  amount,
                  data.shippingAddress(),
                  data.customerId());
          dependencies.log("Validation Succeeded (Try) for order " + data.orderId());
          return validated;
        });
  }

  // --- Asynchronous Steps ---

  /**
   * Helper method to simulate an asynchronous operation with a delay. Logs start/end of delay.
   *
   * @param action The action to perform asynchronously.
   * @param delayMillis The simulated delay in milliseconds.
   * @param <T> The result type of the action.
   * @return A CompletableFuture that will complete with the result of the action after the delay.
   */
  private <T> CompletableFuture<T> simulateAsync(
      java.util.function.Supplier<T> action, long delayMillis, String stepName) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            dependencies.log(format("... %s: simulating %dms delay ...", stepName, delayMillis));
            TimeUnit.MILLISECONDS.sleep(delayMillis);
            T result = action.get(); // Execute the core logic
            dependencies.log(format("... %s: delay complete.", stepName));
            return result;
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dependencies.log(format("... %s: interrupted during delay.", stepName));
            throw new CompletionException(e); // Wrap interruption
          } catch (Exception e) {
            dependencies.log(
                format("... %s: action failed with %s.", stepName, e.getClass().getSimpleName()));
            throw new CompletionException(e);
          }
        });
  }

  /**
   * Asynchronously checks inventory for a product.
   *
   * @param productId The ID of the product.
   * @param quantity The quantity to check.
   * @return A {@code Kind} representing a {@link CompletableFuture} of an {@link Either} containing
   *     {@link Unit} on success or a {@link DomainError} on failure.
   */
  public Kind<CompletableFutureKind.Witness, Either<DomainError, Unit>> checkInventoryAsync(
      String productId, int quantity) {
    dependencies.log(
        format(
            "Step (async): Checking inventory for product %s, quantity %d", productId, quantity));
    var future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                dependencies.log(
                    format(
                        "... Check Inventory: simulating %dms delay ...",
                        100 + random.nextInt(200)));
                Thread.sleep(100 + random.nextInt(200));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.<DomainError, Unit>left(
                    new DomainError.StockError("Inventory check interrupted"));
              }
              if ("OUT_OF_STOCK".equals(productId)) {
                return Either.<DomainError, Unit>left(new DomainError.StockError(productId));
              }
              if (quantity <= 0) { // Though validateOrder should catch this, good for robustness
                return Either.<DomainError, Unit>left(
                    new DomainError.ValidationError("Quantity must be positive: " + quantity));
              }
              dependencies.log(format("... Check Inventory: delay complete for %s.", productId));
              return Either.<DomainError, Unit>right(Unit.INSTANCE);
            });
    return FUTURE.widen(future);
  }

  /**
   * Asynchronously processes payment.
   *
   * @param paymentDetails Payment details string.
   * @param amount The amount to process.
   * @return A {@code Kind} representing a {@link CompletableFuture} of an {@link Either} containing
   *     {@link PaymentConfirmation} on success or a {@link DomainError} on failure.
   */
  public Kind<CompletableFutureKind.Witness, Either<DomainError, PaymentConfirmation>>
      processPaymentAsync(String paymentDetails, double amount) {
    dependencies.log(format("Step (async): Processing payment for amount %.2f", amount));
    CompletableFuture<Either<DomainError, PaymentConfirmation>> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                dependencies.log(
                    format(
                        "... Process Payment: simulating %dms delay ...",
                        150 + random.nextInt(250)));
                Thread.sleep(150 + random.nextInt(250));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.<DomainError, PaymentConfirmation>left(
                    new DomainError.PaymentError("Payment processing interrupted"));
              }
              if ("INVALID_CARD".equals(paymentDetails)) {
                return Either.<DomainError, PaymentConfirmation>left(
                    new DomainError.PaymentError("Invalid card details"));
              }
              dependencies.log(
                  format(
                      "... Process Payment: delay complete for card: %s.",
                      paymentDetails.substring(0, Math.min(paymentDetails.length(), 4)) + "..."));
              return Either.right(
                  new PaymentConfirmation(
                      "TXN_" + System.currentTimeMillis() + "_" + random.nextInt(1000)));
            });
    return FUTURE.widen(future);
  }

  /**
   * Asynchronously creates a shipment.
   *
   * @param orderId The ID of the order.
   * @param shippingAddress The shipping address.
   * @return A {@code Kind} representing a {@link CompletableFuture} of an {@link Either} containing
   *     {@link ShipmentInfo} on success or a {@link DomainError} on failure.
   */
  public Kind<CompletableFutureKind.Witness, Either<DomainError, ShipmentInfo>> createShipmentAsync(
      String orderId, String shippingAddress) {
    dependencies.log(
        format("Step (async): Creating shipment for order %s to %s", orderId, shippingAddress));
    // Explicitly type the CompletableFuture
    CompletableFuture<Either<DomainError, ShipmentInfo>> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                dependencies.log(
                    format(
                        "... Create Shipment: simulating %dms delay ...",
                        200 + random.nextInt(300)));
                Thread.sleep(200 + random.nextInt(300));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.<DomainError, ShipmentInfo>left(
                    new DomainError.ShippingError("Shipment creation interrupted"));
              }
              if (shippingAddress == null || shippingAddress.trim().isEmpty()) {
                return Either.<DomainError, ShipmentInfo>left(
                    new DomainError.ShippingError("Missing shipping address"));
              }
              if ("FAIL_SHIPMENT"
                  .equals(orderId)) { // Specific orderId to simulate temporary failure
                dependencies.log("Simulating temporary shipping glitch for order: " + orderId);
                return Either.<DomainError, ShipmentInfo>left(
                    new DomainError.ShippingError("Temporary Glitch"));
              }
              dependencies.log(
                  format("... Create Shipment: delay complete for order %s.", orderId));
              return Either.right(
                  new ShipmentInfo(
                      "TRACK_" + System.currentTimeMillis() + "_" + random.nextInt(1000)));
            });
    return FUTURE.widen(future);
  }

  /**
   * Asynchronously notifies the customer. This operation can fail but is not critical.
   *
   * @param customerId The ID of the customer.
   * @param message The message to send.
   * @return A {@code Kind} representing a {@link CompletableFuture} of an {@link Either} containing
   *     {@link Unit} on success or a {@link DomainError} on failure.
   */
  public Kind<CompletableFutureKind.Witness, Either<DomainError, Unit>> notifyCustomerAsync(
      String customerId, String message) {
    dependencies.log(
        format("Step (async): Notifying customer %s with message: %s", customerId, message));
    var future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                dependencies.log(
                    format(
                        "... Notify Customer: simulating %dms delay ...",
                        50 + random.nextInt(100)));
                Thread.sleep(50 + random.nextInt(100));
                // Simulate a non-critical failure for specific customer ID
                if ("FAIL_NOTIFICATION_CUST".equals(customerId)) {
                  dependencies.log("Simulating notification failure for customer: " + customerId);
                  return Either.<DomainError, Unit>left(
                      new DomainError.NotificationError("SMTP server down (simulated)"));
                }
                dependencies.log(
                    "Successfully sent notification to " + customerId + ": " + message);
                return Either.<DomainError, Unit>right(Unit.INSTANCE);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.<DomainError, Unit>left(
                    new DomainError.NotificationError("Notification interrupted"));
              }
            });
    return FUTURE.widen(future);
  }
}
