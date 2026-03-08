// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HKJConfiguration")
class HKJConfigurationTest {

  private MavenProject project;
  private Plugin plugin;

  @BeforeEach
  void setUp() {
    project = new MavenProject();
    project.setVersion("1.0.0");
    plugin = new Plugin();
    plugin.setGroupId("io.github.higher-kinded-j");
    plugin.setArtifactId("hkj-maven-plugin");
  }

  @Nested
  @DisplayName("default configuration")
  class DefaultConfiguration {

    @Test
    @DisplayName("uses project version when no version specified")
    void usesProjectVersion_whenNoVersionSpecified() {
      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin, project);

      assertThat(config.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("preview defaults to true")
    void previewDefaultsToTrue() {
      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin, project);

      assertThat(config.preview()).isTrue();
    }

    @Test
    @DisplayName("spring defaults to false")
    void springDefaultsToFalse() {
      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin, project);

      assertThat(config.spring()).isFalse();
    }

    @Test
    @DisplayName("pathTypeMismatch defaults to true")
    void pathTypeMismatchDefaultsToTrue() {
      HKJConfiguration config = HKJConfiguration.fromPlugin(plugin, project);

      assertThat(config.pathTypeMismatch()).isTrue();
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

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin, project);

      assertThat(result.version()).isEqualTo("0.3.7-SNAPSHOT");
    }

    @Test
    @DisplayName("reads preview disabled")
    void readsPreviewDisabled() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "preview", "false");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin, project);

      assertThat(result.preview()).isFalse();
    }

    @Test
    @DisplayName("reads spring enabled")
    void readsSpringEnabled() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "spring", "true");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin, project);

      assertThat(result.spring()).isTrue();
    }

    @Test
    @DisplayName("reads pathTypeMismatch disabled")
    void readsPathTypeMismatchDisabled() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "pathTypeMismatch", "false");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin, project);

      assertThat(result.pathTypeMismatch()).isFalse();
    }

    @Test
    @DisplayName("handles blank values as defaults")
    void handlesBlankValuesAsDefaults() {
      Xpp3Dom config = new Xpp3Dom("configuration");
      addChild(config, "version", "   ");
      addChild(config, "preview", "");
      plugin.setConfiguration(config);

      HKJConfiguration result = HKJConfiguration.fromPlugin(plugin, project);

      assertThat(result.version()).isEqualTo("1.0.0");
      assertThat(result.preview()).isTrue();
    }
  }

  private void addChild(Xpp3Dom parent, String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    parent.addChild(child);
  }
}
