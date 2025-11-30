// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Prisms;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 10: Advanced Prism Patterns
 *
 * <p>This tutorial covers advanced prism techniques including predicate-based matching, exclusion
 * filtering, and cross-optic composition patterns.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>{@code nearly}: Predicate-based matching (complement to {@code only})
 *   <li>{@code doesNotMatch}: Exclusion filtering
 *   <li>Lens + Prism = Traversal: Composing through optional paths
 *   <li>Prism + Lens = Traversal: Accessing fields of sum type variants
 * </ul>
 *
 * <p>When to use these patterns:
 *
 * <ul>
 *   <li>Validation and filtering based on predicates
 *   <li>Excluding certain variants from processing
 *   <li>Navigating complex data with optional/variant fields
 * </ul>
 */
public class Tutorial10_AdvancedPrismPatterns {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // --- Domain models for exercises ---

  /** API response sum type for cross-optic composition exercises. */
  sealed interface ApiResponse permits Success, ClientError, ServerError {}

  record Success(ResponseData data, String timestamp) implements ApiResponse {}

  record ClientError(String message, int code) implements ApiResponse {}

  record ServerError(String message, String stackTrace) implements ApiResponse {}

  record ResponseData(String content, int size) {}

  /** Configuration with optional database settings. */
  record Config(String name, Optional<DatabaseSettings> database) {}

  record DatabaseSettings(String host, int port) {}

  // Manual prisms for exercises
  static class ApiResponsePrisms {
    public static Prism<ApiResponse, Success> success() {
      return Prism.of(
          resp -> resp instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);
    }

    public static Prism<ApiResponse, ClientError> clientError() {
      return Prism.of(
          resp -> resp instanceof ClientError e ? Optional.of(e) : Optional.empty(), e -> e);
    }

    public static Prism<ApiResponse, ServerError> serverError() {
      return Prism.of(
          resp -> resp instanceof ServerError e ? Optional.of(e) : Optional.empty(), e -> e);
    }
  }

  // =========================================================================
  // Part 1: The 'nearly' Prism - Predicate-Based Matching
  // =========================================================================

  /**
   * Exercise 1: Creating a nearly prism for validation
   *
   * <p>The {@code nearly} prism matches values that satisfy a predicate. Unlike {@code only} which
   * matches exact values, {@code nearly} matches categories of values.
   *
   * <p>Task: Create a prism that matches non-empty strings
   */
  @Test
  void exercise1_nearlyForNonEmptyStrings() {
    // TODO: Create a nearly prism that matches non-empty strings
    // Hint: Prisms.nearly(defaultValue, predicate)
    // The default value is used when building - use "default" as the default
    Prism<String, Unit> nonEmptyPrism = answerRequired();

    // Test matching
    assertThat(nonEmptyPrism.matches("hello")).isTrue();
    assertThat(nonEmptyPrism.matches("")).isFalse();
    assertThat(nonEmptyPrism.matches("  ")).isTrue(); // Whitespace is non-empty

    // Test build returns default
    assertThat(nonEmptyPrism.build(Unit.INSTANCE)).isEqualTo("default");
  }

  /**
   * Exercise 2: Using nearly for numeric validation
   *
   * <p>Task: Create a prism that matches positive integers and use it to filter a list
   */
  @Test
  void exercise2_nearlyForPositiveNumbers() {
    // TODO: Create a nearly prism that matches positive integers (> 0)
    // Use 1 as the default value
    Prism<Integer, Unit> positivePrism = answerRequired();

    List<Integer> numbers = List.of(-5, 0, 3, -2, 7, 10, -1);

    // TODO: Filter to get only positive numbers using the prism
    // Hint: numbers.stream().filter(positivePrism::matches).collect(...)
    List<Integer> positives = answerRequired();

    assertThat(positives).containsExactly(3, 7, 10);
  }

