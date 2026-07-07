// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProcessorUtils - shared processor string helpers")
class ProcessorUtilsTest {

  @Test
  @DisplayName("capitalise upper-cases the first character and leaves the rest alone")
  void capitaliseUpperCasesFirstCharacter() {
    assertThat(ProcessorUtils.capitalise("name")).isEqualTo("Name");
    assertThat(ProcessorUtils.capitalise("alreadyUpper")).isEqualTo("AlreadyUpper");
    assertThat(ProcessorUtils.capitalise("x")).isEqualTo("X");
  }

  @Test
  @DisplayName("capitalise passes null and empty inputs through unchanged")
  void capitalisePassesDegenerateInputsThrough() {
    assertThat(ProcessorUtils.capitalise(null)).isNull();
    assertThat(ProcessorUtils.capitalise("")).isEmpty();
  }
}
