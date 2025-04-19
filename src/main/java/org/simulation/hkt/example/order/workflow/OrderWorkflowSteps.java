package org.simulation.hkt.example.order.workflow;

import org.simulation.hkt.Kind;
import org.simulation.hkt.either.Either;
import org.simulation.hkt.either.EitherKind;
import org.simulation.hkt.either.EitherKindHelper;
import org.simulation.hkt.example.order.error.DomainError;

import static org.simulation.hkt.example.order.error.DomainError.*;


import static org.simulation.hkt.example.order.model.WorkflowModels.*;

import java.util.Random;

public class OrderWorkflowSteps {

  private final Random random = new Random(); // For simulations

  // Helper to wrap Either results into the Kind simulation
  private <R> Kind<EitherKind<DomainError, ?>, R> success(R value) {
    return EitherKindHelper.wrap(Either.right(value));
  }

  private <R> Kind<EitherKind<DomainError, ?>, R> failure(DomainError error) {
    return EitherKindHelper.wrap(Either.left(error));
  }

  // --- Workflow Step Methods ---

  public Kind<EitherKind<DomainError, ?>, ValidatedOrder> validateOrder(OrderData data) {
    System.out.println("Step: Validating order " + data.orderId());
    if (data.quantity() <= 0) {
      return failure(new ValidationError("Quantity must be positive for order " + data.orderId()));
    }
    if (data.productId() == null || data.productId().isEmpty()) {
      return failure(new ValidationError("Product ID missing for order " + data.orderId()));
    }
    // Simulate calculation
    double amount = data.quantity() * 19.99;
    ValidatedOrder validated = new ValidatedOrder(data.orderId(), data.productId(), data.quantity(),
        data.paymentDetails(), amount, data.shippingAddress(), data.customerId());
    return success(validated);
  }

  public Kind<EitherKind<DomainError, ?>, Void> checkInventory(String productId, int quantity) {
    System.out.println("Step: Checking inventory for " + quantity + " of " + productId);
    // Simulate check
    if ("OUT_OF_STOCK".equalsIgnoreCase(productId) && quantity > 0) {
      return failure(new StockError(productId));
    }
    return success(null); // Use Void for success when no value is needed
  }

  public Kind<EitherKind<DomainError, ?>, PaymentConfirmation> processPayment(String paymentDetails, double amount) {
    System.out.println("Step: Processing payment of " + amount + " using " + paymentDetails);
    // Simulate payment
    if ("INVALID_CARD".equalsIgnoreCase(paymentDetails)) {
      return failure(new PaymentError("Card declined"));
    }
    return success(new PaymentConfirmation("txn-" + System.nanoTime()));
  }

  public Kind<EitherKind<DomainError, ?>, ShipmentInfo> createShipment(String orderId, String shippingAddress) {
    System.out.println("Step: Creating shipment for order " + orderId + " to " + shippingAddress);
    // Simulate shipment creation
    if (shippingAddress == null || shippingAddress.isBlank()) {
      return failure(new ShippingError("Address invalid for order " + orderId));
    }
    // Simulate some random shipment failure
    if (random.nextInt(10) == 0) { // ~10% chance of failure
      return failure(new ShippingError("Simulated random shipment service failure for " + orderId));
    }
    return success(new ShipmentInfo("track-" + System.nanoTime()));
  }

  // Optional: Notification step (not included in main chain for simplicity here)
  public Kind<EitherKind<DomainError, ?>, Void> notifyCustomer(String customerId, String message) {
    System.out.println("Notify: Customer " + customerId + ": " + message);
    // Simulate notification - could fail but maybe we don't fail the workflow
    if ("UNREACHABLE".equalsIgnoreCase(customerId)) {
      // Log failure but maybe return success for the workflow
      System.err.println("WARN: Failed to notify unreachable customer " + customerId);
      // return failure(new NotificationError("Email bounced for " + customerId));
    }
    return success(null);
  }
}
