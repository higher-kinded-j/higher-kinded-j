// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 24: Multi-edit and sparse updates.
 *
 * <p>Pain to Promise. Applying several independent edits to one value means hand-threading the
 * result through reassignments (`p = lens1.set(...); p = lens2.set(..., p)`), wrapping each
 * optional field in an {@code if}, and reporting only the first bad value. The {@code Edits}
 * builder folds all of that into two entry points: {@code Edits.combine} (pure edits, one reusable
 * {@link Update}) and {@code Edits.accumulate} (validated PATCH: every bad field reported at once,
 * located).
 *
 * <pre>
 *   Update&lt;Profile&gt; tidy = Edits.combine(
 *       Edit.modify(EMAIL, String::toLowerCase),
 *       Edit.modify(NAME,  String::trim));
 *
 *   Validated&lt;NonEmptyList&lt;FieldError&gt;, Profile&gt; patched =
 *       Edits.accumulate(
 *               Edit.setIfPresent(NAME, request.name()),                       // null -&gt; no-op
 *               Edit.parseIfPresent(EMAIL, request.email(), Tutorial24_MultiEdit::parseEmail))
 *           .apply(profile);  // labelled paths locate failures automatically
 * </pre>
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@code Edit.set / modify} pair an optic path with a write; {@code Edits.combine} folds pure
 *       edits left to right into one {@code Update} (a fallible edit does not compile there);
 *   <li>{@code …IfPresent} treats {@code null} as absent - the edit contributes the identity
 *       update, the basis for sparse PATCH;
 *   <li>{@code parseIfPresent(path, raw, parser).at("label")} validates the incoming value first;
 *       {@code Edits.accumulate} reports every located failure at once, in edit order, and applies
 *       the writes only if all validated.
 * </ul>
 *
 * <p>Limits, stated up front: edits target exactly-one paths ({@code FocusPath}/{@code Setter});
 * overlapping paths see earlier writes (disjoint paths commute); genuinely coupled fields belong in
 * one atomic edit - that is Tutorial 23's {@code CoupledLenses} territory.
 *
 * <p>Prerequisites: Tutorial 01 (Lens Basics); Tutorial 12 (Accumulating Assembly) is the
 * construction-side twin of Part 3. Read the Multi-Edit and Sparse Updates chapter.
 *
 * <p>Estimated time: ~12 minutes.
 *
 * <p>Replace each {@code answerRequired()} placeholder with the correct code to make the tests
 * pass.
 */
@DisplayName("Tutorial 24: Multi-Edit and Sparse Updates")
public class Tutorial24_MultiEdit {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

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
     * Exercise 1: one reusable update from two edits.
     *
     * <p>Task: fold "lower-case the email" and "trim the display name" into one {@code
     * Update<Profile>} and apply it to both profiles.
     *
     * <pre>
     *   // Hint 1: {@code Edit.modify(EMAIL, String::toLowerCase)} is a pure edit.
     *   // Hint 2: {@code Edits.combine(edit1, edit2)} folds them left to right.
     *   // Hint 3: the result is an {@code Update<Profile>} - apply it like a function.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: fold two edits into one reusable Update")
    void exercise1_combineIntoOneUpdate() {
      // TODO: fold the two tidying edits into one update.
      Update<Profile> tidy = answerRequired();

      Profile alice = new Profile("Alice@Example.COM", "  Alice  ", 34);
      Profile bob = new Profile("BOB@example.com", "Bob", 41);

      assertThat(tidy.apply(alice)).isEqualTo(new Profile("alice@example.com", "Alice", 34));
      assertThat(tidy.apply(bob)).isEqualTo(new Profile("bob@example.com", "Bob", 41));
    }

