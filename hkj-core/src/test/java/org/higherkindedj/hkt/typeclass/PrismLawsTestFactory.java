// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.prism.Prisms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Dynamic test factory for Prism laws using JUnit 5's @TestFactory.
 *
 * <p>This class demonstrates how to use {@code @TestFactory} to generate tests dynamically at
 * runtime, providing comprehensive law testing across all prism implementations with minimal
 * boilerplate.
 *
 * <p>Prism laws tested:
 *
 * <ul>
 *   <li><b>Review law:</b> {@code getOptional(build(a)) == Some(a)} - Building then extracting
 *       returns the original value
 *   <li><b>Consistency:</b> {@code modify} should only affect values that match the prism
 * </ul>
 *
 * <p>Note: The full "Preview-Review" law ({@code getOptional(s) == Some(a) => build(a) == s}) only
 * holds for reversible prisms. Since prisms work with sum types where not all cases match, we focus
 * on the universal Review law and behavioral consistency.
 *
 * <p>Benefits of @TestFactory approach:
 *
 * <ul>
 *   <li>Tests are generated at runtime based on actual prism implementations
 *   <li>Adding new prisms automatically adds test coverage
 *   <li>Clear, structured test output showing which prism/law combination passed/failed
 *   <li>Each test runs independently with proper isolation
 * </ul>
 */
@DisplayName("Prism Laws - Dynamic Test Factory")
class PrismLawsTestFactory {

  // Test data structures
  sealed interface Json permits JsonString, JsonNumber {}

  record JsonString(String value) implements Json {}

  record JsonNumber(int value) implements Json {}

  sealed interface Result permits Success, Failure {}

  record Success(String value) implements Result {}

  record Failure(String error) implements Result {}

  sealed interface Shape permits Circle, Rectangle {}

  record Circle(double radius) implements Shape {}

  record Rectangle(double width, double height) implements Shape {}

  /**
   * Test data record containing all information needed to test a prism.
   *
   * @param <S> the source type (sum type)
   * @param <A> the focus type (one case of the sum)
   */
  record PrismTestData<S, A>(
      String name,
      Prism<S, A> prism,
      S matchingValue,
      S nonMatchingValue,
      A testValue,
      A alternateValue) {

    static <S, A> PrismTestData<S, A> of(
        String name,
        Prism<S, A> prism,
        S matchingValue,
        S nonMatchingValue,
        A testValue,
        A alternateValue) {
      return new PrismTestData<>(
          name, prism, matchingValue, nonMatchingValue, testValue, alternateValue);
    }
  }

  /**
   * Provides test data for all prism implementations.
   *
   * <p>This is a centralized source of test data. Adding a new prism implementation requires only
   * adding one line here, and all law tests will automatically cover it.
   */
  private static Stream<PrismTestData<?, ?>> allPrisms() {
    // Json String prism
    Prism<Json, String> jsonStringPrism =
        Prism.of(
            json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
            JsonString::new);

    // Json Number prism
    Prism<Json, Integer> jsonNumberPrism =
        Prism.of(
            json -> json instanceof JsonNumber n ? Optional.of(n.value()) : Optional.empty(),
            JsonNumber::new);

    // Result Success prism
    Prism<Result, String> resultSuccessPrism =
        Prism.of(
            result -> result instanceof Success s ? Optional.of(s.value()) : Optional.empty(),
            Success::new);

    // Result Failure prism
    Prism<Result, String> resultFailurePrism =
        Prism.of(
            result -> result instanceof Failure f ? Optional.of(f.error()) : Optional.empty(),
            Failure::new);

    // Shape Circle prism
    Prism<Shape, Double> shapeCirclePrism =
        Prism.of(
            shape -> shape instanceof Circle c ? Optional.of(c.radius()) : Optional.empty(),
            Circle::new);

    // Shape Rectangle prism (extracts width)
    Prism<Shape, Double> shapeRectangleWidthPrism =
        Prism.of(
            shape -> shape instanceof Rectangle r ? Optional.of(r.width()) : Optional.empty(),
            width -> new Rectangle(width, 1.0));

    // Optional.some prism
    Prism<Optional<String>, String> optionalSomePrism = Prisms.some();

    // Optional.none prism
    Prism<Optional<String>, Unit> optionalNonePrism = Prisms.none();

    // Either.right prism
    Prism<Either<String, Integer>, Integer> eitherRightPrism =
        Prism.of(
            either -> either.isRight() ? Optional.of(either.getRight()) : Optional.empty(),
            Either::right);

    // Either.left prism
    Prism<Either<String, Integer>, String> eitherLeftPrism =
        Prism.of(
            either -> either.isLeft() ? Optional.of(either.getLeft()) : Optional.empty(),
            Either::left);

    // Composed prism: Result -> Success -> uppercase check
    Prism<String, String> uppercasePrism =
        Prism.of(
            s -> s.equals(s.toUpperCase()) ? Optional.of(s) : Optional.empty(),
            s -> s.toUpperCase());

    Prism<Result, String> resultUppercasePrism = resultSuccessPrism.andThen(uppercasePrism);

    return Stream.of(
        PrismTestData.of(
            "Json.string",
            jsonStringPrism,
            new JsonString("hello"),
            new JsonNumber(42),
            "test",
            "world"),
        PrismTestData.of(
            "Json.number", jsonNumberPrism, new JsonNumber(123), new JsonString("text"), 456, 789),
        PrismTestData.of(
            "Result.success",
            resultSuccessPrism,
            new Success("ok"),
            new Failure("error"),
            "data",
            "value"),
        PrismTestData.of(
            "Result.failure",
            resultFailurePrism,
            new Failure("error"),
            new Success("ok"),
            "bad",
            "wrong"),
        PrismTestData.of(
            "Shape.circle", shapeCirclePrism, new Circle(5.0), new Rectangle(10.0, 20.0), 3.5, 7.2),
        PrismTestData.of(
            "Shape.rectangle.width",
            shapeRectangleWidthPrism,
            new Rectangle(10.0, 5.0),
            new Circle(3.0),
            15.0,
            20.0),
        PrismTestData.of(
            "Optional.some",
            optionalSomePrism,
            Optional.of("present"),
            Optional.empty(),
            "value",
            "other"),
        PrismTestData.of(
            "Optional.none",
            optionalNonePrism,
            Optional.empty(),
            Optional.of("present"),
            Unit.INSTANCE,
            Unit.INSTANCE),
        PrismTestData.of(
            "Either.right", eitherRightPrism, Either.right(42), Either.left("error"), 100, 200),
        PrismTestData.of(
            "Either.left", eitherLeftPrism, Either.left("error"), Either.right(42), "bad", "wrong"),
        PrismTestData.of(
            "Result.uppercase (composed)",
            resultUppercasePrism,
            new Success("HELLO"),
            new Failure("error"),
            "WORLD",
            "TEST"));
  }

