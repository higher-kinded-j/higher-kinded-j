// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.laws.ValidatedPrismLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 25: ValidatedPrism.
 *
 * <p>The pattern throughout: name the wire boundary once as {@code of(parse, build)} with a
 * faithful render, verify it with the published laws, then feed {@code prism::parse} wherever a
 * located leaf is needed — sibling accumulation stays the assembly builders' job.
 */
@DisplayName("Tutorial 25: ValidatedPrism (Solutions)")
public class Tutorial25_ValidatedPrism_Solution {

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
     * Why this is idiomatic: parse normalises and reports; build renders faithfully — the section
     * law ({@code parse(s) == Valid(a) => build(a) == s}) is what keeps the two honest.
     */
    @Test
    @DisplayName("Exercise 1: of(parse, build) with both round-trip laws")
    void exercise1_buildThePrism() {
      ValidatedPrism<String, Percentage> percent =
          ValidatedPrism.of(Tutorial25_ValidatedPrism_Solution::parsePercent, p -> p.value() + "%");

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
     * Why this is idiomatic: a plain prism's empty match cannot say why; the lift attaches the
     * reason once, at the boundary, rather than at every call site.
     */
    @Test
    @DisplayName("Exercise 2: fromPrism supplies the reason Optional.empty cannot")
    void exercise2_liftAPlainPrism() {
      Prism<String, Percentage> plain =
          Prism.of(
              s -> parsePercent(s).fold(errors -> Optional.empty(), Optional::of),
              p -> p.value() + "%");

      ValidatedPrism<String, Percentage> lifted =
          ValidatedPrism.fromPrism(plain, FieldError.of("not a percentage"));

      Validated<NonEmptyList<FieldError>, Percentage> refused = lifted.parse("nope");
      assertThatValidated(refused).isInvalid();
      assertThat(refused.getError().head()).hasToString("not a percentage");
    }
  }

  @Nested
  @DisplayName("Part 3: the boundary in practice - siblings accumulate")
  class SiblingsAccumulate {

    /**
     * Why this is idiomatic: nesting short-circuits but siblings accumulate — the prism supplies
     * each located leaf and {@code fields()} does the all-at-once reporting.
     */
    @Test
    @DisplayName("Exercise 3: fields() accumulates siblings; the prism supplies located leaves")
    void exercise3_siblingAccumulation() {
      ValidatedPrism<String, Percentage> percent =
          ValidatedPrism.of(Tutorial25_ValidatedPrism_Solution::parsePercent, p -> p.value() + "%");
      Validated<NonEmptyList<FieldError>, String> badName =
          Validated.invalidNel(FieldError.of("blank"));

      Validated<NonEmptyList<FieldError>, Discount> result =
          Validated.fields()
              .field("name", badName)
              .field("percent", percent.parse("125%"))
              .apply(Discount::new);

      assertThatValidated(result).isInvalid();
      assertThat(result.getError().toJavaList().stream().map(FieldError::toString).toList())
          .isEqualTo(List.of("name: blank", "percent: must be between 0 and 100"));
    }
  }
}
