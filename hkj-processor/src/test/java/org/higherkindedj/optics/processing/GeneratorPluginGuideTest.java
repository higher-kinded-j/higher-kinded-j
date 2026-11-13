// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds the generator plugin guide's example the way the guide's own build does: the generator and
 * its {@code module-info.java} compiled as a module against hkj-processor's published API, with
 * Avaje's SPI processor writing the service file and checking the {@code provides} clause. The
 * {@code pluginGuideTest} task passes the author's module path in {@code
 * hkj.pluginAuthorModulePath}, their processor path in {@code hkj.pluginAuthorProcessorPath}, and
 * the guide's path in {@code hkj.pluginGuide}, so a type the SPI uses but does not export fails
 * here, not in the author's build.
 *
 * <p>Each code block is found by the bold step line above it, so the guide and the test cannot
 * drift apart.
 */
@DisplayName("The generator plugin guide's example")
class GeneratorPluginGuideTest {

  private static final String GENERATOR = "com.example.generator.NonEmptyListGenerator";

  /** A quoted {@code group:artefact:version} in a build block, capturing its group and artefact. */
  private static final Pattern COORDINATE = Pattern.compile("\"([^\":]+:[^\":]+):[^\"]+\"");

  @Test
  @DisplayName(
      "compiles as a module against hkj-processor's API alone, and registers its generator")
  void compilesAgainstThePublishedApi(@TempDir Path dir) throws IOException {
    String guide = guide();
    Path moduleInfo =
        write(
            dir.resolve("src/module-info.java"),
            blockAfter(guide, "**2. Add the module-info.java provides clause:**", "java"));
    Path generator =
        write(
            dir.resolve("src/com/example/generator/NonEmptyListGenerator.java"),
            blockAfter(guide, "**1. Create the generator class:**", "java"));
    Path classes = dir.resolve("classes");

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean compiled;
    try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
      // A named module never reads the class path, but in-process javac would set it to this
      // test's own, which holds all of HKJ; empty, it holds only what the author's build gives.
      files.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of());
      List<String> options =
          List.of(
              "--module-path",
              required("hkj.pluginAuthorModulePath"),
              "--processor-path",
              required("hkj.pluginAuthorProcessorPath"),
              "-Werror",
              "-d",
              classes.toString());
      compiled =
          javac
              .getTask(
                  null,
                  files,
                  diagnostics,
                  options,
                  null,
                  files.getJavaFileObjectsFromPaths(List.of(moduleInfo, generator)))
              .call();
    }

    // Avaje reports what it registers as notes; anything louder is a fault in the example.
    assertThat(diagnostics.getDiagnostics())
        .filteredOn(diagnostic -> diagnostic.getKind() != Diagnostic.Kind.NOTE)
        .isEmpty();
    assertThat(compiled).isTrue();
    assertThat(
            Files.readAllLines(
                classes.resolve(
                    "META-INF/services/org.higherkindedj.optics.processing.spi.TraversableGenerator")))
        .contains(GENERATOR);
  }

  @Test
  @DisplayName("is built with hkj-processor and the Avaje SPI alone")
  void buildsWithHkjProcessorAndAvajeAlone() {
    String build =
        blockAfter(guide(), "**3. Add hkj-processor and Avaje SPI to your build:**", "kotlin");

    assertThat(COORDINATE.matcher(build).results().map(coordinate -> coordinate.group(1)))
        .containsOnly("io.github.higher-kinded-j:hkj-processor", "io.avaje:avaje-spi-service");
  }

  /** The guide's text, with its line endings as a Unix checkout writes them. */
  private static String guide() {
    try {
      return Files.readString(Path.of(required("hkj.pluginGuide"))).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new IllegalStateException("The plugin guide cannot be read", e);
    }
  }

  /** The body of the first {@code language} code block after the line {@code marker}. */
  private static String blockAfter(String page, String marker, String language) {
    int at = page.indexOf(marker);
    assertThat(at).as("the guide's line %s", marker).isNotNegative();
    String fence = "```" + language + "\n";
    int open = page.indexOf(fence, at);
    assertThat(open).as("a %s block after the guide's line %s", language, marker).isNotNegative();
    int start = open + fence.length();
    int end = page.indexOf("```", start);
    assertThat(end)
        .as("the end of the %s block after the guide's line %s", language, marker)
        .isNotNegative();
    return page.substring(start, end);
  }

  /** A path the {@code pluginGuideTest} task passes; run elsewhere, the test says so. */
  private static String required(String property) {
    String value = System.getProperty(property);
    if (value == null) {
      throw new IllegalStateException(
          "System property '%s' is not set. Run this via Gradle: `gradle :hkj-processor:pluginGuideTest`."
              .formatted(property));
    }
    return value;
  }

  private static Path write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    return Files.writeString(file, content);
  }
}
