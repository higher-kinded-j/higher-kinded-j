// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateLenses;

/** A product as a category lists it: whether it is on sale, and any promotion running on it. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record ProductListing(
    Product product, ProductStatus status, Optional<Promotion> activePromotion) {}