    /**
     * Exercise 2: order is application order.
     *
     * <p>Task: two edits target the same field, so the fold order is observable. Build the pipeline
     * that produces {@code "Ada!?"} from {@code "Ada"}.
     *
     * <pre>
     *   // Hint 1: {@code Edit.modify(NAME, n -> n + "!")} then {@code Edit.modify(NAME, n -> n + "?")}.
     *   // Hint 2: combine applies left to right - the first edit runs first.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: overlapping edits apply left to right")
    void exercise2_orderIsApplicationOrder() {
      // TODO: build the pipeline that appends "!" first, then "?".
      Update<Profile> decorate = answerRequired();

      Profile ada = new Profile("ada@example.com", "Ada", 36);

      assertThat(decorate.apply(ada).displayName()).isEqualTo("Ada!?");
    }
  }

  @Nested
  @DisplayName("Part 2: sparse updates - absent means leave it alone")
  class SparseUpdates {

    /**
     * Exercise 3: a sparse patch with no if-ceremony.
     *
     * <p>Task: a request supplies a new email and an age increment, but no display name. Build one
     * update in which the absent field contributes nothing.
     *
     * <pre>
     *   // Hint 1: {@code Edit.setIfPresent(NAME, null)} is the identity update.
     *   // Hint 2: {@code Edit.setIfPresent(EMAIL, "carol@new.example")} writes.
     *   // Hint 3: {@code Edit.modifyIfPresent(AGE, 1, Integer::sum)} combines (incoming, current).
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: absent fields contribute the identity update")
    void exercise3_sparsePatch() {
      // TODO: fold the three sparse edits (name absent, email and age present) into one update.
      Update<Profile> patch = answerRequired();

      Profile carol = new Profile("carol@old.example", "Carol", 28);

      assertThat(patch.apply(carol)).isEqualTo(new Profile("carol@new.example", "Carol", 29));
    }
  }

  @Nested
  @DisplayName("Part 3: validated PATCH with Edits.accumulate")
  class ValidatedPatch {

    /**
     * Exercise 4: parse the incoming value, locate the write.
     *
     * <p>Task: accumulate a single fallible edit that parses a raw email and writes the normalised
     * result, then apply it.
     *
     * <pre>
     *   // Hint 1: {@code Edit.parseIfPresent(EMAIL, "  Dan@Example.COM ", Tutorial24_MultiEdit::parseEmail)}.
     *   // Hint 2: EMAIL is a labelled path, so failures self-locate as "email: ..." - no .at
     *   //         needed. (.at("account") would still prepend outer context.)
     *   // Hint 3: {@code Edits.accumulate(...).apply(dan)} returns a Validated.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: a fallible edit parses, then writes")
    void exercise4_parseThenWrite() {
      Profile dan = new Profile("dan@old.example", "Dan", 52);

      // TODO: accumulate the single located parse edit and apply it.
      Validated<NonEmptyList<FieldError>, Profile> patched = answerRequired();

      assertThatValidated(patched).isValid().hasValue(new Profile("dan@example.com", "Dan", 52));
    }

    /**
     * Exercise 5: every bad field, at once, located.
     *
     * <p>Task: a request supplies a bad email AND a bad age. Accumulate both fallible edits so the
     * result reports both failures, in edit order, each located by its label.
     *
     * <pre>
     *   // Hint 1: {@code parseIfPresent(EMAIL, "nope", ...)} and
     *   //         {@code parseIfPresent(AGE, "not-a-number", Tutorial24_MultiEdit::parseAge)} -
     *   //         the labelled paths locate each failure automatically.
     *   // Hint 2: unlike flatMap chaining, accumulate does NOT short-circuit.
     *   // Hint 3: compare with Tutorial 12 - this is the same all-errors-at-once model, for
     *   //         updating instead of constructing.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: all failures reported at once, in edit order")
    void exercise5_allErrorsAtOnce() {
      Profile eve = new Profile("eve@example.com", "Eve", 61);

      // TODO: accumulate BOTH located fallible edits and apply.
      Validated<NonEmptyList<FieldError>, Profile> patched = answerRequired();

      assertThatValidated(patched).isInvalid();
      assertThat(errorStrings(patched))
          .containsExactly("email: not an email address", "age: must be a whole number");
    }
  }

  /*
   * Where to next?
   *   • Multi-Edit and Sparse Updates (Optics chapter) — the full story: two-phase semantics,
   *     the ValidationPath twin (applyPath), and reusing one accumulated patch across sources.
   *   • Tutorial 23 — genuinely coupled fields (a cross-field invariant) belong in one atomic
   *     edit: CoupledLenses, not independent edits.
   *   • Tutorial 12 — the construction-side twin: assembling a NEW record from N validated
   *     fields with fields()/accumulate().
   */
}
