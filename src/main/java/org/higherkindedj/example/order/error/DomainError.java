package org.higherkindedj.example.order.error;

import java.io.Serializable;

public sealed interface DomainError extends Serializable {
  String message();

  record ValidationError(String message) implements DomainError {
    @Override
    public String message() {
      return message;
    }
  }

  record StockError(String productId) implements DomainError {
    @Override
    public String message() {
      return "Out of stock for product: " + productId;
    }
  }

  record PaymentError(String reason) implements DomainError {
    @Override
    public String message() {
      return "Payment failed: " + reason;
    }
  }

  record ShippingError(String reason) implements DomainError {
    @Override
    public String message() {
      return "Shipping failed: " + reason;
    }
  }
}
