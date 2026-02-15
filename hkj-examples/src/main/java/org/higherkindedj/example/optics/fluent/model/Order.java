// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/** Represents an e-commerce order with items and shipping details. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
@GenerateTraversals(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record Order(
    String orderId, OrderStatus status, List<OrderItem> items, ShippingAddress address) {}
