// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The law checks behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/absence.html">Absent Fields and Record
 * Invariants</a> page. The page {@code {{#include}}}s the anchored regions below, so the snippet it
 * displays is this test, and it is green.
 *
 * <p>{@code hkj-test} is test-scope, which is why the laws live in a test rather than beside the
 * spec.
 */
@DisplayName("the Absent Fields and Record Invariants page's mappings obey the mapping laws")
class AbsenceBookTest {

  @Test
  void deliveryMappingLocatesAWindowsInvariantAndObeysTheLaws() {
    DeliveryDto accepted =
        new DeliveryDto("Ada", List.of(new DeliveryWindowDto("2026-03-01", "2026-03-04")));
    DeliveryDto refused =
        new DeliveryDto("Ada", List.of(new DeliveryWindowDto("2026-03-09", "2026-03-07")));
    MappingLaws.assertMappingLaws(
        DeliveryMappingImpl.INSTANCE.asValidatedPrism(), accepted, refused);

    // The located errors the page shows, exactly:
    assertThatValidated(
            DeliveryMappingImpl.INSTANCE.parse(
                new DeliveryDto(
                    null,
                    List.of(
                        new DeliveryWindowDto("2026-03-01", "2026-03-04"),
                        new DeliveryWindowDto("2026-03-09", "2026-03-07")))))
        .isInvalid()
        .hasFieldErrors("recipient: must not be null", "windows.1: latest must be after earliest");
  }

  @Test
  @DisplayName("a bridged leaf over the date reads null as absent and still parses a present date")
  void aBridgedLeafOverTheDateReadsNullAsAbsent() {
    // ANCHOR: voucher_proof
    VoucherMappingImpl voucherMapping = VoucherMappingImpl.INSTANCE;

    assertThatValidated(voucherMapping.parse(new VoucherDto("SPRING10", null)))
        .hasValue(new Voucher("SPRING10", Optional.empty())); // left out: absent
    assertThatValidated(voucherMapping.parse(new VoucherDto("SPRING10", "07/03/2026")))
        .hasFieldErrors("expiry: not an ISO-8601 date (expected e.g. 2026-07-28)");
    // ANCHOR_END: voucher_proof
  }

  @Test
  @DisplayName("a constructor's bug reaches the client as its message, at the record's path")
  void aConstructorsBugReachesTheClientAtTheRecordsPath() {
    // ANCHOR: discount_proof
    assertThatValidated(
            BasketMappingImpl.INSTANCE.parse(new BasketDto("B-7", new BulkDiscountDto(1000, 0))))
        .hasFieldErrors("discount: / by zero");
    // ANCHOR_END: discount_proof
  }

  @Test
  @DisplayName("the per-item limit rounds up, so a remainder cannot slip past it")
  void thePerItemLimitRoundsUp() {
    assertThatValidated(
            BasketMappingImpl.INSTANCE.parse(new BasketDto("B-8", new BulkDiscountDto(1001, 2))))
        .hasFieldErrors("discount: at most 500p off per item");
    assertThatValidated(
            BasketMappingImpl.INSTANCE.parse(new BasketDto("B-9", new BulkDiscountDto(1000, 2))))
        .isValid();
  }

  @Test
  @DisplayName("a constructor's refusal locates where its record does")
  void aRefusalLocatesWhereItsRecordDoes() {
    DeliveryWindowDto reversed = new DeliveryWindowDto("2026-03-09", "2026-03-07");

    assertThatValidated(DeliveryWindowMappingImpl.INSTANCE.parse(reversed)) // unlabelled alone
        .hasFieldErrors("latest must be after earliest");
    assertThatValidated(
            DeliveryMappingImpl.INSTANCE.parse(
                new DeliveryDto(
                    "Ada", List.of(new DeliveryWindowDto("2026-03-01", "2026-03-04"), reversed))))
        .hasFieldErrors("windows.1: latest must be after earliest");
  }
}
