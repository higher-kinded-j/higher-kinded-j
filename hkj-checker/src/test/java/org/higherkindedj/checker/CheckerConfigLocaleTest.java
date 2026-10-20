// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import javax.tools.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ResourceLock(Resources.LOCALE)
class CheckerConfigLocaleTest {
  @ParameterizedTest
  @ValueSource(strings = {"tr-TR", "en-US", "ar-EG"})
  @DisplayName("global and per-check warning severities do not depend on the default locale")
  void warningSeverityIsLocaleIndependent(String languageTag) {
    Locale previous = Locale.getDefault();
    Locale display = Locale.getDefault(Locale.Category.DISPLAY);
    Locale format = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag(languageTag));
      for (String value : new String[] {"WARNING", "WaRnInG", "WARN", "warning"}) {
        assertThat(CheckerConfig.parse("severity=" + value).severity())
            .as("global severity %s under %s", value, languageTag)
            .isEqualTo(Diagnostic.Kind.WARNING);
        assertThat(
                CheckerConfig.parse("severity:path-type-mismatch=" + value)
                    .severityFor(CheckerConfig.PATH_TYPE_MISMATCH))
            .as("per-check severity %s under %s", value, languageTag)
            .isEqualTo(Diagnostic.Kind.WARNING);
      }
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, display);
      Locale.setDefault(Locale.Category.FORMAT, format);
    }
  }
}
