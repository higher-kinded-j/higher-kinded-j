// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import java.util.Optional;
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
import org.higherkindedj.example.tutorials.mapping.PreferencesPatchForm;
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
 * Solutions for Tutorial 27: Boundary Edge Cases.
 *
 * <p>The pattern throughout: {@code parse} and {@code updateFrom} return every edge case as a
 * value, never a thrown exception. A {@code null} field is a located error unless the spec says it
 * means absent; a list element is located by its index; a record's own refusal is reported where
 * the record is. The one case no compiler can see, a PATCH bean's default, comes back valid and
 * wrong, so a law with a well-chosen sample is what catches it.
 */
@DisplayName("Tutorial 27: Boundary Edge Cases (Solutions)")
public class Tutorial27_BoundaryEdgeCases_Solution {

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
     * Why this is idiomatic: a {@code null} field is the client's mistake, so it is reported like
     * any other, at the field that held it, beside every other error. The null check runs before
     * the leaf, so {@code GuestCodecs.EMAIL}, which would throw on a {@code null}, never sees one.
     *
     * <p>Common wrong attempt: expecting {@code parse} to throw a {@code NullPointerException}, or
     * listing only the first error. {@code parse} accumulates, so both are reported.
     */
    @Test
    @DisplayName("Exercise 1: a null has an address")
    void exercise1_nullHasAnAddress() {
      BookingDto wire = new BookingDto(null, new GuestDto("Ada Lovelace", null), "2026-07-28", 3);

      Validated<NonEmptyList<FieldError>, Booking> parsed = BOOKING_MAPPING.parse(wire);

      List<String> expected = List.of("id: must not be null", "guest.email: must not be null");

      assertThatValidated(parsed).isInvalid().hasFieldErrors(expected.toArray(String[]::new));
    }

    /**
     * Why this is idiomatic: the path names the domain's components, and a list element is named by
     * its index, so a client can point at the exact guest to fix.
     *
     * <p>Common wrong attempt: {@code guests[1].email} (a bracket, as Bean Validation writes it),
     * {@code guests.2.email} (counting from 1), or {@code guests.0.fullName} (the wire's name for
     * the component, not the domain's).
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

      List<String> expected =
          List.of("guests.0.name: must not be null", "guests.1.email: not an email address");

      assertThatValidated(parsed).isInvalid().hasFieldErrors(expected.toArray(String[]::new));
    }

    /**
     * Why this is idiomatic: absence is declared on the spec, per component, and the domain holds
     * it in the type that says so, an {@code Optional}.
     *
     * <p>Common wrong attempt: {@code "note: must not be null"}, which is what a {@code null} means
     * on a component the spec did not bridge, such as {@code roomType}.
     */
    @Test
    @DisplayName("Exercise 3: @OptionalBridge reads a null as absent")
    void exercise3_bridgeReadsNullAsAbsent() {
      RoomRequestDto wire = new RoomRequestDto("double", null);

      RoomRequest expected = new RoomRequest("double", Optional.empty());

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
     * Why this is idiomatic: the constructor keeps its guard, and {@code parse} reports the refusal
     * where the record is. At the top level that is no path at all, so the error renders as its
     * message alone. Under a parent it would read {@code stay: departure must be after arrival}.
     *
     * <p>Common wrong attempt: {@code "departure: departure must be after arrival"}, which puts the
     * refusal on one field. A rule that spans two fields belongs to neither.
     */
    @Test
    @DisplayName("Exercise 4: a constructor's refusal becomes an error")
    void exercise4_invariantBecomesAnError() {
      StayDto wire = new StayDto("2026-07-28", "2026-07-27");

      String expected = "departure must be after arrival";

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
     * Why this is idiomatic: a request that sends nothing must change nothing, and a freshly
     * constructed bean is exactly that request, so it is the law's all-absent wire.
     *
     * <p>Alternative: {@code MappingLaws.assertMappingLaws(updateFrom, sample, empty, valid,
     * invalid)} checks all three sparse laws at once. It needs a wire that fails to parse, which
     * this mapping cannot have, since it has no leaf that can fail.
     *
     * <p>Common wrong attempt: a form built with {@code setMarketingOptIn(false)} "to be explicit".
     * That sends {@code false}, so it is no longer the request that sent nothing.
     */
    @Test
    @DisplayName("Exercise 5: an empty PATCH changes nothing")
    void exercise5_emptyPatchChangesNothing() {
      ThrowingCallable identityLaw =
          () ->
              MappingLaws.assertSparseIdentity(
                  PREFERENCES_PATCH::updateFrom, STORED, new PreferencesPatchForm());

      assertThatCode(identityLaw).doesNotThrowAnyException();
    }

    /**
     * Why this is idiomatic: the law is the standing check that sees a default, but only when the
     * sample differs from it. So a PATCH law's sample should differ from every default in every
     * field.
     *
     * <p>Common wrong attempt: a sample that already holds the default, such as a guest who never
     * opted in. Writing {@code false} over {@code false} changes nothing, so the law passes.
     */
    @Test
    @DisplayName("Diagnostic: a sample that holds the default hides it; one that differs finds it")
    void diagnostic_sampleMustDifferFromTheDefault() {
      // The existing test, and it is green: its guest never opted in, so the default hides.
      MappingLaws.assertSparseIdentity(
          DEFAULTED_PREFERENCES_PATCH::updateFrom,
          new GuestPreferences("en-GB", false),
          new DefaultedPreferencesPatchForm());

      GuestPreferences sample = new GuestPreferences("en-GB", true);

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
}
