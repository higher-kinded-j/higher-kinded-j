// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.LOCALE)
class LoggingOpticInterpreterLocaleTest {
  @Test
  @DisplayName("numeric log counts use root-locale digits")
  void countsUseRootLocaleDigits() {
    Locale previous = Locale.getDefault();
    Locale display = Locale.getDefault(Locale.Category.DISPLAY);
    Locale format = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag("ar-EG"));
      LoggingOpticInterpreter logger = OpticInterpreters.logging();
      var elements = List.of(1, 2, 3);
      logger.run(OpticPrograms.getAll(elements, Traversals.forList()));
      logger.run(OpticPrograms.count(elements, Traversals.forList()));
      assertThat(logger.getLog())
          .satisfiesExactly(
              getAll -> assertThat(getAll).endsWith(" -> 3 items"),
              count -> assertThat(count).endsWith(" -> 3"));
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, display);
      Locale.setDefault(Locale.Category.FORMAT, format);
    }
  }
}
