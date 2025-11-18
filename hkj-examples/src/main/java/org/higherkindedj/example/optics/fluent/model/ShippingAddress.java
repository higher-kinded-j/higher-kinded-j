// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import org.higherkindedj.optics.annotations.GenerateLenses;

/** Represents a shipping address for an order. */
@GenerateLenses
public record ShippingAddress(String street, String city, String postCode, String country) {}
