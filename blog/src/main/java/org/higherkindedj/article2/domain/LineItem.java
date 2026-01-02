// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.math.BigDecimal;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A line item in an order.
 *
 * <p>The {@code @GenerateLenses} annotation generates {@code LineItemLenses} with static lens
 * methods for each field.
 */
@GenerateLenses
public record LineItem(String productId, int quantity, BigDecimal price) {

  /** Calculate the total for this line item. */
  public BigDecimal total() {
    return price.multiply(BigDecimal.valueOf(quantity));
  }
}
