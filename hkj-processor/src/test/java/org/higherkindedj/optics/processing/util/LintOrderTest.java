// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Lint")
class LintOrderTest {

  @Test
  @DisplayName("declares its constants in the order of their keys, which a suppression lists")
  void declaresItsConstantsInKeyOrder() {
    assertThat(Arrays.stream(Lint.values()).map(Lint::key).toList()).isSorted();
  }
}
