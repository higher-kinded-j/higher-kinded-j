// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial10 AdvancedPrismPatterns — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
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

  /**
   * Why this is idiomatic: {@code Prisms.nearly(default, predicate)} promotes any predicate to a
   * prism. The match is "is the input non-empty?" and the build side returns the supplied default —
   * a clean bridge from value-level checks to optic composition.
   *
   * <p>Alternative: {@code s -> !s.isEmpty()} as a plain {@code Predicate}. Same boolean answer;
   * the prism is what other optics ({@code .andThen}, {@code .filtered}) attach to.
   *
   * <p>Common wrong attempt: assume the default value is the matched value. {@code build} returns
   * the constant you supplied, regardless of which input matched; the prism is one-way.
   */
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

  /**
   * Why this is idiomatic: {@code positivePrism::matches} as a method reference slots straight into
   * {@code Stream.filter}. The optic is the data; the stream is the iteration.
   *
   * <p>Alternative: a hand-rolled lambda {@code n -> n > 0}. Equivalent for this single filter; the
   * prism reference stays consistent with the rest of the optic-driven code.
   *
   * <p>Common wrong attempt: build a {@code List<Predicate<Integer>>} alongside the prism and keep
   * them in sync manually. Two definitions of "positive" drift; the prism is the single source.
   */
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

  /**
   * Why this is idiomatic: {@code Prisms.only(value)} is the equality-based prism; {@code
   * Prisms.nearly(default, predicate)} is the predicate-based one. Each names a different intent —
   * "exact match" vs. "category match".
   *
   * <p>Alternative: write {@code only} in terms of {@code nearly} with an equality predicate. Same
   * runtime; the named {@code only} reads better when an exact value is meant.
   *
   * <p>Common wrong attempt: pick {@code only} when "starts with" or "is positive" was meant. The
   * match is too narrow and the next test case breaks; reach for {@code nearly} as soon as the
   * predicate goes beyond equality.
   */
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

  /**
   * Why this is idiomatic: {@code successPrism::doesNotMatch} reads as "everything except a
   * success" and slots into {@code Stream.filter} as a method reference. Negation lives in the
   * prism, not in a stray lambda.
   *
   * <p>Alternative: {@code .filter(r -> !successPrism.matches(r))}. Same answer; the named negation
   * is the cleaner spelling.
   *
   * <p>Common wrong attempt: filter on {@code !(r instanceof Success)} and lose the prism. Now a
   * refactor of the prism's predicate has to be tracked through the stream filter as well; using
   * the prism keeps a single source of truth.
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

    // SOLUTION: Filter using doesNotMatch to get non-success responses
    List<ApiResponse> errors =
        responses.stream().filter(successPrism::doesNotMatch).collect(Collectors.toList());

    assertThat(errors).hasSize(3);
    assertThat(errors).noneMatch(r -> r instanceof Success);
  }

  /**
   * Why this is idiomatic: one prism, two complementary streams — {@code matches} for the positive
   * class, {@code doesNotMatch} for the rest. The split is symmetric, parallel-safe, and the
   * assertion that sizes add up is a quick sanity check.
   *
   * <p>Alternative: {@code Collectors.partitioningBy(successPrism::matches)}. Single pass, returns
   * a {@code Map<Boolean, List<...>>}; reach for it when the two sides are processed together.
   *
   * <p>Common wrong attempt: filter once and "infer" the rest by removing the matched items from
   * the original list. Order changes and equality on the wrapper types makes this fragile; let the
   * prism do the partition.
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

  /**
   * Why this is idiomatic: lens-into-{@code Optional}-field then {@code Prisms.some()} produces an
   * {@code Affine<Config, DatabaseSettings>}. The affine reads partially (config may not have a
   * database) and writes totally (replacing or installing the settings).
   *
   * <p>Alternative: read the option with the lens and {@code map}/{@code orElse} on it. Same
   * answer; the affine packages the path so {@code modify} and {@code matches} attach to the same
   * definition.
   *
   * <p>Common wrong attempt: forget {@code Prisms.some()} and try to compose the lens with a custom
   * prism. The library already provides the canonical "Optional → present" prism; use it.
   */
  @Test
  void exercise6_lensThenPrism() {
    Lens<Config, Optional<DatabaseSettings>> databaseLens =
        Lens.of(Config::database, (config, db) -> new Config(config.name(), db));

    Prism<Optional<DatabaseSettings>, DatabaseSettings> somePrism = Prisms.some();

    // SOLUTION: Compose lens with prism to get Affine
    Affine<Config, DatabaseSettings> databaseAffine = databaseLens.andThen(somePrism);

    Config withDb = new Config("prod", Optional.of(new DatabaseSettings("localhost", 5432)));
    Config withoutDb = new Config("dev", Optional.empty());

    // SOLUTION: Use Affine.getOptional to extract values
    Optional<DatabaseSettings> foundDb = databaseAffine.getOptional(withDb);
    Optional<DatabaseSettings> notFoundDb = databaseAffine.getOptional(withoutDb);

    assertThat(foundDb).isPresent();
    assertThat(foundDb.get().host()).isEqualTo("localhost");
    assertThat(notFoundDb).isEmpty();
  }

  // =========================================================================
  // Part 4: Cross-Optic Composition - Prism + Lens
  // =========================================================================

  /**
   * Why this is idiomatic: prism-into-variant then lens-into-field is the symmetric counterpart to
   * the previous exercise. Reading is partial (only success responses match); writing is partial
   * too (errors pass through unchanged).
   *
   * <p>Alternative: {@code if (resp instanceof Success s) ...} guards. Same outcome locally; the
   * affine composes with other paths and stays inspectable as data.
   *
   * <p>Common wrong attempt: compose lens-then-prism in this direction; the types disagree because
   * the lens's source type would have to be the variant, not the sealed parent. Lead with the prism
   * when the input is the sum type.
   */
  @Test
  void exercise7_prismThenLens() {
    Prism<ApiResponse, Success> successPrism = ApiResponsePrisms.success();

    Lens<Success, ResponseData> dataLens =
        Lens.of(Success::data, (success, data) -> new Success(data, success.timestamp()));

    Lens<ResponseData, String> contentLens =
        Lens.of(ResponseData::content, (rd, content) -> new ResponseData(content, rd.size()));

    // SOLUTION: Compose prism with lens to get Affine
    Affine<ApiResponse, ResponseData> successDataAffine = successPrism.andThen(dataLens);

    ApiResponse success = new Success(new ResponseData("Hello", 5), "2024-01-01");
    ApiResponse error = new ClientError("Not Found", 404);

    // SOLUTION: Use Affine.getOptional
    Optional<ResponseData> successData = successDataAffine.getOptional(success);
    Optional<ResponseData> errorData = successDataAffine.getOptional(error);

    assertThat(successData).isPresent();
    assertThat(successData.get().content()).isEqualTo("Hello");
    assertThat(errorData).isEmpty();
  }

  /**
   * Why this is idiomatic: prism + lens + lens chains into a {@code Traversal<ApiResponse,
   * String>}. Each step is one concern, and the trailing lenses are lifted with {@code
   * asTraversal()} to keep the whole chain in the same optic family.
   *
   * <p>Alternative: bind intermediate {@code Affine}s to local variables and compose with {@code
   * andThen}. Equivalent; the inline chain is fine when the path is read-once and the named version
   * wins when the same path is reused.
   *
   * <p>Common wrong attempt: forget the {@code asTraversal()} lifts and mix optic types. Some
   * {@code andThen} overloads accept mixed kinds, but the chain becomes harder to read; be explicit
   * and the diagnostics improve too.
   */
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
