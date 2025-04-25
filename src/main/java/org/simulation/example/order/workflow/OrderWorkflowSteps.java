package org.simulation.example.order.workflow;

import static org.simulation.example.order.error.DomainError.*;
import static org.simulation.example.order.model.WorkflowModels.*;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.simulation.example.order.error.DomainError;
import org.simulation.hkt.Kind;
import org.simulation.hkt.either.Either;
import org.simulation.hkt.either.EitherKind;
import org.simulation.hkt.either.EitherKindHelper;
import org.simulation.hkt.future.CompletableFutureKind;
import org.simulation.hkt.future.CompletableFutureKindHelper;
import org.simulation.hkt.trymonad.TryKind;
import org.simulation.hkt.trymonad.TryKindHelper;

/**
 * Defines the individual steps involved in processing an order. This class demonstrates a mix of
 * synchronous and asynchronous step implementations, using the HKT simulation framework with {@link
 * Either} for error handling and {@link CompletableFuture} for asynchronous operations.
 *
 * <p>Asynchronous steps return results wrapped in {@code Kind<CompletableFutureKind<?>,
 * Either<DomainError, T>>}, representing a future computation that will eventually yield either a
 * {@link DomainError} or a successful result {@code T}. Synchronous steps return results wrapped in
 * {@code Kind<EitherKind<DomainError, ?>, T>}, representing an immediate computation that resulted
 * in either a {@link DomainError} or a successful result {@code T}.
 */
public class OrderWorkflowSteps {

  private final Random random = new Random();

  // --- Synchronous Step ---

