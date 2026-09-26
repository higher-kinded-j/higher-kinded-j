// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.tutorials.mapping.BookingDto;
import org.higherkindedj.example.tutorials.mapping.BookingMappingImpl;
import org.higherkindedj.example.tutorials.mapping.GeneratedPreferencesForm;
import org.higherkindedj.example.tutorials.mapping.GeneratedPreferencesPatchMappingImpl;
import org.higherkindedj.example.tutorials.mapping.GuestDto;
import org.higherkindedj.example.tutorials.mapping.GuestPreferences;
import org.higherkindedj.example.tutorials.mapping.PartyDto;
import org.higherkindedj.example.tutorials.mapping.PartyMappingImpl;
import org.higherkindedj.example.tutorials.mapping.PreferencesForm;
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
 * <p>The pattern throughout: a boundary's edge cases are ordinary values, never exceptions. A
 * {@code null} is a located error unless the spec says it means absent; a list element is located
 * by its index; a record's own refusal is located where the record is; and a PATCH bean's default
 * is caught by a law, because no compiler can see it.
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

  private static final GeneratedPreferencesPatchMappingImpl GENERATED_PREFERENCES_PATCH =
      GeneratedPreferencesPatchMappingImpl.INSTANCE;

  // A guest who has opted in. Its opt-in differs from the generated bean's default, which is what
  // lets the law in Part 3 see that default.
  private static final GuestPreferences OPTED_IN = new GuestPreferences("en-GB", true);

  @Nested
  @DisplayName("Part 1: nulls, lists and absence")
  class NullsListsAndAbsence {

    /**
     * Why this is idiomatic: a {@code null} on the wire is the client's mistake, so it is reported
     * like any other, at the field that held it, beside every other error.
     */
    @Test
    @DisplayName("Exercise 1: a null has an address")
    void exercise1_aNullHasAnAddress() {
      BookingDto wire = new BookingDto(null, new GuestDto("Ada Lovelace", null), "2026-07-28", 3);

      Validated<NonEmptyList<FieldError>, ?> parsed = BOOKING_MAPPING.parse(wire);

      List<String> expected = List.of("id: must not be null", "guest.email: must not be null");

      assertThatValidated(parsed).isInvalid().hasFieldErrors(expected.toArray(String[]::new));
    }

    /**
     * Why this is idiomatic: the path names the domain's components, and a list element is named by
     * its index, so a client can point at the exact guest to fix.
     */
    @Test
    @DisplayName("Exercise 2: a list element is located by its index")
    void exercise2_aListIndexInThePath() {
      PartyDto wire =
          new PartyDto(
              BOOKING_ID,
              List.of(
                  new GuestDto(null, "ada@corp.example"),
                  new GuestDto("Grace Hopper", "not-an-email")));

      Validated<NonEmptyList<FieldError>, ?> parsed = PARTY_MAPPING.parse(wire);

      List<String> expected =
          List.of("guests.0.name: must not be null", "guests.1.email: not an email address");

      assertThatValidated(parsed).isInvalid().hasFieldErrors(expected.toArray(String[]::new));
    }

    /**
     * Why this is idiomatic: absence is declared on the spec, per component, and the domain holds
     * it in the type that says so, an {@code Optional}.
     */
    @Test
    @DisplayName("Exercise 3: @OptionalBridge reads a null as absent")
    void exercise3_absentIsNotAnError() {
      RoomRequestDto wire = new RoomRequestDto("double", null);

      RoomRequest expected = new RoomRequest("double", Optional.empty());

      assertThatValidated(ROOM_REQUEST_MAPPING.parse(wire)).isValid().hasValue(expected);
      // The other direction writes an empty Optional back as null, as JSON has it.
      assertThat(ROOM_REQUEST_MAPPING.build(expected)).isEqualTo(wire);
    }
  }

  @Nested
  @DisplayName("Part 2: the record's own rules")
  class TheRecordsOwnRules {

    /**
     * Why this is idiomatic: the constructor keeps its guard, and {@code parse} reports the refusal
     * where the record is. At the top level that is no path at all, so the error is its message.
     */
    @Test
    @DisplayName("Exercise 4: a constructor's refusal becomes an error")
    void exercise4_anInvariantBecomesAnError() {
      StayDto wire = new StayDto("2026-07-28", "2026-07-27");

      String expected = "departure must be after arrival";

      assertThatValidated(STAY_MAPPING.parse(wire)).isInvalid().hasFieldErrors(expected);
      // The constructor runs only once both dates have parsed, so a bad date is reported alone.
      assertThatValidated(STAY_MAPPING.parse(new StayDto("2026-07-28", "tomorrow")))
          .isInvalid()
          .hasFieldErrors("departure: not an ISO-8601 date (expected e.g. 2026-07-28)");
    }
  }

  @Nested
  @DisplayName("Part 3: a PATCH bean's defaults")
  class APatchBeansDefaults {

    /**
     * Why this is idiomatic: a request that sends nothing must change nothing, and a freshly
     * constructed bean is exactly that request, so it is the law's all-absent wire.
     */
    @Test
    @DisplayName("Exercise 5: the sparse identity law")
    void exercise5_theSparseIdentityLaw() {
      PreferencesForm allAbsent = new PreferencesForm();

      MappingLaws.assertSparseIdentity(PREFERENCES_PATCH::updateFrom, OPTED_IN, allAbsent);
    }

    /**
     * Why this is idiomatic: the law is the only check that can see a default, and only when the
     * sample differs from it. Test with a sample whose every field differs from every default.
     */
    @Test
    @DisplayName("Diagnostic: a field initialiser reads as sent, and the law catches it")
    void diagnostic_aDefaultReadsAsSent() {
      Validated<NonEmptyList<FieldError>, GuestPreferences> patched =
          GENERATED_PREFERENCES_PATCH.updateFrom(new GeneratedPreferencesForm()).apply(OPTED_IN);

      GuestPreferences expected = new GuestPreferences("en-GB", false);

      assertThatValidated(patched).isValid().hasValue(expected);
      assertThatThrownBy(
              () ->
                  MappingLaws.assertSparseIdentity(
                      GENERATED_PREFERENCES_PATCH::updateFrom,
                      OPTED_IN,
                      new GeneratedPreferencesForm()))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("Sparse identity law");
      // A sample that already holds the default hides the defect: writing false over false changes
      // nothing, so the same law passes.
      MappingLaws.assertSparseIdentity(
          GENERATED_PREFERENCES_PATCH::updateFrom,
          new GuestPreferences("en-GB", false),
          new GeneratedPreferencesForm());
    }
  }
}
