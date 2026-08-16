// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static org.assertj.core.api.Assertions.assertThat;

import javax.tools.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CheckerConfig")
class CheckerConfigTest {

  @Nested
  @DisplayName("defaults")
  class Defaults {

    @Test
    @DisplayName("enables every check and reports at ERROR")
    void defaults_allEnabledErrorSeverity() {
      CheckerConfig config = CheckerConfig.defaults();

      assertThat(config.isEnabled(CheckerConfig.PATH_TYPE_MISMATCH)).isTrue();
      assertThat(config.isEnabled(CheckerConfig.EFFECT_COMPOSITION)).isTrue();
      assertThat(config.severity()).isEqualTo(Diagnostic.Kind.ERROR);
    }

    @Test
    @DisplayName("parse with no arguments equals defaults")
    void parse_noArgs_equalsDefaults() {
      CheckerConfig config = CheckerConfig.parse();

      assertThat(config.isEnabled(CheckerConfig.PATH_TYPE_MISMATCH)).isTrue();
      assertThat(config.severity()).isEqualTo(Diagnostic.Kind.ERROR);
    }

    @Test
    @DisplayName("parse with null array is safe")
    void parse_nullArray_safe() {
      CheckerConfig config = CheckerConfig.parse((String[]) null);

      assertThat(config.isEnabled(CheckerConfig.PATH_TYPE_MISMATCH)).isTrue();
    }
  }

  @Nested
  @DisplayName("disable")
  class Disable {

    @Test
    @DisplayName("disables a single named check")
    void disable_single() {
      CheckerConfig config = CheckerConfig.parse("disable=path-type-mismatch");

      assertThat(config.isEnabled(CheckerConfig.PATH_TYPE_MISMATCH)).isFalse();
      assertThat(config.isEnabled(CheckerConfig.EFFECT_COMPOSITION)).isTrue();
    }

    @Test
    @DisplayName("disables multiple comma-separated checks")
    void disable_multipleCommaSeparated() {
      CheckerConfig config = CheckerConfig.parse("disable=path-type-mismatch,effect-composition");

      assertThat(config.isEnabled(CheckerConfig.PATH_TYPE_MISMATCH)).isFalse();
      assertThat(config.isEnabled(CheckerConfig.EFFECT_COMPOSITION)).isFalse();
    }

    @Test
    @DisplayName("accepts disable and severity as separate arguments")
    void disable_separateArgs() {
      CheckerConfig config = CheckerConfig.parse("disable=effect-composition", "severity=warn");

      assertThat(config.isEnabled(CheckerConfig.EFFECT_COMPOSITION)).isFalse();
      assertThat(config.severity()).isEqualTo(Diagnostic.Kind.WARNING);
    }

    @Test
    @DisplayName("accepts disable and severity combined in one ;-separated token")
    void disable_combinedToken() {
      CheckerConfig config = CheckerConfig.parse("disable=effect-composition;severity=warn");

      assertThat(config.isEnabled(CheckerConfig.EFFECT_COMPOSITION)).isFalse();
      assertThat(config.severity()).isEqualTo(Diagnostic.Kind.WARNING);
    }
  }

  @Nested
  @DisplayName("severity")
  class Severity {

    @Test
    @DisplayName("severity=warn downgrades to WARNING")
    void severity_warn() {
      assertThat(CheckerConfig.parse("severity=warn").severity())
          .isEqualTo(Diagnostic.Kind.WARNING);
    }

    @Test
    @DisplayName("severity=warning is accepted as an alias")
    void severity_warningAlias() {
      assertThat(CheckerConfig.parse("severity=warning").severity())
          .isEqualTo(Diagnostic.Kind.WARNING);
    }

    @Test
    @DisplayName("severity=error stays ERROR")
    void severity_error() {
      assertThat(CheckerConfig.parse("severity=error").severity()).isEqualTo(Diagnostic.Kind.ERROR);
    }

    @Test
    @DisplayName("invalid severity keeps the default ERROR")
    void severity_invalidIgnored() {
      assertThat(CheckerConfig.parse("severity=banana").severity())
          .isEqualTo(Diagnostic.Kind.ERROR);
    }
  }

  @Nested
  @DisplayName("robustness")
  class Robustness {

    @Test
    @DisplayName("unknown keys are ignored, not fatal")
    void unknownKey_ignored() {
      CheckerConfig config = CheckerConfig.parse("nonsense=true", "disable=path-type-mismatch");

      assertThat(config.isEnabled(CheckerConfig.PATH_TYPE_MISMATCH)).isFalse();
    }

    @Test
    @DisplayName("non key=value tokens are ignored")
    void bareToken_ignored() {
      CheckerConfig config = CheckerConfig.parse("justaword", "severity=warn");

      assertThat(config.severity()).isEqualTo(Diagnostic.Kind.WARNING);
    }

    @Test
    @DisplayName("blank and null arguments are ignored")
    void blankArgs_ignored() {
      CheckerConfig config = CheckerConfig.parse("", "   ", null, "severity=warn");

      assertThat(config.severity()).isEqualTo(Diagnostic.Kind.WARNING);
    }
  }

  /**
   * The ids are a published vocabulary: they are spelled as bare strings in {@code disable=} and
   * {@code severity:<id>=} plugin arguments, in {@code @SuppressWarnings} tokens across hkj-core
   * and downstream code, and in the book. Nothing outside this module can reference the constants,
   * so renaming one would silently stop honouring every existing spelling — pin them here.
   */
  @Nested
  @DisplayName("the id vocabulary")
  class IdVocabulary {

    @Test
    @DisplayName("every check id keeps its published spelling")
    void checkIds_arePinned() {
      assertThat(CheckerConfig.PATH_TYPE_MISMATCH).isEqualTo("path-type-mismatch");
      assertThat(CheckerConfig.EFFECT_COMPOSITION).isEqualTo("effect-composition");
      assertThat(CheckerConfig.TRANSFORMER_MISSING_MONAD).isEqualTo("transformer-missing-monad");
      assertThat(CheckerConfig.FREE_SWITCH_EXHAUSTIVE).isEqualTo("free-switch-exhaustive");
      assertThat(CheckerConfig.DISCARDED_EFFECT).isEqualTo("discarded-effect");
      assertThat(CheckerConfig.STATE_T_MAPT_ARITY).isEqualTo("state-t-mapt-arity");
      assertThat(CheckerConfig.ERROR_TYPE_MISMATCH).isEqualTo("error-type-mismatch");
      assertThat(CheckerConfig.KIND_VALUE_NARROW).isEqualTo("kind-value-narrow");
      assertThat(CheckerConfig.WITNESS_ARITY).isEqualTo("witness-arity");
      assertThat(CheckerConfig.VIA_NON_PATH).isEqualTo("via-non-path");
      assertThat(CheckerConfig.MAP_NESTS_EFFECT).isEqualTo("map-nests-effect");
      assertThat(CheckerConfig.MIGRATION_NUDGE).isEqualTo("migration-nudge");
      assertThat(CheckerConfig.RAW_KIND).isEqualTo("raw-kind");
    }

    @Test
    @DisplayName("the blanket suppression token keeps its published spelling")
    void suppressAll_isPinned() {
      assertThat(CheckerConfig.SUPPRESS_ALL).isEqualTo("hkj-checker");
    }
  }
}
