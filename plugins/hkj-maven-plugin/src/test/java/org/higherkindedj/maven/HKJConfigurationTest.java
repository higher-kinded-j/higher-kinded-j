// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HKJConfiguration")
class HKJConfigurationTest {

  /** The version the build bundles into the plugin, passed in by the test task. */
  private static final String EXPECTED_VERSION = System.getProperty("hkj.expectedVersion");

  private Plugin plugin;

  @BeforeEach
  void setUp() {
    plugin = new Plugin();
    plugin.setGroupId("io.github.higher-kinded-j");
    plugin.setArtifactId("hkj-maven-plugin");
  }

  @Nested
  @DisplayName("plugin version")
  class PluginVersion {

    @Test
    @DisplayName("uses the version bundled in the plugin when no version is configured")
    void usesBundledVersion_whenNoVersionConfigured() {
      plugin.setVersion("9.9.9");

      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin);

      assertThat(EXPECTED_VERSION).as("hkj.expectedVersion is set by the test task").isNotBlank();
      assertThat(config.version()).isEqualTo(EXPECTED_VERSION);
    }

    @Test
    @DisplayName("defaults() uses the version bundled in the plugin")
    void defaultsUseBundledVersion() {
      assertThat(HKJConfiguration.defaults().version()).isEqualTo(EXPECTED_VERSION);
    }

    @Test
    @DisplayName("prefers the bundled version to the declared one")
    void prefersBundledVersion_whenBothExist() {
      assertThat(HKJConfiguration.pluginVersion(Optional.of("1.0.0"), Optional.of("2.0.0")))
          .isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("falls back to the version the POM declares for the plugin")
    void fallsBackToDeclaredVersion_whenNothingBundled() {
      assertThat(HKJConfiguration.pluginVersion(Optional.empty(), Optional.of("1.2.3")))
          .isEqualTo("1.2.3");
    }

    @Test
    @DisplayName("throws with a fix line when neither a bundled nor a declared version exists")
    void throwsWithFixLine_whenNeitherBundledNorDeclared() {
      assertThatThrownBy(() -> HKJConfiguration.pluginVersion(Optional.empty(), Optional.empty()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(HKJConfiguration.NO_VERSION_MESSAGE)
          .hasMessageContaining("<configuration><version>");
    }

    @Test
    @DisplayName("refuses a blank version")
    void refusesBlankVersion() {
      assertThatThrownBy(() -> new HKJConfiguration(" ", true, false, false, true))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("default configuration")
  class DefaultConfiguration {

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
    void handlesBlankValuesAsDefaults() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "version", "   ");
      addChild(config, "preview", "");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin);

      assertThat(result.version()).isEqualTo(EXPECTED_VERSION);
      assertThat(result.preview()).isTrue();
    }
  }

  private void addChild(Xpp3Dom parent, String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    parent.addChild(child);
  }
}
