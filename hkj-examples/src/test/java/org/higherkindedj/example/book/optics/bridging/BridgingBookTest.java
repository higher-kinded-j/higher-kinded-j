// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.bridging;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.optics.bridge.domain.Office;
import org.higherkindedj.example.optics.bridge.domain.OfficeNetwork;
import org.higherkindedj.example.optics.bridge.domain.OfficeWorker;
import org.higherkindedj.example.optics.bridge.external.Address;
import org.higherkindedj.example.optics.bridge.external.ContactDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Focus DSL with External Libraries page shows for its bridge. */
@DisplayName("Focus DSL with External Libraries: reads and a write across the bridge")
class BridgingBookTest {

  private static final Address HEADQUARTERS =
      Address.builder().street("1 Corporate Plaza").city("New York").postcode("10001").build();

  /** The page's sample: New York headquarters, and offices in Boston and Chicago. */
  private static OfficeNetwork acme() {
    OfficeWorker alice =
        new OfficeWorker(
            "E001",
            "Alice Smith",
            ContactDetails.builder().email("alice@acme.com").phone("617-555-0101").build(),
            new BigDecimal("95000"));
    OfficeWorker bob =
        new OfficeWorker(
            "E002",
            "Bob Jones",
            ContactDetails.builder().email("bob@acme.com").phone("617-555-0102").build(),
            new BigDecimal("85000"));
    OfficeWorker carol =
        new OfficeWorker(
            "E003",
            "Carol White",
            ContactDetails.builder().email("carol@acme.com").phone("312-555-0201").build(),
            new BigDecimal("75000"));
    Office engineering =
        new Office(
            "Engineering",
            alice,
            List.of(alice, bob),
            Address.builder().street("100 Tech Drive").city("Boston").postcode("02101").build());
    Office sales =
        new Office(
            "Sales",
            carol,
            List.of(carol),
            Address.builder().street("200 Commerce St").city("Chicago").postcode("60601").build());
    return new OfficeNetwork("Acme Corp", HEADQUARTERS, List.of(engineering, sales));
  }

  @Test
  @DisplayName("the bridge reads the headquarters' city, and every email and phone")
  void readsAcrossTheBoundary() {
    BridgingBook.BridgeReads reads = BridgingBook.readThroughTheBridge(acme());

    assertThat(reads.city()).isEqualTo("New York");
    assertThat(reads.emails()).containsExactly("alice@acme.com", "bob@acme.com", "carol@acme.com");
    assertThat(reads.phones()).containsExactly("617-555-0101", "617-555-0102", "312-555-0201");
  }

  @Test
  @DisplayName("the bridge moves the headquarters' city and leaves the rest of the Address alone")
  void setChangesOnlyTheCity() {
    OfficeNetwork acme = acme();

    OfficeNetwork moved = BridgingBook.readThroughTheBridge(acme).moved();

    assertThat(moved.headquarters()).isEqualTo(HEADQUARTERS.withCity("Seattle"));
    assertThat(moved.offices()).isSameAs(acme.offices());
    assertThat(acme.headquarters()).isEqualTo(HEADQUARTERS);
  }
}
