// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.entry;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedParse - the parse half, on its own")
class ValidatedParseTest {

  record Email(String value) {}

  private static Validated<NonEmptyList<FieldError>, Email> parseEmail(String raw) {
    return raw.contains("@")
        ? Validated.validNel(new Email(raw))
        : Validated.invalidNel(FieldError.of("not an email address"));
  }

  /** A parse with no build at all: what a read-only wire offers. */
  private static final ValidatedParse<String, Email> EMAIL =
      ValidatedParse.of(ValidatedParseTest::parseEmail);

  @Nested
  @DisplayName("A parse built from one direction")
  class OneDirection {

    @Test
    @DisplayName("parse and parsePath report the same located outcome")
    void parseAndParsePath() {
      assertThatValidated(EMAIL.parse("ada@corp.example"))
          .isValid()
          .hasValue(new Email("ada@corp.example"));
      assertThatValidated(EMAIL.parse("nope")).isInvalid().hasFieldErrors("not an email address");
      assertThat(EMAIL.parsePath("nope").run()).isEqualTo(EMAIL.parse("nope"));
    }

    @Test
    @DisplayName("the bulk forms lift a parse with no build, locating as a prism's do")
    void bulkFormsNeedNoBuild() {
      assertThatValidated(EMAIL.parseAll(List.of("a@b", "nope")))
          .isInvalid()
          .hasFieldErrors("1: not an email address");
      assertThatValidated(EMAIL.parseAll(new LinkedHashSet<>(List.of("a@b"))))
          .isValid()
          .hasValue(Set.of(new Email("a@b")));
      assertThatValidated(EMAIL.parseAll(new String[] {"nope"}, Email[]::new))
          .isInvalid()
          .hasFieldErrors("0: not an email address");

      Map<String, String> byKey = new LinkedHashMap<>();
      byKey.put("home", "a@b");
      byKey.put("work", "nope");
      assertThatValidated(EMAIL.parseValues(byKey))
          .isInvalid()
          .hasFieldErrors("work: not an email address");
      assertThat(EMAIL.parseKeys(Map.of("a@b", 1)).get())
          .containsExactly(entry(new Email("a@b"), 1));
    }

    @Test
    @DisplayName("parseEntries takes a parse for the values, so neither side needs a build")
    void parseEntriesTakesAParse() {
      assertThat(EMAIL.parseEntries(Map.of("a@b", "x@y"), EMAIL).get())
          .containsExactly(entry(new Email("a@b"), new Email("x@y")));
      assertThatValidated(EMAIL.parseEntries(Map.of("a@b", "nope"), EMAIL))
          .isInvalid()
          .hasFieldErrors("a@b: not an email address");
    }

    @Test
    @DisplayName("a whole prism serves wherever a parse is asked for")
    void prismIsAParse() {
      ValidatedParse<String, Email> fromPrism =
          ValidatedPrism.of(ValidatedParseTest::parseEmail, Email::value);
      assertThat(EMAIL.parseEntries(Map.of("a@b", "x@y"), fromPrism).get())
          .containsExactly(entry(new Email("a@b"), new Email("x@y")));
    }
  }

  @Nested
  @DisplayName("Guards")
  class Guards {

    @Test
    @DisplayName("the factory and the Of implementation reject nulls in and out")
    void nullGuards() {
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedParse.of(null))
          .withMessage("parse must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> new ValidatedParse.Of<String, Email>(null))
          .withMessage("parseFn must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.parse(null))
          .withMessage("source must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedParse.<String, Email>of(s -> null).parse("x"))
          .withMessage("parse must not return null");

      ValidatedParse.Of<String, Email> leaf =
          new ValidatedParse.Of<>(ValidatedParseTest::parseEmail);
      assertThat(leaf.parseFn()).isNotNull();
    }
  }
}
