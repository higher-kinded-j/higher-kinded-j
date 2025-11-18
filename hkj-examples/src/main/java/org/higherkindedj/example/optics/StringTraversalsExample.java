// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.stream.Collectors;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.StringTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates string traversals for declarative text processing using {@link StringTraversals}.
 *
 * <p>String traversals provide composable, type-safe text manipulation by breaking strings into
 * traversable units:
 *
 * <ul>
 *   <li>{@code chars()} - Focus on individual characters
 *   <li>{@code worded()} - Focus on whitespace-delimited words
 *   <li>{@code lined()} - Focus on line-separated content
 * </ul>
 *
 * <p>This example covers:
 *
 * <ul>
 *   <li>Email normalisation for data consistency
 *   <li>Log file filtering and transformation
 *   <li>CSV data processing
 *   <li>Configuration file sanitisation
 *   <li>Character-level validation and transformation
 * </ul>
 */
public class StringTraversalsExample {

  public static void main(String[] args) {
    System.out.println("=== STRING TRAVERSALS EXAMPLE ===\n");

    demonstrateCharacterTraversals();
    demonstrateWordTraversals();
    demonstrateLineTraversals();
    demonstrateRealWorldScenarios();

    System.out.println("\n=== STRING TRAVERSALS COMPLETE ===");
  }

  private static void demonstrateCharacterTraversals() {
    System.out.println("--- SCENARIO 1: Character-Level Operations ---\n");

    // Basic character transformation
    Traversal<String, Character> chars = StringTraversals.chars();

    String input = "Hello World";
    String uppercased = Traversals.modify(chars, Character::toUpperCase, input);
    System.out.println("Original:    " + input);
    System.out.println("Uppercased:  " + uppercased);

    // Selective character transformation using filtered
    Traversal<String, Character> vowels = chars.filtered(c -> "aeiouAEIOU".indexOf(c) >= 0);

    String vowelsUppercased = Traversals.modify(vowels, Character::toUpperCase, input);
    System.out.println("Vowels only: " + vowelsUppercased);

    // Character frequency analysis
    String text = "banana";
    List<Character> allChars = Traversals.getAll(chars, text);
    System.out.println("\nCharacters in '" + text + "': " + allChars);
    System.out.println(
        "Character 'a' frequency: " + allChars.stream().filter(c -> c == 'a').count() + " times");

    // ROT13 cipher demonstration
    Traversal<String, Character> letters = chars.filtered(c -> Character.isLetter(c));

    String secret = "hello";
    String encoded =
        Traversals.modify(
            letters,
            c -> {
              if (Character.isLowerCase(c)) {
                return (char) ('a' + (c - 'a' + 13) % 26);
              } else {
                return (char) ('A' + (c - 'A' + 13) % 26);
              }
            },
            secret);
    System.out.println("\nROT13 encoding:");
    System.out.println("Original: " + secret);
    System.out.println("Encoded:  " + encoded);
    System.out.println();
  }

  private static void demonstrateWordTraversals() {
    System.out.println("--- SCENARIO 2: Word-Level Operations ---\n");

    Traversal<String, String> words = StringTraversals.worded();

    // Title case formatting
    String rawTitle = "functional programming in java";
    String titleCase =
        Traversals.modify(
            words,
            word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase(),
            rawTitle);
    System.out.println("Raw title:    " + rawTitle);
    System.out.println("Title case:   " + titleCase);

    // Email domain normalisation
    String email = "  User.Name@EXAMPLE.COM  ";
    String normalised = Traversals.modify(words, String::toLowerCase, email.trim());
    System.out.println("\nEmail normalisation:");
    System.out.println("Input:       '" + email + "'");
    System.out.println("Normalised:  '" + normalised + "'");

    // Word filtering and extraction
    String sentence = "The quick brown fox jumps over the lazy dog";
    Traversal<String, String> longWords = words.filtered(w -> w.length() > 4);

    List<String> extracted = Traversals.getAll(longWords, sentence);
    System.out.println("\nLong words (>4 chars) in: " + sentence);
    System.out.println("Result: " + extracted);

    // Emphasis transformation
    String emphasised = Traversals.modify(longWords, String::toUpperCase, sentence);
    System.out.println("Emphasised: " + emphasised);

    // Whitespace normalisation
    String messyText = "  inconsistent   whitespace\t\there  ";
    List<String> normalizedWords = Traversals.getAll(words, messyText);
    String cleaned = String.join(" ", normalizedWords);
    System.out.println("\nWhitespace normalisation:");
    System.out.println("Input:  '" + messyText + "'");
    System.out.println("Output: '" + cleaned + "'");
    System.out.println();
  }

