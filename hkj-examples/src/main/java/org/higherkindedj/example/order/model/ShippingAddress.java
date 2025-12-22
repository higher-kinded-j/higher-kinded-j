// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Shipping address for an order.
 *
 * @param name recipient name
 * @param street street address
 * @param city city
 * @param postcode postal code
 * @param country country code (e.g., "GB", "US")
 */
@GenerateLenses
@GenerateFocus
public record ShippingAddress(
    String name, String street, String city, String postcode, String country) {}
