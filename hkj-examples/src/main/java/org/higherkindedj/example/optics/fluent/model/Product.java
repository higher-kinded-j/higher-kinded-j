// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.math.BigDecimal;
import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateLenses;

/** Represents a product with pricing and promotion details. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record Product(
    String productId,
    String name,
    BigDecimal price,
    int stockLevel,
    ProductStatus status,
    Optional<Promotion> activePromotion) {}
