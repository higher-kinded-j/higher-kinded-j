// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HKJPlugin")
class HKJPluginTest {

  private Project project;

  @BeforeEach
  void setUp() {
    project = ProjectBuilder.builder().build();
  }

  private void applyPlugin() {
    project.getPluginManager().apply("io.github.higher-kinded-j.hkj");
  }

  private void evaluateProject() {
    // Force afterEvaluate callbacks to run
    ((ProjectInternal) project).evaluate();
  }

  private Set<String> dependencyNotations(String configurationName) {
    Configuration config = project.getConfigurations().findByName(configurationName);
    if (config == null) {
      return Set.of();
    }
    return config.getDependencies().stream()
        .map(dep -> dep.getGroup() + ":" + dep.getName() + ":" + dep.getVersion())
        .collect(Collectors.toSet());
  }

  @Nested
  @DisplayName("plugin application")
  class PluginApplication {

    @Test
    @DisplayName("applies successfully without exception")
    void plugin_appliesSuccessfully() {
      assertThatCode(() -> applyPlugin()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("creates hkj extension")
    void plugin_createsHKJExtension() {
      applyPlugin();
      assertThat(project.getExtensions().findByName("hkj")).isNotNull();
      assertThat(project.getExtensions().findByType(HKJExtension.class)).isNotNull();
    }
  }

  @Nested
  @DisplayName("default configuration")
  class DefaultConfiguration {

    @Test
    @DisplayName("adds hkj-core to implementation")
    void plugin_addsCoreDependency() {
      applyPlugin();
      evaluateProject();

      assertThat(dependencyNotations("implementation")).anyMatch(dep -> dep.contains("hkj-core"));
    }

    @Test
    @DisplayName("adds hkj-processor-plugins to annotationProcessor")
    void plugin_addsProcessorDependency() {
      applyPlugin();
      evaluateProject();

      assertThat(dependencyNotations("annotationProcessor"))
          .anyMatch(dep -> dep.contains("hkj-processor-plugins"));
    }

    @Test
    @DisplayName("adds --enable-preview to JavaCompile tasks when preview is enabled")
    void plugin_addsPreviewFlags_whenEnabled() {
      applyPlugin();
      evaluateProject();

      project
          .getTasks()
          .withType(JavaCompile.class)
          .configureEach(
              task -> assertThat(task.getOptions().getCompilerArgs()).contains("--enable-preview"));
    }

    @Test
    @DisplayName("adds hkj-checker to annotationProcessor when checks are enabled")
    void plugin_addsCheckerDependency_whenEnabled() {
      applyPlugin();
      evaluateProject();

      assertThat(dependencyNotations("annotationProcessor"))
          .anyMatch(dep -> dep.contains("hkj-checker"));
    }

    @Test
    @DisplayName("adds -Xplugin:HKJChecker to compiler args when checks are enabled")
    void plugin_addsXpluginArg_whenChecksEnabled() {
      applyPlugin();
      evaluateProject();

      project
          .getTasks()
          .withType(JavaCompile.class)
          .configureEach(
              task ->
                  assertThat(task.getOptions().getCompilerArgs()).contains("-Xplugin:HKJChecker"));
    }

    @Test
    @DisplayName("registers hkjDiagnostics task in help group")
    void plugin_registersHKJDiagnosticsTask() {
      applyPlugin();

      Task diagnosticsTask = project.getTasks().findByName("hkjDiagnostics");
      assertThat(diagnosticsTask).isNotNull();
      assertThat(diagnosticsTask.getGroup()).isEqualTo("help");
    }
  }

  @Nested
  @DisplayName("custom configuration")
  class CustomConfiguration {

    @Test
    @DisplayName("skips --enable-preview when preview is disabled")
    void plugin_skipsPreviewFlags_whenDisabled() {
      applyPlugin();
      HKJExtension ext = project.getExtensions().getByType(HKJExtension.class);
      ext.getPreview().set(false);
      evaluateProject();

      project
          .getTasks()
          .withType(JavaCompile.class)
          .configureEach(
              task ->
                  assertThat(task.getOptions().getCompilerArgs())
                      .doesNotContain("--enable-preview"));
    }

    @Test
    @DisplayName("skips checker when pathTypeMismatch is disabled")
    void plugin_skipsChecker_whenDisabled() {
      applyPlugin();
      HKJExtension ext = project.getExtensions().getByType(HKJExtension.class);
      ext.getChecks().getPathTypeMismatch().set(false);
      evaluateProject();

      assertThat(dependencyNotations("annotationProcessor"))
          .noneMatch(dep -> dep.contains("hkj-checker"));

      project
          .getTasks()
          .withType(JavaCompile.class)
          .configureEach(
              task ->
                  assertThat(task.getOptions().getCompilerArgs())
                      .doesNotContain("-Xplugin:HKJChecker"));
    }

    @Test
    @DisplayName("adds hkj-spring-boot-starter when spring is enabled")
    void plugin_addsSpringStarter_whenEnabled() {
      applyPlugin();
      HKJExtension ext = project.getExtensions().getByType(HKJExtension.class);
      ext.getSpring().set(true);
      evaluateProject();

      assertThat(dependencyNotations("implementation"))
          .anyMatch(dep -> dep.contains("hkj-spring-boot-starter"));
    }

    @Test
    @DisplayName("checks(Action) DSL configures pathTypeMismatch")
    void plugin_checksActionDSL_configuresPathTypeMismatch() {
      applyPlugin();
      HKJExtension ext = project.getExtensions().getByType(HKJExtension.class);
      ext.checks(checks -> checks.getPathTypeMismatch().set(false));
      evaluateProject();

      assertThat(dependencyNotations("annotationProcessor"))
          .noneMatch(dep -> dep.contains("hkj-checker"));
    }

    @Test
    @DisplayName("checks extension is accessible and has correct defaults")
    void plugin_checksExtension_hasCorrectDefaults() {
      applyPlugin();
      HKJExtension ext = project.getExtensions().getByType(HKJExtension.class);

      assertThat(ext.getChecks()).isNotNull();
      assertThat(ext.getChecks().getPathTypeMismatch().get()).isTrue();
    }
  }
}
