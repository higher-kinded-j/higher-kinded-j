// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.bridging;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.optics.bridge.domain.Company;
import org.higherkindedj.example.optics.bridge.domain.Department;
import org.higherkindedj.example.optics.bridge.domain.Employee;
import org.higherkindedj.example.optics.bridge.external.Address;
import org.higherkindedj.example.optics.bridge.external.ContactInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Focus DSL with External Libraries page shows for its bridge. */
@DisplayName("Focus DSL with External Libraries: reads and a write across the bridge")
class BridgingBookTest {

  private static final Address HEADQUARTERS =
      Address.builder().street("1 Corporate Plaza").city("New York").postcode("10001").build();

  /** The page's sample: New York headquarters, and departments in Boston and Chicago. */
  private static Company acme() {
    Employee alice =
        new Employee(
            "E001",
            "Alice Smith",
            ContactInfo.builder().email("alice@acme.com").phone("617-555-0101").build(),
            new BigDecimal("95000"));
    Employee bob =
        new Employee(
            "E002",
            "Bob Jones",
            ContactInfo.builder().email("bob@acme.com").phone("617-555-0102").build(),
            new BigDecimal("85000"));
    Employee carol =
        new Employee(
            "E003",
            "Carol White",
            ContactInfo.builder().email("carol@acme.com").phone("312-555-0201").build(),
            new BigDecimal("75000"));
    Department engineering =
        new Department(
            "Engineering",
            alice,
            List.of(alice, bob),
            Address.builder().street("100 Tech Drive").city("Boston").postcode("02101").build());
    Department sales =
        new Department(
            "Sales",
            carol,
            List.of(carol),
            Address.builder().street("200 Commerce St").city("Chicago").postcode("60601").build());
    return new Company("Acme Corp", HEADQUARTERS, List.of(engineering, sales));
  }

  @Test
  @DisplayName("the bridge reads the headquarters' city, and every email and phone")
  void readsAcrossTheBoundary() {
    BridgingBook.Reads reads = BridgingBook.readThroughTheBridge(acme());

    assertThat(reads.city()).isEqualTo("New York");
    assertThat(reads.emails()).containsExactly("alice@acme.com", "bob@acme.com", "carol@acme.com");
    assertThat(reads.phones()).containsExactly("617-555-0101", "617-555-0102", "312-555-0201");
  }

  @Test
  @DisplayName("the bridge moves the headquarters' city and leaves the rest of the Address alone")
  void setChangesOnlyTheCity() {
    Company acme = acme();

    Company moved = BridgingBook.readThroughTheBridge(acme).moved();

    assertThat(moved.headquarters()).isEqualTo(HEADQUARTERS.withCity("Seattle"));
    assertThat(moved.departments()).isSameAs(acme.departments());
    assertThat(acme.headquarters()).isEqualTo(HEADQUARTERS);
  }
}
