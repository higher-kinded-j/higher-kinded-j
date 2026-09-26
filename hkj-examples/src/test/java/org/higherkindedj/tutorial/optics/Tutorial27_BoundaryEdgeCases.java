// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.higherkindedj.example.tutorials.mapping.Booking;
import org.higherkindedj.example.tutorials.mapping.BookingDto;
import org.higherkindedj.example.tutorials.mapping.BookingMappingImpl;
import org.higherkindedj.example.tutorials.mapping.DefaultedPreferencesPatchForm;
import org.higherkindedj.example.tutorials.mapping.DefaultedPreferencesPatchMappingImpl;
import org.higherkindedj.example.tutorials.mapping.GuestDto;
import org.higherkindedj.example.tutorials.mapping.GuestPreferences;
import org.higherkindedj.example.tutorials.mapping.Party;
import org.higherkindedj.example.tutorials.mapping.PartyDto;
import org.higherkindedj.example.tutorials.mapping.PartyMappingImpl;
import org.higherkindedj.example.tutorials.mapping.PreferencesPatchMappingImpl;
import org.higherkindedj.example.tutorials.mapping.RoomRequest;
import org.higherkindedj.example.tutorials.mapping.RoomRequestDto;
import org.higherkindedj.example.tutorials.mapping.RoomRequestMappingImpl;
import org.higherkindedj.example.tutorials.mapping.StayDto;
import org.higherkindedj.example.tutorials.mapping.StayMappingImpl;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 27: Boundary Edge Cases — where a bad request goes when it is not the obvious kind.
 *
 * <p>Pain to Promise. Tutorial 26 parsed a wire with three bad values. Real requests also leave
 * fields out, send a list with one bad element, or break a rule that spans two fields. A
 * hand-written mapper meets these with a {@code NullPointerException}, or stops at the first bad
 * element:
 *
 * <pre>
 *   new Stay(LocalDate.parse(dto.arrival()), LocalDate.parse(dto.departure()));  // throws
 *   STAY_MAPPING.parse(dto);  // Invalid, with the constructor's message as a located error
 * </pre>
 *
 * <p>The generated mapper returns each of them as a value: a located error, or the record you
 * meant. One edge case fools every mapper, generated ones included: a PATCH bean that fills in a
 * value nobody sent. That one comes back valid and wrong, and Part 3 shows how a law catches it.
 *
 * <p>Java idiom anchor: {@code @OptionalBridge} is {@code Optional.ofNullable(dto.note())},
 * declared once on the spec rather than written at every call.
 *
 * <p>The specs live in {@code org.higherkindedj.example.tutorials.mapping} beside Tutorial 26's.
 * Each exercise names the one it uses; open it when you reach that exercise.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>a {@code null} field on the wire is a located {@code must not be null};
 *   <li>a list element is located by its index, as in {@code guests.1.email};
 *   <li>{@code @OptionalBridge} declares that a {@code null} means absent, per component;
 *   <li>a record's constructor refusal becomes an error at the record's path;
 *   <li>a PATCH bean's default reads as sent, and the sparse identity law catches it, given a
 *       sample that differs from the default.
 * </ul>
 *
 * <p>Prerequisites: Tutorial 26 (Record Mapping). The Mapping at the Boundary chapter explains each
 * rule in full: nulls on the Record Mapping Basics page, list paths on Nesting, Containers, Sealed
 * Hierarchies, bridges and invariants on Absent Fields and Record Invariants, and PATCH beans on
 * Sparse PATCH.
 *
 * <p>Estimated time: ~15 minutes.
 *
 * <p>Each exercise's hints climb from a nudge to the answer; stop reading as soon as you have what
 * you need. Replace each {@code answerRequired()} placeholder with the correct code to make the
 * tests pass.
 */
@DisplayName("Tutorial 27: Boundary Edge Cases")
public class Tutorial27_BoundaryEdgeCases {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  private static final String BOOKING_ID = "123e4567-e89b-12d3-a456-426614174000";

  // The generated Impls, bound once for the whole class and reused by every exercise.
  private static final BookingMappingImpl BOOKING_MAPPING = BookingMappingImpl.INSTANCE;

  private static final PartyMappingImpl PARTY_MAPPING = PartyMappingImpl.INSTANCE;

  private static final RoomRequestMappingImpl ROOM_REQUEST_MAPPING =
      RoomRequestMappingImpl.INSTANCE;

  private static final StayMappingImpl STAY_MAPPING = StayMappingImpl.INSTANCE;

  private static final PreferencesPatchMappingImpl PREFERENCES_PATCH =
      PreferencesPatchMappingImpl.INSTANCE;

  private static final DefaultedPreferencesPatchMappingImpl DEFAULTED_PREFERENCES_PATCH =
      DefaultedPreferencesPatchMappingImpl.INSTANCE;

  // A guest who speaks British English and has opted in to marketing.
  private static final GuestPreferences STORED = new GuestPreferences("en-GB", true);

  @Nested
  @DisplayName("Part 1: nulls, lists and absence")
  class NullsListsAndAbsence {

