// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
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
  void reservationMappingLocatesAStaysInvariantAndObeysTheLaws() {
    ReservationDto accepted =
        new ReservationDto("Ada", List.of(new StayDto("2026-03-01", "2026-03-04")));
    ReservationDto refused =
        new ReservationDto("Ada", List.of(new StayDto("2026-03-09", "2026-03-07")));
    MappingLaws.assertMappingLaws(
        ReservationMappingImpl.INSTANCE.asValidatedPrism(), accepted, refused);

    // The located errors the page shows, exactly:
    assertThatValidated(
            ReservationMappingImpl.INSTANCE.parse(
                new ReservationDto(
                    null,
                    List.of(
                        new StayDto("2026-03-01", "2026-03-04"),
                        new StayDto("2026-03-09", "2026-03-07")))))
        .isInvalid()
        .hasFieldErrors("guest: must not be null", "stays.1: checkOut must be after checkIn");
  }
}
