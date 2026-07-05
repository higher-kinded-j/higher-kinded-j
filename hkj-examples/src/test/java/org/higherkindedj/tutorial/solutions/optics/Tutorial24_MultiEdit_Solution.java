// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.edit.Edit;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 24: Multi-edit and sparse updates.
 *
 * <p>The pattern throughout: pair an optic path with a write ({@code Edit}), fold the edits ({@code
 * Edits.combine} for pure, {@code Edits.accumulate} for validated), apply once. Absent incoming
 * values contribute the identity update; failed parses surface as located {@code FieldError}s, all
 * at once.
 */
@DisplayName("Tutorial 24: Multi-Edit and Sparse Updates (Solutions)")
public class Tutorial24_MultiEdit_Solution {

  record Profile(String email, String displayName, int age) {}

  // Labelled paths (a @GenerateFocus companion emits exactly this shape): failures self-locate.
  static final FocusPath<Profile, String> EMAIL =
      FocusPath.of(
          Lens.of(Profile::email, (p, e) -> new Profile(e, p.displayName(), p.age())), "email");
  static final FocusPath<Profile, String> NAME =
      FocusPath.of(
          Lens.of(Profile::displayName, (p, n) -> new Profile(p.email(), n, p.age())), "name");
  static final FocusPath<Profile, Integer> AGE =
      FocusPath.of(
          Lens.of(Profile::age, (p, a) -> new Profile(p.email(), p.displayName(), a)), "age");

  static Validated<NonEmptyList<FieldError>, String> parseEmail(String raw) {
    return raw.contains("@")
        ? Validated.validNel(raw.trim().toLowerCase())
        : Validated.invalidNel(FieldError.of("not an email address"));
  }

  static Validated<NonEmptyList<FieldError>, Integer> parseAge(String raw) {
    try {
      int age = Integer.parseInt(raw.trim());
      return age >= 0
          ? Validated.validNel(age)
          : Validated.invalidNel(FieldError.of("must not be negative"));
    } catch (NumberFormatException e) {
      return Validated.invalidNel(FieldError.of("must be a whole number"));
    }
  }

  private static List<String> errorStrings(Validated<NonEmptyList<FieldError>, ?> validated) {
    return validated.getError().toJavaList().stream().map(FieldError::toString).toList();
  }

  @Nested
  @DisplayName("Part 1: pure multi-edit with Edits.combine")
  class PureMultiEdit {

    /**
     * Why this is idiomatic: the fold produces a named value ({@code tidy}) that is applied to many
     * sources and composes onward via {@code Update.andThen} - no reassignment threading.
     */
    @Test
    @DisplayName("Exercise 1: fold two edits into one reusable Update")
    void exercise1_combineIntoOneUpdate() {
      Update<Profile> tidy =
          Edits.combine(Edit.modify(EMAIL, String::toLowerCase), Edit.modify(NAME, String::trim));

      Profile alice = new Profile("Alice@Example.COM", "  Alice  ", 34);
      Profile bob = new Profile("BOB@example.com", "Bob", 41);

      assertThat(tidy.apply(alice)).isEqualTo(new Profile("alice@example.com", "Alice", 34));
      assertThat(tidy.apply(bob)).isEqualTo(new Profile("bob@example.com", "Bob", 41));
    }

    /**
     * Why this matters: function composition is not commutative. Disjoint paths commute, but two
     * edits on the same path see each other's results in fold order.
     */
    @Test
    @DisplayName("Exercise 2: overlapping edits apply left to right")
    void exercise2_orderIsApplicationOrder() {
      Update<Profile> decorate =
          Edits.combine(Edit.modify(NAME, n -> n + "!"), Edit.modify(NAME, n -> n + "?"));

      Profile ada = new Profile("ada@example.com", "Ada", 36);

      assertThat(decorate.apply(ada).displayName()).isEqualTo("Ada!?");
    }
  }

  @Nested
  @DisplayName("Part 2: sparse updates - absent means leave it alone")
  class SparseUpdates {

    /**
     * Why this is idiomatic: the absent field contributes the monoid identity, so the sparse
     * request lands one-to-one with no {@code if} at the application site.
     */
    @Test
    @DisplayName("Exercise 3: absent fields contribute the identity update")
    void exercise3_sparsePatch() {
      Update<Profile> patch =
          Edits.combine(
              Edit.setIfPresent(NAME, null),
              Edit.setIfPresent(EMAIL, "carol@new.example"),
              Edit.modifyIfPresent(AGE, 1, Integer::sum));

      Profile carol = new Profile("carol@old.example", "Carol", 28);

      assertThat(patch.apply(carol)).isEqualTo(new Profile("carol@new.example", "Carol", 29));
    }
  }

  @Nested
  @DisplayName("Part 3: validated PATCH with Edits.accumulate")
  class ValidatedPatch {

    /**
     * Why this is idiomatic: the parser stays a plain smart constructor (no path parameter); the
     * labelled path locates the failure automatically, and {@code .at(label)} remains available to
     * prepend outer context.
     */
    @Test
    @DisplayName("Exercise 4: a fallible edit parses, then writes")
    void exercise4_parseThenWrite() {
      Profile dan = new Profile("dan@old.example", "Dan", 52);

      Validated<NonEmptyList<FieldError>, Profile> patched =
          Edits.accumulate(
                  Edit.parseIfPresent(
                      EMAIL, "  Dan@Example.COM ", Tutorial24_MultiEdit_Solution::parseEmail))
              .apply(dan);

      assertThatValidated(patched).isValid().hasValue(new Profile("dan@example.com", "Dan", 52));
    }

    /**
     * Why this is idiomatic: unlike {@code flatMap} chaining (which short-circuits and hides later
     * failures), {@code accumulate} validates every edit independently - one response can list
     * every bad field, located and in edit order.
     */
    @Test
    @DisplayName("Exercise 5: all failures reported at once, in edit order")
    void exercise5_allErrorsAtOnce() {
      Profile eve = new Profile("eve@example.com", "Eve", 61);

      Validated<NonEmptyList<FieldError>, Profile> patched =
          Edits.accumulate(
                  Edit.parseIfPresent(EMAIL, "nope", Tutorial24_MultiEdit_Solution::parseEmail),
                  Edit.parseIfPresent(AGE, "not-a-number", Tutorial24_MultiEdit_Solution::parseAge))
              .apply(eve);

      assertThatValidated(patched).isInvalid();
      assertThat(errorStrings(patched))
          .containsExactly("email: not an email address", "age: must be a whole number");
    }
  }
}
