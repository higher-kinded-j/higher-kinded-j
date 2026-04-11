// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("HKJPlugin functional tests")
class HKJPluginFunctionalTest {

  @TempDir Path testProjectDir;

  @BeforeEach
  void setUp() throws IOException {
    // Create settings.gradle
    Files.writeString(
        testProjectDir.resolve("settings.gradle"), "rootProject.name = 'test-project'\n");
  }

  private void writeBuildFile(String content) throws IOException {
    Files.writeString(testProjectDir.resolve("build.gradle"), content);
  }

  private GradleRunner runner(String... args) {
    return GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath()
        .withArguments(args)
        .forwardOutput();
  }

  @Test
  @DisplayName("build succeeds with default configuration")
  void build_succeeds_withDefaultConfiguration() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        repositories {
            mavenCentral()
        }
        """);

    // Create a minimal Java source file
    Path srcDir = testProjectDir.resolve("src/main/java/test");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("App.java"),
        """
        package test;
        public class App {}
        """);

    // We only test that the plugin applies without error and tasks are configured.
    // Actual compilation would require published HKJ artifacts on the classpath.
    BuildResult result = runner("tasks", "--all").build();
    assertThat(result.getOutput()).contains("hkjDiagnostics");
  }

  @Test
  @DisplayName("diagnostics task prints configuration")
  void diagnostics_task_printsConfiguration() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        version = '1.0.0'

        hkj {
            version = '0.3.0'
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("hkjDiagnostics").build();
    String output = result.getOutput();

    assertThat(output).contains("HKJ Configuration:");
    assertThat(output).contains("Version:            0.3.0");
    assertThat(output).contains("Preview features:   enabled");
    assertThat(output).contains("Spring integration: disabled");
    assertThat(output).contains("Path type mismatch: enabled");
    assertThat(output).contains("hkj-core:0.3.0");
    assertThat(output).contains("hkj-processor-plugins:0.3.0");
    assertThat(output).contains("hkj-checker:0.3.0");
    assertThat(output).contains("--enable-preview");
    assertThat(output).contains("-Xplugin:HKJChecker");
  }

  @Test
  @DisplayName("diagnostics task reflects disabled preview")
  void diagnostics_task_reflectsDisabledPreview() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
            preview = false
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("hkjDiagnostics").build();
    String output = result.getOutput();

    assertThat(output).contains("Preview features:   disabled");
    assertThat(output).doesNotContain("--enable-preview");
  }

  @Test
  @DisplayName("diagnostics task reflects spring enabled")
  void diagnostics_task_reflectsSpringEnabled() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
            spring = true
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("hkjDiagnostics").build();
    String output = result.getOutput();

    assertThat(output).contains("Spring integration: enabled");
    assertThat(output).contains("hkj-spring-boot-starter:0.3.0");
  }

  @Test
  @DisplayName("custom version overrides default")
  void customVersion_overridesDefault() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.2.2'
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("hkjDiagnostics").build();
    String output = result.getOutput();

    assertThat(output).contains("Version:            0.2.2");
    assertThat(output).contains("hkj-core:0.2.2");
  }

  @Test
  @DisplayName("checks closure DSL disables path type mismatch")
  void checks_closure_disablesPathTypeMismatch() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
            checks {
                pathTypeMismatch = false
            }
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("hkjDiagnostics").build();
    String output = result.getOutput();

    assertThat(output).contains("Path type mismatch: disabled");
    assertThat(output).doesNotContain("hkj-checker");
    assertThat(output).doesNotContain("-Xplugin:HKJChecker");
  }

  @Test
  @DisplayName("dependencies task lists hkj-core in implementation")
  void dependencies_listsCoreDependency() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("dependencies", "--configuration", "implementation").build();
    String output = result.getOutput();

    assertThat(output).contains("io.github.higher-kinded-j:hkj-core:0.3.0");
  }

  @Test
  @DisplayName("dependencies task lists checker in annotationProcessor")
  void dependencies_listsCheckerDependency() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("dependencies", "--configuration", "annotationProcessor").build();
    String output = result.getOutput();

    assertThat(output).contains("io.github.higher-kinded-j:hkj-checker:0.3.0");
    assertThat(output).contains("io.github.higher-kinded-j:hkj-processor-plugins:0.3.0");
  }

  @Test
  @DisplayName("dependencies task excludes checker when checks disabled")
  void dependencies_excludesCheckerWhenDisabled() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
            checks {
                pathTypeMismatch = false
            }
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("dependencies", "--configuration", "annotationProcessor").build();
    String output = result.getOutput();

    assertThat(output).contains("io.github.higher-kinded-j:hkj-processor-plugins:0.3.0");
    assertThat(output).doesNotContain("hkj-checker");
  }

  @Test
  @DisplayName("diagnostics task reflects skills enabled")
  void diagnostics_task_reflectsSkillsEnabled() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
            skills = true
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("hkjDiagnostics").build();
    String output = result.getOutput();

    assertThat(output).contains("Claude Code skills: enabled");
  }

  @Test
  @DisplayName("diagnostics task reflects skills disabled by default")
  void diagnostics_task_reflectsSkillsDisabledByDefault() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("hkjDiagnostics").build();
    String output = result.getOutput();

    assertThat(output).contains("Claude Code skills: disabled");
  }

  @Test
  @DisplayName("hkjInstallSkills task is registered")
  void installSkillsTask_isRegistered() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("tasks", "--all").build();
    assertThat(result.getOutput()).contains("hkjInstallSkills");
  }

  @Test
  @DisplayName("hkjInstallSkills task installs skills into .claude/skills/")
  void installSkillsTask_installsSkills() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("hkjInstallSkills").build();
    String output = result.getOutput();

    // The task should complete and report installed files
    assertThat(output).contains("Installed");
    assertThat(output).contains("HKJ Claude Code skill files");

    // Verify skill files were actually created
    Path skillsDir = testProjectDir.resolve(".claude/skills");
    assertThat(skillsDir).isDirectory();
    assertThat(skillsDir.resolve("hkj-guide/SKILL.md")).isRegularFile();
    assertThat(skillsDir.resolve("hkj-optics/SKILL.md")).isRegularFile();
    assertThat(skillsDir.resolve("hkj-effects/SKILL.md")).isRegularFile();
    assertThat(skillsDir.resolve("hkj-bridge/SKILL.md")).isRegularFile();
    assertThat(skillsDir.resolve("hkj-spring/SKILL.md")).isRegularFile();
    assertThat(skillsDir.resolve("hkj-arch/SKILL.md")).isRegularFile();
  }

  @Test
  @DisplayName("all options combined configures correctly")
  void allOptionsCombined() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
            preview = false
            spring = true
            skills = true
            checks {
                pathTypeMismatch = false
            }
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result = runner("hkjDiagnostics").build();
    String output = result.getOutput();

    assertThat(output).contains("Version:            0.3.0");
    assertThat(output).contains("Preview features:   disabled");
    assertThat(output).contains("Spring integration: enabled");
    assertThat(output).contains("Claude Code skills: enabled");
    assertThat(output).contains("Path type mismatch: disabled");
    assertThat(output).contains("hkj-spring-boot-starter:0.3.0");
    assertThat(output).doesNotContain("hkj-checker");
    assertThat(output).doesNotContain("--enable-preview");
    assertThat(output).doesNotContain("-Xplugin:HKJChecker");
  }
}
