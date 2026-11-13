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
import org.gradle.testkit.runner.BuildTask;
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

  /**
   * Writes empty jars standing in for the published HKJ artifacts at 0.3.0, in {@code libs}.
   * Storing a configuration cache entry resolves compileJava's classpaths, and with no sources
   * javac never runs.
   */
  private void writeEmptyHkjJars() throws IOException {
    Path libs = Files.createDirectories(testProjectDir.resolve("libs"));
    for (String artifact : List.of("hkj-core", "hkj-processor-plugins", "hkj-checker")) {
      Files.createFile(libs.resolve(artifact + "-0.3.0.jar"));
    }
  }

  private static final String ISOLATED_PROJECTS = "-Dorg.gradle.unsafe.isolated-projects=true";

  /** Fails a build that uses a deprecated Gradle feature. */
  private static final String WARNINGS_FAIL = "--warning-mode=fail";

  /**
   * Writes a build whose root applies no plugin, as in most multi-project builds, and whose {@code
   * modules} each apply it, with {@code hkj} in their {@code hkj} block.
   */
  private void writeModules(String hkj, String... modules) throws IOException {
    Files.writeString(
        testProjectDir.resolve("settings.gradle"),
        "rootProject.name = 'test-project'\ninclude "
            + String.join(", ", Stream.of(modules).map(m -> "'" + m + "'").toList())
            + "\n");
    for (String module : modules) {
      Files.writeString(
          Files.createDirectories(testProjectDir.resolve(module)).resolve("build.gradle"),
          """
          plugins {
              id 'java'
              id 'io.github.higher-kinded-j.hkj'
          }

          hkj {
              version = '0.3.0'
              %s
          }

          repositories {
              flatDir { dirs '../libs' }
          }
          """
              .formatted(hkj));
    }
    writeEmptyHkjJars();
  }

  /** The outcomes of the tasks at {@code paths}, each of which the build must have run. */
  private static List<TaskOutcome> outcomes(BuildResult result, String... paths) {
    return Stream.of(paths)
        .map(
            path -> {
              BuildTask task = result.task(path);
              assertThat(task).as("task %s", path).isNotNull();
              return task.getOutcome();
            })
        .toList();
  }

  /** The skills directory at the root of the test build, as the plugin names it. */
  private Path rootSkills() throws IOException {
    return testProjectDir.toRealPath().resolve(".claude").resolve("skills");
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

  /** The options file Gradle wrote for the Javadoc task {@code taskName}. */
  private String javadocOptions(String taskName) throws IOException {
    return Files.readString(testProjectDir.resolve("build/tmp/" + taskName + "/javadoc.options"));
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
    assertThat(javadocOptions("javadoc"))
        .contains("--enable-preview")
        .contains("--release '" + Runtime.version().feature() + "'");
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
  @DisplayName("Javadoc tasks run beside the build's own -source, --release or tool, however set")
  void javadoc_runsBesideTheBuildsOwnSettings() throws IOException {
    writeDocumentedProject(
        """

        def documented = { Javadoc task, String dir ->
            task.source = sourceSets.main.allJava
            task.classpath = sourceSets.main.compileClasspath + sourceSets.main.output
            task.destinationDir = layout.buildDirectory.dir(dir).get().asFile
        }
        tasks.register('javadocWithSource', Javadoc) {
            documented(it, 'withSource')
        }
        tasks.register('javadocWithRelease', Javadoc) {
            documented(it, 'withRelease')
            options.addStringOption('-release', JavaVersion.current().majorVersion)
        }
        tasks.register('javadocWithSourceOption', Javadoc) {
            documented(it, 'withSourceOption')
            options.addStringOption('source', JavaVersion.current().majorVersion)
        }
        afterEvaluate {
            tasks.named('javadocWithSource', Javadoc) {
                options.source = JavaVersion.current().majorVersion
            }
            tasks.named('javadoc', Javadoc) {
                javadocTool = javaToolchains.javadocToolFor {
                    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
                }
            }
        }
        """);

    // The afterEvaluate settings land after the plugin configures the tasks. javadoc refuses
    // -source beside --release, and Gradle locks a javadoc tool once it has been read.
    BuildResult result =
        runner("javadocWithSource", "javadocWithRelease", "javadocWithSourceOption", "javadoc")
            .build();

    assertThat(result.task(":javadocWithSource").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(javadocOptions("javadocWithSource")).contains("-source").doesNotContain("--release");
    assertThat(result.task(":javadocWithRelease").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.task(":javadocWithSourceOption").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.task(":javadoc").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  @DisplayName("javadoc gets neither --enable-preview nor --release with preview features off")
  void javadoc_getsNeitherFlag_withPreviewOff() throws IOException {
    writeDocumentedProject(
        """

        hkj {
            preview = false
        }
        """);

    BuildResult result = runner("javadoc").build();

    assertThat(result.task(":javadoc").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(javadocOptions("javadoc"))
        .doesNotContain("--enable-preview")
        .doesNotContain("--release");
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

    // Verify skill files were actually created, and stamped with the plugin's release
    Path skillsDir = testProjectDir.resolve(".claude/skills");
    String release = HKJPlugin.readPluginVersion();
    assertThat(output).contains("skill files for Higher-Kinded-J " + release);
    assertThat(skillsDir.resolve(InstalledSkills.STAMP_FILE))
        .content()
        .contains("version=" + release);
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
          .allMatch(name -> name.startsWith("hkj-") || name.equals(InstalledSkills.STAMP_FILE));
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
    writeEmptyHkjJars();
    Path skillsDir = testProjectDir.resolve(".claude/skills");
    String installedInto = "into " + rootSkills();

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
  @DisplayName("a multi-project build installs one copy, at its root, and one up to date quietly")
  void multiProjectBuild_installsOneCopy_atItsRoot() throws IOException {
    writeModules("skills = true", "app", "lib");
    Path skillsDir = testProjectDir.resolve(".claude/skills");
    // A copy an older plugin installed into one module is left as it is, with a line saying so
    Path libCopy = testProjectDir.resolve("lib/.claude/skills/hkj-guide/SKILL.md");
    Files.createDirectories(libCopy.getParent());
    Files.writeString(libCopy, "old");

    // Each project's install runs, and the first to run leaves the second nothing to do
    BuildResult result = buildWithConfigurationCache("classes", WARNINGS_FAIL);

    assertThat(outcomes(result, ":app:hkjInstallSkills", ":lib:hkjInstallSkills"))
        .containsExactlyInAnyOrder(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
    assertThat(result.getOutput())
        .contains("Configuration cache entry stored.")
        .containsOnlyOnce("Installed ")
        .contains("into " + rootSkills());
    assertThat(skillsDir.resolve("hkj-guide/SKILL.md")).isRegularFile();
    assertThat(skillsDir.resolve(InstalledSkills.STAMP_FILE))
        .content()
        .contains("version=" + HKJPlugin.readPluginVersion());
    assertThat(testProjectDir.resolve("app/.claude")).doesNotExist();
    assertThat(libCopy).hasContent("old");
    assertThat(testProjectDir.resolve("lib/.claude/skills").resolve(InstalledSkills.STAMP_FILE))
        .doesNotExist();
    String strayLine =
        "skills in "
            + testProjectDir.toRealPath().resolve("lib/.claude/skills")
            + " are a copy this build no longer updates";
    assertThat(result.getOutput()).containsOnlyOnce(strayLine);

    // The next build finds the skills up to date, and says nothing about them. It reuses the
    // configuration cache, and still names the copy in the module, once its tasks have run
    BuildResult again = buildWithConfigurationCache("classes", WARNINGS_FAIL);
    assertThat(outcomes(again, ":app:hkjInstallSkills", ":lib:hkjInstallSkills"))
        .containsOnly(TaskOutcome.UP_TO_DATE);
    assertThat(again.getOutput())
        .contains("Reusing configuration cache.")
        .doesNotContain("Installed ")
        .doesNotContain("Stamped ")
        .containsOnlyOnce(strayLine);

    // Any project's install, run on its own, repairs what has changed since another one wrote it
    Path guide = skillsDir.resolve("hkj-guide/SKILL.md");
    String bundled = Files.readString(guide);
    Files.writeString(guide, "edited");
    Files.writeString(skillsDir.resolve(InstalledSkills.STAMP_FILE), "version=0.0.1\n");
    BuildResult repaired = runner(":lib:hkjInstallSkills", WARNINGS_FAIL).build();
    assertThat(repaired.task(":lib:hkjInstallSkills").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(repaired.getOutput()).contains("Installed 1 HKJ Claude Code skill file for");
    assertThat(guide).hasContent(bundled);
    assertThat(skillsDir.resolve(InstalledSkills.STAMP_FILE))
        .content()
        .contains("version=" + HKJPlugin.readPluginVersion());
    assertThat(libCopy).hasContent("old");
  }

  @Test
  @DisplayName("projects configured in isolation report unstamped skills once, and install once")
  void isolatedProjects_reportOnce_andInstallOnce() throws IOException {
    writeModules("", "a", "b", "c", "d");
    Files.createDirectories(testProjectDir.resolve(".claude/skills/hkj-guide"));

    assertThat(runner("help", "--parallel", ISOLATED_PROJECTS).build().getOutput())
        .containsOnlyOnce("do not say which Higher-Kinded-J release they describe");

    BuildResult install = runner("hkjInstallSkills", "--parallel", ISOLATED_PROJECTS).build();
    assertThat(install.getOutput())
        .containsOnlyOnce("Installed ")
        .doesNotContain("do not say which");
  }

  @Test
  @DisplayName(
      "a multi-project build warns once about skills of another release, unless it installs")
  void multiProjectBuild_warnsOnce_unlessItInstalls() throws IOException {
    writeModules("", "app", "lib");
    Path skillsDir = testProjectDir.resolve(".claude/skills");
    Files.createDirectories(skillsDir.resolve("hkj-guide"));
    Files.writeString(skillsDir.resolve(InstalledSkills.STAMP_FILE), "version=0.0.1\n");

    assertThat(runner("help", WARNINGS_FAIL).build().getOutput())
        .containsOnlyOnce("describe Higher-Kinded-J 0.0.1");

    // One project's install, run on its own, brings the copy up to date, so the build says nothing
    BuildResult install = runner(":lib:hkjInstallSkills", WARNINGS_FAIL).build();
    assertThat(install.getOutput()).doesNotContain("describe Higher-Kinded-J 0.0.1");
    assertThat(skillsDir.resolve(InstalledSkills.STAMP_FILE))
        .content()
        .contains("version=" + HKJPlugin.readPluginVersion());
  }

  @Test
  @DisplayName(
      "skills of another release give a warning on every build, until they are reinstalled")
  void skillsOfAnotherRelease_warnEveryBuild_untilReinstalled() throws IOException {
    writeBuildFile(
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }
        """);
    Path skillsDir = testProjectDir.resolve(".claude/skills");
    Files.createDirectories(skillsDir.resolve("hkj-guide"));
    Files.writeString(skillsDir.resolve(InstalledSkills.STAMP_FILE), "version=0.0.1\n");
    String release = HKJPlugin.readPluginVersion();
    String warning =
        "The HKJ Claude Code skills in "
            + rootSkills()
            + " describe Higher-Kinded-J 0.0.1, but this build's HKJ plugin is "
            + release;

    // The report comes once the build's tasks have run, so a build that reuses the
    // configuration cache gives it too
    assertThat(buildWithConfigurationCache("help").getOutput())
        .containsOnlyOnce(warning)
        .contains("Run the hkjInstallSkills task to install the " + release + " skills");
    assertThat(buildWithConfigurationCache("help").getOutput())
        .contains("Reusing configuration cache.")
        .containsOnlyOnce(warning);

    // The build that reinstalls them says nothing about the copy it replaces, nor does the next
    assertThat(runner("hkjInstallSkills").build().getOutput())
        .doesNotContain("HKJ Claude Code skills in");
    assertThat(buildWithConfigurationCache("help").getOutput())
        .doesNotContain("HKJ Claude Code skills in");
  }

  @Test
  @DisplayName("unstamped skills get a line suggesting a reinstall, unless the build installs them")
  void unstampedSkills_suggestReinstall_unlessTheBuildInstalls() throws IOException {
    String build =
        """
        plugins {
            id 'java'
            id 'io.github.higher-kinded-j.hkj'
        }

        hkj {
            version = '0.3.0'
            skills = %s
        }

        repositories {
            flatDir { dirs 'libs' }
        }
        """;
    writeEmptyHkjJars();
    Files.createDirectories(testProjectDir.resolve(".claude/skills/hkj-guide"));

    writeBuildFile(build.formatted(false));
    assertThat(runner("classes").build().getOutput())
        .containsOnlyOnce("do not say which Higher-Kinded-J release they describe")
        .contains("Run the hkjInstallSkills task");

    // A build with skills = true installs them before it compiles, so it has nothing to say
    writeBuildFile(build.formatted(true));
    assertThat(runner("classes").build().getOutput())
        .contains("Installed ")
        .doesNotContain("HKJ Claude Code skills in");
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
