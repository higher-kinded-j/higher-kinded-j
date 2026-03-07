// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HKJCheckerPlugin")
class HKJCheckerPluginTest {

  @Nested
  @DisplayName("getName")
  class GetName {

    @Test
    @DisplayName("returns 'HKJChecker'")
    void getName_returnsPluginName() {
      var plugin = new HKJCheckerPlugin();
      assertThat(plugin.getName()).isEqualTo("HKJChecker");
    }

    @Test
    @DisplayName("matches the PLUGIN_NAME constant")
    void getName_matchesConstant() {
      var plugin = new HKJCheckerPlugin();
      assertThat(plugin.getName()).isEqualTo(HKJCheckerPlugin.PLUGIN_NAME);
    }
  }

  @Nested
  @DisplayName("PLUGIN_NAME")
  class PluginName {

    @Test
    @DisplayName("is a non-empty string")
    void pluginName_isNonEmpty() {
      assertThat(HKJCheckerPlugin.PLUGIN_NAME).isNotEmpty();
    }

    @Test
    @DisplayName("does not contain spaces")
    void pluginName_noSpaces() {
      assertThat(HKJCheckerPlugin.PLUGIN_NAME).doesNotContain(" ");
    }
  }
}
