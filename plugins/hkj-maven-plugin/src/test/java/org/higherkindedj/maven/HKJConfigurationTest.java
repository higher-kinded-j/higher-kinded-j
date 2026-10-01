// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HKJConfiguration")
class HKJConfigurationTest {

  private Plugin plugin;

  @BeforeEach
  void setUp() {
    plugin = new Plugin();
    plugin.setGroupId("io.github.higher-kinded-j");
    plugin.setArtifactId("hkj-maven-plugin");
  }

  @Nested
  @DisplayName("default configuration")
  class DefaultConfiguration {

    @Test
    @DisplayName("uses the plugin's bundled version when no version is specified")
    void usesBundledPluginVersion_whenNoVersionSpecified() throws IOException {
      plugin.setVersion("9.9.9");

      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin);

      assertThat(config.version()).isEqualTo(bundledVersion()).isNotEqualTo("9.9.9");
    }

    @Test
    @DisplayName("falls back to the version the POM declares for the plugin")
    void fallsBackToDeclaredPluginVersion_whenNothingBundled() {
      plugin.setVersion(" 1.2.3 ");

      assertThat(HKJConfiguration.pluginVersion(null, plugin)).isEqualTo("1.2.3");
    }

    @Test
    @DisplayName("refuses to guess when neither a bundled nor a declared version exists")
    void refuses_whenNoVersionAnywhere() {
      plugin.setVersion(" ");

      assertThatThrownBy(() -> HKJConfiguration.pluginVersion(null, plugin))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Set <version>");
    }

    @Test
    @DisplayName("preview defaults to true")
    void previewDefaultsToTrue() {
      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin);

      assertThat(config.preview()).isTrue();
    }

    @Test
    @DisplayName("spring defaults to false")
    void springDefaultsToFalse() {
      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin);

      assertThat(config.spring()).isFalse();
    }

    @Test
    @DisplayName("pathTypeMismatch defaults to true")
    void pathTypeMismatchDefaultsToTrue() {
      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin);

      assertThat(config.pathTypeMismatch()).isTrue();
    }

    @Test
    @DisplayName("skills defaults to false")
    void skillsDefaultsToFalse() {
      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin);

      assertThat(config.skills()).isFalse();
    }
  }

  @Nested
  @DisplayName("custom configuration")
  class CustomConfiguration {

    @Test
    @DisplayName("reads custom version")
    void readsCustomVersion() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "version", "0.3.7-SNAPSHOT");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin);

      assertThat(result.version()).isEqualTo("0.3.7-SNAPSHOT");
    }

    @Test
    @DisplayName("reads preview disabled")
    void readsPreviewDisabled() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "preview", "false");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin);

      assertThat(result.preview()).isFalse();
    }

    @Test
    @DisplayName("reads spring enabled")
    void readsSpringEnabled() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "spring", "true");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin);

      assertThat(result.spring()).isTrue();
    }

    @Test
    @DisplayName("reads pathTypeMismatch disabled")
    void readsPathTypeMismatchDisabled() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "pathTypeMismatch", "false");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin);

      assertThat(result.pathTypeMismatch()).isFalse();
    }

    @Test
    @DisplayName("reads skills enabled")
    void readsSkillsEnabled() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "skills", "true");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin);

      assertThat(result.skills()).isTrue();
    }

    @Test
    @DisplayName("handles blank values as defaults")
    void handlesBlankValuesAsDefaults() throws IOException {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "version", "   ");
      addChild(config, "preview", "");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin);

      assertThat(result.version()).isEqualTo(bundledVersion());
      assertThat(result.preview()).isTrue();
    }
  }

  private void addChild(Xpp3Dom parent, String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    parent.addChild(child);
  }

  private static String bundledVersion() throws IOException {
    try (InputStream in = HKJConfiguration.class.getResourceAsStream("/hkj-version.properties")) {
      assertThat(in).as("hkj-version.properties is bundled in the plugin").isNotNull();
      Properties props = new Properties();
      props.load(in);
      return props.getProperty("version");
    }
  }
}
