// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
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

  private BuildResult buildWithConfigurationCache(String... tasks) {
    List<String> args = new ArrayList<>(List.of(tasks));
    args.add("--configuration-cache");
    return runner(args.toArray(String[]::new)).build();
  }

  private static void deleteRecursively(Path dir) throws IOException {
    try (Stream<Path> paths = Files.walk(dir)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }

  /**
   * Writes a project with one documented class, whose HKJ dependencies resolve to empty jars, so
   * that it compiles and documents without the published artifacts. The checker stays off, since it
   * needs the real hkj-checker jar. {@code extra} is appended to the build file.
   */
  private void writeDocumentedProject(String extra) throws IOException {
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
            flatDir { dirs 'libs' }
        }
        """
            + extra);
    Path libs = Files.createDirectories(testProjectDir.resolve("libs"));
    for (String artifact : List.of("hkj-core", "hkj-processor-plugins")) {
      new JarOutputStream(Files.newOutputStream(libs.resolve(artifact + "-0.3.0.jar"))).close();
    }
    Path srcDir = Files.createDirectories(testProjectDir.resolve("src/main/java/test"));
    Files.writeString(
        srcDir.resolve("App.java"),
        """
        package test;

        /** An application. */
        public class App {}
        """);
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
    // Regression: both annotation processor classpaths must be reported so users
    // can see that compileTestJava has the checker/processor available too.
    assertThat(output)
        .contains("testAnnotationProcessor: io.github.higher-kinded-j:hkj-processor-plugins:0.3.0");
    assertThat(output)
        .contains("testAnnotationProcessor: io.github.higher-kinded-j:hkj-checker:0.3.0");
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
    assertThat(output).contains("hkj-spring-boot-client-processor:0.3.0");
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
    // hkj-processor-plugins is always registered on both configurations, even when
    // the checker is disabled.
    assertThat(output)
        .contains("testAnnotationProcessor: io.github.higher-kinded-j:hkj-processor-plugins:0.3.0");
  }

  @Test
  @DisplayName("a checker argument the build script writes is the only one javac receives")
  void buildScriptCheckerArg_isTheOnlyCheckerArg() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
        }

        def severity = 'warn'
        tasks.withType(JavaCompile).configureEach {
            options.compilerArgs << "-Xplugin:HKJChecker severity=${severity}"
        }

        tasks.register('pluginArgs') {
            doLast {
                tasks.withType(JavaCompile).each { task ->
                    def args = task.options.allCompilerArgs.findAll { it.startsWith('-Xplugin:') }
                    println "${task.name}: ${args}"
                }
            }
        }
        """);

    // Compiling would need published HKJ artifacts, so the test stops at the arguments
    // Gradle hands javac, as the script's own GString leaves them.
    String output = runner("pluginArgs").build().getOutput();

    assertThat(output)
        .contains("compileJava: [-Xplugin:HKJChecker severity=warn]")
        .contains("compileTestJava: [-Xplugin:HKJChecker severity=warn]");
  }

  @Test
  @DisplayName("javadoc runs with preview features on")
  void javadoc_runsWithPreviewFeatures() throws IOException {
    writeDocumentedProject("");

    BuildResult result = runner("javadoc").build();

    assertThat(result.task(":javadoc").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(testProjectDir.resolve("build/docs/javadoc/test/App.html")).isRegularFile();
  }

  @Test
  @DisplayName("javadoc runs with preview features on and a toolchain")
  void javadoc_runsWithPreviewFeaturesAndToolchain() throws IOException {
    writeDocumentedProject(
        """

        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
            }
        }
        """);

    BuildResult result = runner("javadoc").build();

    assertThat(result.task(":javadoc").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(testProjectDir.resolve("build/docs/javadoc/test/App.html")).isRegularFile();
  }

  @Test
  @DisplayName("a Javadoc task that passes its own -source or --release keeps it")
  void javadoc_keepsTheBuildsOwnSourceOrRelease() throws IOException {
    writeDocumentedProject(
        """

        def documented = { Javadoc task, String dir ->
            task.source = sourceSets.main.allJava
            task.classpath = sourceSets.main.compileClasspath + sourceSets.main.output
            task.destinationDir = layout.buildDirectory.dir(dir).get().asFile
        }
        tasks.register('javadocWithSource', Javadoc) {
            documented(it, 'withSource')
            options.source = JavaVersion.current().majorVersion
        }
        tasks.register('javadocWithRelease', Javadoc) {
            documented(it, 'withRelease')
            options.addStringOption('-release', JavaVersion.current().majorVersion)
        }
        """);

    // javadoc refuses -source beside --release, so either task fails if the plugin adds its own
    BuildResult result = runner("javadocWithSource", "javadocWithRelease").build();

    assertThat(result.task(":javadocWithSource").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.task(":javadocWithRelease").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
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
  @DisplayName("dependencies task lists checker in testAnnotationProcessor")
  void dependencies_listsCheckerDependency_onTestAnnotationProcessor() throws IOException {
    // Regression test for the "plug-in not found: HKJChecker" error during compileTestJava.
    // Because -Xplugin:HKJChecker is added to every JavaCompile task, the checker JAR must
    // be registered on testAnnotationProcessor too, otherwise javac cannot load the plugin
    // when compiling test sources.
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

    BuildResult result =
        runner("dependencies", "--configuration", "testAnnotationProcessor").build();
    String output = result.getOutput();

    assertThat(output).contains("io.github.higher-kinded-j:hkj-checker:0.3.0");
    assertThat(output).contains("io.github.higher-kinded-j:hkj-processor-plugins:0.3.0");
  }

  @Test
  @DisplayName("dependencies task lists checker on a custom source set's processor classpath")
  void dependencies_listsCheckerDependency_onCustomSourceSet() throws IOException {
    // Custom source sets (integrationTest, functionalTest, etc.) also receive
    // -Xplugin:HKJChecker on their JavaCompile task, so their annotation processor
    // configuration must carry the checker and processor-plugins JARs too.
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
        }

        sourceSets {
            integrationTest {}
        }

        repositories {
            mavenCentral()
        }
        """);

    BuildResult result =
        runner("dependencies", "--configuration", "integrationTestAnnotationProcessor").build();
    String output = result.getOutput();

    assertThat(output).contains("io.github.higher-kinded-j:hkj-checker:0.3.0");
    assertThat(output).contains("io.github.higher-kinded-j:hkj-processor-plugins:0.3.0");
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
    // Only hkj-* skills ship: a skill for contributors to this repository stays out of consumer
    // projects, whatever it is called
    assertThat(skillsDir.resolve("book-authoring")).doesNotExist();
    try (var installed = Files.list(skillsDir)) {
      assertThat(installed.map(dir -> dir.getFileName().toString()))
          .allMatch(name -> name.startsWith("hkj-"));
    }
  }

  @Test
  @DisplayName("diagnostics task runs with the configuration cache, and a second build reuses it")
  void diagnostics_task_runsWithConfigurationCache() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
        }

        sourceSets {
            integrationTest {}
        }
        """);

    String stored = buildWithConfigurationCache("hkjDiagnostics").getOutput();
    String reused = buildWithConfigurationCache("hkjDiagnostics").getOutput();

    assertThat(stored).contains("Configuration cache entry stored.");
    assertThat(reused).contains("Reusing configuration cache.");
    // The reused build prints the report the first build stored, source sets included
    for (String output : List.of(stored, reused)) {
      assertThat(output)
          .contains("Version:            0.3.0")
          .contains("Path type mismatch: enabled")
          .contains(
              "integrationTestAnnotationProcessor: io.github.higher-kinded-j:hkj-checker:0.3.0");
    }
  }

  @Test
  @DisplayName("a skills build runs with the configuration cache, and a second build reuses it")
  void skillsBuild_runsWithConfigurationCache() throws IOException {
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
            flatDir { dirs 'libs' }
        }
        """);
    // Storing the entry resolves compileJava's classpaths. Empty files stand in for the published
    // jars, since with no sources javac never runs.
    Path libs = Files.createDirectories(testProjectDir.resolve("libs"));
    for (String artifact : List.of("hkj-core", "hkj-processor-plugins", "hkj-checker")) {
      Files.createFile(libs.resolve(artifact + "-0.3.0.jar"));
    }
    Path skillsDir = testProjectDir.resolve(".claude/skills");
    String installedInto = "into " + Path.of(".claude", "skills");

    BuildResult stored = buildWithConfigurationCache("classes");
    assertThat(stored.task(":hkjInstallSkills").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(stored.getOutput())
        .contains("Configuration cache entry stored.")
        .contains(installedInto);
    assertThat(skillsDir.resolve("hkj-guide/SKILL.md")).isRegularFile();

    // Removing the skills makes the install run again, from the stored entry
    deleteRecursively(skillsDir);
    BuildResult reused = buildWithConfigurationCache("classes");
    assertThat(reused.task(":hkjInstallSkills").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(reused.getOutput()).contains("Reusing configuration cache.").contains(installedInto);
    assertThat(skillsDir.resolve("hkj-guide/SKILL.md")).isRegularFile();
  }

  @Test
  @DisplayName("an install task the build registers installs into .claude/skills or its outputDir")
  void installSkillsTask_registeredByTheBuild_installsIntoItsOutputDir(@TempDir Path elsewhere)
      throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        tasks.register('installAgain', org.higherkindedj.gradle.HKJInstallSkillsTask)
        tasks.register('installElsewhere', org.higherkindedj.gradle.HKJInstallSkillsTask) {
            outputDir = file('%s')
        }
        """
            .formatted(elsewhere.toString().replace('\\', '/')));

    BuildResult result = runner("installAgain", "installElsewhere").build();

    assertThat(result.task(":installAgain").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(testProjectDir.resolve(".claude/skills/hkj-guide/SKILL.md")).isRegularFile();
    assertThat(result.task(":installElsewhere").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(elsewhere.resolve("hkj-guide/SKILL.md")).isRegularFile();
    // A directory outside the project is named in full
    assertThat(result.getOutput()).contains("into " + elsewhere);
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
