// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

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

  private static ContactPatchBean patch(String name, String email) {
    ContactPatchBean bean = new ContactPatchBean();
    bean.setName(name);
    bean.setEmail(email);
    return bean;
  }
}
