// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.stream.Collectors;
import org.higherkindedj.example.optics.doesnotmatch.ApiResponsePrisms;
import org.higherkindedj.example.optics.doesnotmatch.JsonValuePrisms;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A runnable example demonstrating the {@code doesNotMatch} method on Prisms.
 *
 * <p>The {@code doesNotMatch} method is the logical negation of {@code matches()}, providing a
 * convenient way to check when a prism fails to match.
 *
 * <p><strong>Key concepts:</strong>
 *
 * <ul>
 *   <li>Using {@code doesNotMatch} for negative case handling
 *   <li>Stream filtering for exclusion patterns
 *   <li>Conditional logic with prism negation
 *   <li>Real-world validation rejection scenarios
 * </ul>
 *
 * @see org.higherkindedj.optics.Prism#doesNotMatch(Object)
 * @see org.higherkindedj.optics.Prism#matches(Object)
 */
public class DoesNotMatchExample {

  // Domain model for examples - uses targetPackage to avoid conflicts with other examples
  @GeneratePrisms(targetPackage = "org.higherkindedj.example.optics.doesnotmatch")
  public sealed interface JsonValue permits JsonString, JsonNumber, JsonBoolean, JsonNull {}

  public record JsonString(String value) implements JsonValue {}

  public record JsonNumber(double value) implements JsonValue {}

  public record JsonBoolean(boolean value) implements JsonValue {}

  public record JsonNull() implements JsonValue {}

  // API response model - uses targetPackage to avoid conflicts with other examples
  @GeneratePrisms(targetPackage = "org.higherkindedj.example.optics.doesnotmatch")
  public sealed interface ApiResponse permits Success, ClientError, ServerError {}

  public record Success(String data) implements ApiResponse {}

  public record ClientError(int code, String message) implements ApiResponse {}

  public record ServerError(String message, String traceId) implements ApiResponse {}

  public static void main(String[] args) {
    System.out.println("=== DoesNotMatch Examples ===\n");

    demonstrateBasicUsage();
    demonstrateFilteringExclusion();
    demonstrateConditionalLogic();
    demonstrateErrorHandling();
    demonstrateSymmetryWithMatches();
  }

  /**
   * Demonstrates basic usage of {@code doesNotMatch}.
   *
   * <p>The method returns {@code true} when the prism fails to match the given value.
   */
  private static void demonstrateBasicUsage() {
    System.out.println("--- Basic Usage ---");

    Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

    JsonValue stringValue = new JsonString("hello");
    JsonValue numberValue = new JsonNumber(42.0);

    System.out.println("Testing doesNotMatch vs matches:");
    System.out.println();

    System.out.println("JsonString('hello'):");
    System.out.println("  matches: " + stringPrism.matches(stringValue));
    System.out.println("  doesNotMatch: " + stringPrism.doesNotMatch(stringValue));

    System.out.println();

    System.out.println("JsonNumber(42.0):");
    System.out.println("  matches: " + stringPrism.matches(numberValue));
    System.out.println("  doesNotMatch: " + stringPrism.doesNotMatch(numberValue));

    System.out.println();
  }

  /**
   * Demonstrates using {@code doesNotMatch} to filter out specific types from a collection.
   *
   * <p>This is useful when you want to exclude certain variants rather than include them.
   */
  private static void demonstrateFilteringExclusion() {
    System.out.println("--- Filtering with Exclusion ---");

    Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();
    Prism<JsonValue, JsonNull> nullPrism = JsonValuePrisms.jsonNull();

    List<JsonValue> values =
        List.of(
            new JsonString("hello"),
            new JsonNumber(42.0),
            new JsonBoolean(true),
            new JsonNull(),
            new JsonString("world"),
            new JsonNumber(3.14));

    System.out.println("Original values: " + formatJsonValues(values));

    // Filter to get all non-string values
    List<JsonValue> nonStrings =
        values.stream().filter(stringPrism::doesNotMatch).collect(Collectors.toList());

    System.out.println("Non-string values: " + formatJsonValues(nonStrings));

    // Filter to get all non-null values
    List<JsonValue> nonNulls =
        values.stream().filter(nullPrism::doesNotMatch).collect(Collectors.toList());

    System.out.println("Non-null values: " + formatJsonValues(nonNulls));

    // Combine exclusions: neither strings nor nulls
    List<JsonValue> numbersAndBooleans =
        values.stream()
            .filter(v -> stringPrism.doesNotMatch(v) && nullPrism.doesNotMatch(v))
            .collect(Collectors.toList());

    System.out.println("Numbers and booleans only: " + formatJsonValues(numbersAndBooleans));

    System.out.println();
  }

