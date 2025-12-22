// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Customer details retrieved from the customer service.
 *
 * @param id the customer identifier
 * @param name the customer's name
 * @param email the customer's email address
 * @param phone optional phone number
 * @param status account status
 * @param loyaltyTier loyalty programme tier
 */
@GenerateLenses
@GenerateFocus
public record Customer(
    CustomerId id,
    String name,
    String email,
    String phone,
    CustomerStatus status,
    LoyaltyTier loyaltyTier) {
  /** Customer account status. */
  public enum CustomerStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
  }

  /** Loyalty programme tier, affecting discounts and benefits. */
  public enum LoyaltyTier {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM
  }

  /**
   * Checks if the customer account is active.
   *
   * @return true if the customer can place orders
   */
  public boolean isActive() {
    return status == CustomerStatus.ACTIVE;
  }
}
