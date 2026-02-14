// Copyright (c) 2025 - 2026 Magnus Smith
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
   * Asserts that the generated file contains the expected raw text (without normalisation). This is
   * useful for checking Javadoc content and other documentation that would be stripped by the
   * normaliser.
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
      final String normalisedActual = normaliseCode(actualGeneratedCode);
      final String normalisedExpected = normaliseCode(expectedCode);

      assertTrue(
          normalisedActual.contains(normalisedExpected),
          String.format(
              "Expected generated code to contain:%n---%n%s%n---%nBut was:%n---%n%s%n---",
              normalisedExpected, normalisedActual));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  /**
   * Normalises a Java code string by: 1. Removing package and import statements. 2. Removing
   * comments. 3. Replacing fully qualified names with simple names for common types. 4. Removing
   * ALL whitespace.
   *
   * @param code The code string to normalise.
   * @return A normalised version of the code with no whitespace.
   */
  private static String normaliseCode(final String code) {
    String normalised =
        code
            // Remove package and import statements
            .replaceAll("package [\\w.]+;\\s*", "")
            .replaceAll("import [\\w.]+;\\s*", "")
            // Remove all block and line comments
            .replaceAll("/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/", "")
            .replaceAll("//.*", "");

    // Normalise fully qualified class names to simple names
    normalised =
        normalised
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
            .replaceAll("com\\.example\\.Wrapper", "Wrapper")
            // External types for ImportOptics tests
            .replaceAll("com\\.external\\.Customer", "Customer")
            .replaceAll("com\\.external\\.Pair", "Pair")
            .replaceAll("com\\.external\\.Point", "Point")
            .replaceAll("com\\.external\\.Order", "Order")
            .replaceAll("com\\.external\\.Item", "Item")
            .replaceAll("com\\.external\\.Product", "Product")
            .replaceAll("com\\.external\\.Shape", "Shape")
            .replaceAll("com\\.external\\.Circle", "Circle")
            .replaceAll("com\\.external\\.Rectangle", "Rectangle")
            .replaceAll("com\\.external\\.Status", "Status")
            .replaceAll("com\\.external\\.HttpStatus", "HttpStatus")
            .replaceAll("com\\.external\\.ImmutableDate", "ImmutableDate")
            .replaceAll("com\\.external\\.MutablePerson", "MutablePerson")
            .replaceAll("com\\.external\\.PlainClass", "PlainClass")
            // Spec interface test types
            .replaceAll("com\\.external\\.Person", "Person")
            .replaceAll("com\\.external\\.Config", "Config")
            .replaceAll("com\\.external\\.Request", "Request")
            .replaceAll("com\\.external\\.LocalDate", "LocalDate")
            .replaceAll("com\\.external\\.PaymentMethod", "PaymentMethod")
            .replaceAll("com\\.external\\.CreditCard", "CreditCard")
            .replaceAll("com\\.external\\.BankTransfer", "BankTransfer")
            .replaceAll("com\\.external\\.JsonNode", "JsonNode")
            .replaceAll("com\\.external\\.ArrayNode", "ArrayNode")
            .replaceAll("com\\.external\\.ObjectNode", "ObjectNode")
            .replaceAll("com\\.external\\.Team", "Team")
            .replaceAll("com\\.external\\.Simple", "Simple")
            .replaceAll("com\\.external\\.Base", "Base")
            .replaceAll("com\\.external\\.Sub", "Sub")
            .replaceAll("com\\.external\\.Data", "Data")
            .replaceAll("com\\.external\\.Animal", "Animal")
            .replaceAll("com\\.external\\.Plant", "Plant");

    // Remove ALL whitespace to make the comparison robust.
    return normalised.replaceAll("\\s+", "").trim();
  }
}
