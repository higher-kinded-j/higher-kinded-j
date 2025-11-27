// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.math.BigDecimal;
import org.higherkindedj.optics.annotations.GenerateLenses;

/** Represents an item in an order. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record OrderItem(String productId, String productName, int quantity, BigDecimal price) {}
