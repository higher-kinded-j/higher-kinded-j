// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
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
 * Tutorial 27: Boundary Edge Cases — where a bad request goes when it is not the obvious kind.
 *
 * <p>Pain to Promise. Tutorial 26 parsed a wire with three bad values. Real requests also leave
 * fields out, send a list with one bad element, break a rule that spans two fields, or arrive as a
 * PATCH bean that fills in values nobody sent. Each of these has one place to land, and a
 * hand-written mapper usually finds none of them: it throws a {@code NullPointerException}, stops
 * at the first list element, or quietly writes a default over the stored value.
 *
 * <p>The specs live in {@code org.higherkindedj.example.tutorials.mapping} beside Tutorial 26's:
 * {@code PartyMapping} (a list of guests), {@code RoomRequestMapping} (a note the guest may leave
 * out), {@code StayMapping} (two dates the record guards) and two PATCH mappings over preference
 * beans. Open them first; they are short.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>a {@code null} is a located {@code must not be null}, never an exception;
 *   <li>a list element is located by its index, as in {@code guests.1.email};
 *   <li>{@code @OptionalBridge} declares that a {@code null} means absent, per component;
 *   <li>a record's constructor refusal becomes an error at the record's path;
 *   <li>a PATCH bean's default reads as sent, and the sparse identity law catches it.
 * </ul>
 *
 * <p>Prerequisites: Tutorial 26 (Record Mapping). The Mapping at the Boundary chapter's Absent
 * Fields and Record Invariants page, and its Sparse PATCH page, explain each rule in full.
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

  private static final GeneratedPreferencesPatchMappingImpl GENERATED_PREFERENCES_PATCH =
      GeneratedPreferencesPatchMappingImpl.INSTANCE;

  // A guest who has opted in. Its opt-in differs from the generated bean's default, which is what
  // lets the law in Part 3 see that default.
  private static final GuestPreferences OPTED_IN = new GuestPreferences("en-GB", true);

  @Nested
  @DisplayName("Part 1: nulls, lists and absence")
  class NullsListsAndAbsence {

    /**
     * Exercise 1: a null has an address.
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
    void exercise1_aNullHasAnAddress() {
      BookingDto wire = new BookingDto(null, new GuestDto("Ada Lovelace", null), "2026-07-28", 3);

      Validated<NonEmptyList<FieldError>, ?> parsed = BOOKING_MAPPING.parse(wire);

      // TODO: the two located errors, in declaration order.
      List<String> expected = answerRequired();

      assertThatValidated(parsed).isInvalid().hasFieldErrors(expected.toArray(String[]::new));
    }

    /**
     * Exercise 2: a list element is located by its index.
     *
     * <p>Task: the first guest has no name, and the second has a bad email. Write both errors.
     *
     * <pre>
     *   // Nudge:    the path names the domain's components, not the wire's.
     *   // Strategy: a list element adds its index, counted from 0, as a path segment.
     *   // Spoiler:  List.of("guests.0.name: must not be null", "guests.1.email: not an email address")
     * </pre>
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

      // TODO: both located errors, in list order.
      List<String> expected = answerRequired();

      assertThatValidated(parsed).isInvalid().hasFieldErrors(expected.toArray(String[]::new));
    }

    /**
     * Exercise 3: {@code @OptionalBridge} reads a null as absent.
     *
     * <p>Task: {@code RoomRequestMapping} marks the note with {@code @OptionalBridge}. Write the
     * {@code RoomRequest} that parsing a request without a note gives.
     *
     * <pre>
     *   // Nudge:    the spec declared that null means "left out", so this is not an error.
     *   // Strategy: the domain holds a left-out value as an empty Optional.
     *   // Spoiler:  new RoomRequest("double", Optional.empty())
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: @OptionalBridge reads a null as absent")
    void exercise3_absentIsNotAnError() {
      RoomRequestDto wire = new RoomRequestDto("double", null);

      // TODO: the parsed room request.
      RoomRequest expected = answerRequired();

      assertThatValidated(ROOM_REQUEST_MAPPING.parse(wire)).isValid().hasValue(expected);
      // The other direction writes an empty Optional back as null, as JSON has it.
      assertThat(ROOM_REQUEST_MAPPING.build(expected)).isEqualTo(wire);
    }
  }

  @Nested
  @DisplayName("Part 2: the record's own rules")
  class TheRecordsOwnRules {

    /**
     * Exercise 4: a constructor's refusal becomes an error.
     *
     * <p>{@code Stay}'s constructor throws when the departure is not after the arrival. Both dates
     * here parse, so the constructor runs and refuses them.
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
    void exercise4_anInvariantBecomesAnError() {
      StayDto wire = new StayDto("2026-07-28", "2026-07-27");

      // TODO: the one error, as hasFieldErrors renders it.
      String expected = answerRequired();

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
     * Exercise 5: the sparse identity law.
     *
     * <p>A PATCH that sends nothing must change nothing. {@code assertSparseIdentity} checks that
     * against a domain sample and a wire with every property absent.
     *
     * <p>Task: supply the all-absent wire for {@code PreferencesPatchMapping}.
     *
     * <pre>
     *   // Nudge:    what does a binder hand over for a request body of {}?
     *   // Strategy: it calls no setter, so the bean is exactly as its constructor left it.
     *   // Spoiler:  new PreferencesForm()
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: the sparse identity law")
    void exercise5_theSparseIdentityLaw() {
      // TODO: a request that sent nothing.
      PreferencesForm allAbsent = answerRequired();

      MappingLaws.assertSparseIdentity(PREFERENCES_PATCH::updateFrom, OPTED_IN, allAbsent);
    }

    /**
     * Diagnostic exercise: a default that reads as sent.
     *
     * <p>{@code GeneratedPreferencesForm} is the same bean as a code generator renders it from a
     * schema that says {@code default: false}. The default becomes a field initialiser:
     *
     * <pre>
     *   // private Boolean marketingOptIn = false;   // wrong for a PATCH bean: reads as sent
     * </pre>
     *
     * <p>It compiles cleanly, and so does its PATCH mapping. But {@code getMarketingOptIn()} now
     * answers {@code false} on a request that never mentioned it, and {@code updateFrom} writes
     * that {@code false} over the guest's choice.
     *
     * <p>Task: write what an empty request does to a guest who had opted in. The law below then
     * catches it, and the last line shows why the sample must differ from the default.
     *
     * <pre>
     *   // Nudge:    the language was absent, so it is kept; the opt-in was "sent".
     *   // Strategy: the opt-in becomes the bean's default.
     *   // Spoiler:  new GuestPreferences("en-GB", false)
     * </pre>
     */
    @Test
    @DisplayName("Diagnostic: a field initialiser reads as sent, and the law catches it")
    void diagnostic_aDefaultReadsAsSent() {
      Validated<NonEmptyList<FieldError>, GuestPreferences> patched =
          GENERATED_PREFERENCES_PATCH.updateFrom(new GeneratedPreferencesForm()).apply(OPTED_IN);

      // TODO: the preferences after an empty PATCH.
      GuestPreferences expected = answerRequired();

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

  /**
   * Congratulations! You've completed Tutorial 27: Boundary Edge Cases
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ That a {@code null} on the wire is a located error, beside every other error
   *   <li>✓ How a list element is located by its index
   *   <li>✓ How {@code @OptionalBridge} declares absence, per component
   *   <li>✓ That a record's own refusal is located where the record is
   *   <li>✓ Why a PATCH bean must not default a field, and how the sparse law catches one
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>Every edge case is a value in the {@code Validated}, never an exception
   *   <li>A law catches a default only when the sample differs from it
   * </ul>
   *
   * <p>Next: the Mapping at the Boundary chapter's Sparse PATCH page, for the PATCH rules in full.
   */
}
