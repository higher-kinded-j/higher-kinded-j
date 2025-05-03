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

  private final @NonNull Dependencies dependencies; // Inject dependencies
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
    if (data.productId() == null || data.productId().isEmpty()) {
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
  public Kind<TryKind<?>, ValidatedOrder> validateOrderWithTry(OrderData data) {
    // Wrap potentially throwing logic using TryKindHelper.tryOf
    return TryKindHelper.tryOf(
        () -> {
          dependencies.log("Step (sync - Try): Validating order " + data.orderId());
          // Use standard checks that throw exceptions on failure
          if (data.quantity() <= 0) {
            String msg = "Quantity must be positive for order " + data.orderId();
            dependencies.log("Validation Failed (Try - Exception): " + msg);
            throw new IllegalArgumentException(msg);
          }
          if (data.productId() == null || data.productId().isEmpty()) {
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
   * Checks inventory asynchronously. Returns: K{@code ind<CompletableFutureKind<?>, Either<DomainError,
   * Void>>}
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, Void>> checkInventoryAsync(
      String productId, int quantity) {
    String step = "Check Inventory";
    dependencies.log(
        String.format("Step (async - %s): Checking %d of %s", step, quantity, productId));
    CompletableFuture<Either<DomainError, Void>> future =
        simulateAsync(
            () -> {
              if ("OUT_OF_STOCK".equalsIgnoreCase(productId) && quantity > 0) {
                dependencies.log(
                    String.format("%s: Failed - Out of stock for %s", step, productId));
                return Either.left(new StockError(productId));
              }
              dependencies.log(String.format("%s: Succeeded for %s", step, productId));
              return Either.right(null); // Use Void for success
            },
            50, // delay
            step); // step name for logging
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Processes payment asynchronously. Returns: {@code Kind<CompletableFutureKind<?>, Either<DomainError,
   * PaymentConfirmation>>}
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, PaymentConfirmation>>
      processPaymentAsync(String paymentDetails, double amount) {
    String step = "Process Payment";
    dependencies.log(
        String.format("Step (async - %s): Processing %.2f using %s", step, amount, paymentDetails));
    CompletableFuture<Either<DomainError, PaymentConfirmation>> future =
        simulateAsync(
            () -> {
              if ("INVALID_CARD".equalsIgnoreCase(paymentDetails)) {
                dependencies.log(String.format("%s: Failed - Card declined", step));
                return Either.left(new PaymentError("Card declined"));
              }
              String txnId = "async-txn-" + System.nanoTime();
              dependencies.log(String.format("%s: Succeeded (Txn: %s)", step, txnId));
              return Either.right(new PaymentConfirmation(txnId));
            },
            80, // delay
            step); // step name
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Creates a shipment asynchronously. Returns: {@code Kind<CompletableFutureKind<?>, Either<DomainError,
   * ShipmentInfo>>}
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, ShipmentInfo>> createShipmentAsync(
      String orderId, String shippingAddress) {
    String step = "Create Shipment";
    dependencies.log(
        String.format("Step (async - %s): Order %s to %s", step, orderId, shippingAddress));
    CompletableFuture<Either<DomainError, ShipmentInfo>> future =
        simulateAsync(
            () -> {
              if (shippingAddress == null || shippingAddress.isBlank()) {
                String msg = "Address invalid for order " + orderId;
                dependencies.log(String.format("%s: Failed - %s", step, msg));
                return Either.left(new ShippingError(msg));
              }
              // Simulate occasional recoverable or permanent failures
              if ("FAIL_SHIPMENT".equalsIgnoreCase(orderId) || random.nextInt(10) == 0) {
                String reason =
                    "FAIL_SHIPMENT".equalsIgnoreCase(orderId)
                        ? "Temporary Glitch" // Recoverable reason
                        : "Simulated random shipment service failure for "
                            + orderId; // Non-recoverable
                dependencies.log(String.format("%s: Failed - %s", step, reason));
                return Either.left(new ShippingError(reason));
              }
              String trackId = "async-track-" + System.nanoTime();
              dependencies.log(String.format("%s: Succeeded (Track: %s)", step, trackId));
              return Either.right(new ShipmentInfo(trackId));
            },
            60, // delay
            step); // step name
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Notifies the customer asynchronously (optional step). Returns: {@code Kind<CompletableFutureKind<?>,
   * Either<DomainError, Void>>}
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, Void>> notifyCustomerAsync(
      String customerId, String message) {
    String step = "Notify Customer";
    dependencies.log(
        String.format("Step (async - %s): Customer %s: %s", step, customerId, message));
    CompletableFuture<Either<DomainError, Void>> future =
        simulateAsync(
            () -> {
              if ("UNREACHABLE".equalsIgnoreCase(customerId)) {
                // Simulate a non-critical failure (e.g., email bounce)
                // We log it but still return success (Right) because the main order succeeded.
                dependencies.log(
                    String.format("WARN (%s): Failed - Unreachable customer %s", step, customerId));
              } else {
                dependencies.log(String.format("%s: Succeeded for customer %s", step, customerId));
              }
              // Notification failure isn't a DomainError blocking the order result here.
              return Either.right(null);
            },
            20, // delay
            step); // step name
    return CompletableFutureKindHelper.wrap(future);
  }
}
