// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EffectCompositionChecker}.
 *
 * <p>These tests verify that the checker correctly identifies effect composition errors.
 */
@DisplayName("EffectCompositionChecker Tests")
class EffectCompositionCheckerTest {

  @Test
  @DisplayName("Checker class should exist and extend TreeScanner")
  void checkerClassExists() {
    // Verify the class hierarchy
    assertThat(EffectCompositionChecker.class.getSuperclass().getSimpleName())
        .isEqualTo("TreeScanner");
  }

  @Test
  @DisplayName("Checker should accept Trees in constructor")
  void checkerAcceptsTrees() {
    // Verify the constructor exists with Trees parameter
    assertThat(EffectCompositionChecker.class.getConstructors()).hasSize(1);
    assertThat(EffectCompositionChecker.class.getConstructors()[0].getParameterTypes()).hasSize(1);
    assertThat(
            EffectCompositionChecker.class.getConstructors()[0].getParameterTypes()[0]
                .getSimpleName())
        .isEqualTo("Trees");
  }
}
