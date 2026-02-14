// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A runnable example demonstrating the convenience methods available on the {@link Prism}
 * interface.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>{@code matches()} - Type checking without extraction
 *   <li>{@code getOrElse()} - Providing default values
 *   <li>{@code mapOptional()} - Transforming matched values
 *   <li>{@code modify()} - Simple modifications
 *   <li>{@code modifyWhen()} - Conditional modifications with predicates
 *   <li>{@code setWhen()} - Conditional setting with predicates
 *   <li>{@code orElse()} - Fallback prism chaining
 * </ul>
 */
public class PrismConvenienceMethodsExample {

  // Domain model for API responses
  @GeneratePrisms
  public sealed interface HttpResponse {}

  @GenerateLenses(targetPackage = "org.higherkindedj.example.optics.convenience")
  public record Success(String data, int statusCode) implements HttpResponse {}

  public record ValidationError(List<String> errors, String field) implements HttpResponse {}

  @GenerateLenses(targetPackage = "org.higherkindedj.example.optics.convenience")
  public record ServerError(String message, String traceId) implements HttpResponse {}

  public record RateLimitError(long retryAfterMs) implements HttpResponse {}

  public static void main(String[] args) {
    System.out.println("=== Prism Convenience Methods Examples ===\n");

    demonstrateMatches();
    demonstrateGetOrElse();
    demonstrateMapOptional();
    demonstrateModify();
    demonstrateModifyWhen();
    demonstrateSetWhen();
    demonstrateOrElse();
  }

  private static void demonstrateMatches() {
    System.out.println("--- matches(): Type Checking Without Extraction ---");

    Prism<HttpResponse, Success> successPrism = HttpResponsePrisms.success();

    List<HttpResponse> responses =
        List.of(
            new Success("User data", 200),
            new ValidationError(List.of("Invalid email"), "email"),
            new Success("Order data", 200),
            new ServerError("Database connection failed", "trace-123"));

    // Use matches() for filtering
    List<HttpResponse> successfulResponses =
        responses.stream().filter(successPrism::matches).collect(Collectors.toList());

    System.out.println("Total responses: " + responses.size());
    System.out.println("Successful responses: " + successfulResponses.size());

    // Use matches() in conditional logic
    HttpResponse response = responses.get(0);
    if (successPrism.matches(response)) {
      System.out.println("First response was successful");
    }

    System.out.println();
  }

  private static void demonstrateGetOrElse() {
    System.out.println("--- getOrElse(): Providing Default Values ---");

    Prism<HttpResponse, Success> successPrism = HttpResponsePrisms.success();

    HttpResponse successResponse = new Success("User data", 200);
    HttpResponse errorResponse = new ServerError("Internal error", "trace-456");

    // Extract success data or use default
    Success defaultSuccess = new Success("fallback", 200);

    Success result1 = successPrism.getOrElse(defaultSuccess, successResponse);
    Success result2 = successPrism.getOrElse(defaultSuccess, errorResponse);

    System.out.println("Success response data: " + result1.data());
    System.out.println("Error response data (with default): " + result2.data());

    System.out.println();
  }

  private static void demonstrateMapOptional() {
    System.out.println("--- mapOptional(): Transforming Matched Values ---");

    Prism<HttpResponse, Success> successPrism = HttpResponsePrisms.success();

    HttpResponse response = new Success("User: Alice", 200);

    // Extract and transform in one operation
    Optional<String> upperCaseData =
        successPrism.mapOptional(s -> s.data().toUpperCase(), response);

    Optional<Integer> dataLength = successPrism.mapOptional(s -> s.data().length(), response);

    Optional<Boolean> isOk = successPrism.mapOptional(s -> s.statusCode() == 200, response);

    System.out.println("Uppercase data: " + upperCaseData.orElse("N/A"));
    System.out.println("Data length: " + dataLength.orElse(0));
    System.out.println("Is OK status: " + isOk.orElse(false));

    // Won't transform if prism doesn't match
    HttpResponse errorResponse = new ServerError("Error", "trace-789");
    Optional<String> noTransform =
        successPrism.mapOptional(s -> s.data().toUpperCase(), errorResponse);
    System.out.println("Transform on error: " + noTransform.orElse("No match"));

    System.out.println();
  }

