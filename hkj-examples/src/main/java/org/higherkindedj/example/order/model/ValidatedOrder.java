// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.model.value.PromoCode;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A fully validated order ready for processing.
 *
 * <p>This record is created after validation passes. It contains enriched data including the full
 * customer and product details, calculated totals, and a generated order ID.
 *
 * @param orderId the generated order identifier
 * @param customerId the customer identifier
 * @param customer the full customer details
 * @param lines the validated order lines with product details
 * @param promoCode optional validated promotional code
 * @param shippingAddress the validated shipping address
 * @param paymentMethod the payment method
 * @param subtotal the order subtotal before discounts
 * @param createdAt when the order was created
 */
@GenerateLenses
@GenerateFocus
public record ValidatedOrder(
    OrderId orderId,
    CustomerId customerId,
    Customer customer,
    List<ValidatedOrderLine> lines,
    Optional<PromoCode> promoCode,
    ValidatedShippingAddress shippingAddress,
    PaymentMethod paymentMethod,
    Money subtotal,
    Instant createdAt) {
  /**
   * Calculates the subtotal from the order lines.
   *
   * @param lines the order lines
   * @return the sum of all line totals
   */
  public static Money calculateSubtotal(List<ValidatedOrderLine> lines) {
    return lines.stream().map(ValidatedOrderLine::lineTotal).reduce(Money.ZERO_GBP, Money::add);
  }
}
