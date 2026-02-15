// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden file tests for verifying generated code stability.
 *
 * <p>These tests compare generated optics code against known-good "golden" files. This helps detect
 * unintended changes in code generation and ensures the output remains stable.
 *
 * <p>Golden files are stored in {@code src/test/resources/golden/} and are loaded as classpath
 * resources at runtime.
 *
 * <h3>Updating Golden Files</h3>
 *
 * <p>When intentional changes are made to the code generator, run:
 *
 * <pre>
 * ./gradlew :hkj-processor:updateGoldenFiles
 * </pre>
 *
 * <p>This regenerates all golden files with the current output.
 */
@DisplayName("Golden File Tests")
class GoldenFileTest {

  private static final String GOLDEN_RESOURCE_PATH = "/golden/";

  // =============================================================================
  // Test Source Types
  // =============================================================================

  /** Simple record for lens generation */
  private static JavaFileObject customerRecord() {
    return JavaFileObjects.forSourceString(
        "com.test.Customer",
        """
        package com.test;

        public record Customer(String id, String name, String email, int loyaltyPoints) {}
        """);
  }

  /** Sealed interface for prism generation */
  private static JavaFileObject shapeSealed() {
    return JavaFileObjects.forSourceString(
        "com.test.Shape",
        """
        package com.test;

        public sealed interface Shape permits Circle, Rectangle {}
        """);
  }

  private static JavaFileObject circleRecord() {
    return JavaFileObjects.forSourceString(
        "com.test.Circle",
        """
        package com.test;

        public record Circle(double radius) implements Shape {}
        """);
  }

  private static JavaFileObject rectangleRecord() {
    return JavaFileObjects.forSourceString(
        "com.test.Rectangle",
        """
        package com.test;

        public record Rectangle(double width, double height) implements Shape {}
        """);
  }

  /** Enum for prism generation */
  private static JavaFileObject statusEnum() {
    return JavaFileObjects.forSourceString(
        "com.test.Status",
        """
        package com.test;

        public enum Status {
            PENDING, ACTIVE, COMPLETED, CANCELLED
        }
        """);
  }

  /** Generic record for generic lens generation */
  private static JavaFileObject pairRecord() {
    return JavaFileObjects.forSourceString(
        "com.test.Pair",
        """
        package com.test;

        public record Pair<A, B>(A first, B second) {}
        """);
  }

  /** Record with nested types for complex lens generation */
  private static JavaFileObject nestedRecord() {
    return JavaFileObjects.forSourceString(
        "com.test.Nested",
        """
        package com.test;

        import java.util.List;
        import java.util.Optional;

        public record Nested(String name, Optional<String> description, List<String> tags) {}
        """);
  }

  private static JavaFileObject packageInfo(String... typeNames) {
    StringBuilder imports = new StringBuilder();
    StringBuilder classes = new StringBuilder();

    for (int i = 0; i < typeNames.length; i++) {
      String typeName = typeNames[i];
      imports.append("import ").append(typeName).append(";\n");
      if (i > 0) classes.append(", ");
      classes.append(typeName.substring(typeName.lastIndexOf('.') + 1)).append(".class");
    }

    return JavaFileObjects.forSourceString(
        "com.test.optics.package-info",
        String.format(
            """
            @ImportOptics({%s})
            package com.test.optics;

            import org.higherkindedj.optics.annotations.ImportOptics;
            %s
            """,
            classes, imports));
  }

  // =============================================================================
  // Golden File Test Cases
  // =============================================================================

  record GoldenTestCase(
      String description,
      String generatedClassName,
      String goldenFileName,
      JavaFileObject... sources) {
    @Override
    public String toString() {
      return description;
    }
  }

  static Stream<GoldenTestCase> goldenFileTestCases() {
    return Stream.of(
        new GoldenTestCase(
            "Record lenses",
            "com.test.optics.CustomerLenses",
            "CustomerLenses.java.golden",
            customerRecord(),
            packageInfo("com.test.Customer")),
        new GoldenTestCase(
            "Sealed interface prisms",
            "com.test.optics.ShapePrisms",
            "ShapePrisms.java.golden",
            shapeSealed(),
            circleRecord(),
            rectangleRecord(),
            packageInfo("com.test.Shape")),
        new GoldenTestCase(
            "Enum prisms",
            "com.test.optics.StatusPrisms",
            "StatusPrisms.java.golden",
            statusEnum(),
            packageInfo("com.test.Status")),
        new GoldenTestCase(
            "Generic record lenses",
            "com.test.optics.PairLenses",
            "PairLenses.java.golden",
            pairRecord(),
            packageInfo("com.test.Pair")),
        new GoldenTestCase(
            "Nested types record lenses",
            "com.test.optics.NestedLenses",
            "NestedLenses.java.golden",
            nestedRecord(),
            packageInfo("com.test.Nested")));
  }

  // =============================================================================
  // Tests
  // =============================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("goldenFileTestCases")
  @DisplayName("Generated code matches golden file")
  void generatedCodeMatchesGolden(GoldenTestCase testCase) throws IOException {
    Compilation compilation = compile(testCase.sources());
    assertThat(compilation.status())
        .as("Compilation should succeed for %s", testCase.description())
        .isEqualTo(Compilation.Status.SUCCESS);

    String generated = getGeneratedSource(compilation, testCase.generatedClassName());

    // When run with -DupdateGolden=true, update the golden file instead of comparing
    if ("true".equals(System.getProperty("updateGolden"))) {
      Path goldenDir = Path.of("src/test/resources/golden");
      if (!Files.exists(goldenDir)) {
        goldenDir = Path.of("hkj-processor/src/test/resources/golden");
      }
      if (!Files.exists(goldenDir)) {
        Files.createDirectories(goldenDir);
      }
      Path goldenPath = goldenDir.resolve(testCase.goldenFileName());
      Files.writeString(goldenPath, generated, StandardCharsets.UTF_8);
      System.out.println("Updated: " + testCase.goldenFileName());
      return;
    }

    String golden = readGoldenFile(testCase.goldenFileName());

    if (golden == null || golden.contains("PLACEHOLDER")) {
      fail(
          "Golden file not initialised: "
              + testCase.goldenFileName()
              + ". Run: ./gradlew updateGoldenFiles");
    }

    assertThat(normalizeForComparison(generated))
        .as("Generated code should match golden file for %s", testCase.description())
        .isEqualTo(normalizeForComparison(golden));
  }

  // =============================================================================
  // Helper Methods
  // =============================================================================

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new ImportOpticsProcessor()).compile(sources);
  }

  private String getGeneratedSource(Compilation compilation, String className) {
    Optional<JavaFileObject> generated = compilation.generatedSourceFile(className);
    if (generated.isEmpty()) {
      throw new AssertionError("Generated source file not found: " + className);
    }
    try {
      return generated.get().getCharContent(true).toString();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read generated source: " + className, e);
    }
  }

  private String readGoldenFile(String fileName) throws IOException {
    try (InputStream is = getClass().getResourceAsStream(GOLDEN_RESOURCE_PATH + fileName)) {
      if (is == null) {
        return null; // File not found
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /**
   * Normalizes source code for comparison by:
   *
   * <ul>
   *   <li>Converting line endings to LF
   *   <li>Removing trailing whitespace from lines
   *   <li>Trimming leading/trailing whitespace from the file
   * </ul>
   */
  private String normalizeForComparison(String source) {
    return source.replace("\r\n", "\n").replaceAll("[ \t]+\n", "\n").trim();
  }
}