  /**
   * Helper method to test Review law for a specific prism.
   *
   * <p>Law: {@code getOptional(build(a)) == Some(a)}
   *
   * <p>Building a value and then extracting it should return the original value.
   */
  private <S, A> void testReviewLaw(PrismTestData<S, A> data) {
    Prism<S, A> prism = data.prism();
    A testValue = data.testValue();

    // Build the outer type from the value
    S built = prism.build(testValue);

    // Extract it back
    Optional<A> extracted = prism.getOptional(built);

    // Should match the original value
    assertThat(extracted).isPresent().contains(testValue);
  }

  /**
   * Dynamically generates tests for the Review law: {@code getOptional(build(a)) == Some(a)}
   *
   * <p>This test factory creates one test per prism implementation, each verifying that building a
   * value and then extracting it returns the original value.
   */
  @TestFactory
  @DisplayName("Review Law: getOptional(build(a)) = Some(a)")
  Stream<DynamicTest> reviewLaw() {
    return allPrisms()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies review law", () -> testReviewLaw(data)));
  }

  /**
   * Helper method to test that getOptional extracts matching values.
   *
   * <p>Consistency property: {@code getOptional} should extract values from matching cases.
   */
  private <S, A> void testGetOptionalMatches(PrismTestData<S, A> data) {
    Prism<S, A> prism = data.prism();
    S matchingValue = data.matchingValue();

    Optional<A> extracted = prism.getOptional(matchingValue);

    assertThat(extracted).isPresent();
  }

