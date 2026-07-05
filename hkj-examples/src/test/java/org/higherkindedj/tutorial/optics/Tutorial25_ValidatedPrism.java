// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.ValidatedPrismLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 25: ValidatedPrism — parse, don't validate, as an optic.
 *
 * <p>Pain to Promise. A {@code Prism}'s match answers yes/no; at a validated boundary (wire value
 * to domain value) the no needs <em>reasons</em> — located, and all of them. {@code ValidatedPrism}
 * is that boundary as an optic: {@code parse : S -> Validated<NonEmptyList<FieldError>, A>}
 * (fallible, accumulating) and a <b>total</b> {@code build : A -> S}.
 *
 * <pre>
 *   ValidatedPrism&lt;String, Percentage&gt; percent = ValidatedPrism.of(
 *       Tutorial25_ValidatedPrism::parsePercent,   // reasons on failure
 *       p -&gt; p.value() + "%");                     // total render
 * </pre>
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@code of(parse, build)}; {@code parsePath} lands on the {@code ValidationPath} railway;
 *   <li>nesting ({@code andThen}) <b>short-circuits</b>; sibling fields <b>accumulate</b> through
 *       {@code Validated.fields()} fed by {@code prism::parse};
 *   <li>{@code fromPrism(prism, reason)} lifts a plain prism; {@code toPrism()} forgets reasons;
 *   <li>both round-trip laws ship in {@code ValidatedPrismLaws} — the section law forbids a {@code
 *       build} that normalises.
 * </ul>
 *
 * <p>Limits, stated up front: only build-preserving compositions exist ({@code ValidatedPrism},
 * {@code Iso}, {@code Prism} + reason) — a {@code Lens} needs a base, so no total build survives
 * it.
 *
 * <p>Prerequisites: Tutorial 03 (Prism Basics); Tutorial 12 (Accumulating Assembly) for Part 3.
 * Read the Validated Prisms chapter.
 *
 * <p>Estimated time: ~10 minutes.
 *
 * <p>Replace each {@code answerRequired()} placeholder with the correct code to make the tests
 * pass.
 */
@DisplayName("Tutorial 25: ValidatedPrism - parse-don't-validate as an optic")
public class Tutorial25_ValidatedPrism {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  record Percentage(int value) {}

  record Discount(String name, Percentage percent) {}

  static Validated<NonEmptyList<FieldError>, Percentage> parsePercent(String raw) {
    if (!raw.endsWith("%")) {
      return Validated.invalidNel(FieldError.of("must end with %"));
    }
    try {
      int value = Integer.parseInt(raw.substring(0, raw.length() - 1));
      return value >= 0 && value <= 100
          ? Validated.validNel(new Percentage(value))
          : Validated.invalidNel(FieldError.of("must be between 0 and 100"));
    } catch (NumberFormatException e) {
      return Validated.invalidNel(FieldError.of("must be a whole number"));
    }
  }

  @Nested
  @DisplayName("Part 1: the boundary as an optic")
  class BoundaryAsOptic {

    /**
     * Exercise 1: build the prism and check both laws.
     *
     * <p>Task: create the {@code String <-> Percentage} prism from {@code parsePercent} and a total
     * render, then verify it with the published laws.
     *
     * <pre>
     *   // Hint 1: {@code ValidatedPrism.of(Tutorial25_ValidatedPrism::parsePercent, ...)}.
     *   // Hint 2: the render is {@code p -> p.value() + "%"} - faithful, no normalising.
     *   // Hint 3: {@code ValidatedPrismLaws.assertValidatedPrismLaws(prism, "25%", "nope")}.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: of(parse, build) with both round-trip laws")
    void exercise1_buildThePrism() {
      // TODO: create the prism.
      ValidatedPrism<String, Percentage> percent = answerRequired();

      assertThatValidated(percent.parse("25%")).isValid().hasValue(new Percentage(25));
      assertThatValidated(percent.parse("125%")).isInvalid();
      assertThat(percent.build(new Percentage(25))).isEqualTo("25%");

      ValidatedPrismLaws.assertValidatedPrismLaws(percent, "25%", "nope");
    }
  }

  @Nested
  @DisplayName("Part 2: composition - nesting short-circuits")
  class Composition {

    /**
     * Exercise 2: give a plain prism a reason.
     *
     * <p>Task: lift the yes/no percent-string prism into a {@code ValidatedPrism} whose failure
     * says why.
     *
     * <pre>
     *   // Hint 1: {@code ValidatedPrism.fromPrism(plain, FieldError.of("not a percentage"))}.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: fromPrism supplies the reason Optional.empty cannot")
    void exercise2_liftAPlainPrism() {
      org.higherkindedj.optics.Prism<String, Percentage> plain =
          org.higherkindedj.optics.Prism.of(
              s ->
                  parsePercent(s)
                      .fold(errors -> java.util.Optional.empty(), java.util.Optional::of),
              p -> p.value() + "%");

      // TODO: lift it with a reason.
      ValidatedPrism<String, Percentage> lifted = answerRequired();

      Validated<NonEmptyList<FieldError>, Percentage> refused = lifted.parse("nope");
      assertThatValidated(refused).isInvalid();
      assertThat(refused.getError().head()).hasToString("not a percentage");
    }
  }

  @Nested
  @DisplayName("Part 3: the boundary in practice - siblings accumulate")
  class SiblingsAccumulate {

    /**
     * Exercise 3: assemble a record from prism-parsed leaves.
     *
     * <p>Task: build a {@code Discount} from a name and a raw percent string so that BOTH bad
     * fields are reported at once, each located.
     *
     * <pre>
     *   // Hint 1: {@code Validated.fields().field("name", ...).field("percent", ...)}.
     *   // Hint 2: the percent leaf is the prism's parse: {@code percent.parse(rawPercent)}.
     *   // Hint 3: finish with {@code .apply(Discount::new)}.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: fields() accumulates siblings; the prism supplies located leaves")
    void exercise3_siblingAccumulation() {
      ValidatedPrism<String, Percentage> percent =
          ValidatedPrism.of(Tutorial25_ValidatedPrism::parsePercent, p -> p.value() + "%");
      Validated<NonEmptyList<FieldError>, String> badName =
          Validated.invalidNel(FieldError.of("blank"));

      // TODO: assemble Discount from badName (label "name") and percent.parse("125%")
      // (label "percent") so both failures surface together.
      Validated<NonEmptyList<FieldError>, Discount> result = answerRequired();

      assertThatValidated(result).isInvalid();
      assertThat(result.getError().toJavaList().stream().map(FieldError::toString).toList())
          .isEqualTo(List.of("name: blank", "percent: must be between 0 and 100"));
    }
  }

  /*
   * Where to next?
   *   • Validated Prisms (Optics chapter) — the composition matrix, the lattice bridges, and
   *     why the Lens cell is deliberately absent.
   *   • Tutorial 12 — the assembly builders this tutorial fed with prism::parse.
   *   • Tutorial 24 — the update-side counterpart: Edits.parseIfPresent takes exactly a
   *     ValidatedPrism's parse.
   */
}
