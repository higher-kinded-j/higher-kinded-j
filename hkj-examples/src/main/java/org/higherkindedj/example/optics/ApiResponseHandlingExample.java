// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A runnable example demonstrating type-safe HTTP API response handling using prisms.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Type-safe response variant handling with sealed interfaces
 *   <li>Eliminating instanceof checks with {@code matches()} and {@code getOptional()}
 *   <li>Safe value extraction with {@code getOrElse()} and {@code mapOptional()}
 *   <li>Error handling and recovery strategies using {@code orElse()}
 *   <li>Response transformation pipelines with {@code modify()}
 * </ul>
 */
public class ApiResponseHandlingExample {

  // API response variants for User data
  @GeneratePrisms
  public sealed interface ApiResponse
      permits ApiSuccess, ApiClientError, ApiServerError, ApiNetworkError {}

  @GenerateLenses
  public record ApiSuccess(User data, int statusCode) implements ApiResponse {}

  @GenerateLenses
  public record ApiClientError(String message, int statusCode) implements ApiResponse {}

  @GenerateLenses
  public record ApiServerError(String message, int statusCode, String traceId)
      implements ApiResponse {}

  @GenerateLenses
  public record ApiNetworkError(String reason) implements ApiResponse {}

  // Domain models
  public record User(String id, String name, String email) {}

  public record Product(String id, String name, double price) {}

  // Product response variants
  @GeneratePrisms
  public sealed interface ProductResponse
      permits ProductSuccess, ProductClientError, ProductServerError {}

  @GenerateLenses
  public record ProductSuccess(Product data, int statusCode) implements ProductResponse {}

  public record ProductClientError(String message, int statusCode) implements ProductResponse {}

  public record ProductServerError(String message, int statusCode, String traceId)
      implements ProductResponse {}

  // Prisms for type-safe access
  private static final Prism<ApiResponse, ApiSuccess> SUCCESS = ApiResponsePrisms.apiSuccess();
  private static final Prism<ApiResponse, ApiClientError> CLIENT_ERROR =
      ApiResponsePrisms.apiClientError();
  private static final Prism<ApiResponse, ApiServerError> SERVER_ERROR =
      ApiResponsePrisms.apiServerError();
  private static final Prism<ApiResponse, ApiNetworkError> NETWORK_ERROR =
      ApiResponsePrisms.apiNetworkError();

  public static void main(String[] args) {
    System.out.println("=== API Response Handling with Prisms ===\n");

    demonstrateBasicResponseChecking();
    demonstrateSafeDataExtraction();
    demonstrateErrorRecovery();
    demonstrateResponseTransformation();
    demonstrateBatchProcessing();
  }

  private static void demonstrateBasicResponseChecking() {
    System.out.println("--- Basic Response Type Checking ---");

    ApiResponse successResponse =
        new ApiSuccess(new User("123", "Alice", "alice@example.com"), 200);
    ApiResponse errorResponse = new ApiClientError("Invalid user ID", 400);
    ApiResponse serverErrorResponse = new ApiServerError("Database unavailable", 503, "abc");

    // Check response types without instanceof
    System.out.println("Success response is success: " + SUCCESS.matches(successResponse));
    System.out.println("Error response is success: " + SUCCESS.matches(errorResponse));
    System.out.println("Error response is client error: " + CLIENT_ERROR.matches(errorResponse));
    System.out.println(
        "Server error is server error: " + SERVER_ERROR.matches(serverErrorResponse));

    System.out.println();
  }

  private static void demonstrateSafeDataExtraction() {
    System.out.println("--- Safe Data Extraction ---");

    ApiResponse successResponse =
        new ApiSuccess(new User("123", "Alice", "alice@example.com"), 200);
    ApiResponse errorResponse = new ApiClientError("Invalid user ID", 400);

    // Extract user data with default fallback
    User defaultUser = new User("0", "Guest", "guest@example.com");

    User user1 = SUCCESS.mapOptional(ApiSuccess::data, successResponse).orElse(defaultUser);
    User user2 = SUCCESS.mapOptional(ApiSuccess::data, errorResponse).orElse(defaultUser);

    System.out.println("User from success: " + user1);
    System.out.println("User from error (fallback): " + user2);

    // Extract error messages safely
    String errorMessage =
        CLIENT_ERROR.mapOptional(ApiClientError::message, errorResponse).orElse("No error message");

    System.out.println("Error message: " + errorMessage);

    // Extract trace ID from server errors
    ApiResponse serverError = new ApiServerError("Database timeout", 503, "trace-xyz");

    String traceId =
        SERVER_ERROR.mapOptional(ApiServerError::traceId, serverError).orElse("No trace ID");

    System.out.println("Server error trace ID: " + traceId);

    System.out.println();
  }