  /** Dynamically generates tests verifying that getOptional extracts values from matching cases. */
  @TestFactory
  @DisplayName("getOptional extracts matching values")
  Stream<DynamicTest> getOptionalMatches() {
    return allPrisms()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " extracts matching values", () -> testGetOptionalMatches(data)));
  }

  /**
   * Helper method to test that getOptional returns empty for non-matching values.
   *
   * <p>Consistency property: {@code getOptional} should return empty for non-matching cases.
   */
  private <S, A> void testGetOptionalNonMatches(PrismTestData<S, A> data) {
    Prism<S, A> prism = data.prism();
    S nonMatchingValue = data.nonMatchingValue();

    Optional<A> extracted = prism.getOptional(nonMatchingValue);

    assertThat(extracted).isEmpty();
  }

  /**
   * Dynamically generates tests verifying that getOptional returns empty for non-matching cases.
   */
  @TestFactory
  @DisplayName("getOptional returns empty for non-matching values")
  Stream<DynamicTest> getOptionalNonMatches() {
    return allPrisms()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " returns empty for non-matching",
                    () -> testGetOptionalNonMatches(data)));
  }

  /**
   * Provides test data for prisms with String focus type.
   *
   * <p>Used for testing modify operations that require String-specific transformations.
   */
  private static Stream<PrismTestData<?, String>> stringPrisms() {
    // Json String prism
    Prism<Json, String> jsonStringPrism =
        Prism.of(
            json -> json instanceof JsonString s ? Optional.of(s.value()) : Optional.empty(),
            JsonString::new);

    // Result Success prism
    Prism<Result, String> resultSuccessPrism =
        Prism.of(
            result -> result instanceof Success s ? Optional.of(s.value()) : Optional.empty(),
            Success::new);

    // Result Failure prism
    Prism<Result, String> resultFailurePrism =
        Prism.of(
            result -> result instanceof Failure f ? Optional.of(f.error()) : Optional.empty(),
            Failure::new);

    // Optional.some prism
    Prism<Optional<String>, String> optionalSomePrism = Prisms.some();

    // Either.left prism
    Prism<Either<String, Integer>, String> eitherLeftPrism =
        Prism.of(
            either -> either.isLeft() ? Optional.of(either.getLeft()) : Optional.empty(),
            Either::left);

    // Composed prism: Result -> Success -> uppercase check
    Prism<String, String> uppercasePrism =
        Prism.of(
            s -> s.equals(s.toUpperCase()) ? Optional.of(s) : Optional.empty(),
            s -> s.toUpperCase());

    Prism<Result, String> resultUppercasePrism = resultSuccessPrism.andThen(uppercasePrism);

    return Stream.of(
        PrismTestData.of(
            "Json.string",
            jsonStringPrism,
            new JsonString("hello"),
            new JsonNumber(42),
            "test",
            "world"),
        PrismTestData.of(
            "Result.success",
            resultSuccessPrism,
            new Success("ok"),
            new Failure("error"),
            "data",
            "value"),
        PrismTestData.of(
            "Result.failure",
            resultFailurePrism,
            new Failure("error"),
            new Success("ok"),
            "bad",
            "wrong"),
        PrismTestData.of(
            "Optional.some",
            optionalSomePrism,
            Optional.of("present"),
            Optional.empty(),
            "value",
            "other"),
        PrismTestData.of(
            "Either.left", eitherLeftPrism, Either.left("error"), Either.right(42), "bad", "wrong"),
        PrismTestData.of(
            "Result.uppercase (composed)",
            resultUppercasePrism,
            new Success("HELLO"),
            new Failure("error"),
            "WORLD",
            "TEST"));
  }

  /**
   * Helper method to test that modify only affects matching values.
   *
   * <p>Consistency property: {@code modify} should transform matching values and leave non-matching
   * values unchanged.
   */
  private <S> void testModifyOnlyAffectsMatching(PrismTestData<S, String> data) {
    Prism<S, String> prism = data.prism();
    S matchingValue = data.matchingValue();
    S nonMatchingValue = data.nonMatchingValue();

    // Modify matching value - should be transformed
    S modifiedMatching = prism.modify(String::toUpperCase, matchingValue);
    Optional<String> extractedMatching = prism.getOptional(modifiedMatching);
    assertThat(extractedMatching).isPresent();
    // The extracted value should be uppercase
    extractedMatching.ifPresent(val -> assertThat(val).isEqualTo(val.toUpperCase()));

    // Modify non-matching value - should be unchanged
    S modifiedNonMatching = prism.modify(String::toUpperCase, nonMatchingValue);
    assertThat(modifiedNonMatching).isSameAs(nonMatchingValue);
  }

  /**
   * Dynamically generates tests verifying that modify behaves correctly for matching and
   * non-matching values.
   */
  @TestFactory
  @DisplayName("modify only affects matching values")
  Stream<DynamicTest> modifyOnlyAffectsMatching() {
    return stringPrisms()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " modify only affects matching",
                    () -> testModifyOnlyAffectsMatching(data)));
  }

  /**
   * Helper method to test that matches correctly identifies matching values.
   *
   * <p>Convenience method consistency: {@code matches} should return true for matching values and
   * false for non-matching values.
   */
  private <S, A> void testMatchesConsistency(PrismTestData<S, A> data) {
    Prism<S, A> prism = data.prism();
    S matchingValue = data.matchingValue();
    S nonMatchingValue = data.nonMatchingValue();

    assertThat(prism.matches(matchingValue)).isTrue();
    assertThat(prism.matches(nonMatchingValue)).isFalse();
  }

  /**
   * Dynamically generates tests verifying that matches correctly identifies matching and
   * non-matching values.
   */
  @TestFactory
  @DisplayName("matches correctly identifies matching values")
  Stream<DynamicTest> matchesConsistency() {
    return allPrisms()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " matches is consistent", () -> testMatchesConsistency(data)));
  }
}
