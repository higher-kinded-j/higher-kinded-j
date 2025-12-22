// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A validated shipping address that has passed address validation checks.
 *
 * @param name recipient name
 * @param street validated street address
 * @param city validated city
 * @param postcode validated and normalised postal code
 * @param country validated country code
 * @param shippingZone determined shipping zone for carrier selection
 */
@GenerateLenses
@GenerateFocus
public record ValidatedShippingAddress(
    String name,
    String street,
    String city,
    String postcode,
    String country,
    ShippingZone shippingZone) {
  /** Shipping zones for carrier and rate selection. */
  public enum ShippingZone {
    DOMESTIC,
    EUROPE,
    INTERNATIONAL
  }

  /**
   * Creates a validated address from a raw shipping address.
   *
   * @param address the original address
   * @param zone the determined shipping zone
   * @return a validated shipping address
   */
  public static ValidatedShippingAddress from(ShippingAddress address, ShippingZone zone) {
    return new ValidatedShippingAddress(
        address.name(),
        address.street(),
        address.city(),
        address.postcode().toUpperCase().replaceAll("\\s+", " "),
        address.country().toUpperCase(),
        zone);
  }
}
