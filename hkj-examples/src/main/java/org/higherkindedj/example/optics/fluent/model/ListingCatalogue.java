// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/** Represents a catalogue of product listings, grouped into categories. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
@GenerateTraversals(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record ListingCatalogue(String catalogueId, String name, List<ListingCategory> categories) {}
