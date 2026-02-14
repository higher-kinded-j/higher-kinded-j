// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A validated order line with product details and calculated totals.
 *
 * @param productId the product identifier
 * @param product the full product details
 * @param quantity the quantity ordered
 * @param unitPrice the price per unit at time of order
 * @param lineTotal the total for this line (unitPrice * quantity)
 */
@GenerateLenses
@GenerateFocus
public record ValidatedOrderLine(
    ProductId productId, Product product, int quantity, Money unitPrice, Money lineTotal) {
  /**
   * Creates a validated order line, calculating the line total.
   *
   * @param productId the product identifier
   * @param product the product details
   * @param quantity the quantity
   * @return a new ValidatedOrderLine with calculated total
   */
  public static ValidatedOrderLine of(ProductId productId, Product product, int quantity) {
    var unitPrice = product.price();
    var lineTotal = unitPrice.multiply(quantity);
    return new ValidatedOrderLine(productId, product, quantity, unitPrice, lineTotal);
  }
}
