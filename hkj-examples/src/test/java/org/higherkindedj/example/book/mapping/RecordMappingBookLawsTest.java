// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.validated.FieldError;
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
  void contactPatchMappingObeysTheSparseLaws() {
    // ANCHOR: update_laws
    MappingLaws.assertMappingLaws(
        ContactPatchMappingImpl.INSTANCE::updateFrom,
        new Customer("Ada", new EmailAddress("ada@example.org")), // the current value
        patch(null, null), // all-absent   -> identity
        patch("Grace", "grace@example.org"), // present valid -> changes the domain
        patch(null, "not-an-email")); // present invalid -> located failure
    // ANCHOR_END: update_laws
  }

  @Test
  void subscriberPatchMappingObeysThePatchLaws() {
    // ANCHOR: patch_laws
    MappingLaws.assertMappingLaws(
        SubscriberDetailsMappingImpl.INSTANCE::patch,
        SubscriberDetailsMappingImpl.INSTANCE::build,
        new Subscriber("7", new EmailAddress("ada@example.org"), 36), // the current value
        new SubscriberDetailsDto("grace@example.org", 41), // parses and changes the domain
        new SubscriberDetailsDto("not-an-email", 36)); // located failure
    // ANCHOR_END: patch_laws
  }

  @Test
  void rosterPatchMappingObeysTheSparseLawsOverContainerElements() {
    // ANCHOR: update_container_laws
    MappingLaws.assertMappingLaws(
        RosterPatchMappingImpl.INSTANCE::updateFrom,
        new Roster("core", List.of(new PhoneNumber("+44"))), // the current value
        rosterPatch(null, null), // all-absent    -> identity
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

  private static RosterPatchBean rosterPatch(String team, List<String> phones) {
    RosterPatchBean bean = new RosterPatchBean();
    bean.setTeam(team);
    bean.setPhones(phones);
    return bean;
  }
}
