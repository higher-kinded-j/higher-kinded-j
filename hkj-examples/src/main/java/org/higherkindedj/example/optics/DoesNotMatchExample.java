// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.stream.Collectors;
import org.higherkindedj.example.optics.doesnotmatch.JsonScalarPrisms;
import org.higherkindedj.example.optics.doesnotmatch.LookupResponsePrisms;
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
  public sealed interface JsonScalar permits JsonString, JsonNumber, JsonBoolean, JsonNull {}

  public record JsonString(String value) implements JsonScalar {}

  public record JsonNumber(double value) implements JsonScalar {}

  public record JsonBoolean(boolean value) implements JsonScalar {}

  public record JsonNull() implements JsonScalar {}

  // API response model - uses targetPackage to avoid conflicts with other examples
  @GeneratePrisms(targetPackage = "org.higherkindedj.example.optics.doesnotmatch")
  public sealed interface LookupResponse permits SuccessResponse, ClientError, ServerError {}

  public record SuccessResponse(String data, int statusCode) implements LookupResponse {}

  public record ClientError(int code, String message) implements LookupResponse {}

  public record ServerError(String message, String traceId) implements LookupResponse {}

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

    Prism<JsonScalar, JsonString> stringPrism = JsonScalarPrisms.jsonString();

    JsonScalar stringValue = new JsonString("hello");
    JsonScalar numberValue = new JsonNumber(42.0);

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

    Prism<JsonScalar, JsonString> stringPrism = JsonScalarPrisms.jsonString();
    Prism<JsonScalar, JsonNull> nullPrism = JsonScalarPrisms.jsonNull();

    List<JsonScalar> values =
        List.of(
            new JsonString("hello"),
            new JsonNumber(42.0),
            new JsonBoolean(true),
            new JsonNull(),
            new JsonString("world"),
            new JsonNumber(3.14));

    System.out.println("Original values: " + formatJsonScalars(values));

    // Filter to get all non-string values
    List<JsonScalar> nonStrings =
        values.stream().filter(stringPrism::doesNotMatch).collect(Collectors.toList());

    System.out.println("Non-string values: " + formatJsonScalars(nonStrings));

    // Filter to get all non-null values
    List<JsonScalar> nonNulls =
        values.stream().filter(nullPrism::doesNotMatch).collect(Collectors.toList());

    System.out.println("Non-null values: " + formatJsonScalars(nonNulls));

    // Combine exclusions: neither strings nor nulls
    List<JsonScalar> numbersAndBooleans =
        values.stream()
            .filter(v -> stringPrism.doesNotMatch(v) && nullPrism.doesNotMatch(v))
            .collect(Collectors.toList());

    System.out.println("Numbers and booleans only: " + formatJsonScalars(numbersAndBooleans));

    System.out.println();
  }

  /**
   * Demonstrates using {@code doesNotMatch} in conditional logic.
   *
   * <p>Checking for non-matches can lead to cleaner code in some scenarios.
   */
  private static void demonstrateConditionalLogic() {
    System.out.println("--- Conditional Logic ---");

    Prism<JsonScalar, JsonNull> nullPrism = JsonScalarPrisms.jsonNull();

    List<JsonScalar> values =
        List.of(new JsonString("data"), new JsonNull(), new JsonNumber(100.0));

    System.out.println("Processing values with null check using doesNotMatch:");
    for (JsonScalar value : values) {
      if (nullPrism.doesNotMatch(value)) {
        System.out.println("  " + formatJsonScalar(value) + " -> Processing value");
      } else {
        System.out.println("  " + formatJsonScalar(value) + " -> Skipping null");
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

    Prism<LookupResponse, SuccessResponse> successPrism = LookupResponsePrisms.successResponse();

    List<LookupResponse> responses =
        List.of(
            new SuccessResponse("Data loaded", 200),
            new ClientError(404, "Not found"),
            new SuccessResponse("More data", 200),
            new ServerError("Database timeout", "trace-123"),
            new ClientError(401, "Unauthorized"));

    System.out.println("Processing API responses:");
    System.out.println();

    // Separate successes from failures
    List<LookupResponse> failures =
        responses.stream().filter(successPrism::doesNotMatch).collect(Collectors.toList());

    List<LookupResponse> successes =
        responses.stream().filter(successPrism::matches).collect(Collectors.toList());

    System.out.println("Successful responses (" + successes.size() + "):");
    for (LookupResponse response : successes) {
      System.out.println("  " + response);
    }

    System.out.println();

    System.out.println("Failed responses (" + failures.size() + "):");
    for (LookupResponse response : failures) {
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

    Prism<JsonScalar, JsonString> stringPrism = JsonScalarPrisms.jsonString();

    List<JsonScalar> testCases =
        List.of(
            new JsonString("hello"), new JsonNumber(42.0), new JsonBoolean(true), new JsonNull());

    System.out.println("Verifying: doesNotMatch(x) == !matches(x) for all values");
    System.out.println();

    boolean allSymmetric = true;
    for (JsonScalar value : testCases) {
      boolean matches = stringPrism.matches(value);
      boolean doesNotMatch = stringPrism.doesNotMatch(value);
      boolean symmetric = doesNotMatch == !matches;
      allSymmetric = allSymmetric && symmetric;

      System.out.println(formatJsonScalar(value) + ":");
      System.out.println("  matches: " + matches);
      System.out.println("  doesNotMatch: " + doesNotMatch);
      System.out.println("  symmetric: " + symmetric);
    }

    System.out.println();
    System.out.println("All values symmetric: " + allSymmetric);
  }

  // Helper methods for formatting output
  private static String formatJsonScalar(JsonScalar value) {
    return switch (value) {
      case JsonString js -> "JsonString(\"" + js.value() + "\")";
      case JsonNumber jn -> "JsonNumber(" + jn.value() + ")";
      case JsonBoolean jb -> "JsonBoolean(" + jb.value() + ")";
      case JsonNull jnull -> "JsonNull";
    };
  }

  private static String formatJsonScalars(List<JsonScalar> values) {
    return values.stream()
        .map(DoesNotMatchExample::formatJsonScalar)
        .collect(Collectors.toList())
        .toString();
  }
}
