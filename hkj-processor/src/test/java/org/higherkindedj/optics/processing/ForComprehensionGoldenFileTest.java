// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden file tests for verifying for-comprehension generated code stability.
 *
 * <p>These tests compare generated for-comprehension support code (Tuples, MonadicSteps,
 * FilterableSteps, and PathSteps) against known-good "golden" files. This helps detect unintended
 * changes in code generation.
 *
 * <p>Golden files are stored in {@code src/test/resources/golden/forcomp/} and are loaded as
 * classpath resources at runtime.
 *
 * <p>Note: the overall compilation will not succeed because generated code references types from
 * {@code hkj-core} which is not on the test classpath. However, the annotation processor still runs
 * and generates source files which can be extracted for comparison.
 *
 * <h3>Updating Golden Files</h3>
 *
 * <p>Run with: ./gradlew :hkj-processor:test --tests "*ForComprehensionGoldenFileTest*"
 * -DupdateGolden=true
 */
@DisplayName("For-Comprehension Golden File Tests")
class ForComprehensionGoldenFileTest {

  private static final String GOLDEN_RESOURCE_PATH = "/golden/forcomp/";

  // =============================================================================
  // Test Source
  // =============================================================================

  /** Package-info source with @GenerateForComprehensions trigger. */
  private static JavaFileObject packageInfoSource() {
    return JavaFileObjects.forSourceString(
        "org.higherkindedj.hkt.expression.package-info",
        """
        @GenerateForComprehensions(minArity = 6, maxArity = 8)
        package org.higherkindedj.hkt.expression;

        import org.higherkindedj.optics.annotations.GenerateForComprehensions;
        """);
  }

  // =============================================================================
  // Golden File Test Cases
  // =============================================================================

  record GoldenTestCase(String description, String generatedClassName, String goldenFileName) {
    @Override
    public String toString() {
      return description;
    }
  }

  static Stream<GoldenTestCase> goldenFileTestCases() {
    return Stream.of(
        // Tuple generators
        new GoldenTestCase(
            "Tuple6 record", "org.higherkindedj.hkt.tuple.Tuple6", "Tuple6.java.golden"),
        new GoldenTestCase(
            "Tuple7 record", "org.higherkindedj.hkt.tuple.Tuple7", "Tuple7.java.golden"),
        new GoldenTestCase(
            "Tuple8 record", "org.higherkindedj.hkt.tuple.Tuple8", "Tuple8.java.golden"),
        // For step generators
        new GoldenTestCase(
            "MonadicSteps6 (non-terminal)",
            "org.higherkindedj.hkt.expression.MonadicSteps6",
            "MonadicSteps6.java.golden"),
        new GoldenTestCase(
            "MonadicSteps8 (terminal)",
            "org.higherkindedj.hkt.expression.MonadicSteps8",
            "MonadicSteps8.java.golden"),
        new GoldenTestCase(
            "FilterableSteps6 (non-terminal)",
            "org.higherkindedj.hkt.expression.FilterableSteps6",
            "FilterableSteps6.java.golden"),
        new GoldenTestCase(
            "FilterableSteps8 (terminal)",
            "org.higherkindedj.hkt.expression.FilterableSteps8",
            "FilterableSteps8.java.golden"),
        // ForPath step generators (representative samples)
        new GoldenTestCase(
            "EitherPathSteps6 (non-filterable, extra type param)",
            "org.higherkindedj.hkt.expression.EitherPathSteps6",
            "EitherPathSteps6.java.golden"),
        new GoldenTestCase(
            "MaybePathSteps6 (filterable)",
            "org.higherkindedj.hkt.expression.MaybePathSteps6",
            "MaybePathSteps6.java.golden"),
        new GoldenTestCase(
            "GenericPathSteps6 (generic witness)",
            "org.higherkindedj.hkt.expression.GenericPathSteps6",
            "GenericPathSteps6.java.golden"));
  }

  // =============================================================================
  // Tests
  // =============================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("goldenFileTestCases")
  @DisplayName("Generated for-comprehension code matches golden file")
  void generatedCodeMatchesGolden(GoldenTestCase testCase) throws IOException {
    Compilation compilation = compile();

    // The generated source should exist even if overall compilation fails
    // (it fails because generated code references hkj-core types not on test classpath)
    Optional<JavaFileObject> generatedFile =
        compilation.generatedSourceFile(testCase.generatedClassName());
    assertThat(generatedFile)
        .as("Generated file should exist: %s", testCase.generatedClassName())
        .isPresent();

    String generated = generatedFile.get().getCharContent(true).toString();
    String golden = readGoldenFile(testCase.goldenFileName());

    assumeTrue(
        golden != null && !golden.contains("PLACEHOLDER"),
        "Golden file not initialised: "
            + testCase.goldenFileName()
            + ". Run with -DupdateGolden=true to generate.");

    assertThat(normalizeForComparison(generated))
        .as("Generated code should match golden file for %s", testCase.description())
        .isEqualTo(normalizeForComparison(golden));
  }

  /**
   * Updates golden files when run with -DupdateGolden=true.
   *
   * <p>Run with: ./gradlew :hkj-processor:test --tests "*ForComprehensionGoldenFileTest*"
   * -DupdateGolden=true
   */
  @Test
  @EnabledIfSystemProperty(named = "updateGolden", matches = "true")
  @DisplayName("Update for-comprehension golden files (run with -DupdateGolden=true)")
  void updateGoldenFiles() throws IOException {
    Path goldenDir = Path.of("src/test/resources/golden/forcomp");
    if (!Files.exists(goldenDir)) {
      goldenDir = Path.of("hkj-processor/src/test/resources/golden/forcomp");
    }
    if (!Files.exists(goldenDir)) {
      Files.createDirectories(goldenDir);
      System.out.println("Created golden directory: " + goldenDir.toAbsolutePath());
    }

    Compilation compilation = compile();

    for (GoldenTestCase testCase : goldenFileTestCases().toList()) {
      Optional<JavaFileObject> generatedFile =
          compilation.generatedSourceFile(testCase.generatedClassName());
      if (generatedFile.isEmpty()) {
        System.err.println("Generated file not found: " + testCase.generatedClassName());
        continue;
      }

      String generated = generatedFile.get().getCharContent(true).toString();
      Path goldenPath = goldenDir.resolve(testCase.goldenFileName());
      Files.writeString(goldenPath, generated, StandardCharsets.UTF_8);
      System.out.println("Updated: " + testCase.goldenFileName());
    }
  }

  // =============================================================================
  // Helper Methods
  // =============================================================================

  private Compilation compile() {
    return javac().withProcessors(new ForComprehensionProcessor()).compile(packageInfoSource());
  }

  private String readGoldenFile(String fileName) throws IOException {
    try (InputStream is = getClass().getResourceAsStream(GOLDEN_RESOURCE_PATH + fileName)) {
      if (is == null) {
        return null;
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String normalizeForComparison(String source) {
    return source.replace("\r\n", "\n").replaceAll("[ \t]+\n", "\n").trim();
  }
}
