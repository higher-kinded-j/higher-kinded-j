// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.math.BigDecimal;
import java.util.Objects;

// ANCHOR: pricing_config
/**
 * What pricing reads from its environment: the VAT rate, and the delivery charge for an order below
 * the free-delivery threshold.
 */
public record PricingConfig(
    BigDecimal vatRate, BigDecimal deliveryCharge, BigDecimal freeDeliveryFrom) {

  public PricingConfig {
    Objects.requireNonNull(vatRate, "vatRate");
    Objects.requireNonNull(deliveryCharge, "deliveryCharge");
    Objects.requireNonNull(freeDeliveryFrom, "freeDeliveryFrom");
  }
}
// ANCHOR_END: pricing_config
