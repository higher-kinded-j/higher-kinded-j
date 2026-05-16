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
}
