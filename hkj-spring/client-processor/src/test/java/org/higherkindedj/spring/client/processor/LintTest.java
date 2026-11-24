// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Lint")
class LintTest {

  @Test
  @DisplayName("declares its constants in the order of their categories, which name order is")
  void declaresItsConstantsInCategoryOrder() {
    assertThat(Arrays.stream(Lint.values()).map(Lint::category).toList()).isSorted();
  }
}
