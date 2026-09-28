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
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The proof behind the book's "Capstone: An Estate in Three Modules" page. The page includes the
 * anchored regions below, so each claim it makes about the three modules is asserted here.
 */
@DisplayName("the estate boundary: three modules, a Lombok wire, a PATCH that clears, and the laws")
class EstateBoundaryTest {

  // The generated Impls, bound once for the class.
  private static final CustomerResourceMappingImpl RESOURCE = CustomerResourceMappingImpl.INSTANCE;
  private static final CustomerPatchMappingImpl PATCH = CustomerPatchMappingImpl.INSTANCE;

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final Customer ADA =
      new Customer(
          UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
          "Ada Lovelace",
          new EmailAddress("ada@example.org"),
          Optional.of("Countess"),
          new Address("1 High Street", "Leeds", "LS1 4AP"));

  /** Binds a PATCH body as a controller would, then applies it to Ada as she is stored. */
  private static Validated<NonEmptyList<FieldError>, Customer> patch(String body) {
    return PATCH.updateFrom(JSON.readValue(body, CustomerPatch.class)).apply(ADA);
  }

  @Test
  @DisplayName("the resource round-trips through the vocabulary, the Lombok bean and the bridge")
  void resourceRoundTrips() {
    // ANCHOR: round_trip
    CustomerResource wire = RESOURCE.build(ADA);

    assertThat(wire.getFullName()).isEqualTo("Ada Lovelace"); // the api module's rename
    assertThat(wire.getNickname()).isEqualTo("Countess"); // the bean bridges the Optional
    assertThat(wire.getAddress().getCity()).isEqualTo("Leeds"); // Lombok's accessors, from a jar
    assertThatValidated(RESOURCE.parse(wire)).isValid().hasValue(ADA);
    // ANCHOR_END: round_trip
  }

  @Test
  @DisplayName("every bad field of the resource is located, the nested Lombok bean's included")
  void resourceLocatesEveryBadField() {
    // ANCHOR: every_bad_field
    CustomerResource wire = RESOURCE.build(ADA);
    wire.setId("NOPE");
    wire.setEmail("not-an-email");
    wire.getAddress().setCity(null);

    assertThatValidated(RESOURCE.parse(wire))
        .isInvalid()
        .hasFieldErrors(
            "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
            "email: not an email address", // the api module's leaf
            "address.city: must not be null"); // inside the Lombok bean
    // ANCHOR_END: every_bad_field
  }

  @Test
  @DisplayName("a PATCH keeps what it omits, clears on a JSON null, and replaces the address whole")
  void patchClearsAndReplaces() {
    // ANCHOR: patch_json
    assertThatValidated(patch("{}")).hasValue(ADA); // omitted: kept

    assertThatValidated(patch("{\"nickname\": null}")) // a JSON null: cleared
        .hasValue(new Customer(ADA.id(), ADA.name(), ADA.email(), Optional.empty(), ADA.address()));

    assertThatValidated(
            patch(
                "{\"address\": {\"street\": \"2 Park Row\", \"city\": \"York\","
                    + " \"postcode\": \"YO1 7HH\"}}")) // an address: replaced whole
        .hasValue(
            new Customer(
                ADA.id(),
                ADA.name(),
                ADA.email(),
                ADA.nickname(),
                new Address("2 Park Row", "York", "YO1 7HH")));
    // ANCHOR_END: patch_json
  }

  @Test
  @DisplayName("an address sent in part is refused, not merged: the address is replaced whole")
  void aPartialAddressIsRefused() {
    // ANCHOR: partial_address
    assertThatValidated(patch("{\"address\": {\"street\": \"2 Park Row\"}}"))
        .isInvalid()
        .hasFieldErrors("address.city: must not be null", "address.postcode: must not be null");
    // ANCHOR_END: partial_address
  }

  @Test
  @DisplayName("both mappings obey their laws")
  void bothMappingsObeyTheirLaws() {
    // ANCHOR: laws
    CustomerResource good = RESOURCE.build(ADA);
    CustomerResource bad = RESOURCE.build(ADA);
    bad.setEmail("nope");
    MappingLaws.assertMappingLaws(RESOURCE.asValidatedPrism(), good, bad);

    CustomerPatch rename = new CustomerPatch();
    rename.setFullName("Augusta Ada King");
    CustomerPatch badEmail = new CustomerPatch();
    badEmail.setEmail("nope");
    MappingLaws.assertMappingLaws(PATCH::updateFrom, ADA, new CustomerPatch(), rename, badEmail);
    // ANCHOR_END: laws
  }

  @Test
  @DisplayName(
      "the Lombok bean is a plain bean to the mapper: a no-args constructor, getters, setters")
  void theLombokBeanIsAPlainBean() {
    AddressBean bean = new AddressBean();
    bean.setStreet("1 High Street");
    assertThat(bean.getStreet()).isEqualTo("1 High Street");
  }
}
