// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Individual line item in an order request.
 *
 * @param productId the product identifier
 * @param quantity the quantity requested (must be positive)
 */
@GenerateLenses
@GenerateFocus
public record OrderLineRequest(String productId, int quantity) {
  public OrderLineRequest {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Quantity must be positive, got: " + quantity);
    }
  }
}
