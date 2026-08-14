// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping.capstone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves the capstone page's two claims: the five-defect wire reports every located error at once,
 * and the boundary's mappings obey the mapping laws. The page {@code {{#include}}}s the anchored
 * regions, so what it shows is a green test.
 */
@DisplayName("the capstone boundary locates every failure and obeys the mapping laws")
class BoundaryCapstoneBookLawsTest {

  private static final Order ORDER =
      new Order(
          UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
          new Customer("Ada Lovelace", new EmailAddress("ada@corp.example")),
          List.of(new LineItem("SKU-1", 2, new BigDecimal("9.99"))),
          Instant.parse("2026-07-28T12:34:56Z"),
          Currency.getInstance("GBP"),
          OrderStatus.PAID);

  @Test
  void theFiveDefectWireReportsEveryLocatedErrorAtOnce() {
    // ANCHOR: capstone_errors
    OrderDto hostile =
        new OrderDto(
            "NOPE",
            new CustomerDto("Ada Lovelace", "not-an-email"),
            List.of(new LineItemDto("SKU-1", 2, "9.99"), new LineItemDto("SKU-2", 1, "1E+3")),
            "28/07/2026",
            "GBP",
            "DISPATCHED",
            null);

    Validated<NonEmptyList<FieldError>, Order> parsed = OrderMappingImpl.INSTANCE.parse(hostile);

    assertThatValidated(parsed).isInvalid();
    assertThat(rendered(parsed))
        .containsExactly(
            "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
            "customer.email: not an email address",
            "lines.1.price: not a number in plain notation (expected e.g. 123.45)",
            "placedAt: not an ISO-8601 instant (expected e.g. 2026-07-28T12:34:56Z)",
            "status: unknown OrderStatus (expected one of NEW, PAID, SHIPPED)");
    // ANCHOR_END: capstone_errors
  }

  @Test
  void theOrderMappingObeysTheLaws() {
    // ANCHOR: capstone_laws
    // Fallible tier: a wire that parses (its derived displayTotal matching what build
    // would produce, keeping the no-parse check honest), and a wire that must not.
    MappingLaws.assertMappingLaws(
        OrderMappingImpl.INSTANCE.asValidatedPrism(),
        new OrderDto(
            "123e4567-e89b-12d3-a456-426614174000",
            new CustomerDto("Ada Lovelace", "ada@corp.example"),
            List.of(new LineItemDto("SKU-1", 2, "9.99")),
            "2026-07-28T12:34:56Z",
            "GBP",
            "PAID",
            "GBP 19.98"),
        new OrderDto(
            "NOPE",
            new CustomerDto("Ada Lovelace", "ada@corp.example"),
            List.of(),
            "2026-07-28T12:34:56Z",
            "GBP",
            "PAID",
            null));
    // ANCHOR_END: capstone_laws
  }

  @Test
  void thePatchMappingObeysTheSparseLaws() {
    // ANCHOR: capstone_patch_laws
    MappingLaws.assertMappingLaws(
        CustomerPatchMappingImpl.INSTANCE::updateFrom,
        new Customer("Ada Lovelace", new EmailAddress("ada@corp.example")), // the current value
        patch(null, null), // all-absent    -> identity
        patch(null, "countess@lovelace.example"), // present valid -> changes the domain
        patch(null, "not-an-email")); // present invalid -> located failure
    // ANCHOR_END: capstone_patch_laws
  }

  @Test
  void theBuildDirectionComputesTheDerivedTotal() {
    assertThat(OrderMappingImpl.INSTANCE.build(ORDER).displayTotal()).isEqualTo("GBP 19.98");
  }

  @Test
  void theReceiptMergesOrderAndCustomerByComponentName() {
    assertThat(ReceiptAssemblyImpl.INSTANCE.assemble(ORDER, ORDER.customer()))
        .isEqualTo(
            new Receipt(
                ORDER.id(),
                "Ada Lovelace",
                new EmailAddress("ada@corp.example"),
                ORDER.placedAt()));
  }

  private static CustomerPatchBean patch(String name, String email) {
    CustomerPatchBean bean = new CustomerPatchBean();
    bean.setName(name);
    bean.setEmail(email);
    return bean;
  }

  private static List<String> rendered(Validated<NonEmptyList<FieldError>, ?> result) {
    return result.fold(nel -> nel.map(FieldError::toString).toJavaList(), _ -> List.of());
  }
}