  private static void demonstrateErrorRecovery() {
    System.out.println("--- Error Recovery Strategies ---");

    // Simulate multiple fallback strategies
    ApiResponse networkError = new ApiNetworkError("Connection timeout");
    ApiResponse serverError = new ApiServerError("Service unavailable", 503, "abc");
    ApiResponse clientError = new ApiClientError("Not found", 404);

    // Extract error messages from different error types using mapOptional
    String networkErrorMessage =
        CLIENT_ERROR
            .mapOptional(ApiClientError::message, networkError)
            .or(() -> SERVER_ERROR.mapOptional(ApiServerError::message, networkError))
            .or(() -> NETWORK_ERROR.mapOptional(ApiNetworkError::reason, networkError))
            .orElse("Unknown error");

    String serverErrorMessage =
        CLIENT_ERROR
            .mapOptional(ApiClientError::message, serverError)
            .or(() -> SERVER_ERROR.mapOptional(ApiServerError::message, serverError))
            .or(() -> NETWORK_ERROR.mapOptional(ApiNetworkError::reason, serverError))
            .orElse("Unknown error");

    String clientErrorMessage =
        CLIENT_ERROR
            .mapOptional(ApiClientError::message, clientError)
            .or(() -> SERVER_ERROR.mapOptional(ApiServerError::message, clientError))
            .or(() -> NETWORK_ERROR.mapOptional(ApiNetworkError::reason, clientError))
            .orElse("Unknown error");

    System.out.println("Network error message: " + networkErrorMessage);
    System.out.println("Server error message: " + serverErrorMessage);
    System.out.println("Client error message: " + clientErrorMessage);

    // Retry logic: convert network errors to retryable flag
    boolean shouldRetry =
        NETWORK_ERROR
            .mapOptional(ne -> true, networkError)
            .or(() -> SERVER_ERROR.mapOptional(se -> se.statusCode() >= 500, serverError))
            .orElse(false);

    System.out.println("Should retry network error: " + shouldRetry);

    System.out.println();
  }

  private static void demonstrateResponseTransformation() {
    System.out.println("--- Response Transformation ---");

    ApiResponse response = new ApiSuccess(new User("123", "alice", "alice@example.com"), 200);

    // Normalise user name to title case using modify
    ApiResponse normalised =
        SUCCESS.modify(
            success ->
                new ApiSuccess(
                    new User(
                        success.data().id(),
                        capitalise(success.data().name()),
                        success.data().email()),
                    success.statusCode()),
            response);

    System.out.println("Original response: " + response);
    System.out.println("Normalised response: " + normalised);

    // Redact email addresses in error messages
    ApiResponse errorWithEmail =
        new ApiClientError("Invalid email: alice@example.com for user alice", 400);

    ApiResponse redacted =
        CLIENT_ERROR.modify(
            error ->
                new ApiClientError(
                    error
                        .message()
                        .replaceAll(
                            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[REDACTED]"),
                    error.statusCode()),
            errorWithEmail);

    System.out.println("Original error: " + errorWithEmail);
    System.out.println("Redacted error: " + redacted);

    System.out.println();
  }

  private static void demonstrateBatchProcessing() {
    System.out.println("--- Batch Response Processing ---");

    Prism<ProductResponse, ProductSuccess> productSuccess = ProductResponsePrisms.productSuccess();

    List<ProductResponse> responses =
        List.of(
            new ProductSuccess(new Product("p1", "Laptop", 999.99), 200),
            new ProductClientError("Product not found", 404),
            new ProductSuccess(new Product("p2", "Mouse", 29.99), 200),
            new ProductServerError("Inventory service down", 503, "trace-1"),
            new ProductSuccess(new Product("p3", "Keyboard", 79.99), 200));

    // Extract all successful products
    List<Product> products =
        responses.stream()
            .flatMap(resp -> productSuccess.mapOptional(ProductSuccess::data, resp).stream())
            .toList();

    System.out.println("Successfully retrieved " + products.size() + " products:");
    products.forEach(p -> System.out.println("  - " + p.name() + ": £" + p.price()));

    // Count errors by type
    long clientErrors =
        responses.stream()
            .filter(resp -> ProductResponsePrisms.productClientError().matches(resp))
            .count();

    long serverErrors =
        responses.stream()
            .filter(resp -> ProductResponsePrisms.productServerError().matches(resp))
            .count();

    System.out.println("Client errors: " + clientErrors);
    System.out.println("Server errors: " + serverErrors);

    // Calculate total value of successful products
    double totalValue =
        responses.stream()
            .flatMap(resp -> productSuccess.mapOptional(ProductSuccess::data, resp).stream())
            .mapToDouble(Product::price)
            .sum();

    System.out.println("Total value of retrieved products: £" + String.format("%.2f", totalValue));

    System.out.println();
  }

  // Helper methods

  private static String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }
}
