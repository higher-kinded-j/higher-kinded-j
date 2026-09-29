// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.book.mapping.proto.CustomerMessage;
import org.higherkindedj.example.book.mapping.proto.DispatchRequest;
import org.higherkindedj.example.book.mapping.proto.Priority;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import tools.jackson.databind.json.JsonMapper;

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

  @Test
  @DisplayName("an openapi-generator model maps through its plain pair, and sends empty as null")
  void jsonNullableCompanionIsLeftOut() {
    JsonMapper json = JsonMapper.builder().addModule(new JsonNullableJackson3Module()).build();
    Listing lamp = new Listing("Lamp", Optional.empty());

    // ANCHOR: json_nullable_null
    // build writes the empty subtitle through setSubtitle(null), which the model sends as null,
    ListingModel built = ListingModelMappingImpl.INSTANCE.build(lamp);
    assertThat(json.writeValueAsString(built)).isEqualTo("{\"title\":\"Lamp\",\"subtitle\":null}");
    // where a fresh model leaves the property out.
    ListingModel fresh = new ListingModel();
    fresh.setTitle("Lamp");
    assertThat(json.writeValueAsString(fresh)).isEqualTo("{\"title\":\"Lamp\"}");

    // getSubtitle() answers null for an omitted property and for an explicit null alike.
    ListingModel omitted = json.readValue("{\"title\":\"Lamp\"}", ListingModel.class);
    ListingModel sentNull =
        json.readValue("{\"title\":\"Lamp\",\"subtitle\":null}", ListingModel.class);
    assertThatValidated(ListingModelMappingImpl.INSTANCE.parse(omitted)).hasValue(lamp);
    assertThatValidated(ListingModelMappingImpl.INSTANCE.parse(sentNull)).hasValue(lamp);
    // ANCHOR_END: json_nullable_null

    // So a parsing sample for the laws sets the subtitle: an omitted one comes back set.
    ListingModel untitled = json.readValue("{\"subtitle\":\"Brass\"}", ListingModel.class);
    MappingLaws.assertMappingLaws(
        ListingModelMappingImpl.INSTANCE.asValidatedPrism(), sentNull, untitled);
    assertThatThrownBy(
            () ->
                MappingLaws.assertMappingLaws(
                    ListingModelMappingImpl.INSTANCE.asValidatedPrism(), omitted, untitled))
        .isInstanceOf(AssertionError.class);
  }

  private static TransferBean transfer(String department) {
    TransferBean bean = new TransferBean();
    bean.setDepartment(department);
    return bean;
  }

  @Test
  @DisplayName(
      "a protobuf message maps by its fields: an unset message field is missing, and an empty"
          + " Optional leaves its field unset")
  void protobufMessageObeysTheLaws() {
    DispatchRequest request =
        DispatchRequest.newBuilder()
            .setCustomer(CustomerMessage.newBuilder().setName("Ada").setEmail("ada@corp.example"))
            .addSkus("SKU-1")
            .setNote("leave at the door")
            .setPriority(Priority.PRIORITY_EXPRESS)
            .setPickupPoint("PP-9")
            .build();
    MappingLaws.assertMappingLaws(
        DispatchMappingImpl.INSTANCE.asValidatedPrism(),
        request, // parses, and builds back equal
        request.toBuilder().clearCustomer().build()); // an unset message field: located failure
    MappingLaws.assertMappingLaws(
        DispatchMappingImpl.INSTANCE.asValidatedPrism(),
        new Dispatch(
            new Customer("Grace", new EmailAddress("grace@corp.example")),
            List.of(),
            Optional.empty(),
            DispatchPriority.STANDARD,
            Optional.of("LK-4"),
            Optional.empty()));

    assertThatValidated(
            DispatchMappingImpl.INSTANCE.parse(request.toBuilder().clearCustomer().build()))
        .isInvalid()
        .hasFieldErrors("customer: must not be null");
  }

  @Test
  @DisplayName(
      "an open enum reads an unknown number as UNRECOGNIZED: a leaf refuses it, and build throws"
          + " on it where no leaf does")
  void unrecognisedEnumNumber() {
    // ANCHOR: protobuf_enum_trap_proof
    DispatchRequest fromNewerClient =
        DispatchRequest.newBuilder()
            .setCustomer(CustomerMessage.newBuilder().setName("Ada").setEmail("ada@corp.example"))
            .setPriorityValue(3) // a priority added to the .proto after this build
            .build();

    // Kept as the generated enum, it parses as UNRECOGNIZED, which no builder can write back.
    DispatchRecord kept = DispatchRecordMappingImpl.INSTANCE.parse(fromNewerClient).get();
    assertThat(kept.priority()).isEqualTo(Priority.UNRECOGNIZED);
    assertThatThrownBy(() -> DispatchRecordMappingImpl.INSTANCE.build(kept))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Can't get the number of an unknown enum value.");

    // Converted through a leaf, it is refused where it is read.
    assertThatValidated(DispatchMappingImpl.INSTANCE.parse(fromNewerClient))
        .isInvalid()
        .hasFieldErrors("priority: not a priority: UNRECOGNIZED");
    // ANCHOR_END: protobuf_enum_trap_proof
  }

  @Test
  @DisplayName("a domain value holding two members of one oneof builds only the last one written")
  void twoOneofMembers() {
    // ANCHOR: protobuf_oneof_trap_proof
    Dispatch both =
        new Dispatch(
            new Customer("Ada", new EmailAddress("ada@corp.example")),
            List.of("SKU-1"),
            Optional.empty(),
            DispatchPriority.STANDARD,
            Optional.of("LK-4"), // a locker
            Optional.of("PP-9")); // and a pickup point: the record allows both

    DispatchRequest built = DispatchMappingImpl.INSTANCE.build(both);
    assertThat(built.getDestinationCase()).isEqualTo(DispatchRequest.DestinationCase.PICKUP_POINT);
    assertThat(DispatchMappingImpl.INSTANCE.parse(built).get().locker()).isEmpty();
    // ANCHOR_END: protobuf_oneof_trap_proof
  }
}