  private static void demonstrateLineTraversals() {
    System.out.println("--- SCENARIO 3: Line-Level Operations ---\n");

    Traversal<String, String> lines = StringTraversals.lined();

    // Application log processing
    String logContent =
        """
        [2025-01-15 10:00:00] INFO Application started
        [2025-01-15 10:00:01] INFO Loading configuration
        [2025-01-15 10:00:02] ERROR Failed to connect to database
        [2025-01-15 10:00:03] WARN Retrying connection
        [2025-01-15 10:00:04] INFO Connected successfully
        """;

    System.out.println("--- Original Log ---");
    System.out.println(logContent);

    // Extract ERROR lines only
    Traversal<String, String> errorLines = lines.filtered(line -> line.contains("ERROR"));

    List<String> errors = Traversals.getAll(errorLines, logContent);
    System.out.println("--- ERROR Lines Only ---");
    errors.forEach(System.out::println);

    // Add prefixes to each line
    String prefixed = Traversals.modify(lines, line -> "> " + line, logContent);
    System.out.println("\n--- Prefixed Log ---");
    System.out.println(prefixed);

    // Number lines
    final int[] lineNumber = {1};
    String numbered = Traversals.modify(lines, line -> lineNumber[0]++ + ". " + line, logContent);
    System.out.println("--- Numbered Log ---");
    System.out.println(numbered);

    // Filter and transform
    String warningsAndErrors =
        Traversals.modify(
            lines.filtered(line -> line.contains("ERROR") || line.contains("WARN")),
            String::toUpperCase,
            logContent);
    System.out.println("--- Emphasised Warnings and Errors ---");
    System.out.println(warningsAndErrors);
    System.out.println();
  }

  private static void demonstrateRealWorldScenarios() {
    System.out.println("--- SCENARIO 4: Real-World Use Cases ---\n");

    demonstrateEmailNormalisation();
    demonstrateCsvProcessing();
    demonstrateConfigurationSanitisation();
  }

  private static void demonstrateEmailNormalisation() {
    System.out.println("Use Case 1: Email Normalisation Service");
    System.out.println("----------------------------------------");

    String[] emails = {
      "  User.Name@EXAMPLE.COM  ", "admin@TEST-DOMAIN.org", "contact@My-Company.CO.UK  "
    };

    Traversal<String, String> words = StringTraversals.worded();

    System.out.println("\nNormalising email addresses:");
    for (String email : emails) {
      String normalised = Traversals.modify(words, String::toLowerCase, email.trim());
      System.out.println("  " + email + " -> " + normalised);
    }
    System.out.println();
  }

  private static void demonstrateCsvProcessing() {
    System.out.println("Use Case 2: CSV Data Processing");
    System.out.println("--------------------------------");

    String csvData =
        """
        Product,Price,Stock
        Widget,10.50,100
        Gadget,25.00,50
        Gizmo,15.75,75
        """;

    Traversal<String, String> lines = StringTraversals.lined();

    // Extract header
    List<String> allLines = Traversals.getAll(lines, csvData);
    String header = allLines.get(0);
    System.out.println("Header: " + header);

    // Process data rows (skip header)
    Traversal<String, String> dataLines = lines.filtered(line -> !line.startsWith("Product"));

    System.out.println("\nData rows:");
    List<String> dataRows = Traversals.getAll(dataLines, csvData);
    dataRows.forEach(row -> System.out.println("  " + row));

    // Transform: uppercase product names (first column)
    String transformed =
        Traversals.modify(
            dataLines,
            line -> {
              String[] parts = line.split(",");
              if (parts.length >= 3) {
                parts[0] = parts[0].toUpperCase();
                return String.join(",", parts);
              }
              return line;
            },
            csvData);

    System.out.println("\nTransformed (uppercase product names):");
    System.out.println(transformed);
  }

  private static void demonstrateConfigurationSanitisation() {
    System.out.println("Use Case 3: Configuration File Sanitisation");
    System.out.println("--------------------------------------------");

    String configFile =
        """
        # Database Configuration
        db.host=localhost
        db.port=5432
        db.username=admin
        db.password=secret123

        # Application Settings
        app.name=MyApplication
        app.version=1.0.0
        app.debug=true
        """;

    Traversal<String, String> lines = StringTraversals.lined();

    // Remove comments
    Traversal<String, String> nonCommentLines =
        lines.filtered(line -> !line.trim().startsWith("#") && !line.trim().isEmpty());

    String withoutComments =
        Traversals.getAll(nonCommentLines, configFile).stream().collect(Collectors.joining("\n"));

    System.out.println("Without comments:");
    System.out.println(withoutComments);

    // Redact sensitive values
    String redacted =
        Traversals.modify(
            lines,
            line -> {
              if (line.contains("password")) {
                int equalsIndex = line.indexOf('=');
                if (equalsIndex != -1) {
                  return line.substring(0, equalsIndex + 1) + "***REDACTED***";
                }
              }
              return line;
            },
            configFile);

    System.out.println("\nWith redacted passwords:");
    System.out.println(redacted);

    // Trim all property values
    String trimmed =
        Traversals.modify(
            nonCommentLines,
            line -> {
              String[] parts = line.split("=", 2);
              if (parts.length == 2) {
                return parts[0].trim() + "=" + parts[1].trim();
              }
              return line;
            },
            configFile);

    System.out.println("\nWith trimmed values:");
    System.out.println(trimmed);
  }
}
