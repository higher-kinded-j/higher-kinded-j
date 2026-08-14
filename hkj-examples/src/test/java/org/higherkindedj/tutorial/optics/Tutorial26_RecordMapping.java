// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 26: Record Mapping — the boundary, generated.
 *
 * <p>Pain to Promise. Hand-written DTO mappers drift, stop at the first bad field, and throw
 * unlocated exceptions. {@code @GenerateMapping} derives the whole boundary from a spec interface:
 * a <b>total</b> {@code build} out, and an accumulating {@code parse} back that reports every bad
 * field at once, each located by a dotted, domain-named path.
 *
 * <p>The specs this tutorial exercises live in {@code org.higherkindedj.example.tutorials.mapping}
 * (main sources, where the annotation processor runs): a {@code Booking ↔ BookingDto} pair with
 * stock codecs, a nested {@code Guest ↔ GuestDto} pair with a rename and an email leaf, and a
 * sparse {@code GuestPatchMapping} sharing the same vocabulary. Open those files first; they are
 * short.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@code build} is total; {@code parse} returns {@code Validated<NonEmptyList<FieldError>,
 *       Domain>};
 *   <li>nesting composes error paths ({@code guest.email}); codecs locate with copy-worthy
 *       messages;
 *   <li>{@code MappingLaws.assertMappingLaws} proves the mapping lawful in one call;
 *   <li>{@code UpdateSpec} gives a PATCH bean null-as-absent without weakening validation.
 * </ul>
 *
 * <p>Prerequisites: Tutorial 25 (ValidatedPrism); Tutorial 12 (Accumulating Assembly). Read the
 * Mapping at the Boundary chapter's Basics page.
 *
 * <p>Estimated time: ~12 minutes.
 *
 * <p>Replace each {@code answerRequired()} placeholder with the correct code to make the tests
 * pass.
 */
@DisplayName("Tutorial 26: Record Mapping - the boundary, generated")
public class Tutorial26_RecordMapping {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  private static final Booking BOOKING =
      new Booking(
          UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
          new Guest("Ada Lovelace", new GuestEmail("ada@corp.example")),
          LocalDate.of(2026, 7, 28),
          3);

  @Nested
  @DisplayName("Part 1: two directions, two shapes")
  class TwoDirections {

