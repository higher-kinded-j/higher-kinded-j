// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Input request for creating an order.
 *
 * <p>This is the unvalidated input from the client. The workflow validates this and transforms it
 * into a {@link ValidatedOrder}.
 *
 * @param customerId the customer placing the order
 * @param lines the line items being ordered
 * @param promoCode optional promotional code
 * @param shippingAddress the delivery address
 * @param paymentMethod how the order will be paid
 */
@GenerateLenses
@GenerateFocus
public record OrderRequest(
    String customerId,
    List<OrderLineRequest> lines,
    Optional<String> promoCode,
    ShippingAddress shippingAddress,
    PaymentMethod paymentMethod) {
  public OrderRequest {
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException("Order must have at least one line item");
    }
  }
}