  /**
   * Validates the initial order data synchronously using {@link Either} for explicit domain errors.
   * Checks for positive quantity and the presence of a product ID. Calculates the order amount.
   *
   * <p>Use this when validation failures are expected, defined business rule violations, and should
   * be represented explicitly by subtypes of {@link DomainError}.
   *
   * @param data The initial {@link OrderData}.
   * @return A {@code Kind<EitherKind<DomainError, ?>, ValidatedOrder>} containing either a {@link
   *     ValidatedOrder} on success, or a {@link DomainError.ValidationError} on failure. The result
   *     is available immediately.
   */
  public Kind<EitherKind<DomainError, ?>, ValidatedOrder> validateOrder(OrderData data) {
    System.out.println("Step (sync - Either): Validating order " + data.orderId());
    // Simulate immediate validation logic
    if (data.quantity() <= 0) {
      return EitherKindHelper.wrap(
          Either.left(
              new ValidationError("Quantity must be positive for order " + data.orderId())));
    }
    if (data.productId() == null || data.productId().isEmpty()) {
      return EitherKindHelper.wrap(
          Either.left(new ValidationError("Product ID missing for order " + data.orderId())));
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
    return EitherKindHelper.wrap(Either.right(validated));
  }

  /**
   * Validates the initial order data synchronously using {@link Try} to capture potential
   * exceptions. Checks for positive quantity and the presence of a product ID by throwing
   * exceptions on failure. Calculates the order amount.
   *
   * <p>Use this when the validation logic itself might throw runtime exceptions (e.g., during
   * complex calculations, parsing, or accessing potentially inconsistent state) that aren't
   * explicitly modeled as specific {@link DomainError} subtypes beforehand.
   *
   * @param data The initial {@link OrderData}.
   * @return A {@code Kind<TryKind<?>, ValidatedOrder>} containing either a {@link
   *     Try.Success<ValidatedOrder>} or a {@link Try.Failure} wrapping the thrown exception.
   */
  public Kind<TryKind<?>, ValidatedOrder> validateOrderWithTry(OrderData data) {
    // Wrap potentially throwing logic using TryKindHelper.tryOf
    return TryKindHelper.tryOf(
        () -> {
          System.out.println("Step (sync - Try): Validating order " + data.orderId());
          // Use standard checks that throw exceptions on failure
          if (data.quantity() <= 0) {
            throw new IllegalArgumentException(
                "Quantity must be positive for order " + data.orderId());
          }
          if (data.productId() == null || data.productId().isEmpty()) {
            throw new IllegalArgumentException("Product ID missing for order " + data.orderId());
          }
          // Simulate calculation (could potentially throw ArithmeticException etc. in real code)
          double amount = data.quantity() * 19.99;
          // If all checks pass and calculations succeed, return the validated order
          return new ValidatedOrder(
              data.orderId(),
              data.productId(),
              data.quantity(),
              data.paymentDetails(),
              amount,
              data.shippingAddress(),
              data.customerId());
        });
  }

  // --- Asynchronous Steps ---

  /**
   * Helper method to simulate an asynchronous operation with a delay.
   *
   * @param action The action to perform asynchronously.
   * @param delayMillis The simulated delay in milliseconds.
   * @param <T> The result type of the action.
   * @return A CompletableFuture that will complete with the result of the action after the delay.
   */
  private <T> CompletableFuture<T> simulateAsync(
      java.util.function.Supplier<T> action, long delayMillis) {
    return CompletableFuture.supplyAsync(
        () -> {
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
   * Checks inventory asynchronously. Returns: Kind<CompletableFutureKind<?>, Either<DomainError,
   * Void>>
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, Void>> checkInventoryAsync(
      String productId, int quantity) {
    System.out.println("Step (async): Checking inventory for " + quantity + " of " + productId);
    CompletableFuture<Either<DomainError, Void>> future =
        simulateAsync(
            () -> {
              if ("OUT_OF_STOCK".equalsIgnoreCase(productId) && quantity > 0) {
                return Either.left(new StockError(productId));
              }
              return Either.right(null); // Use Void for success
            },
            50);
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Processes payment asynchronously. Returns: Kind<CompletableFutureKind<?>, Either<DomainError,
   * PaymentConfirmation>>
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, PaymentConfirmation>>
      processPaymentAsync(String paymentDetails, double amount) {
    System.out.println(
        "Step (async): Processing payment of " + amount + " using " + paymentDetails);
    CompletableFuture<Either<DomainError, PaymentConfirmation>> future =
        simulateAsync(
            () -> {
              if ("INVALID_CARD".equalsIgnoreCase(paymentDetails)) {
                return Either.left(new PaymentError("Card declined"));
              }
              return Either.right(new PaymentConfirmation("async-txn-" + System.nanoTime()));
            },
            80);
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Creates a shipment asynchronously. Returns: Kind<CompletableFutureKind<?>, Either<DomainError,
   * ShipmentInfo>>
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, ShipmentInfo>> createShipmentAsync(
      String orderId, String shippingAddress) {
    System.out.println(
        "Step (async): Creating shipment for order " + orderId + " to " + shippingAddress);
    CompletableFuture<Either<DomainError, ShipmentInfo>> future =
        simulateAsync(
            () -> {
              if (shippingAddress == null || shippingAddress.isBlank()) {
                return Either.left(new ShippingError("Address invalid for order " + orderId));
              }
              if ("FAIL_SHIPMENT".equalsIgnoreCase(orderId) || random.nextInt(10) == 0) {
                System.out.println("!!! Simulating shipment failure for " + orderId);
                String reason =
                    "FAIL_SHIPMENT".equalsIgnoreCase(orderId)
                        ? "Temporary Glitch"
                        : "Simulated random shipment service failure for " + orderId;
                return Either.left(new ShippingError(reason));
              }
              return Either.right(new ShipmentInfo("async-track-" + System.nanoTime()));
            },
            60);
    return CompletableFutureKindHelper.wrap(future);
  }

  /**
   * Notifies the customer asynchronously (optional step). Returns: Kind<CompletableFutureKind<?>,
   * Either<DomainError, Void>>
   */
  public Kind<CompletableFutureKind<?>, Either<DomainError, Void>> notifyCustomerAsync(
      String customerId, String message) {
    System.out.println("Notify (async): Customer " + customerId + ": " + message);
    CompletableFuture<Either<DomainError, Void>> future =
        simulateAsync(
            () -> {
              if ("UNREACHABLE".equalsIgnoreCase(customerId)) {
                System.err.println(
                    "WARN (async): Failed to notify unreachable customer " + customerId);
                // Return Right, as notification failure isn't critical here
              }
              return Either.right(null);
            },
            20);
    return CompletableFutureKindHelper.wrap(future);
  }
}
