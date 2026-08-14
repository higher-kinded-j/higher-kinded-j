// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.tutorials.mapping.Booking;
import org.higherkindedj.example.tutorials.mapping.BookingDto;
import org.higherkindedj.example.tutorials.mapping.BookingMappingImpl;
import org.higherkindedj.example.tutorials.mapping.Guest;
import org.higherkindedj.example.tutorials.mapping.GuestDto;
import org.higherkindedj.example.tutorials.mapping.GuestEmail;
import org.higherkindedj.example.tutorials.mapping.GuestPatchForm;
import org.higherkindedj.example.tutorials.mapping.GuestPatchMappingImpl;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 26: Record Mapping.
 *
 * <p>The pattern throughout: the generated Impl is the boundary. {@code build} is total, {@code
 * parse} accumulates located {@code FieldError}s, {@code asValidatedPrism()} is the mapping as a
 * leaf, and the sparse sibling's {@code updateFrom} folds only the present fields.
 */
@DisplayName("Tutorial 26: Record Mapping (Solutions)")
public class Tutorial26_RecordMapping_Solution {

  private static final Booking BOOKING =
      new Booking(
          UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
          new Guest("Ada Lovelace", new GuestEmail("ada@corp.example")),
          LocalDate.of(2026, 7, 28),
          3);

  @Nested
  @DisplayName("Part 1: two directions, two shapes")
  class TwoDirections {

    /** Why this is idiomatic: the render direction is a plain total function, so no wrapping. */
    @Test
    @DisplayName("Exercise 1: build is total")
    void exercise1_buildIsTotal() {
      BookingDto dto = BookingMappingImpl.INSTANCE.build(BOOKING);

      assertThat(dto)
          .isEqualTo(
              new BookingDto(
                  "123e4567-e89b-12d3-a456-426614174000",
                  new GuestDto("Ada Lovelace", "ada@corp.example"),
                  "2026-07-28",
                  3));
    }

    /** Why this is idiomatic: parse-don't-validate; the domain value arrives already trusted. */
    @Test
    @DisplayName("Exercise 2: parse round-trips a good wire")
    void exercise2_parseAGoodWire() {
      BookingDto dto = BookingMappingImpl.INSTANCE.build(BOOKING);

      Validated<NonEmptyList<FieldError>, Booking> parsed = BookingMappingImpl.INSTANCE.parse(dto);

      assertThatValidated(parsed).isValid().hasValue(BOOKING);
    }
  }

  @Nested
  @DisplayName("Part 2: every bad field, located")
  class EveryBadFieldLocated {

    /**
     * Why this is idiomatic: accumulation is the point; one response carries every defect, each
     * located by a domain-named dotted path, in declaration order.
     */
    @Test
    @DisplayName("Exercise 3: three defects, one result, three located errors")
    void exercise3_everyBadFieldAtOnce() {
      BookingDto hostile =
          new BookingDto("NOPE", new GuestDto("Ada Lovelace", "not-an-email"), "28/07/2026", 3);

      Validated<NonEmptyList<FieldError>, Booking> parsed =
          BookingMappingImpl.INSTANCE.parse(hostile);
      List<String> errors = parsed.getError().map(FieldError::toString).toJavaList();

      assertThat(errors)
          .containsExactly(
              "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
              "guest.email: not an email address",
              "arrival: not an ISO-8601 date (expected e.g. 2026-07-28)");
    }

    /** Why this is idiomatic: "lawful" is a passing test; one call covers the whole tier. */
    @Test
    @DisplayName("Exercise 4: one MappingLaws call per mapping")
    void exercise4_lawChecked() {
      BookingDto good = BookingMappingImpl.INSTANCE.build(BOOKING);
      BookingDto bad =
          new BookingDto("NOPE", new GuestDto("Ada Lovelace", "ada@corp.example"), "2026-07-28", 3);

      ValidatedPrism<BookingDto, Booking> mapping = BookingMappingImpl.INSTANCE.asValidatedPrism();

      MappingLaws.assertMappingLaws(mapping, good, bad);
    }
  }

  @Nested
  @DisplayName("Part 3: the sparse sibling")
  class TheSparseSibling {

    /**
     * Why this is idiomatic: null-as-absent is the PATCH contract, and the present email still
     * parses through the same leaf the full mapping uses; sparseness never weakens validation.
     */
    @Test
    @DisplayName("Exercise 5: absent keeps, present validates")
    void exercise5_sparsePatch() {
      Guest current = new Guest("Ada Lovelace", new GuestEmail("ada@corp.example"));
      GuestPatchForm form = new GuestPatchForm();
      form.setEmail("countess@lovelace.example");

      Validated<NonEmptyList<FieldError>, Guest> patched =
          GuestPatchMappingImpl.INSTANCE.updateFrom(form).apply(current);

      assertThatValidated(patched)
          .isValid()
          .hasValue(new Guest("Ada Lovelace", new GuestEmail("countess@lovelace.example")));

      GuestPatchForm badForm = new GuestPatchForm();
      badForm.setEmail("nope");
      assertThatValidated(GuestPatchMappingImpl.INSTANCE.updateFrom(badForm).apply(current))
          .isInvalid();
    }
  }
}
