// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.lang.reflect.Method;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The law checks behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/tiers.html">What Your Spec Generates</a>
 * page, and the proofs of its checkpoint answers. The page {@code {{#include}}}s the anchored
 * regions below, so the snippet it displays is this test, and it is green.
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

  // ANCHOR: laws_each_spelling
  @ParameterizedTest
  @CsvSource({
    "ada@example.org,        not-an-email",
    "grace@corp.example,     grace.corp.example",
    "ada+orders@example.org, ''"
  })
  void customerMappingObeysTheLawsAtEachSpelling(String parses, String fails) {
    MappingLaws.assertMappingLaws(
        CustomerMappingImpl.INSTANCE.asValidatedPrism(),
        new CustomerDto("Ada", parses),
        new CustomerDto("Ada", fails));
  }

  // ANCHOR_END: laws_each_spelling

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
  @DisplayName("a leaf that never fails still costs the pair its asIso()")
  void aLeafThatNeverFailsCostsTheIso() {
    // ANCHOR: check_leaf_no_iso
    assertThat(CouponMappingImpl.class.getMethods())
        .extracting(Method::getName)
        .contains("build", "parse", "asValidatedPrism")
        .doesNotContain("asIso", "asLens");
    // ANCHOR_END: check_leaf_no_iso
  }

  @Test
  @DisplayName("reverseGet lets a bound null into the domain, where parse locates it")
  void reverseGetLetsABoundNullIn() {
    // ANCHOR: check_reverse_get
    PersonDto bound = new PersonDto(null, 36); // the request body left out "name"

    assertThat(PersonMappingImpl.INSTANCE.asIso().reverseGet(bound))
        .isEqualTo(new Person(null, 36)); // no error, and no exception
    assertThatValidated(PersonMappingImpl.INSTANCE.parse(bound))
        .hasFieldErrors("name: must not be null"); // parse locates it
    // ANCHOR_END: check_reverse_get
  }
}
