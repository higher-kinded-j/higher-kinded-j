// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.higherkindedj.optics.annotations.GenerateLenses;

/** Represents a percentage discount with a validity period. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record TimedDiscount(
    String promotionId, BigDecimal discountPercent, LocalDate startDate, LocalDate endDate) {}