    /**
     * Exercise 1: a null has an address.
     *
     * <p>A client that sends {@code null} has made a mistake like any other, so it should hear
     * about it in the same place: at the field, beside every other error. The spec is Tutorial 26's
     * {@code BookingMapping}.
     *
     * <p>Task: this wire has no id, and its guest has no email. Write the two errors {@code parse}
     * reports, each as {@code "path: message"}, in declaration order.
     *
     * <pre>
     *   // Nudge:    a null is reported like any other bad value, at the field that held it.
     *   // Strategy: the message is "must not be null"; a nested field's path joins with a dot.
     *   // Spoiler:  List.of("id: must not be null", "guest.email: must not be null")
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: a null has an address")
    void exercise1_nullHasAnAddress() {
      BookingDto wire = new BookingDto(null, new GuestDto("Ada Lovelace", null), "2026-07-28", 3);

      Validated<NonEmptyList<FieldError>, Booking> parsed = BOOKING_MAPPING.parse(wire);

      // TODO: write the two located errors, in declaration order.
      List<String> expected = answerRequired();

      assertThatValidated(parsed).isInvalid().hasFieldErrors(expected.toArray(String[]::new));
    }

    /**
     * Exercise 2: a list element is located by its index.
     *
     * <p>One bad element should not hide the others, and the client needs to know which element to
     * fix. Open {@code PartyMapping}: it names no mapping for its guests, yet each one is parsed by
     * {@code GuestMapping}.
     *
     * <p>Task: the first guest has no name, and the second has a bad email. Write both errors.
     *
     * <pre>
     *   // Nudge:    the path names the domain's components, not the wire's.
     *   // Strategy: the index, counted from 0, is a segment joined with a dot, like any other:
     *   //           guests.0.something, not guests[0].something.
     *   // Spoiler:  List.of("guests.0.name: must not be null",
     *   //                   "guests.1.email: not an email address")
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: a list element is located by its index")
    void exercise2_listIndexInThePath() {
      PartyDto wire =
          new PartyDto(
              BOOKING_ID,
              List.of(
                  new GuestDto(null, "ada@corp.example"),
                  new GuestDto("Grace Hopper", "not-an-email")));

      Validated<NonEmptyList<FieldError>, Party> parsed = PARTY_MAPPING.parse(wire);

      // TODO: write both located errors, in list order.
      List<String> expected = answerRequired();

      assertThatValidated(parsed).isInvalid().hasFieldErrors(expected.toArray(String[]::new));
    }

    /**
     * Exercise 3: {@code @OptionalBridge} reads a null as absent.
     *
     * <p>Some fields are optional: a guest may make a room request without a note. On the wire a
     * left-out note is {@code null}, and the domain wants an {@code Optional}. Open {@code
     * RoomRequestMapping} and {@code RoomRequestDto} to see how the spec says so.
     *
     * <p>Task: write the {@code RoomRequest} that parsing a request without a note gives.
     *
     * <pre>
     *   // Nudge:    the spec declared that null means "left out", so this is not an error.
     *   // Strategy: the domain holds a left-out value as an empty Optional
     *   //           (import java.util.Optional).
     *   // Spoiler:  new RoomRequest("double", Optional.empty())
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: @OptionalBridge reads a null as absent")
    void exercise3_bridgeReadsNullAsAbsent() {
      RoomRequestDto wire = new RoomRequestDto("double", null);

      // TODO: write the parsed room request.
      RoomRequest expected = answerRequired();

      assertThatValidated(ROOM_REQUEST_MAPPING.parse(wire)).isValid().hasValue(expected);
      // The other direction writes an empty Optional back as null.
      assertThat(ROOM_REQUEST_MAPPING.build(expected)).isEqualTo(wire);
      // The bridge is per component: the room type is not bridged, so its null is still an error.
      assertThatValidated(ROOM_REQUEST_MAPPING.parse(new RoomRequestDto(null, null)))
          .isInvalid()
          .hasFieldErrors("roomType: must not be null");
    }
  }

  @Nested
  @DisplayName("Part 2: a record's invariants")
  class RecordInvariants {

    /**
     * Exercise 4: a constructor's refusal becomes an error.
     *
     * <p>Open {@code Stay}: its constructor throws when the departure is not after the arrival.
     * {@code StayMapping} keeps that guard, but reports a refusal instead of throwing it. Both
     * dates in this wire parse, so the constructor runs and refuses them.
     *
     * <p>Task: write the error {@code parse} reports.
     *
     * <pre>
     *   // Nudge:    the refusal is located where the record is, and this record is the whole wire.
     *   // Strategy: an error at the top level has no path, so it renders as its message alone.
     *   // Spoiler:  "departure must be after arrival"
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: a constructor's refusal becomes an error")
    void exercise4_invariantBecomesAnError() {
      StayDto wire = new StayDto("2026-07-28", "2026-07-27");

      // TODO: write the one error, as hasFieldErrors renders it.
      String expected = answerRequired();

      assertThatValidated(STAY_MAPPING.parse(wire)).isInvalid().hasFieldErrors(expected);
      // The constructor runs only once both dates have parsed, so a bad date is reported alone,
      // and a client meets the invariant only on its second attempt.
      assertThatValidated(STAY_MAPPING.parse(new StayDto("2026-07-28", "tomorrow")))
          .isInvalid()
          .hasFieldErrors("departure: not an ISO-8601 date (expected e.g. 2026-07-28)");
    }
  }

  @Nested
  @DisplayName("Part 3: a PATCH bean's defaults")
  class PatchBeanDefaults {

    /**
     * Exercise 5: an empty PATCH changes nothing.
     *
     * <p>A PATCH that sends nothing must change nothing. {@code MappingLaws.assertSparseIdentity}
     * checks exactly that: it takes the mapping's {@code updateFrom}, a stored domain value, and a
     * wire that sent nothing. The spec is {@code PreferencesPatchMapping}, over {@code
     * PreferencesPatchForm}. A PATCH mapping with a leaf that can fail would use the five-argument
     * {@code MappingLaws.assertMappingLaws} instead, which checks all three sparse laws.
     *
     * <p>Task: write the law check, for {@code PREFERENCES_PATCH} and {@code STORED}.
     *
     * <pre>
     *   // Nudge:    a binder given {} calls no setter, so the wire is a bean as its constructor
     *   //           left it.
     *   // Strategy: MappingLaws.assertSparseIdentity(updateFrom, stored, allAbsentWire),
     *   //           wrapped in a lambda.
     *   // Spoiler:  () -> MappingLaws.assertSparseIdentity(
     *   //               PREFERENCES_PATCH::updateFrom, STORED, new PreferencesPatchForm())
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: an empty PATCH changes nothing")
    void exercise5_emptyPatchChangesNothing() {
      // TODO: write the sparse identity law check, as a lambda.
      ThrowingCallable identityLaw = answerRequired();

      assertThatCode(identityLaw).doesNotThrowAnyException();
    }

    /**
     * Diagnostic exercise: a law that passes when it should fail.
     *
     * <p>Open {@code DefaultedPreferencesPatchForm}. It is the same bean as a code generator
     * renders it from a schema that says {@code default: false}, and the default becomes a field
     * initialiser. The team's existing test runs the sparse identity law against it, and the test
     * is green:
     *
     * <pre>
     *   // MappingLaws.assertSparseIdentity(DEFAULTED_PREFERENCES_PATCH::updateFrom,
     *   //     new GuestPreferences("en-GB", false),     // wrong: the sample holds the default
     *   //     new DefaultedPreferencesPatchForm());
     * </pre>
     *
     * <p>Task: write a sample that makes the same law fail, so the defect shows.
     *
     * <p>Rule of thumb: give a PATCH law a sample whose every field differs from every default. The
     * fix itself is in the schema: drop {@code default: false}, or give the PATCH request a schema
     * of its own, and the generator leaves the field uninitialised, as {@code PreferencesPatchForm}
     * is. The chapter's Sparse PATCH page tells the same story as a support ticket.
     *
     * <pre>
     *   // Nudge:    what does the bean's getter answer on a request that sent nothing?
     *   // Strategy: a guest whose stored opt-in is not that answer.
     *   // Spoiler:  new GuestPreferences("en-GB", true)
     * </pre>
     */
    @Test
    @DisplayName("Diagnostic: a sample that holds the default hides it; one that differs finds it")
    void diagnostic_sampleMustDifferFromTheDefault() {
      // The existing test, and it is green.
      MappingLaws.assertSparseIdentity(
          DEFAULTED_PREFERENCES_PATCH::updateFrom,
          new GuestPreferences("en-GB", false),
          new DefaultedPreferencesPatchForm());

      // TODO: write a sample whose opt-in differs from the bean's default.
      GuestPreferences sample = answerRequired();

      assertThatThrownBy(
              () ->
                  MappingLaws.assertSparseIdentity(
                      DEFAULTED_PREFERENCES_PATCH::updateFrom,
                      sample,
                      new DefaultedPreferencesPatchForm()))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("Sparse identity law");
      // What the empty request did: the language was absent and kept; the opt-in was "sent".
      assertThatValidated(
              DEFAULTED_PREFERENCES_PATCH
                  .updateFrom(new DefaultedPreferencesPatchForm())
                  .apply(sample))
          .isValid()
          .hasValue(new GuestPreferences("en-GB", false));
    }
  }

  /**
   * Congratulations! You've completed Tutorial 27: Boundary Edge Cases
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ That a {@code null} field on the wire is a located error, beside every other error
   *   <li>✓ How a list element is located by its index
   *   <li>✓ How {@code @OptionalBridge} declares absence, per component
   *   <li>✓ That a record's constructor refusal is reported where the record is
   *   <li>✓ Why a PATCH bean must not default a field, and how the sparse identity law catches one
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>{@code parse} and {@code updateFrom} return every edge case as a value, never a thrown
   *       exception
   *   <li>A PATCH bean's default comes back valid and wrong, and a law sees it only when the sample
   *       differs from the default
   * </ul>
   *
   * <p>Next: the Mapping at the Boundary chapter's Sparse PATCH page, for the PATCH rules in full.
   */
}
