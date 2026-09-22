// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The law check shown on the book's Record Mapping page. The page {@code {{#include}}}s the
 * anchored region below, so the snippet it displays is this test, and it is green.
 *
 * <p>{@code hkj-test} is test-scope, which is why the laws live in a test rather than beside the
 * spec.
 */
@DisplayName("the book's record mappings obey the mapping laws")
class RecordMappingBookLawsTest {

  @Test
  void customerMappingObeysTheLaws() {
    // ANCHOR: laws
    MappingLaws.assertMappingLaws(
        CustomerMappingImpl.INSTANCE.asValidatedPrism(),
        new CustomerDto("Ada", "ada@example.org"), // parses
        new CustomerDto("Bob", "not-an-email")); // must not parse
    // ANCHOR_END: laws
  }

  @Test
  void reservationMappingLocatesAStaysInvariantAndObeysTheLaws() {
    ReservationDto accepted =
        new ReservationDto("Ada", List.of(new StayDto("2026-03-01", "2026-03-04")));
    ReservationDto refused =
        new ReservationDto("Ada", List.of(new StayDto("2026-03-09", "2026-03-07")));
    MappingLaws.assertMappingLaws(
        ReservationMappingImpl.INSTANCE.asValidatedPrism(), accepted, refused);

    // The located errors the page shows, exactly:
    assertThatValidated(
            ReservationMappingImpl.INSTANCE.parse(
                new ReservationDto(
                    null,
                    List.of(
                        new StayDto("2026-03-01", "2026-03-04"),
                        new StayDto("2026-03-09", "2026-03-07")))))
        .isInvalid()
        .hasFieldErrors("guest: must not be null", "stays.1: checkOut must be after checkIn");
  }

  @Test
  void contactPatchMappingObeysTheSparseLaws() {
    // ANCHOR: update_laws
    MappingLaws.assertMappingLaws(
        ContactPatchMappingImpl.INSTANCE::updateFrom,
        new Customer("Ada", new EmailAddress("ada@example.org")), // the current value
        new ContactPatchBean(), // all-absent, as bound from {} -> identity
        patch("Grace", "grace@example.org"), // present valid -> changes the domain
        patch(null, "not-an-email")); // present invalid -> located failure
    // ANCHOR_END: update_laws
  }

  @Test
  @DisplayName("the price band PATCH constructs once, as the page's comments claim")
  void priceBandPatchConstructsOnce() {
    PriceBand band = new PriceBand(10, 20);
    PriceBandPatch raise = new PriceBandPatch();
    raise.setFloor(30);
    raise.setCeiling(40);
    PriceBandPatch floorOnly = new PriceBandPatch();
    floorOnly.setFloor(30);

    assertThatValidated(PriceBandPatchMappingImpl.INSTANCE.updateFrom(raise).apply(band))
        .isValid()
        .hasValue(new PriceBand(30, 40));
    // ANCHOR: update_invariant_laws
    var priceBandMapping = PriceBandPatchMappingImpl.INSTANCE;
    MappingLaws.assertSparseIdentity(priceBandMapping::updateFrom, band, new PriceBandPatch());
    MappingLaws.assertSparseIdempotent(priceBandMapping::updateFrom, band, raise);
    assertThatValidated(priceBandMapping.updateFrom(floorOnly).apply(band))
        .hasFieldErrors("floor above ceiling"); // the constructor's refusal, unlabelled
    // ANCHOR_END: update_invariant_laws
  }

  @Test
  void subscriberPatchMappingObeysThePatchLaws() {
    // ANCHOR: patch_laws
    var subscriberDetailsMapping = SubscriberDetailsMappingImpl.INSTANCE;
    MappingLaws.assertMappingLaws(
        subscriberDetailsMapping::patch,
        subscriberDetailsMapping::build,
        new Subscriber("7", new EmailAddress("ada@example.org"), 36), // the current value
        new SubscriberDetailsDto("grace@example.org", 41), // parses and changes the domain
        new SubscriberDetailsDto("not-an-email", 36)); // located failure
    // ANCHOR_END: patch_laws
  }

  @Test
  void transferBeanProjectionObeysThePatchLaws() {
    Employee ada = new Employee("Ada", "Research", 36);
    MappingLaws.assertMappingLaws(
        TransferMappingImpl.INSTANCE::patch,
        TransferMappingImpl.INSTANCE::build,
        ada, // the current value
        transfer("Platform"), // parses and changes the domain
        transfer(null)); // an unset property: located failure

    // The located error the page shows, exactly:
    assertThatValidated(TransferMappingImpl.INSTANCE.patch(ada, new TransferBean()))
        .isInvalid()
        .hasFieldErrors("department: must not be null");
  }

  @Test
  void oneDirectionalBeanMappingsObeyTheirLaws() {
    // ANCHOR: one_way_laws
    MappingLaws.assertMappingLaws(
        CustomerViewMappingImpl.INSTANCE.asValidatedParse(),
        new CustomerView("Ada", "ada@example.org"), // parses
        new CustomerView("Bob", "not-an-email")); // located failure

    MappingLaws.assertMappingLaws(
        CustomerRequestMappingImpl.INSTANCE.asValidatedBuild(),
        new Customer("Ada", new EmailAddress("ada@example.org"))); // renders without failing
    // ANCHOR_END: one_way_laws
  }

  @Test
  void rosterPatchMappingObeysTheSparseLawsOverContainerElements() {
    // ANCHOR: update_container_laws
    MappingLaws.assertMappingLaws(
        RosterPatchMappingImpl.INSTANCE::updateFrom,
        new Roster("core", List.of(new PhoneNumber("+44"))), // the current value
        new RosterPatchBean(), // all-absent, as bound from {} -> identity
        rosterPatch(null, List.of("+1", "+353")), // present valid -> wholesale replacement
        rosterPatch(null, List.of("+1", "nope"))); // bad element   -> located phones.1
    // ANCHOR_END: update_container_laws

    // The located element error, exactly:
    assertThat(
            RosterPatchMappingImpl.INSTANCE
                .updateFrom(rosterPatch(null, List.of("+1", "nope")))
                .apply(new Roster("core", List.of(new PhoneNumber("+44"))))
                .getError()
                .toJavaList())
        .containsExactly(new FieldError(List.of("phones", "1"), "not a phone number"));
  }

  private static ContactPatchBean patch(String name, String email) {
    ContactPatchBean bean = new ContactPatchBean();
    bean.setName(name);
    bean.setEmail(email);
    return bean;
  }

  private static TransferBean transfer(String department) {
    TransferBean bean = new TransferBean();
    bean.setDepartment(department);
    return bean;
  }

  private static RosterPatchBean rosterPatch(String team, List<String> phones) {
    RosterPatchBean bean = new RosterPatchBean();
    bean.setTeam(team);
    bean.setPhones(phones);
    return bean;
  }

  @Test
  @DisplayName("each container locates a failure by whatever identifies an element in it")
  void everyContainerLocatesItsOwnWay() {
    // ANCHOR: check_container_paths
    Validated<NonEmptyList<FieldError>, Crew> parsed =
        CrewMappingImpl.INSTANCE.parse(
            new CrewDto(
                Set.of("nope"), // a set has no index
                new String[] {"ada@example.org", "also-nope"}, // an array does
                Map.of("bad-key", "a note"))); // a map has the key as sent

    assertThatValidated(parsed)
        .isInvalid()
        .hasFieldErrors(
            "members.nope: not an email address",
            "reserves.1: not an email address",
            "notes.bad-key: not an email address");
    // ANCHOR_END: check_container_paths
  }
}
