// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.testing.compile.Compilation;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;

public final class GeneratorTestHelper {

  /**
   * Asserts that the generated file contains the expected raw text (without normalization). This is
   * useful for checking Javadoc content and other documentation that would be stripped by the
   * normalizer.
   *
   * @param compilation The result from the compiler.
   * @param generatedFileName The fully qualified name of the generated file.
   * @param expectedText The expected text to find.
   */
  public static void assertGeneratedCodeContainsRaw(
      final Compilation compilation, final String generatedFileName, final String expectedText) {

    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return;
    }

    try {
      final String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();

      assertTrue(
          actualGeneratedCode.contains(expectedText),
          String.format(
              "Expected generated code to contain (raw):%n---%n%s%n---%nBut was:%n---%n%s%n---",
              expectedText, actualGeneratedCode));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  /**
   * Asserts that the generated file contains the expected code snippet, after normalizing both for
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
      return;
    }

    try {
      final String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();
      final String normalizedActual = normalizeCode(actualGeneratedCode);
      final String normalizedExpected = normalizeCode(expectedCode);

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
   * Normalizes a Java code string by: 1. Removing package and import statements. 2. Removing
   * comments. 3. Replacing fully qualified names with simple names for common types. 4. Removing
   * ALL whitespace.
   *
   * @param code The code string to normalize.
   * @return A normalized version of the code with no whitespace.
   */
  private static String normalizeCode(final String code) {
    String normalized =
        code
            // Remove package and import statements
            .replaceAll("package [\\w.]+;\\s*", "")
            .replaceAll("import [\\w.]+;\\s*", "")
            // Remove all block and line comments
            .replaceAll("/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/", "")
            .replaceAll("//.*", "");

    // Normalize fully qualified class names to simple names
    normalized =
        normalized
            .replaceAll("java\\.util\\.Optional", "Optional")
            .replaceAll("java\\.util\\.function\\.Function", "Function")
            .replaceAll("org\\.higherkindedj\\.optics\\.Prism", "Prism")
            .replaceAll("org\\.higherkindedj\\.optics\\.Lens", "Lens")
            .replaceAll("org\\.higherkindedj\\.optics\\.Traversal", "Traversal")
            .replaceAll("org\\.higherkindedj\\.optics\\.util\\.Traversals", "Traversals")
            .replaceAll("org\\.higherkindedj\\.optics\\.focus\\.FocusPath", "FocusPath")
            .replaceAll("org\\.higherkindedj\\.optics\\.focus\\.AffinePath", "AffinePath")
            .replaceAll("org\\.higherkindedj\\.optics\\.focus\\.TraversalPath", "TraversalPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.Applicative", "Applicative")
            // Test-specific types
            .replaceAll("com\\.example\\.Shape", "Shape")
            .replaceAll("com\\.example\\.User", "User")
            .replaceAll("com\\.example\\.Address", "Address")
            .replaceAll("com\\.example\\.Company", "Company")
            .replaceAll("com\\.example\\.Department", "Department")
            .replaceAll("com\\.example\\.Team", "Team")
            .replaceAll("com\\.example\\.Level1", "Level1")
            .replaceAll("com\\.example\\.Level2", "Level2")
            .replaceAll("com\\.example\\.Level3", "Level3")
            .replaceAll("com\\.example\\.Playlist", "Playlist")
            .replaceAll("com\\.example\\.model\\.Person", "Person")
            .replaceAll("com\\.example\\.Wrapper", "Wrapper");

    // Remove ALL whitespace to make the comparison robust.
    return normalized.replaceAll("\\s+", "").trim();
  }
}