  /**
   * Exercise 3: Comparing only vs nearly
   *
   * <p>Task: Understand the difference between exact matching and predicate matching
   */
  @Test
  void exercise3_onlyVsNearly() {
    // 'only' matches exact values
    Prism<String, Unit> exactHelloPrism = Prisms.only("hello");

    // TODO: Create a 'nearly' prism that matches any string starting with "hello"
    // Use "hello" as the default value
    Prism<String, Unit> startsWithHelloPrism = answerRequired();

    // Test exact matching with 'only'
    assertThat(exactHelloPrism.matches("hello")).isTrue();
    assertThat(exactHelloPrism.matches("hello world")).isFalse();
    assertThat(exactHelloPrism.matches("Hello")).isFalse();

    // Test predicate matching with 'nearly'
    assertThat(startsWithHelloPrism.matches("hello")).isTrue();
    assertThat(startsWithHelloPrism.matches("hello world")).isTrue();
    assertThat(startsWithHelloPrism.matches("Hello")).isFalse(); // Case sensitive
  }

  // =========================================================================
  // Part 2: doesNotMatch - Exclusion Filtering
  // =========================================================================

  /**
   * Exercise 4: Using doesNotMatch for exclusion
   *
   * <p>The {@code doesNotMatch} method is the logical negation of {@code matches}. It's useful for
   * filtering out values that match a prism.
   *
   * <p>Task: Filter API responses to get only error responses (not Success)
   */
  @Test
  void exercise4_doesNotMatchFiltering() {
    Prism<ApiResponse, Success> successPrism = ApiResponsePrisms.success();

    List<ApiResponse> responses =
        List.of(
            new Success(new ResponseData("OK", 100), "2024-01-01"),
            new ClientError("Not Found", 404),
            new Success(new ResponseData("Created", 50), "2024-01-02"),
            new ServerError("Internal Error", "stack..."),
            new ClientError("Bad Request", 400));

    // TODO: Filter to get only non-success responses (errors)
    // Hint: responses.stream().filter(successPrism::doesNotMatch).collect(...)
    List<ApiResponse> errors = answerRequired();

    assertThat(errors).hasSize(3);
    assertThat(errors).noneMatch(r -> r instanceof Success);
  }

  /**
   * Exercise 5: Combining matches and doesNotMatch
   *
   * <p>Task: Partition responses into successes and errors
   */
  @Test
  void exercise5_partitioningWithPrisms() {
    Prism<ApiResponse, Success> successPrism = ApiResponsePrisms.success();

    List<ApiResponse> responses =
        List.of(
            new Success(new ResponseData("OK", 100), "2024-01-01"),
            new ClientError("Not Found", 404),
            new Success(new ResponseData("Created", 50), "2024-01-02"),
            new ServerError("Internal Error", "stack..."));

    // TODO: Get all successful responses
    List<ApiResponse> successes = answerRequired();

    // TODO: Get all error responses (non-successes)
    List<ApiResponse> errors = answerRequired();

    assertThat(successes).hasSize(2);
    assertThat(errors).hasSize(2);
    assertThat(successes.size() + errors.size()).isEqualTo(responses.size());
  }

  // =========================================================================
  // Part 3: Cross-Optic Composition - Lens + Prism
  // =========================================================================

  /**
   * Exercise 6: Lens then Prism composition
   *
   * <p>When you compose a Lens with a Prism, you get a Traversal. This is because the Prism may not
   * match, resulting in zero-or-one focus.
   *
   * <p>Pattern: Lens >>> Prism = Traversal
   *
   * <p>Task: Access the database settings from a Config through the Optional field
   */
  @Test
  void exercise6_lensThenPrism() {
    Lens<Config, Optional<DatabaseSettings>> databaseLens =
        Lens.of(Config::database, (config, db) -> new Config(config.name(), db));

    Prism<Optional<DatabaseSettings>, DatabaseSettings> somePrism = Prisms.some();

    // TODO: Compose the lens with the prism to get a Traversal
    // Hint: databaseLens.andThen(somePrism)
    Traversal<Config, DatabaseSettings> databaseTraversal = answerRequired();

    Config withDb = new Config("prod", Optional.of(new DatabaseSettings("localhost", 5432)));
    Config withoutDb = new Config("dev", Optional.empty());

    // TODO: Get all database settings from withDb (should have 1 element)
    // Hint: Traversals.getAll(traversal, source)
    List<DatabaseSettings> foundDb = answerRequired();

    // TODO: Get all database settings from withoutDb (should be empty)
    List<DatabaseSettings> notFoundDb = answerRequired();

    assertThat(foundDb).hasSize(1);
    assertThat(foundDb.get(0).host()).isEqualTo("localhost");
    assertThat(notFoundDb).isEmpty();
  }

