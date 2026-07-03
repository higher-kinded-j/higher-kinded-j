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
 * Golden file tests for verifying accumulator-stage generated code stability (issue #581).
 *
 * <p>These tests compare generated assembly stage classes ({@code ValidatedAccumN}, {@code
 * ValidatedFieldsN}, {@code ValidationPathAccumN}, {@code ValidationPathFieldsN}, {@code
 * EitherOrBothAccumN}, {@code EitherOrBothFieldsN}) against known-good "golden" files, detecting
 * unintended changes in code generation.
 *
 * <p>Golden files are stored in {@code src/test/resources/golden/assembly/} and are loaded as
 * classpath resources at runtime.
 *
 * <h3>Updating Golden Files</h3>
 *
 * <p>Run with: ./gradlew :hkj-processor:updateGoldenFiles
 */
@DisplayName("Accumulator Golden File Tests")
class AccumulatorGoldenFileTest {

  private static final String GOLDEN_RESOURCE_PATH = "/golden/assembly/";

  // =============================================================================
  // Test Source
  // =============================================================================

  /** Package-info source with the @GenerateAccumulators trigger. */
  private static JavaFileObject packageInfoSource() {
    return JavaFileObjects.forSourceString(
        "com.example.trigger.package-info",
        """
        @GenerateAccumulators(minArity = 1, maxArity = 12)
        package com.example.trigger;

        import org.higherkindedj.optics.annotations.GenerateAccumulators;
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
        // Validated family: entry arity, a mid arity, and the terminal arity
        goldenCase("ValidatedAccum2"),
        goldenCase("ValidatedAccum6"),
        goldenCase("ValidatedAccum12"),
        goldenCase("ValidatedFields1"),
        goldenCase("ValidatedFields2"),
        goldenCase("ValidatedFields12"),
        // ValidationPath family
        goldenCase("ValidationPathAccum2"),
        goldenCase("ValidationPathFields6"),
        // EitherOrBoth family
        goldenCase("EitherOrBothAccum6"),
        goldenCase("EitherOrBothFields2"));
  }

  private static GoldenTestCase goldenCase(String simpleName) {
    return new GoldenTestCase(
        simpleName, packageFor(simpleName) + simpleName, simpleName + ".java.golden");
  }

  /** Each family lands in its carrier's package (see AccumulatorStepGenerator). */
  private static String packageFor(String simpleName) {
    if (simpleName.startsWith("ValidationPath")) {
      return "org.higherkindedj.hkt.effect.";
    }
    if (simpleName.startsWith("Validated")) {
      return "org.higherkindedj.hkt.validated.";
    }
    return "org.higherkindedj.hkt.eitherorboth.";
  }

  // =============================================================================
  // Tests
  // =============================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("goldenFileTestCases")
  @DisplayName("Generated accumulator code matches golden file")
  void generatedCodeMatchesGolden(GoldenTestCase testCase) throws IOException {
    Compilation compilation = compile();

    Optional<JavaFileObject> generatedFile =
        compilation.generatedSourceFile(testCase.generatedClassName());
    assertThat(generatedFile)
        .as("Generated file should exist: %s", testCase.generatedClassName())
        .isPresent();

    String generated = generatedFile.get().getCharContent(true).toString();

    // When run with -DupdateGolden=true, update the golden file instead of comparing
    if ("true".equals(System.getProperty("updateGolden"))) {
      // Resolve against whichever working directory this runs from (module dir or repo root);
      // the parent "golden" dir ships in-tree, so its presence identifies the right base.
      Path goldenDir = Path.of("src/test/resources/golden/assembly");
      if (!Files.exists(goldenDir.getParent())) {
        goldenDir = Path.of("hkj-processor/src/test/resources/golden/assembly");
      }
      Files.createDirectories(goldenDir);
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
              + ". Run: ./gradlew :hkj-processor:updateGoldenFiles");
    }

    assertThat(normalizeForComparison(generated))
        .as("Generated code should match golden file for %s", testCase.description())
        .isEqualTo(normalizeForComparison(golden));
  }

  // =============================================================================
  // Helper Methods
  // =============================================================================

  private Compilation compile() {
    return javac().withProcessors(new AccumulatorProcessor()).compile(packageInfoSource());
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
