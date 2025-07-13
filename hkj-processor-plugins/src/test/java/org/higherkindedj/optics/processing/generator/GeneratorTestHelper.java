// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.testing.compile.Compilation;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;

public final class GeneratorTestHelper {

  /**
   * Asserts that the generated file contains the expected code snippet, after normalising both for
   * whitespace and class name qualifications.
   *
   * @param compilation The result from the compiler.
   * @param generatedFileName The fully qualified name of the generated file.
   * @param expectedCode The expected code snippet.
   */
  public static void assertGeneratedCodeContains(
      final Compilation compilation, final String generatedFileName, final String expectedCode) {

    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return; // fail() will throw, but compiler needs this for flow analysis
    }

    try {
      // Correctly read the content from the JavaFileObject
      final String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();

      final String normalizedActual = normaliseCode(actualGeneratedCode);
      final String normalizedExpected = normaliseCode(expectedCode);

      assertTrue(
          normalizedActual.contains(normalizedExpected),
          String.format(
              "Expected generated code to contain:%n---%n%s%n---%nBut was:%n---%n%s%n---",
              normalizedExpected, normalizedActual));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  /**
   * Normalizes a Java code string by: 1. Removing package and import statements. 2. Removing all
   * comments. 3. Collapsing all sequential whitespace characters (including newlines) into a single
   * space.
   *
   * @param code The code string to normalize.
   * @return A normalised version of the code.
   */
  private static String normaliseCode(final String code) {
    return code
        // Remove package and import statements
        .replaceAll("package [\\w.]+;\\s*", "")
        .replaceAll("import [\\w.]+;\\s*", "")
        // Remove all block and line comments
        .replaceAll("/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/", "")
        .replaceAll("//.*", "")
        // Collapse all whitespace (including newlines and tabs) into a single space
        .replaceAll("\\s+", " ")
        .trim();
  }
}
