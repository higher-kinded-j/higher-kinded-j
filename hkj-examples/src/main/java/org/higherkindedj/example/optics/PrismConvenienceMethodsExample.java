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
  public sealed interface BackendResponse {}

  @GenerateLenses(targetPackage = "org.higherkindedj.example.optics.convenience")
  public record SuccessResponse(String data, int statusCode) implements BackendResponse {}

  public record ValidationError(List<String> errors, String field) implements BackendResponse {}

  @GenerateLenses(targetPackage = "org.higherkindedj.example.optics.convenience")
  public record ServerError(String message, String traceId) implements BackendResponse {}

  public record RateLimitError(long retryAfterMs) implements BackendResponse {}

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

    Prism<BackendResponse, SuccessResponse> successPrism = BackendResponsePrisms.successResponse();

    List<BackendResponse> responses =
        List.of(
            new SuccessResponse("User data", 200),
            new ValidationError(List.of("Invalid email"), "email"),
            new SuccessResponse("Order data", 200),
            new ServerError("Database connection failed", "trace-123"));

    // Use matches() for filtering
    List<BackendResponse> successfulResponses =
        responses.stream().filter(successPrism::matches).collect(Collectors.toList());

    System.out.println("Total responses: " + responses.size());
    System.out.println("Successful responses: " + successfulResponses.size());

    // Use matches() in conditional logic
    BackendResponse response = responses.get(0);
    if (successPrism.matches(response)) {
      System.out.println("First response was successful");
    }

    System.out.println();
  }

  private static void demonstrateGetOrElse() {
    System.out.println("--- getOrElse(): Providing Default Values ---");

    Prism<BackendResponse, SuccessResponse> successPrism = BackendResponsePrisms.successResponse();

    BackendResponse successResponse = new SuccessResponse("User data", 200);
    BackendResponse errorResponse = new ServerError("Internal error", "trace-456");

    // Extract success data or use default
    SuccessResponse defaultSuccess = new SuccessResponse("fallback", 200);

    SuccessResponse result1 = successPrism.getOrElse(defaultSuccess, successResponse);
    SuccessResponse result2 = successPrism.getOrElse(defaultSuccess, errorResponse);

    System.out.println("Success response data: " + result1.data());
    System.out.println("Error response data (with default): " + result2.data());

    System.out.println();
  }

  private static void demonstrateMapOptional() {
    System.out.println("--- mapOptional(): Transforming Matched Values ---");

    Prism<BackendResponse, SuccessResponse> successPrism = BackendResponsePrisms.successResponse();

    BackendResponse response = new SuccessResponse("User: Alice", 200);

    // Extract and transform in one operation
    Optional<String> upperCaseData =
        successPrism.mapOptional(s -> s.data().toUpperCase(), response);

    Optional<Integer> dataLength = successPrism.mapOptional(s -> s.data().length(), response);

    Optional<Boolean> isOk = successPrism.mapOptional(s -> s.statusCode() == 200, response);

    System.out.println("Uppercase data: " + upperCaseData.orElse("N/A"));
    System.out.println("Data length: " + dataLength.orElse(0));
    System.out.println("Is OK status: " + isOk.orElse(false));

    // Won't transform if prism doesn't match
    BackendResponse errorResponse = new ServerError("Error", "trace-789");
    Optional<String> noTransform =
        successPrism.mapOptional(s -> s.data().toUpperCase(), errorResponse);
    System.out.println("Transform on error: " + noTransform.orElse("No match"));

    System.out.println();
  }

  private static void demonstrateModify() {
    System.out.println("--- modify(): Simple Modifications ---");

    Prism<BackendResponse, SuccessResponse> successPrism = BackendResponsePrisms.successResponse();

    BackendResponse response = new SuccessResponse("hello world", 200);

    // Modify if prism matches
    BackendResponse uppercased =
        successPrism.modify(
            s -> new SuccessResponse(s.data().toUpperCase(), s.statusCode()), response);

    System.out.println("Original: " + response);
    System.out.println("Modified: " + uppercased);

    // Returns original if no match
    BackendResponse errorResponse = new ServerError("Error", "trace-xyz");
    BackendResponse unchanged =
        successPrism.modify(
            s -> new SuccessResponse(s.data().toUpperCase(), s.statusCode()), errorResponse);

    System.out.println("Error (unchanged): " + unchanged);

    System.out.println();
  }

  private static void demonstrateModifyWhen() {
    System.out.println("--- modifyWhen(): Conditional Modifications ---");

    Prism<BackendResponse, SuccessResponse> successPrism = BackendResponsePrisms.successResponse();

    List<BackendResponse> responses =
        List.of(
            new SuccessResponse("short", 200),
            new SuccessResponse("This is a longer response message", 200),
            new SuccessResponse("ok", 200),
            new ServerError("Error", "trace-abc"));

    // Only modify success responses with data longer than 10 characters
    List<BackendResponse> processed =
        responses.stream()
            .map(
                r ->
                    successPrism.modifyWhen(
                        s -> s.data().length() > 10,
                        s -> new SuccessResponse("[LONG] " + s.data(), s.statusCode()),
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

    Prism<BackendResponse, SuccessResponse> successPrism = BackendResponsePrisms.successResponse();

    List<BackendResponse> responses =
        List.of(
            new SuccessResponse("valid data", 200),
            new SuccessResponse("", 200), // Empty data
            new SuccessResponse("another valid response", 200),
            new ValidationError(List.of("Error"), "field"));

    SuccessResponse replacement = new SuccessResponse("SANITISED", 200);

    // Only set if current data is empty
    List<BackendResponse> sanitised =
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

    Prism<BackendResponse, SuccessResponse> successPrism = BackendResponsePrisms.successResponse();
    Prism<BackendResponse, ServerError> serverErrorPrism = BackendResponsePrisms.serverError();

    List<BackendResponse> responses =
        List.of(
            new SuccessResponse("Operation completed", 200),
            new ServerError("Database error", "trace-def"),
            new ValidationError(List.of("Invalid"), "field"), // Won't match either prism
            new SuccessResponse("User created", 201));

    System.out.println("Extracting messages:");
    for (BackendResponse response : responses) {
      // Extract messages from either SuccessResponse or ServerError using mapOptional
      String message =
          successPrism
              .mapOptional(SuccessResponse::data, response)
              .or(() -> serverErrorPrism.mapOptional(ServerError::message, response))
              .orElse("No message available");
      System.out.println("  " + response.getClass().getSimpleName() + " -> " + message);
    }

    // Demonstrate prism orElse() for combining prisms of the same type
    // Create two prisms that both extract SuccessResponse but from different response types
    Prism<BackendResponse, SuccessResponse> primaryPrism = BackendResponsePrisms.successResponse();
    Prism<BackendResponse, SuccessResponse> fallbackPrism = BackendResponsePrisms.successResponse();

    // Combine them - orElse tries the first prism, then the second
    Prism<BackendResponse, SuccessResponse> combinedPrism = primaryPrism.orElse(fallbackPrism);

    BackendResponse testResponse = new SuccessResponse("Test data", 200);
    SuccessResponse extracted =
        combinedPrism.getOrElse(new SuccessResponse("default", 500), testResponse);
    System.out.println("\nExtracted with combined prism: " + extracted.data());

    // Building with orElse-chained prism uses the first prism's constructor
    SuccessResponse testSuccess = new SuccessResponse("Built data", 201);
    BackendResponse built = combinedPrism.build(testSuccess);
    System.out.println("Built response type: " + built.getClass().getSimpleName());
    System.out.println("Built response: " + built);

    System.out.println();
  }
}
