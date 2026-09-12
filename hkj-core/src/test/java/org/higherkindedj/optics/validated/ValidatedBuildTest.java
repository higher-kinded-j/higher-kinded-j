// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.entry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedBuild - the build half, on its own")
class ValidatedBuildTest {

  record Email(String value) {}

  /** A build with no parse at all: what a write-only wire offers. */
  private static final ValidatedBuild<String, Email> EMAIL = ValidatedBuild.of(Email::value);

  @Nested
  @DisplayName("A build made from one direction")
  class OneDirection {

    @Test
    @DisplayName("build and the bulk forms render totally, with no parse behind them")
    void bulkFormsNeedNoParse() {
      assertThat(EMAIL.build(new Email("a@b"))).isEqualTo("a@b");
      assertThat(EMAIL.buildAll(List.of(new Email("a@b"), new Email("c@d"))))
          .containsExactly("a@b", "c@d");
      assertThat(EMAIL.buildAll(new LinkedHashSet<>(List.of(new Email("a@b")))))
          .containsExactly("a@b");
      assertThat(EMAIL.buildAll(new Email[] {new Email("a@b")}, String[]::new))
          .containsExactly("a@b");
      assertThat(EMAIL.buildValues(Map.of("home", new Email("a@b"))))
          .containsExactly(entry("home", "a@b"));
      assertThat(EMAIL.buildKeys(Map.of(new Email("a@b"), 1))).containsExactly(entry("a@b", 1));
    }

    @Test
    @DisplayName("buildEntries takes a build for the values, and a whole prism serves as one")
    void buildEntriesTakesABuild() {
      assertThat(EMAIL.buildEntries(Map.of(new Email("a@b"), new Email("x@y")), EMAIL))
          .containsExactly(entry("a@b", "x@y"));

      ValidatedBuild<String, Email> fromPrism =
          ValidatedPrism.of(raw -> Validated.validNel(new Email(raw)), Email::value);
      assertThat(EMAIL.buildEntries(Map.of(new Email("a@b"), new Email("x@y")), fromPrism))
          .containsExactly(entry("a@b", "x@y"));
    }
  }

  @Nested
  @DisplayName("Guards")
  class Guards {

    @Test
    @DisplayName("the factory and the Of implementation reject nulls in and out")
    void nullGuards() {
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedBuild.of(null))
          .withMessage("build must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> new ValidatedBuild.Of<String, Email>(null))
          .withMessage("buildFn must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> EMAIL.build(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ValidatedBuild.<String, Email>of(email -> null).build(new Email("a@b")))
          .withMessage("build must not return null");

      ValidatedBuild.Of<String, Email> leaf = new ValidatedBuild.Of<>(Email::value);
      assertThat(leaf.buildFn()).isNotNull();
    }
  }
}
