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
import org.gradle.api.tasks.SourceSetContainer;
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
    @DisplayName("adds hkj-processor-plugins to testAnnotationProcessor")
    void plugin_addsProcessorDependency_toTestAnnotationProcessor() {
      applyPlugin();
      evaluateProject();

      assertThat(dependencyNotations("testAnnotationProcessor"))
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
    @DisplayName("adds hkj-checker to testAnnotationProcessor when checks are enabled")
    void plugin_addsCheckerDependency_toTestAnnotationProcessor_whenEnabled() {
      // Regression test: -Xplugin:HKJChecker is added to all JavaCompile tasks (including
      // compileTestJava); the checker JAR must also be on testAnnotationProcessor, otherwise
      // javac fails with "plug-in not found: HKJChecker" when compiling test sources.
      applyPlugin();
      evaluateProject();

      assertThat(dependencyNotations("testAnnotationProcessor"))
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
    @DisplayName("enables forked compilation with --add-exports for checker")
    void plugin_addsForkOptionsForChecker_whenChecksEnabled() {
      applyPlugin();
      evaluateProject();

      project
          .getTasks()
          .withType(JavaCompile.class)
          .configureEach(
              task -> {
                assertThat(task.getOptions().isFork()).isTrue();
                assertThat(task.getOptions().getForkOptions().getJvmArgs())
                    .contains("--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED");
              });
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
      assertThat(dependencyNotations("testAnnotationProcessor"))
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

    @Test
    @DisplayName("skills defaults to false")
    void plugin_skillsDefaultsToFalse() {
      applyPlugin();
      HKJExtension ext = project.getExtensions().getByType(HKJExtension.class);

      assertThat(ext.getSkills().get()).isFalse();
    }

    @Test
    @DisplayName("registers hkjInstallSkills task in hkj group")
    void plugin_registersHKJInstallSkillsTask() {
      applyPlugin();

      Task installTask = project.getTasks().findByName("hkjInstallSkills");
      assertThat(installTask).isNotNull();
      assertThat(installTask.getGroup()).isEqualTo("hkj");
    }
  }

  @Nested
  @DisplayName("custom source sets")
  class CustomSourceSets {

    @Test
    @DisplayName(
        "registers hkj-processor-plugins and hkj-checker on a custom source set's processor"
            + " classpath")
    void plugin_registersProcessors_onCustomSourceSet() {
      // Because -Xplugin:HKJChecker is applied to every JavaCompile task, any extra source
      // set (e.g., integrationTest) must also have the checker and processor JARs on its
      // annotation processor classpath. Using SourceSetContainer.all(...) in the plugin
      // ensures both existing and newly-created source sets are covered.
      applyPlugin();
      project.getExtensions().getByType(SourceSetContainer.class).create("integrationTest");
      evaluateProject();

      assertThat(dependencyNotations("integrationTestAnnotationProcessor"))
          .anyMatch(dep -> dep.contains("hkj-processor-plugins"))
          .anyMatch(dep -> dep.contains("hkj-checker"));
    }

    @Test
    @DisplayName("skips hkj-checker on custom source set when checks are disabled")
    void plugin_skipsCheckerOnCustomSourceSet_whenDisabled() {
      applyPlugin();
      HKJExtension ext = project.getExtensions().getByType(HKJExtension.class);
      ext.getChecks().getPathTypeMismatch().set(false);
      project.getExtensions().getByType(SourceSetContainer.class).create("integrationTest");
      evaluateProject();

      assertThat(dependencyNotations("integrationTestAnnotationProcessor"))
          .anyMatch(dep -> dep.contains("hkj-processor-plugins"))
          .noneMatch(dep -> dep.contains("hkj-checker"));
    }
  }
}