  /**
   * Demonstrates using {@code doesNotMatch} in conditional logic.
   *
   * <p>Checking for non-matches can lead to cleaner code in some scenarios.
   */
  private static void demonstrateConditionalLogic() {
    System.out.println("--- Conditional Logic ---");

    Prism<JsonValue, JsonNull> nullPrism = JsonValuePrisms.jsonNull();

    List<JsonValue> values = List.of(new JsonString("data"), new JsonNull(), new JsonNumber(100.0));

    System.out.println("Processing values with null check using doesNotMatch:");
    for (JsonValue value : values) {
      if (nullPrism.doesNotMatch(value)) {
        System.out.println("  " + formatJsonValue(value) + " -> Processing value");
      } else {
        System.out.println("  " + formatJsonValue(value) + " -> Skipping null");
      }
    }

    System.out.println();

    // The above is equivalent to, but more readable than:
    // if (!nullPrism.matches(value))
    System.out.println("Note: 'doesNotMatch' is more readable than '!matches()'");
    System.out.println("  if (nullPrism.doesNotMatch(value))  // clearer intent");
    System.out.println("  if (!nullPrism.matches(value))      // less clear");

    System.out.println();
  }

  /**
   * Demonstrates using {@code doesNotMatch} for error handling scenarios.
   *
   * <p>When processing API responses, you often want to handle non-success cases differently.
   */
  private static void demonstrateErrorHandling() {
    System.out.println("--- Error Handling Patterns ---");

    Prism<ApiResponse, Success> successPrism = ApiResponsePrisms.success();

    List<ApiResponse> responses =
        List.of(
            new Success("Data loaded"),
            new ClientError(404, "Not found"),
            new Success("More data"),
            new ServerError("Database timeout", "trace-123"),
            new ClientError(401, "Unauthorized"));

    System.out.println("Processing API responses:");
    System.out.println();

    // Separate successes from failures
    List<ApiResponse> failures =
        responses.stream().filter(successPrism::doesNotMatch).collect(Collectors.toList());

    List<ApiResponse> successes =
        responses.stream().filter(successPrism::matches).collect(Collectors.toList());

    System.out.println("Successful responses (" + successes.size() + "):");
    for (ApiResponse response : successes) {
      System.out.println("  " + response);
    }

    System.out.println();

    System.out.println("Failed responses (" + failures.size() + "):");
    for (ApiResponse response : failures) {
      System.out.println("  " + response);
    }

    // Count failures that need investigation
    long errorCount = responses.stream().filter(successPrism::doesNotMatch).count();
    System.out.println();
    System.out.println("Total errors requiring investigation: " + errorCount);

    System.out.println();
  }

  /**
   * Demonstrates the symmetry between {@code matches} and {@code doesNotMatch}.
   *
   * <p>These methods are always logical negations of each other.
   */
  private static void demonstrateSymmetryWithMatches() {
    System.out.println("--- Symmetry: matches vs doesNotMatch ---");

    Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

    List<JsonValue> testCases =
        List.of(
            new JsonString("hello"), new JsonNumber(42.0), new JsonBoolean(true), new JsonNull());

    System.out.println("Verifying: doesNotMatch(x) == !matches(x) for all values");
    System.out.println();

    boolean allSymmetric = true;
    for (JsonValue value : testCases) {
      boolean matches = stringPrism.matches(value);
      boolean doesNotMatch = stringPrism.doesNotMatch(value);
      boolean symmetric = doesNotMatch == !matches;
      allSymmetric = allSymmetric && symmetric;

      System.out.println(formatJsonValue(value) + ":");
      System.out.println("  matches: " + matches);
      System.out.println("  doesNotMatch: " + doesNotMatch);
      System.out.println("  symmetric: " + symmetric);
    }

    System.out.println();
    System.out.println("All values symmetric: " + allSymmetric);
  }

  // Helper methods for formatting output
  private static String formatJsonValue(JsonValue value) {
    return switch (value) {
      case JsonString js -> "JsonString(\"" + js.value() + "\")";
      case JsonNumber jn -> "JsonNumber(" + jn.value() + ")";
      case JsonBoolean jb -> "JsonBoolean(" + jb.value() + ")";
      case JsonNull jnull -> "JsonNull";
    };
  }

  private static String formatJsonValues(List<JsonValue> values) {
    return values.stream()
        .map(DoesNotMatchExample::formatJsonValue)
        .collect(Collectors.toList())
        .toString();
  }
}
