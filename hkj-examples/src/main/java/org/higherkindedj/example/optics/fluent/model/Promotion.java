// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.higherkindedj.optics.annotations.GenerateLenses;

/** Represents a promotional discount with validity period. */
@GenerateLenses
public record Promotion(
    String promotionId, BigDecimal discountPercent, LocalDate startDate, LocalDate endDate) {}
