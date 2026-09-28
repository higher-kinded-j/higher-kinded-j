// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The answers behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/structure.html">Nesting, Containers, and
 * Sealed Hierarchies</a> page. The page {@code {{#include}}}s the anchored regions below, so each
 * answer it shows is this test, and it is green.
 */
@DisplayName("the Nesting, Containers, and Sealed Hierarchies page's answers hold")
class StructureBookTest {

  @Test
  @DisplayName("each container locates a failure by whatever identifies an element in it")
  void everyContainerLocatesItsOwnWay() {
    Validated<NonEmptyList<FieldError>, SupportDesk> parsed =
        SupportDeskMappingImpl.INSTANCE.parse(
            new SupportDeskDto(
                Set.of("nope"), // a set has no index
                new String[] {"ada@example.org", "also-nope"}, // an array does
                Map.of("bad-key", "a note"))); // a map has the key as sent

    assertThatValidated(parsed)
        .isInvalid()
        .hasFieldErrors(
            "agents.nope: not an email address",
            "standby.1: not an email address",
            "notes.bad-key: not an email address");
  }

  @Test
  @DisplayName(
      "every bad payment is reported, by its zero-based index, with no segment for the subtype")
  void everyBadPaymentIsReportedByIndex() {
    // ANCHOR: check_payment_path
    assertThatValidated(
            CheckoutMappingImpl.INSTANCE.parse(
                new CheckoutDto(
                    "C-1",
                    List.of(
                        new CardDto(null),
                        new BankDto("GB33BUKB20201555555555"),
                        new BankDto(null)))))
        .hasFieldErrors("payments.0.pan: must not be null", "payments.2.iban: must not be null");
    // ANCHOR_END: check_payment_path
  }

  @Test
  @DisplayName("a same-typed list parses to an unmodifiable copy the request can no longer change")
  void aSameTypedListParsesToAnUnmodifiableCopy() {
    // ANCHOR: check_copy
    List<String> requestTags = new ArrayList<>(List.of("vip")); // what Jackson bound
    OrderNote note =
        OrderNoteMappingImpl.INSTANCE.parse(new OrderNoteDto("Call back", requestTags)).get();
    requestTags.clear(); // a later filter clears the request's list

    assertThat(note.tags()).containsExactly("vip"); // a copy: still there
    assertThatThrownBy(() -> note.tags().add("urgent"))
        .isInstanceOf(UnsupportedOperationException.class); // and unmodifiable
    // ANCHOR_END: check_copy
  }

  @Test
  @DisplayName("Jackson binds the sealed wire through its type information, and not without it")
  void jacksonBindsTheSealedWireThroughItsTypeInformation() {
    JsonMapper json = JsonMapper.builder().build();

    CheckoutDto bound =
        json.readValue(
            """
            {"id": "C-1", "payments": [{"iban": "GB33BUKB20201555555555"}, {"pan": "4111"}]}""",
            CheckoutDto.class);
    assertThat(bound.payments())
        .containsExactly(new BankDto("GB33BUKB20201555555555"), new CardDto("4111"));
    assertThat(json.writeValueAsString(new CardDto("4111"))).isEqualTo("{\"pan\":\"4111\"}");

    assertThatThrownBy(() -> json.readValue("{\"pan\": \"4111\"}", BarePaymentDto.class))
        .isInstanceOf(InvalidDefinitionException.class);
  }

  @Test
  @DisplayName(
      "an empty subtype maps through an empty spec, and Jackson deduces it from an empty object")
  void anEmptySubtypeMapsAndIsDeducedFromAnEmptyObject() {
    JsonMapper json = JsonMapper.builder().build();

    FulfilmentDto collected = json.readValue("{}", FulfilmentDto.class);
    assertThat(collected).isEqualTo(new CollectedDto());
    assertThat(json.readValue("{\"tracking\": \"T-1\"}", FulfilmentDto.class))
        .isEqualTo(new ShippedDto("T-1"));
    assertThat(json.writeValueAsString(new CollectedDto())).isEqualTo("{}");

    assertThatValidated(FulfilmentMappingImpl.INSTANCE.parse(collected))
        .isValid()
        .hasValue(new Collected());
    assertThat(FulfilmentMappingImpl.INSTANCE.build(new Collected())).isEqualTo(new CollectedDto());
  }

  /** A sealed wire with no type information, which Jackson cannot construct. */
  sealed interface BarePaymentDto permits BareCardDto {}

  record BareCardDto(String pan) implements BarePaymentDto {}
}
