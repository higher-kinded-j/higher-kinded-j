package org.simulation.hkt.example.order.model;

public class WorkflowModels {
  // Simple data carriers for the example
  public record OrderData(String orderId, String productId, int quantity, String paymentDetails, String shippingAddress, String customerId) {}

  public record ValidatedOrder(String orderId, String productId, int quantity, String paymentDetails, double amount, String shippingAddress, String customerId) {}

  public record PaymentConfirmation(String transactionId) {}

  public record ShipmentInfo(String trackingId) {}

  public record FinalResult(String orderId, String transactionId, String trackingId) {}
}