    /**
     * Exercise 1: the total direction.
     *
     * <p>Task: build the wire DTO from {@code BOOKING} through the generated Impl.
     *
     * <pre>
     *   // Hint 1: the generated class is {@code BookingMappingImpl}, used via {@code INSTANCE}.
     *   // Hint 2: {@code build} cannot fail - no Validated, no exception.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: build is total")
    void exercise1_buildIsTotal() {
      // TODO: build the DTO from BOOKING.
      BookingDto dto = answerRequired();

      assertThat(dto)
          .isEqualTo(
              new BookingDto(
                  "123e4567-e89b-12d3-a456-426614174000",
                  new GuestDto("Ada Lovelace", "ada@corp.example"),
                  "2026-07-28",
                  3));
    }

    /**
     * Exercise 2: the fallible direction, on a good wire.
     *
     * <p>Task: parse the DTO you just built back into a {@code Booking}.
     *
     * <pre>
     *   // Hint 1: {@code parse} returns {@code Validated<NonEmptyList<FieldError>, Booking>}.
     *   // Hint 2: assert with {@code assertThatValidated(...).isValid().hasValue(...)}.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: parse round-trips a good wire")
    void exercise2_parseAGoodWire() {
      BookingDto dto = BookingMappingImpl.INSTANCE.build(BOOKING);

      // TODO: parse the DTO back.
      Validated<NonEmptyList<FieldError>, Booking> parsed = answerRequired();

      assertThatValidated(parsed).isValid().hasValue(BOOKING);
    }
  }

  @Nested
  @DisplayName("Part 2: every bad field, located")
  class EveryBadFieldLocated {

    /**
     * Exercise 3: the payoff.
     *
     * <p>Task: parse this three-defect wire and collect the rendered errors: a bad id, a bad email
     * inside the <em>nested</em> guest, and a bad date. All three must surface at once, in
     * declaration order, each located.
     *
     * <pre>
     *   // Hint 1: parse it, then render with
     *   //         {@code parsed.getError().map(FieldError::toString).toJavaList()}.
     *   // Hint 2: the nested guest's failure locates as {@code guest.email}.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: three defects, one result, three located errors")
    void exercise3_everyBadFieldAtOnce() {
      BookingDto hostile =
          new BookingDto("NOPE", new GuestDto("Ada Lovelace", "not-an-email"), "28/07/2026", 3);

      // TODO: parse the hostile wire and render its errors as strings.
      List<String> errors = answerRequired();

      assertThat(errors)
          .containsExactly(
              "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
              "guest.email: not an email address",
              "arrival: not an ISO-8601 date (expected e.g. 2026-07-28)");
    }

    /**
     * Exercise 4: prove it lawful.
     *
     * <p>Task: law-check the booking mapping in one call: a wire that parses, and one that must
     * not.
     *
     * <pre>
     *   // Hint 1: the fallible overload takes {@code asValidatedPrism()} plus the two wires.
     *   // Hint 2: reuse {@code BookingMappingImpl.INSTANCE.build(BOOKING)} as the parsing wire.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: one MappingLaws call per mapping")
    void exercise4_lawChecked() {
      BookingDto good = BookingMappingImpl.INSTANCE.build(BOOKING);
      BookingDto bad =
          new BookingDto("NOPE", new GuestDto("Ada Lovelace", "ada@corp.example"), "2026-07-28", 3);

      // TODO: assert the mapping laws for the booking mapping.
      Runnable check = answerRequired();
      check.run();
    }
  }

  @Nested
  @DisplayName("Part 3: the sparse sibling")
  class TheSparseSibling {

    /**
     * Exercise 5: PATCH with null-as-absent.
     *
     * <p>Task: apply a patch that changes only the email. The omitted name must survive, and the
     * present email still parses through the same leaf the full mapping uses.
     *
     * <pre>
     *   // Hint 1: {@code GuestPatchMappingImpl.INSTANCE.updateFrom(form)} returns an
     *   //         {@code Edits.Accumulated<Guest>}; {@code .apply(current)} runs it.
     *   // Hint 2: leave {@code form.setName(...)} uncalled - null means keep.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: absent keeps, present validates")
    void exercise5_sparsePatch() {
      Guest current = new Guest("Ada Lovelace", new GuestEmail("ada@corp.example"));
      GuestPatchForm form = new GuestPatchForm();
      form.setEmail("countess@lovelace.example");

      // TODO: fold the present fields into the current guest.
      Validated<NonEmptyList<FieldError>, Guest> patched = answerRequired();

      assertThatValidated(patched)
          .isValid()
          .hasValue(new Guest("Ada Lovelace", new GuestEmail("countess@lovelace.example")));

      // Sparseness never weakens validation: a present bad email is still a located error.
      GuestPatchForm badForm = new GuestPatchForm();
      badForm.setEmail("nope");
      assertThatValidated(GuestPatchMappingImpl.INSTANCE.updateFrom(badForm).apply(current))
          .isInvalid();
    }
  }

  /*
   * Where to next?
   *   • Mapping at the Boundary (book chapter) — codecs and canonical forms, the emission
   *     tiers, beans and sparse PATCH, generics, merge and error envelopes.
   *   • Capstone: One 422, Every Bad Field — this tutorial's boundary at full scale, with the
   *     Spring 422 rendering.
   *   • Tutorial 24 — the hand-written Edits fold this tutorial's PATCH sibling generates.
   *   • Tutorial 25 — the ValidatedPrism leaf underneath every fallible correspondence.
   */
}
