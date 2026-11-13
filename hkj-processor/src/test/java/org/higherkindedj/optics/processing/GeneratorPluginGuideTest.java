// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * Compiles the generator plugin guide's example the way a plugin author's build does: as a module,
 * against hkj-processor's published API and the dependencies the guide's build snippet lists. The
 * build passes that module path in {@code hkj.pluginAuthorModulePath}, and the guide's path in
 * {@code hkj.pluginGuide}, so a type the SPI uses but does not export fails here, not in the
 * author's build.
 */
@DisplayName("The generator plugin guide's example")
class GeneratorPluginGuideTest {

  @Test
  @DisplayName("compiles as a module against hkj-processor's API alone")
  void compilesAgainstThePublishedApi(@TempDir Path dir) throws IOException {
    String guide = Files.readString(Path.of(System.getProperty("hkj.pluginGuide")));
    Path moduleInfo =
        write(
            dir.resolve("src/module-info.java"),
            javaBlockAfter(guide, "**2. Add the module-info.java provides clause:**"));
    Path generator =
        write(
            dir.resolve("src/com/example/generator/NonEmptyListGenerator.java"),
            javaBlockAfter(guide, "**1. Create the generator class:**"));

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean compiled;
    try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, null)) {
      // In-process javac would otherwise take this test's own classpath as the author's.
      files.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of());
      List<String> options =
          List.of(
              "--module-path",
              System.getProperty("hkj.pluginAuthorModulePath"),
              "-proc:none",
              "-Werror",
              "-d",
              dir.resolve("classes").toString());
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

    assertThat(diagnostics.getDiagnostics()).isEmpty();
    assertThat(compiled).isTrue();
  }

  /** The body of the first {@code java} code block after the line {@code marker}. */
  private static String javaBlockAfter(String page, String marker) {
    int at = page.indexOf(marker);
    assertThat(at).as("the guide's line %s", marker).isNotNegative();
    String fence = "```java\n";
    int start = page.indexOf(fence, at) + fence.length();
    return page.substring(start, page.indexOf("```", start));
  }

  private static Path write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    return Files.writeString(file, content);
  }
}
