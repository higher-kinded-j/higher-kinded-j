// Copyright (c) 2025 - 2026 Magnus Smith
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 10: Advanced Prism Patterns.
 *
 * <p>Pain → Promise. Once we have a basic prism per sum-type variant we want to filter on
 * predicates ("only orders with quantity > 0"), exclude ("everything that is not Cancelled"), and
 * compose lenses across the focused part of a variant. Hand-rolled, that is a maze of {@code
 * instanceof} + boolean logic per call site.
 *
 * <pre>
 *   // We want: every PaidOrder where amount &gt; threshold, mark as VIP.
 *   // Imperative: instanceof PaidOrder, getter chains, copy-construct, repeat.
 * </pre>
 *
 * <p>This tutorial introduces the building blocks that make those queries declarative: {@code
 * nearly} (predicate matching), {@code doesNotMatch} (exclusion), and the composition rules (Lens +
 * Prism = Traversal; Prism + Lens = Traversal).
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
   * <pre>
   *   // Nudge:    Prisms.nearly(default, predicate); the predicate matches non-empty strings.
   *   // Strategy: Prisms.nearly("default", s -&gt; !s.isEmpty())
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: Prisms.nearly for predicate-based prisms")
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
   * Exercise 2: nearly for numeric validation.
   *
   * <pre>
   *   // Nudge:    Default 1, predicate n &gt; 0; then filter through matches.
   *   // Strategy: Prisms.nearly(1, n -&gt; n &gt; 0)
   *   //           numbers.stream().filter(positivePrism::matches).toList()
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: Prisms.nearly for positive integers")
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
   * Exercise 3: only vs nearly.
   *
   * <pre>
   *   // Nudge:    nearly takes a predicate; here we want startsWith("hello").
   *   // Strategy: Prisms.nearly("hello", s -&gt; s.startsWith("hello"))
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: only matches exact values; nearly matches by predicate")
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
   * Exercise 4: doesNotMatch for exclusion.
   *
   * <pre>
   *   // Nudge:    Stream filter through prism::doesNotMatch.
   *   // Strategy: responses.stream().filter(successPrism::doesNotMatch).toList()
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: doesNotMatch filters out the focused variant")
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
   * Exercise 5: matches + doesNotMatch to partition a list.
   *
   * <pre>
   *   // Nudge:    Two filters, one with matches, one with doesNotMatch.
   *   // Strategy: responses.stream().filter(successPrism::matches).toList()
   *   //           responses.stream().filter(successPrism::doesNotMatch).toList()
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: partition via matches + doesNotMatch")
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
   * Exercise 6: Lens andThen Prism = Traversal.
   *
   * <pre>
   *   // Nudge:    databaseLens.andThen(somePrism); use Traversals.getAll to see 0..1 results.
   *   // Strategy: databaseLens.andThen(somePrism)
   *   //           Traversals.getAll(traversal, source)
   *   // Spoiler:  exactly that for both placeholders.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: Lens andThen Prism produces a Traversal")
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
   * Exercise 7: Prism andThen Lens = Traversal.
   *
   * <pre>
   *   // Nudge:    successPrism.andThen(dataLens) for the Traversal; Traversals.getAll to read.
   *   // Strategy: successPrism.andThen(dataLens)
   *   //           Traversals.getAll(traversal, source)
   *   // Spoiler:  exactly that for both placeholders.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: Prism andThen Lens produces a Traversal")
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
   * Exercise 8: Chain Prism + Lens + Lens (with asTraversal at the end).
   *
   * <pre>
   *   // Nudge:    After Prism.andThen(Lens) we have a Traversal; chain another Lens via
   *   //           .asTraversal().
   *   // Strategy: successPrism.andThen(dataLens).andThen(contentLens.asTraversal())
   *   //           Traversals.modify(traversal, String::toUpperCase, source)
   *   // Spoiler:  exactly that for all three placeholders.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 8: chain Prism + Lens + Lens via asTraversal")
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
