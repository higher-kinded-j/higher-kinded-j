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
 * Golden file tests for the per-record assembly companions (issue #586). Goldens live in {@code
 * src/test/resources/golden/assemblycompanion/}; refresh with {@code ./gradlew
 * :hkj-processor:updateGoldenFiles}.
 */
@DisplayName("Assembly Companion Golden File Tests")
class AssemblyGoldenFileTest {

  private static final String GOLDEN_RESOURCE_PATH = "/golden/assemblycompanion/";

  record GoldenTestCase(String description, String generatedClassName, String goldenFileName) {
    @Override
    public String toString() {
      return description;
    }
  }

  static Stream<GoldenTestCase> goldenFileTestCases() {
    return Stream.of(
        new GoldenTestCase(
            "UserAssembly (3 components, primitive boxing)",
            "com.example.UserAssembly",
            "UserAssembly.java.golden"),
        new GoldenTestCase(
            "Wide13Assembly (beyond the Tuple/Function ceiling)",
            "com.example.Wide13Assembly",
            "Wide13Assembly.java.golden"),
        new GoldenTestCase(
            "CustomerAssembly (component typed as another record)",
            "com.example.CustomerAssembly",
            "CustomerAssembly.java.golden"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("goldenFileTestCases")
  @DisplayName("Generated companion matches golden file")
  void generatedCodeMatchesGolden(GoldenTestCase testCase) throws IOException {
    Compilation compilation = compile();

    Optional<JavaFileObject> generatedFile =
        compilation.generatedSourceFile(testCase.generatedClassName());
    assertThat(generatedFile)
        .as("Generated file should exist: %s", testCase.generatedClassName())
        .isPresent();

    String generated = generatedFile.get().getCharContent(true).toString();

    if ("true".equals(System.getProperty("updateGolden"))) {
      // Resolve against whichever working directory this runs from; the parent "golden" dir
      // ships in-tree, so its presence identifies the right base.
      Path goldenDir = Path.of("src/test/resources/golden/assemblycompanion");
      if (!Files.exists(goldenDir.getParent())) {
        goldenDir = Path.of("hkj-processor/src/test/resources/golden/assemblycompanion");
      }
      Files.createDirectories(goldenDir);
      Files.writeString(
          goldenDir.resolve(testCase.goldenFileName()), generated, StandardCharsets.UTF_8);
      System.out.println("Updated: " + testCase.goldenFileName());
      return;
    }

    String golden = readGoldenFile(testCase.goldenFileName());
    if (golden == null) {
      fail(
          "Golden file not initialised: "
              + testCase.goldenFileName()
              + ". Run: ./gradlew :hkj-processor:updateGoldenFiles");
    }
    assertThat(normalize(generated)).isEqualTo(normalize(golden));
  }

  private Compilation compile() {
    StringBuilder comps = new StringBuilder();
    for (int i = 1; i <= 13; i++) {
      comps.append(i > 1 ? ", " : "").append("String f").append(i);
    }
    return javac()
        .withProcessors(new AssemblyProcessor())
        .compile(
            JavaFileObjects.forSourceString(
                "com.example.User",
                """
                package com.example;
                import org.higherkindedj.optics.annotations.GenerateAssembly;
                @GenerateAssembly
                public record User(String name, String email, int age) {}
                """),
            JavaFileObjects.forSourceString(
                "com.example.Wide13",
                """
                package com.example;
                import org.higherkindedj.optics.annotations.GenerateAssembly;
                @GenerateAssembly
                public record Wide13(%s) {}
                """
                    .formatted(comps)),
            JavaFileObjects.forSourceString(
                "com.example.Address",
                """
                package com.example;
                import org.higherkindedj.optics.annotations.GenerateAssembly;
                @GenerateAssembly
                public record Address(String street, String zip) {}
                """),
            JavaFileObjects.forSourceString(
                "com.example.Customer",
                """
                package com.example;
                import org.higherkindedj.optics.annotations.GenerateAssembly;
                @GenerateAssembly
                public record Customer(String name, Address address) {}
                """));
  }

  private String readGoldenFile(String fileName) throws IOException {
    try (InputStream is = getClass().getResourceAsStream(GOLDEN_RESOURCE_PATH + fileName)) {
      return is == null ? null : new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String normalize(String source) {
    return source.replace("\r\n", "\n").replaceAll("[ \t]+\n", "\n").trim();
  }
}
