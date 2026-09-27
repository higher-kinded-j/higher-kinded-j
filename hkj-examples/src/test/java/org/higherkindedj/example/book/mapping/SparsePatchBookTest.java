// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The law checks behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/beans_patch.html">Sparse PATCH</a> page.
 * The page {@code {{#include}}}s the anchored regions below, so the snippet it displays is this
 * test, and it is green.
 *
 * <p>{@code hkj-test} is test-scope, which is why the laws live in a test rather than beside the
 * spec.
 */
@DisplayName("the Sparse PATCH page's mappings obey the mapping laws")
class SparsePatchBookTest {

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
  void storePatchMappingObeysTheSparseLawsOverContainerElements() {
    // ANCHOR: update_container_laws
    MappingLaws.assertMappingLaws(
        StorePatchMappingImpl.INSTANCE::updateFrom,
        new Store("Leeds", List.of(new PhoneNumber("+44"))), // the current value
        new StorePatchBean(), // all-absent, as bound from {} -> identity
        storePatch(null, List.of("+1", "+353")), // present valid -> wholesale replacement
        storePatch(null, List.of("+1", "nope"))); // bad element   -> located phones.1
    // ANCHOR_END: update_container_laws

    // The located element error, exactly:
    assertThat(
            StorePatchMappingImpl.INSTANCE
                .updateFrom(storePatch(null, List.of("+1", "nope")))
                .apply(new Store("Leeds", List.of(new PhoneNumber("+44"))))
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

  private static StorePatchBean storePatch(String name, List<String> phones) {
    StorePatchBean bean = new StorePatchBean();
    bean.setName(name);
    bean.setPhones(phones);
    return bean;
  }

  @Test
  @DisplayName("a PATCH bean's default is written over the domain, and the identity law catches it")
  void aPatchBeanDefaultIsWrittenOverTheDomain() {
    // ANCHOR: defaults_trap_proof
    ProductPage tagged = new ProductPage("Kettle", List.of("kitchen", "sale"));
    ProductPagePatchBean rename = new ProductPagePatchBean();
    rename.setTitle("Stovetop kettle");

    assertThatValidated(ProductPagePatchMappingImpl.INSTANCE.updateFrom(rename).apply(tagged))
        .hasValue(new ProductPage("Stovetop kettle", List.of())); // the tags are gone

    // On a product page with no tags the identity law passes: [] written over [] changes nothing...
    MappingLaws.assertSparseIdentity(
        ProductPagePatchMappingImpl.INSTANCE::updateFrom,
        new ProductPage("Kettle", List.of()),
        new ProductPagePatchBean());

    // ...and on one with tags it fails, with the message your build would report:
    assertThatThrownBy(
            () ->
                MappingLaws.assertSparseIdentity(
                    ProductPagePatchMappingImpl.INSTANCE::updateFrom,
                    tagged,
                    new ProductPagePatchBean()))
        .hasMessageContaining(
            "Sparse identity law: updateFrom(allAbsentWire).apply(ProductPage[title=Kettle, tags=[kitchen,"
                + " sale]]) == Valid(it); got Valid(ProductPage[title=Kettle, tags=[]])");
    // ANCHOR_END: defaults_trap_proof
  }

  @Test
  @DisplayName("each JSON state does what the page's table says, bound by Jackson")
  void eachJsonStateDoesWhatTheTableSays() {
    // ANCHOR: json_states
    Shopper ada = new Shopper("Ada", Optional.of("Countess"));

    assertThatValidated(applyJson(ada, "{}")).hasValue(ada); // omitted: kept
    assertThatValidated(applyJson(ada, "{\"nickname\": null}"))
        .hasValue(new Shopper("Ada", Optional.empty())); // an Optional's null: cleared
    assertThatValidated(applyJson(ada, "{\"nickname\": \"Lady Lovelace\"}"))
        .hasValue(new Shopper("Ada", Optional.of("Lady Lovelace"))); // a value: set
    assertThatValidated(applyJson(ada, "{\"name\": null}")).hasValue(ada); // a plain null: kept
    // ANCHOR_END: json_states
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** Binds a PATCH body as a Spring controller would, then applies it to the current shopper. */
  private static Validated<NonEmptyList<FieldError>, Shopper> applyJson(
      Shopper current, String body) {
    return ShopperPatchMappingImpl.INSTANCE
        .updateFrom(JSON.readValue(body, ShopperPatchBean.class))
        .apply(current);
  }
}
