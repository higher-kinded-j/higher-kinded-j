// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The law checks behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/tiers.html">What Your Spec Generates</a>
 * page. The page {@code {{#include}}}s the anchored regions below, so the snippet it displays is
 * this test, and it is green.
 *
 * <p>{@code hkj-test} is test-scope, which is why the laws live in a test rather than beside the
 * spec.
 */
@DisplayName("the What Your Spec Generates page's mappings obey the mapping laws")
class TiersBookTest {

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
}
