// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Prisms;

/**
 * A runnable example demonstrating the {@code nearly} prism constructor for predicate-based
 * matching.
 *
 * <p>This example shows how to create prisms that match values satisfying a predicate,
 * complementing the {@code only} prism for exact matches.
 *
 * <p><strong>Key concepts:</strong>
 *
 * <ul>
 *   <li>Predicate-based matching with {@code nearly}
 *   <li>Comparison with {@code only} for exact matching
 *   <li>Composition with other optics
 *   <li>Real-world validation scenarios
 * </ul>
 *
 * @see org.higherkindedj.optics.util.Prisms#nearly(Object, java.util.function.Predicate)
 * @see org.higherkindedj.optics.util.Prisms#only(Object)
 */
public class NearlyPrismExample {

  public static void main(String[] args) {
    System.out.println("=== Nearly Prism Examples ===\n");

    demonstrateBasicUsage();
    demonstrateComparisonWithOnly();
    demonstrateValidationPatterns();
    demonstrateComposition();
    demonstrateFiltering();
  }

  /**
   * Demonstrates basic usage of the {@code nearly} prism.
   *
   * <p>The {@code nearly} prism matches any value that satisfies a predicate and returns a default
   * value when building.
   */
  private static void demonstrateBasicUsage() {
    System.out.println("--- Basic Usage ---");

    // Create a prism that matches non-empty strings
    Prism<String, Unit> nonEmptyPrism = Prisms.nearly("default", s -> !s.isEmpty());

    // Test matching
    String hello = "hello";
    String empty = "";

    Optional<Unit> matchResult = nonEmptyPrism.getOptional(hello);
    Optional<Unit> noMatchResult = nonEmptyPrism.getOptional(empty);

    System.out.println("Non-empty string 'hello' matches: " + matchResult.isPresent());
    System.out.println("Empty string '' matches: " + noMatchResult.isPresent());

    // Test building
    String builtValue = nonEmptyPrism.build(Unit.INSTANCE);
    System.out.println("Built value: '" + builtValue + "'");

    // Test matches() convenience method
    System.out.println("'world' matches: " + nonEmptyPrism.matches("world"));
    System.out.println("'' matches: " + nonEmptyPrism.matches(""));

    System.out.println();
  }

  /**
   * Compares {@code nearly} with {@code only} to highlight their differences.
   *
   * <p>{@code only} matches exact values, while {@code nearly} matches categories of values.
   */
  private static void demonstrateComparisonWithOnly() {
    System.out.println("--- Comparison: nearly vs only ---");

    // 'only' matches a specific value
    Prism<Integer, Unit> exactlyFive = Prisms.only(5);

    // 'nearly' matches a category of values
    Prism<Integer, Unit> positive = Prisms.nearly(1, n -> n > 0);

    List<Integer> testValues = List.of(-2, 0, 1, 5, 10);

    System.out.println("Testing values: " + testValues);
    System.out.println();

    System.out.println("Using 'only(5)' - matches exactly 5:");
    for (Integer value : testValues) {
      System.out.println("  " + value + " matches: " + exactlyFive.matches(value));
    }

    System.out.println();

    System.out.println("Using 'nearly(1, n -> n > 0)' - matches any positive number:");
    for (Integer value : testValues) {
      System.out.println("  " + value + " matches: " + positive.matches(value));
    }

    System.out.println();

    System.out.println("Build from 'only(5)': " + exactlyFive.build(Unit.INSTANCE));
    System.out.println("Build from 'nearly(1, n -> n > 0)': " + positive.build(Unit.INSTANCE));

    System.out.println();
  }

  /**
   * Demonstrates using {@code nearly} for validation patterns.
   *
   * <p>The predicate-based nature of {@code nearly} makes it ideal for validating data categories.
   */
  private static void demonstrateValidationPatterns() {
    System.out.println("--- Validation Patterns ---");

    // Email validation (simplified)
    Prism<String, Unit> validEmail =
        Prisms.nearly("user@example.com", s -> s.contains("@") && s.contains("."));

    // Age validation
    Prism<Integer, Unit> adultAge = Prisms.nearly(18, age -> age >= 18 && age <= 120);

    // Password strength validation
    Prism<String, Unit> strongPassword =
        Prisms.nearly(
            "SecureP@ss1",
            pwd ->
                pwd.length() >= 8
                    && pwd.chars().anyMatch(Character::isUpperCase)
                    && pwd.chars().anyMatch(Character::isDigit));

    // Test emails
    System.out.println("Email validation:");
    System.out.println("  'alice@example.com' valid: " + validEmail.matches("alice@example.com"));
    System.out.println("  'not-an-email' valid: " + validEmail.matches("not-an-email"));
    System.out.println("  '@incomplete.' valid: " + validEmail.matches("@incomplete."));

    // Test ages
    System.out.println("\nAge validation (18-120):");
    System.out.println("  17 valid: " + adultAge.matches(17));
    System.out.println("  18 valid: " + adultAge.matches(18));
    System.out.println("  65 valid: " + adultAge.matches(65));
    System.out.println("  150 valid: " + adultAge.matches(150));

    // Test passwords
    System.out.println("\nPassword strength validation:");
    System.out.println("  'weak' valid: " + strongPassword.matches("weak"));
    System.out.println("  'Password1' valid: " + strongPassword.matches("Password1"));
    System.out.println("  'ALLCAPS123' valid: " + strongPassword.matches("ALLCAPS123"));

    System.out.println();
  }

  /**
   * Demonstrates composing {@code nearly} with other prisms.
   *
   * <p>Prisms can be composed to create more complex matching logic.
   */
  private static void demonstrateComposition() {
    System.out.println("--- Composition with Other Prisms ---");

    // Compose some() with a predicate-based prism
    Prism<Optional<String>, String> somePrism = Prisms.some();
    Prism<String, Unit> nonEmpty = Prisms.nearly("default", s -> !s.isEmpty());

    // Composed prism: matches Optional containing a non-empty string
    Prism<Optional<String>, Unit> someNonEmpty = somePrism.andThen(nonEmpty);

    List<Optional<String>> testCases =
        List.of(Optional.of("hello"), Optional.of(""), Optional.empty());

    System.out.println("Testing composed prism (Optional containing non-empty string):");
    for (Optional<String> testCase : testCases) {
      System.out.println("  " + testCase + " matches: " + someNonEmpty.matches(testCase));
    }

    System.out.println();
  }

  /**
   * Demonstrates using {@code nearly} for filtering collections.
   *
   * <p>The {@code matches} method integrates well with stream filtering.
   */
  private static void demonstrateFiltering() {
    System.out.println("--- Filtering Collections ---");

    Prism<Integer, Unit> evenNumber = Prisms.nearly(0, n -> n % 2 == 0);
    Prism<String, Unit> startsWithA = Prisms.nearly("A", s -> s.toUpperCase().startsWith("A"));

    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    List<String> names = List.of("Alice", "Bob", "Anna", "Charlie", "Andrew", "Diana");

    // Filter even numbers using the prism
    List<Integer> evens = numbers.stream().filter(evenNumber::matches).collect(Collectors.toList());

    // Filter names starting with 'A'
    List<String> aNames = names.stream().filter(startsWithA::matches).collect(Collectors.toList());

    System.out.println("Original numbers: " + numbers);
    System.out.println("Even numbers: " + evens);

    System.out.println("\nOriginal names: " + names);
    System.out.println("Names starting with 'A': " + aNames);

    System.out.println();
    System.out.println("Note: The 'nearly' prism provides a declarative way to define");
    System.out.println("matching criteria that can be reused across your application.");
  }
}
