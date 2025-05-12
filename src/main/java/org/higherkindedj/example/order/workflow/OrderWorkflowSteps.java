package org.higherkindedj.example.order.workflow;

import static org.higherkindedj.example.order.error.DomainError.*;
import static org.higherkindedj.example.order.model.WorkflowModels.*;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryKindHelper;
import org.jspecify.annotations.NonNull;

/**
 * Defines the individual steps involved in processing an order, now accepting dependencies. This
 * class demonstrates a mix of synchronous and asynchronous step implementations, using the HKT
 * Higher-Kinded-J framework with {@link Either} for error handling and {@link CompletableFuture}
 * for asynchronous operations. Logging is now performed via the injected {@link Dependencies}.
 *
 * <p>Asynchronous steps return results wrapped in {@code Kind<CompletableFutureKind<?>,
 * Either<DomainError, T>>}. Synchronous steps return results wrapped in {@code
 * Kind<EitherKind<DomainError, ?>, T>} or {@code Kind<TryKind<?>, T>}.
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
   * @return A {@code Kind<EitherKind<DomainError, ?>, ValidatedOrder>} containing either a {@link
   *     ValidatedOrder} on success, or a {@link DomainError.ValidationError} on failure.
   */
  public Kind<EitherKind<DomainError, ?>, ValidatedOrder> validateOrder(OrderData data) {
    dependencies.log("Step (sync - Either): Validating order " + data.orderId());
    // Simulate immediate validation logic
    if (data.quantity() <= 0) {
      String msg = "Quantity must be positive for order " + data.orderId();
      dependencies.log("Validation Failed (Either): " + msg);
      return EitherKindHelper.wrap(Either.left(new ValidationError(msg)));
    }
    if (data.productId().isEmpty()) {
      String msg = "Product ID missing for order " + data.orderId();
      dependencies.log("Validation Failed (Either): " + msg);
      return EitherKindHelper.wrap(Either.left(new ValidationError(msg)));
    }
    // Simulate calculation
    double amount = data.quantity() * 19.99;
    ValidatedOrder validated =
        new ValidatedOrder(
            data.orderId(),
            data.productId(),
            data.quantity(),
            data.paymentDetails(),
            amount,
            data.shippingAddress(),
            data.customerId());
    dependencies.log("Validation Succeeded (Either) for order " + data.orderId());
    return EitherKindHelper.wrap(Either.right(validated));
  }

  /**
   * Validates the initial order data synchronously using {@link Try} to capture potential
   * exceptions. Checks for positive quantity and the presence of a product ID by throwing
   * exceptions on failure. Calculates the order amount. Logs progress.
   *
   * @param data The initial {@link OrderData}.
   * @return A {@code Kind<TryKind<?>, ValidatedOrder>} containing either a {@link
   *     Try.Success<ValidatedOrder>} or a {@link Try.Failure} wrapping the thrown exception.
   */
  public Kind<TryKind.Witness, ValidatedOrder> validateOrderWithTry(OrderData data) {

    return TryKindHelper.tryOf(
        () -> {
          dependencies.log("Step (sync - Try): Validating order " + data.orderId());
          // Use standard checks that throw exceptions on failure
          if (data.quantity() <= 0) {
            String msg = "Quantity must be positive for order " + data.orderId();
            dependencies.log("Validation Failed (Try - Exception): " + msg);
            throw new IllegalArgumentException(msg);
          }
          if (data.productId().isEmpty()) {
            String msg = "Product ID missing for order " + data.orderId();
            dependencies.log("Validation Failed (Try - Exception): " + msg);
            throw new IllegalArgumentException(msg);
          }
          // Simulate calculation (could potentially throw ArithmeticException etc. in real code)
          double amount = data.quantity() * 19.99;
          // If all checks pass and calculations succeed, return the validated order
          ValidatedOrder validated =
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
            dependencies.log(
                String.format("... %s: simulating %dms delay ...", stepName, delayMillis));
            TimeUnit.MILLISECONDS.sleep(delayMillis);
            T result = action.get(); // Execute the core logic
            dependencies.log(String.format("... %s: delay complete.", stepName));
            return result;
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dependencies.log(String.format("... %s: interrupted during delay.", stepName));
            throw new CompletionException(e); // Wrap interruption
          } catch (Exception e) {
            // Log the exception from the action itself
            dependencies.log(
                String.format(
                    "... %s: action failed with %s.", stepName, e.getClass().getSimpleName()));
            // Wrap other exceptions thrown by the action
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
   *     {@link Void} on success or a {@link DomainError} on failure.
   */
  public Kind<CompletableFutureKind.Witness, Either<DomainError, Void>> checkInventoryAsync(
      String productId, int quantity) {
    dependencies.log(
        String.format("Checking inventory for product %s, quantity %d", productId, quantity));
    CompletableFuture<Either<DomainError, Void>> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                dependencies.log(
                    String.format(
                        "... Check Inventory: simulating %dms delay ...",
                        100 + random.nextInt(200)));
                Thread.sleep(100 + random.nextInt(200));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.left(new DomainError.StockError("Inventory check interrupted"));
              }
              if ("OUT_OF_STOCK".equals(productId)) {
                return Either.left(new DomainError.StockError(productId));
              }
              if (quantity <= 0) {
                return Either.left(
                    new DomainError.ValidationError("Quantity must be positive: " + quantity));
              }
              dependencies.log( // Ensure 'dependencies' is used for logging
                  String.format("... Check Inventory: delay complete for %s.", productId));
              return Either.right(null);
            });
    return CompletableFutureKindHelper.wrap(future);
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
    dependencies.log(String.format("Processing payment for amount %.2f", amount));
    CompletableFuture<Either<DomainError, PaymentConfirmation>> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                dependencies.log(
                    String.format(
                        "... Process Payment: simulating %dms delay ...",
                        150 + random.nextInt(250)));
                Thread.sleep(150 + random.nextInt(250));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.left(new DomainError.PaymentError("Payment processing interrupted"));
              }
              if ("INVALID_CARD".equals(paymentDetails)) {
                return Either.left(new DomainError.PaymentError("Invalid card details"));
              }
              dependencies.log(
                  String.format("... Process Payment: delay complete for %s.", paymentDetails));
              return Either.right(
                  new PaymentConfirmation(
                      "TXN_" + System.currentTimeMillis() + "_" + random.nextInt(1000)));
            });
    return CompletableFutureKindHelper.wrap(future);
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
        String.format("Creating shipment for order %s to %s", orderId, shippingAddress));
    CompletableFuture<Either<DomainError, ShipmentInfo>> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                dependencies.log( // Ensure 'dependencies' is used for logging
                    String.format(
                        "... Create Shipment: simulating %dms delay ...",
                        200 + random.nextInt(300)));
                Thread.sleep(200 + random.nextInt(300));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.left(new DomainError.ShippingError("Shipment creation interrupted"));
              }
              if (shippingAddress == null || shippingAddress.trim().isEmpty()) {
                return Either.left(new DomainError.ShippingError("Missing shipping address"));
              }
              if ("FAIL_SHIPMENT".equals(orderId)) {
                dependencies.log("Simulating temporary shipping glitch for order: " + orderId);
                return Either.left(new DomainError.ShippingError("Temporary Glitch"));
              }
              dependencies.log( // Ensure 'dependencies' is used for logging
                  String.format("... Create Shipment: delay complete for order %s.", orderId));
              return Either.right(
                  new ShipmentInfo(
                      "TRACK_" + System.currentTimeMillis() + "_" + random.nextInt(1000)));
            });
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Asynchronously notifies the customer. This operation can fail but is not critical.
   *
   * @param customerId The ID of the customer.
   * @param message The message to send.
   * @return A {@code Kind} representing a {@link CompletableFuture} of an {@link Either} containing
   *     {@link Void} on success or a {@link DomainError} on failure.
   */
  public Kind<CompletableFutureKind.Witness, Either<DomainError, Void>> notifyCustomerAsync(
      String customerId, String message) {
    dependencies.log(String.format("Notifying customer %s with message: %s", customerId, message));
    CompletableFuture<Either<DomainError, Void>> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                dependencies.log(
                    String.format(
                        "... Notify Customer: simulating %dms delay ...",
                        50 + random.nextInt(100)));
                Thread.sleep(50 + random.nextInt(100));
                if ("FAIL_NOTIFICATION".equals(customerId)) {
                  dependencies.log("Simulating notification failure for customer: " + customerId);
                  return Either.left(new DomainError.NotificationError("SMTP server down"));
                }
                dependencies.log(
                    "Successfully sent notification to " + customerId + ": " + message);
                return Either.right(null);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Either.left(new DomainError.NotificationError("Notification interrupted"));
              }
            });
    return CompletableFutureKindHelper.wrap(future);
  }
}