  private static void demonstrateModify() {
    System.out.println("--- modify(): Simple Modifications ---");

    Prism<HttpResponse, Success> successPrism = HttpResponsePrisms.success();

    HttpResponse response = new Success("hello world", 200);

    // Modify if prism matches
    HttpResponse uppercased =
        successPrism.modify(s -> new Success(s.data().toUpperCase(), s.statusCode()), response);

    System.out.println("Original: " + response);
    System.out.println("Modified: " + uppercased);

    // Returns original if no match
    HttpResponse errorResponse = new ServerError("Error", "trace-xyz");
    HttpResponse unchanged =
        successPrism.modify(
            s -> new Success(s.data().toUpperCase(), s.statusCode()), errorResponse);

    System.out.println("Error (unchanged): " + unchanged);

    System.out.println();
  }

  private static void demonstrateModifyWhen() {
    System.out.println("--- modifyWhen(): Conditional Modifications ---");

    Prism<HttpResponse, Success> successPrism = HttpResponsePrisms.success();

    List<HttpResponse> responses =
        List.of(
            new Success("short", 200),
            new Success("This is a longer response message", 200),
            new Success("ok", 200),
            new ServerError("Error", "trace-abc"));

    // Only modify success responses with data longer than 10 characters
    List<HttpResponse> processed =
        responses.stream()
            .map(
                r ->
                    successPrism.modifyWhen(
                        s -> s.data().length() > 10,
                        s -> new Success("[LONG] " + s.data(), s.statusCode()),
                        r))
            .collect(Collectors.toList());

    System.out.println("Original responses:");
    responses.forEach(r -> System.out.println("  " + r));

    System.out.println("\nProcessed responses:");
    processed.forEach(r -> System.out.println("  " + r));

    System.out.println();
  }

  private static void demonstrateSetWhen() {
    System.out.println("--- setWhen(): Conditional Setting ---");

    Prism<HttpResponse, Success> successPrism = HttpResponsePrisms.success();

    List<HttpResponse> responses =
        List.of(
            new Success("valid data", 200),
            new Success("", 200), // Empty data
            new Success("another valid response", 200),
            new ValidationError(List.of("Error"), "field"));

    Success replacement = new Success("SANITISED", 200);

    // Only set if current data is empty
    List<HttpResponse> sanitised =
        responses.stream()
            .map(r -> successPrism.setWhen(s -> s.data().isEmpty(), replacement, r))
            .collect(Collectors.toList());

    System.out.println("Original responses:");
    responses.forEach(r -> System.out.println("  " + r));

    System.out.println("\nSanitised responses:");
    sanitised.forEach(r -> System.out.println("  " + r));

    System.out.println();
  }

  private static void demonstrateOrElse() {
    System.out.println("--- orElse(): Fallback Prism Chaining ---");

    Prism<HttpResponse, Success> successPrism = HttpResponsePrisms.success();
    Prism<HttpResponse, ServerError> serverErrorPrism = HttpResponsePrisms.serverError();

    List<HttpResponse> responses =
        List.of(
            new Success("Operation completed", 200),
            new ServerError("Database error", "trace-def"),
            new ValidationError(List.of("Invalid"), "field"), // Won't match either prism
            new Success("User created", 201));

    System.out.println("Extracting messages:");
    for (HttpResponse response : responses) {
      // Extract messages from either Success or ServerError using mapOptional
      String message =
          successPrism
              .mapOptional(Success::data, response)
              .or(() -> serverErrorPrism.mapOptional(ServerError::message, response))
              .orElse("No message available");
      System.out.println("  " + response.getClass().getSimpleName() + " -> " + message);
    }

    // Demonstrate prism orElse() for combining prisms of the same type
    // Create two prisms that both extract Success but from different response types
    Prism<HttpResponse, Success> primaryPrism = HttpResponsePrisms.success();
    Prism<HttpResponse, Success> fallbackPrism = HttpResponsePrisms.success();

    // Combine them - orElse tries the first prism, then the second
    Prism<HttpResponse, Success> combinedPrism = primaryPrism.orElse(fallbackPrism);

    HttpResponse testResponse = new Success("Test data", 200);
    Success extracted = combinedPrism.getOrElse(new Success("default", 500), testResponse);
    System.out.println("\nExtracted with combined prism: " + extracted.data());

    // Building with orElse-chained prism uses the first prism's constructor
    Success testSuccess = new Success("Built data", 201);
    HttpResponse built = combinedPrism.build(testSuccess);
    System.out.println("Built response type: " + built.getClass().getSimpleName());
    System.out.println("Built response: " + built);

    System.out.println();
  }
}
