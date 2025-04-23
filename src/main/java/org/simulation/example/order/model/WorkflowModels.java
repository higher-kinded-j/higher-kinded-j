package org.simulation.example.order.model;

import org.jspecify.annotations.Nullable;

public class WorkflowModels {
  // Simple data carriers for the example
  public record OrderData(String orderId, String productId, int quantity, String paymentDetails, String shippingAddress, String customerId) {}

  public record ValidatedOrder(String orderId, String productId, int quantity, String paymentDetails, double amount, String shippingAddress, String customerId) {}

  public record PaymentConfirmation(String transactionId) {}

  public record ShipmentInfo(String trackingId) {}

  public record FinalResult(String orderId, String transactionId, String trackingId) {}

  public record WorkflowContext(
          OrderData initialData, // Keep initial data if needed later (e.g., for notification)
          @Nullable ValidatedOrder validatedOrder,
          boolean inventoryChecked, // Simple flag instead of Void
          @Nullable PaymentConfirmation paymentConfirmation,
          @Nullable ShipmentInfo shipmentInfo
  ) {
    // Static factory for initial state
    public static WorkflowContext start(OrderData data) {
      return new WorkflowContext(data, null, false, null, null);
    }

    // "Lens"-like methods to create updated context copies
    public WorkflowContext withValidatedOrder(ValidatedOrder vo) {
      // Ensure initialData is carried over if it exists
      return new WorkflowContext(this.initialData != null ? this.initialData : null, vo, this.inventoryChecked, this.paymentConfirmation, this.shipmentInfo);
    }

    public WorkflowContext withInventoryChecked() {
      return new WorkflowContext(this.initialData, this.validatedOrder, true, this.paymentConfirmation, this.shipmentInfo);
    }

    public WorkflowContext withPaymentConfirmation(PaymentConfirmation pc) {
      return new WorkflowContext(this.initialData, this.validatedOrder, this.inventoryChecked, pc, this.shipmentInfo);
    }

    public WorkflowContext withShipmentInfo(ShipmentInfo si) {
      return new WorkflowContext(this.initialData, this.validatedOrder, this.inventoryChecked, this.paymentConfirmation, si);
    }

    // --- Convenience Getters (could throw if accessed at wrong stage) ---
    // These assume the workflow progresses linearly and checks are made
    public ValidatedOrder validatedOrder() {
      if (validatedOrder == null) throw new IllegalStateException("ValidatedOrder not available yet.");
      return validatedOrder;
    }
    public PaymentConfirmation paymentConfirmation() {
      if (paymentConfirmation == null) throw new IllegalStateException("PaymentConfirmation not available yet.");
      return paymentConfirmation;
    }
    public ShipmentInfo shipmentInfo() {
      if (shipmentInfo == null) throw new IllegalStateException("ShipmentInfo not available yet.");
      return shipmentInfo;
    }
    public OrderData initialData() {
      if (initialData == null) throw new IllegalStateException("InitialData not available.");
      return initialData;
    }
  }
}

