// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 10: Advanced Prism Patterns - SOLUTIONS
 *
 * <p>This file contains the solutions for Tutorial 10. Compare your answers with these solutions
 * after attempting each exercise.
 */
public class Tutorial10_AdvancedPrismPatterns_Solution {

  // --- Domain models for exercises ---

  sealed interface ApiResponse permits Success, ClientError, ServerError {}

  record Success(ResponseData data, String timestamp) implements ApiResponse {}

  record ClientError(String message, int code) implements ApiResponse {}

  record ServerError(String message, String stackTrace) implements ApiResponse {}

  record ResponseData(String content, int size) {}

  record Config(String name, Optional<DatabaseSettings> database) {}

  record DatabaseSettings(String host, int port) {}

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

  @Test
  void exercise1_nearlyForNonEmptyStrings() {
    // SOLUTION: Create a nearly prism that matches non-empty strings
    Prism<String, Unit> nonEmptyPrism = Prisms.nearly("default", s -> !s.isEmpty());

    // Test matching
    assertThat(nonEmptyPrism.matches("hello")).isTrue();
    assertThat(nonEmptyPrism.matches("")).isFalse();
    assertThat(nonEmptyPrism.matches("  ")).isTrue(); // Whitespace is non-empty

    // Test build returns default
    assertThat(nonEmptyPrism.build(Unit.INSTANCE)).isEqualTo("default");
  }

  @Test
  void exercise2_nearlyForPositiveNumbers() {
    // SOLUTION: Create a nearly prism that matches positive integers
    Prism<Integer, Unit> positivePrism = Prisms.nearly(1, n -> n > 0);

    List<Integer> numbers = List.of(-5, 0, 3, -2, 7, 10, -1);

    // SOLUTION: Filter using the prism's matches method
    List<Integer> positives =
        numbers.stream().filter(positivePrism::matches).collect(Collectors.toList());

    assertThat(positives).containsExactly(3, 7, 10);
  }

  @Test
  void exercise3_onlyVsNearly() {
    // 'only' matches exact values
    Prism<String, Unit> exactHelloPrism = Prisms.only("hello");

    // SOLUTION: Create a 'nearly' prism that matches strings starting with "hello"
    Prism<String, Unit> startsWithHelloPrism = Prisms.nearly("hello", s -> s.startsWith("hello"));

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

    // SOLUTION: Filter using doesNotMatch to get non-success responses
    List<ApiResponse> errors =
        responses.stream().filter(successPrism::doesNotMatch).collect(Collectors.toList());

    assertThat(errors).hasSize(3);
    assertThat(errors).noneMatch(r -> r instanceof Success);
  }

  @Test
  void exercise5_partitioningWithPrisms() {
    Prism<ApiResponse, Success> successPrism = ApiResponsePrisms.success();

    List<ApiResponse> responses =
        List.of(
            new Success(new ResponseData("OK", 100), "2024-01-01"),
            new ClientError("Not Found", 404),
            new Success(new ResponseData("Created", 50), "2024-01-02"),
            new ServerError("Internal Error", "stack..."));

    // SOLUTION: Use matches for successes
    List<ApiResponse> successes =
        responses.stream().filter(successPrism::matches).collect(Collectors.toList());

    // SOLUTION: Use doesNotMatch for errors
    List<ApiResponse> errors =
        responses.stream().filter(successPrism::doesNotMatch).collect(Collectors.toList());

    assertThat(successes).hasSize(2);
    assertThat(errors).hasSize(2);
    assertThat(successes.size() + errors.size()).isEqualTo(responses.size());
  }

  // =========================================================================
  // Part 3: Cross-Optic Composition - Lens + Prism
  // =========================================================================

  @Test
  void exercise6_lensThenPrism() {
    Lens<Config, Optional<DatabaseSettings>> databaseLens =
        Lens.of(Config::database, (config, db) -> new Config(config.name(), db));

    Prism<Optional<DatabaseSettings>, DatabaseSettings> somePrism = Prisms.some();

    // SOLUTION: Compose lens with prism to get Traversal
    Traversal<Config, DatabaseSettings> databaseTraversal = databaseLens.andThen(somePrism);

    Config withDb = new Config("prod", Optional.of(new DatabaseSettings("localhost", 5432)));
    Config withoutDb = new Config("dev", Optional.empty());

    // SOLUTION: Use Traversals.getAll to extract values
    List<DatabaseSettings> foundDb = Traversals.getAll(databaseTraversal, withDb);
    List<DatabaseSettings> notFoundDb = Traversals.getAll(databaseTraversal, withoutDb);

    assertThat(foundDb).hasSize(1);
    assertThat(foundDb.get(0).host()).isEqualTo("localhost");
    assertThat(notFoundDb).isEmpty();
  }

  // =========================================================================
  // Part 4: Cross-Optic Composition - Prism + Lens
  // =========================================================================

  @Test
  void exercise7_prismThenLens() {
    Prism<ApiResponse, Success> successPrism = ApiResponsePrisms.success();

    Lens<Success, ResponseData> dataLens =
        Lens.of(Success::data, (success, data) -> new Success(data, success.timestamp()));

    Lens<ResponseData, String> contentLens =
        Lens.of(ResponseData::content, (rd, content) -> new ResponseData(content, rd.size()));

    // SOLUTION: Compose prism with lens to get Traversal
    Traversal<ApiResponse, ResponseData> successDataTraversal = successPrism.andThen(dataLens);

    ApiResponse success = new Success(new ResponseData("Hello", 5), "2024-01-01");
    ApiResponse error = new ClientError("Not Found", 404);

    // SOLUTION: Use Traversals.getAll
    List<ResponseData> successData = Traversals.getAll(successDataTraversal, success);
    List<ResponseData> errorData = Traversals.getAll(successDataTraversal, error);

    assertThat(successData).hasSize(1);
    assertThat(successData.get(0).content()).isEqualTo("Hello");
    assertThat(errorData).isEmpty();
  }

  @Test
  void exercise8_chainingCompositions() {
    Prism<ApiResponse, Success> successPrism = ApiResponsePrisms.success();

    Lens<Success, ResponseData> dataLens =
        Lens.of(Success::data, (success, data) -> new Success(data, success.timestamp()));

    Lens<ResponseData, String> contentLens =
        Lens.of(ResponseData::content, (rd, content) -> new ResponseData(content, rd.size()));

    // SOLUTION: Chain compositions
    // Prism + Lens = Traversal, then use lens.asTraversal() for the next lens
    Traversal<ApiResponse, String> contentTraversal =
        successPrism.andThen(dataLens).andThen(contentLens.asTraversal());

    ApiResponse success = new Success(new ResponseData("hello world", 11), "2024-01-01");
    ApiResponse error = new ClientError("Not Found", 404);

    // SOLUTION: Use Traversals.modify
    ApiResponse modifiedSuccess = Traversals.modify(contentTraversal, String::toUpperCase, success);
    ApiResponse unchangedError = Traversals.modify(contentTraversal, String::toUpperCase, error);

    // Verify success was modified
    assertThat(modifiedSuccess).isInstanceOf(Success.class);
    Success s = (Success) modifiedSuccess;
    assertThat(s.data().content()).isEqualTo("HELLO WORLD");

    // Verify error is unchanged
    assertThat(unchangedError).isEqualTo(error);
  }
}
