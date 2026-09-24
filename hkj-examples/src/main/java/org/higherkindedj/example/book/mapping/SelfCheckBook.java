// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.OptionalBridge;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.jspecify.annotations.Nullable;

/**
 * The specs behind the book's <a
 * href="https://higher-kinded-j.github.io/mapping/self_check.html">Check Your Understanding</a>
 * page. Its answers include these regions, and {@code SelfCheckBookTest} asserts what each answer
 * claims, so an answer cannot drift from what the processor generates.
 */
public final class SelfCheckBook {

  private SelfCheckBook() {}
}

// The trap the page asks about, outside the region so the question does not give it away: an
// instance bound on the spec itself. Never do this; bind the Impl in the calling code.
// SelfCheckBookTest.instanceBoundOnTheSpecDependsOnWhatRunsFirst proves why.
// ANCHOR: trap_spec
record Visitor(String name, EmailAddress email) {}

record VisitorDto(String name, String email) {}

@GenerateMapping
interface VisitorMapping extends MappingSpec<Visitor, VisitorDto> {
  VisitorMappingImpl MAPPER = VisitorMappingImpl.INSTANCE;

  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: trap_spec

// ANCHOR: shipment_pair
record Parcel(String sku, int grams) {}

record ParcelDto(String sku, int grams) {}

record Shipment(UUID id, List<Parcel> parcels, Optional<String> note) {}

record ShipmentDto(String id, List<ParcelDto> items, @Nullable String note) {}

// ANCHOR_END: shipment_pair

// ANCHOR: shipment_spec
@GenerateMapping
interface ParcelMapping extends MappingSpec<Parcel, ParcelDto> {}

@GenerateMapping
interface ShipmentMapping extends MappingSpec<Shipment, ShipmentDto> {
  default ValidatedPrism<String, UUID> id() {
    return StandardCodecs.uuid();
  }

  @MapField(to = "items")
  List<Parcel> parcels();

  @OptionalBridge
  Optional<String> note();
}

// ANCHOR_END: shipment_spec