  // =========================================================================
  // Part 4: Cross-Optic Composition - Prism + Lens
  // =========================================================================

  /**
   * Exercise 7: Prism then Lens composition
   *
   * <p>When you compose a Prism with a Lens, you also get a Traversal. This lets you access fields
   * within a specific variant of a sum type.
   *
   * <p>Pattern: Prism >>> Lens = Traversal
   *
   * <p>Task: Access the ResponseData content from Success responses only
   */
  @Test
  void exercise7_prismThenLens() {
    Prism<ApiResponse, Success> successPrism = ApiResponsePrisms.success();

    Lens<Success, ResponseData> dataLens =
        Lens.of(Success::data, (success, data) -> new Success(data, success.timestamp()));

    Lens<ResponseData, String> contentLens =
        Lens.of(ResponseData::content, (rd, content) -> new ResponseData(content, rd.size()));

    // TODO: Compose successPrism with dataLens to get a Traversal to ResponseData
    // Hint: successPrism.andThen(dataLens)
    Traversal<ApiResponse, ResponseData> successDataTraversal = answerRequired();

    ApiResponse success = new Success(new ResponseData("Hello", 5), "2024-01-01");
    ApiResponse error = new ClientError("Not Found", 404);

    // TODO: Get ResponseData from success (should have 1 element)
    List<ResponseData> successData = answerRequired();

    // TODO: Get ResponseData from error (should be empty)
    List<ResponseData> errorData = answerRequired();

    assertThat(successData).hasSize(1);
    assertThat(successData.get(0).content()).isEqualTo("Hello");
    assertThat(errorData).isEmpty();
  }

  /**
   * Exercise 8: Chaining cross-optic compositions
   *
   * <p>After getting a Traversal from Lens+Prism or Prism+Lens, you can chain further with more
   * optics. Use {@code lens.asTraversal()} when chaining a Lens after a Traversal.
   *
   * <p>Task: Modify the content of Success responses to uppercase
   */
  @Test
  void exercise8_chainingCompositions() {
    Prism<ApiResponse, Success> successPrism = ApiResponsePrisms.success();

    Lens<Success, ResponseData> dataLens =
        Lens.of(Success::data, (success, data) -> new Success(data, success.timestamp()));

    Lens<ResponseData, String> contentLens =
        Lens.of(ResponseData::content, (rd, content) -> new ResponseData(content, rd.size()));

    // TODO: Build a traversal from ApiResponse to content String
    // Chain: successPrism.andThen(dataLens).andThen(contentLens.asTraversal())
    // Note: After Prism+Lens=Traversal, use lens.asTraversal() for the next lens
    Traversal<ApiResponse, String> contentTraversal = answerRequired();

    ApiResponse success = new Success(new ResponseData("hello world", 11), "2024-01-01");
    ApiResponse error = new ClientError("Not Found", 404);

    // TODO: Modify the content to uppercase for success response
    // Hint: Traversals.modify(traversal, String::toUpperCase, source)
    ApiResponse modifiedSuccess = answerRequired();

    // TODO: Try to modify error (should be unchanged)
    ApiResponse unchangedError = answerRequired();

    // Verify success was modified
    assertThat(modifiedSuccess).isInstanceOf(Success.class);
    Success s = (Success) modifiedSuccess;
    assertThat(s.data().content()).isEqualTo("HELLO WORLD");

    // Verify error is unchanged
    assertThat(unchangedError).isEqualTo(error);
  }

  /**
   * Congratulations! You've completed Tutorial 10: Advanced Prism Patterns
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to use {@code nearly} for predicate-based matching
   *   <li>✓ The difference between {@code only} (exact) and {@code nearly} (predicate)
   *   <li>✓ How to use {@code doesNotMatch} for exclusion filtering
   *   <li>✓ Lens + Prism = Traversal composition pattern
   *   <li>✓ Prism + Lens = Traversal composition pattern
   *   <li>✓ How to chain compositions using {@code lens.asTraversal()}
   * </ul>
   *
   * <p>These patterns are essential for working with complex domain models that mix product types
   * (records) with sum types (sealed interfaces) and optional values.
   */
}
