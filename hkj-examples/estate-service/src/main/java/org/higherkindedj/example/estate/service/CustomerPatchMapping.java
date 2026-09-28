// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.service;

import org.higherkindedj.example.estate.api.ContactVocabulary;
import org.higherkindedj.example.estate.clients.CustomerPatch;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.UpdateSpec;

/**
 * The customer's PATCH: an omitted field keeps its value, an empty {@code Optional} nickname clears
 * it, and a sent address replaces the old one whole. The patch has no id, so a PATCH never changes
 * it.
 */
@GenerateMapping
public interface CustomerPatchMapping extends ContactVocabulary, UpdateSpec<Customer, CustomerPatch> {}
