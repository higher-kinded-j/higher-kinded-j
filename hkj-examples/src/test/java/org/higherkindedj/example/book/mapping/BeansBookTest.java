// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.lang.reflect.Method;
import java.util.Optional;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The law checks behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/beans.html">Bean-Shaped Wires</a> page.
 * The page {@code {{#include}}}s the anchored regions below, so the snippet it displays is this
 * test, and it is green.
 *
 * <p>{@code hkj-test} is test-scope, which is why the laws live in a test rather than beside the
 * spec.
 */
@DisplayName("the Bean-Shaped Wires page's mappings obey the mapping laws")
class BeansBookTest {

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
  @DisplayName(
      "a read-only property is read by parse and left unset by build, and each half is lawful")
  void readOnlyPropertyHalvesObeyTheirLaws() {
    // ANCHOR: read_only_laws
    MappingLaws.assertMappingLaws(
        MerchantModelMappingImpl.INSTANCE.asValidatedParse(),
        merchantModel("m-1", "Brightside"), // parses
        merchantModel(null, "Brightside")); // located failure: no id to read

    MappingLaws.assertMappingLaws(
        MerchantModelMappingImpl.INSTANCE.asValidatedBuild(),
        new Merchant("m-1", "Brightside")); // renders without failing
    // ANCHOR_END: read_only_laws

    // parse cannot read back a wire build wrote: the id it never wrote is missing.
    MerchantModel sent = MerchantModelMappingImpl.INSTANCE.build(new Merchant("m-1", "Brightside"));
    assertThatValidated(MerchantModelMappingImpl.INSTANCE.parse(sent))
        .isInvalid()
        .hasFieldErrors("id: must not be null");
    assertThat(MerchantModelMappingImpl.class.getMethods())
        .extracting(Method::getName)
        .contains("asValidatedParse", "asValidatedBuild")
        .doesNotContain("asValidatedPrism", "asIso");
  }

  private static MerchantModel merchantModel(String id, String name) {
    MerchantModel model = new MerchantModel(id);
    model.setName(name);
    return model;
  }

  @Test
  @DisplayName("build replaces a field initialiser, but a getter default reads back as present")
  void aGetterDefaultReadsAbsenceBackAsPresent() {
    // ANCHOR: default_trap_proof
    Listing lamp = new Listing("Lamp", Optional.empty());

    // build writes setSubtitle(null) into both beans, replacing the field's "".
    DraftListingBean draft = DraftListingMappingImpl.INSTANCE.build(lamp);
    assertThatValidated(DraftListingMappingImpl.INSTANCE.parse(draft)).hasValue(lamp);

    // parse reads through the getter, which answers "" for that null.
    ListingBean listing = ListingMappingImpl.INSTANCE.build(lamp);
    assertThatValidated(ListingMappingImpl.INSTANCE.parse(listing))
        .hasValue(new Listing("Lamp", Optional.of("")));

    // A law check from a domain sample with an empty Optional fails on it:
    assertThatThrownBy(
            () ->
                MappingLaws.assertMappingLaws(ListingMappingImpl.INSTANCE.asValidatedPrism(), lamp))
        .isInstanceOf(AssertionError.class);
    // ANCHOR_END: default_trap_proof
  }

  private static TransferBean transfer(String department) {
    TransferBean bean = new TransferBean();
    bean.setDepartment(department);
    return bean;
  }
}
