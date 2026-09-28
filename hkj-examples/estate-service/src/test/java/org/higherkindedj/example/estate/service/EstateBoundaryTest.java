// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.example.estate.api.EmailAddress;
import org.higherkindedj.example.estate.clients.AddressBean;
import org.higherkindedj.example.estate.clients.CustomerPatch;
import org.higherkindedj.example.estate.clients.CustomerResource;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the estate boundary: three modules, a Lombok wire, a PATCH that clears, and the laws")
class EstateBoundaryTest {

  private static final CustomerResourceMappingImpl RESOURCE = CustomerResourceMappingImpl.INSTANCE;
  private static final CustomerPatchMappingImpl PATCH = CustomerPatchMappingImpl.INSTANCE;

  private static final Customer ADA =
      new Customer(
          UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
          "Ada Lovelace",
          new EmailAddress("ada@example.org"),
          Optional.of("Countess"),
          new Address("1 High Street", "Leeds", "LS1 4AP"));

  private static AddressBean address(String street, String city, String postcode) {
    AddressBean bean = new AddressBean();
    bean.setStreet(street);
    bean.setCity(city);
    bean.setPostcode(postcode);
    return bean;
  }

  @Test
  @DisplayName("the resource round-trips through the vocabulary, the Lombok bean and the bridge")
  void resourceRoundTrips() {
    CustomerResource wire = RESOURCE.build(ADA);

    assertThat(wire.getFullName()).isEqualTo("Ada Lovelace");
    assertThat(wire.getAddress()).isEqualTo(address("1 High Street", "Leeds", "LS1 4AP"));
    assertThatValidated(RESOURCE.parse(wire)).isValid().hasValue(ADA);
  }

  @Test
  @DisplayName("every bad field of the resource is located, the nested Lombok bean's included")
  void resourceLocatesEveryBadField() {
    CustomerResource wire = RESOURCE.build(ADA);
    wire.setId("NOPE");
    wire.setEmail("not-an-email");
    wire.getAddress().setCity(null);

    assertThatValidated(RESOURCE.parse(wire))
        .isInvalid()
        .hasFieldErrors(
            "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
            "email: not an email address",
            "address.city: must not be null");
  }

  @Test
  @DisplayName("the resource mapping obeys the mapping laws")
  void resourceObeysTheLaws() {
    CustomerResource good = RESOURCE.build(ADA);
    CustomerResource bad = RESOURCE.build(ADA);
    bad.setEmail("nope");

    MappingLaws.assertMappingLaws(RESOURCE.asValidatedPrism(), good, bad);
  }

  @Test
  @DisplayName("a PATCH keeps what it omits, clears an empty Optional, and replaces the address whole")
  void patchClearsAndReplaces() {
    CustomerPatch patch = new CustomerPatch();
    patch.setNickname(Optional.empty()); // "nickname": null
    patch.setAddress(address("2 Park Row", "York", "YO1 7HH"));

    assertThatValidated(PATCH.updateFrom(patch).apply(ADA))
        .isValid()
        .hasValue(
            new Customer(
                ADA.id(),
                ADA.name(),
                ADA.email(),
                Optional.empty(),
                new Address("2 Park Row", "York", "YO1 7HH")));
  }

  @Test
  @DisplayName("the PATCH mapping obeys the sparse laws")
  void patchObeysTheSparseLaws() {
    CustomerPatch valid = new CustomerPatch();
    valid.setFullName("Augusta Ada King");
    CustomerPatch invalid = new CustomerPatch();
    invalid.setEmail("nope");

    MappingLaws.assertMappingLaws(PATCH::updateFrom, ADA, new CustomerPatch(), valid, invalid);
  }
}
