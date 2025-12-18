// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Semigroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for ValidationPath.
 *
 * <p>Verifies that ValidationPath satisfies Functor and Monad laws:
 *
 * <h2>Functor Laws</h2>
 *
 * <ul>
 *   <li>Identity: {@code path.map(id) == path}
 *   <li>Composition: {@code path.map(f).map(g) == path.map(g.compose(f))}
 * </ul>
 *
 * <h2>Monad Laws</h2>
 *
 * <ul>
 *   <li>Left Identity: {@code Path.valid(a, sg).via(f) == f(a)}
 *   <li>Right Identity: {@code path.via(x -> Path.valid(x, sg)) == path}
 *   <li>Associativity: {@code path.via(f).via(g) == path.via(x -> f(x).via(g))}
 * </ul>
 *
 * <p>Note: ValidationPath's short-circuit mode (via) is tested here for Monad laws. The
 * accumulating mode (zipWithAccum) has different semantics and is tested separately.
 */
@DisplayName("ValidationPath Law Verification Tests")
class ValidationPathLawsTest {

  // Test values
  private static final int TEST_VALUE = 42;
  private static final String TEST_ERROR = "error";

  // Semigroup for error accumulation
  private static final Semigroup<String> STRING_SEMIGROUP = (a, b) -> a + ", " + b;

  // Test functions
  private static final Function<Integer, Integer> ADD_ONE = x -> x + 1;
  private static final Function<Integer, Integer> DOUBLE = x -> x * 2;
  private static final Function<Integer, String> INT_TO_STRING = x -> "value:" + x;
  private static final Function<String, Integer> STRING_LENGTH = String::length;

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawsTests {

    @TestFactory
    @DisplayName("Functor Identity Law: path.map(id) == path")
    Stream<DynamicTest> functorIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Identity law holds for Valid",
              () -> {
                ValidationPath<String, Integer> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
                ValidationPath<String, Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for Invalid",
              () -> {
                ValidationPath<String, Integer> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
                ValidationPath<String, Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for Valid",
              () -> {
                ValidationPath<String, Integer> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

                // Left side: path.map(f).map(g)
                ValidationPath<String, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);

                // Right side: path.map(g.compose(f))
                ValidationPath<String, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for Invalid",
              () -> {
                ValidationPath<String, Integer> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

                ValidationPath<String, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                ValidationPath<String, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                ValidationPath<String, Integer> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

                // map Integer -> String -> Integer
                ValidationPath<String, Integer> leftSide =
                    path.map(INT_TO_STRING).map(STRING_LENGTH);
                ValidationPath<String, Integer> rightSide =
                    path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    // Monadic functions for testing
    private final Function<Integer, ValidationPath<String, String>> intToValidationString =
        x ->
            x > 0
                ? Path.valid("positive:" + x, STRING_SEMIGROUP)
                : Path.invalid("not positive", STRING_SEMIGROUP);

    private final Function<String, ValidationPath<String, Integer>> stringToValidationInt =
        s ->
            s.length() > 5
                ? Path.valid(s.length(), STRING_SEMIGROUP)
                : Path.invalid("too short", STRING_SEMIGROUP);

    private final Function<Integer, ValidationPath<String, Integer>> safeDouble =
        x ->
            x < 1000
                ? Path.valid(x * 2, STRING_SEMIGROUP)
                : Path.invalid("too large", STRING_SEMIGROUP);

    @TestFactory
    @DisplayName("Left Identity Law: Path.valid(a, sg).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity when f returns Valid",
              () -> {
                int value = 10;

                // Left side: Path.valid(a, sg).via(f)
                ValidationPath<String, String> leftSide =
                    Path.valid(value, STRING_SEMIGROUP).via(intToValidationString);

                // Right side: f(a)
                ValidationPath<String, String> rightSide = intToValidationString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity when f returns Invalid",
              () -> {
                int value = -5;

                ValidationPath<String, String> leftSide =
                    Path.valid(value, STRING_SEMIGROUP).via(intToValidationString);
                ValidationPath<String, String> rightSide = intToValidationString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> Path.valid(x, sg)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for Valid",
              () -> {
                ValidationPath<String, Integer> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

                ValidationPath<String, Integer> result =
                    path.via(x -> Path.valid(x, STRING_SEMIGROUP));

                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for Invalid",
              () -> {
                ValidationPath<String, Integer> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

                ValidationPath<String, Integer> result =
                    path.via(x -> Path.valid(x, STRING_SEMIGROUP));

                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for Valid with successful chain",
              () -> {
                ValidationPath<String, Integer> path = Path.valid(10, STRING_SEMIGROUP);

                // Left side: path.via(f).via(g)
                ValidationPath<String, Integer> leftSide =
                    path.via(intToValidationString).via(stringToValidationInt);

                // Right side: path.via(x -> f(x).via(g))
                ValidationPath<String, Integer> rightSide =
                    path.via(x -> intToValidationString.apply(x).via(stringToValidationInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when first function returns Invalid",
              () -> {
                ValidationPath<String, Integer> path = Path.valid(-5, STRING_SEMIGROUP);

                ValidationPath<String, Integer> leftSide =
                    path.via(intToValidationString).via(stringToValidationInt);
                ValidationPath<String, Integer> rightSide =
                    path.via(x -> intToValidationString.apply(x).via(stringToValidationInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when second function returns Invalid",
              () -> {
                ValidationPath<String, Integer> path =
                    Path.valid(1, STRING_SEMIGROUP); // produces "positive:1" (length 10)

                ValidationPath<String, Integer> leftSide =
                    path.via(intToValidationString).via(stringToValidationInt);
                ValidationPath<String, Integer> rightSide =
                    path.via(x -> intToValidationString.apply(x).via(stringToValidationInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for Invalid",
              () -> {
                ValidationPath<String, Integer> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

                ValidationPath<String, Integer> leftSide =
                    path.via(intToValidationString).via(stringToValidationInt);
                ValidationPath<String, Integer> rightSide =
                    path.via(x -> intToValidationString.apply(x).via(stringToValidationInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity with same-type chain",
              () -> {
                ValidationPath<String, Integer> path = Path.valid(100, STRING_SEMIGROUP);
                Function<Integer, ValidationPath<String, Integer>> addTen =
                    x -> Path.valid(x + 10, STRING_SEMIGROUP);

                ValidationPath<String, Integer> leftSide = path.via(safeDouble).via(addTen);
                ValidationPath<String, Integer> rightSide =
                    path.via(x -> safeDouble.apply(x).via(addTen));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("map preserves structure")
    Stream<DynamicTest> mapPreservesStructure() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "map over Valid produces Valid",
              () -> {
                ValidationPath<String, Integer> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);
                ValidationPath<String, String> result = path.map(Object::toString);
                assertThat(result.isValid()).isTrue();
              }),
          DynamicTest.dynamicTest(
              "map over Invalid produces Invalid",
              () -> {
                ValidationPath<String, Integer> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);
                ValidationPath<String, String> result = path.map(Object::toString);
                assertThat(result.isInvalid()).isTrue();
              }));
    }

    @TestFactory
    @DisplayName("via is consistent with flatMap")
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> viaConsistentWithFlatMap() {
      Function<Integer, ValidationPath<String, String>> f =
          x -> Path.valid("value:" + x, STRING_SEMIGROUP);

      return Stream.of(
          DynamicTest.dynamicTest(
              "via and flatMap produce same result for Valid",
              () -> {
                ValidationPath<String, Integer> path = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

                ValidationPath<String, String> viaResult = path.via(f);
                // flatMap returns Chainable<B>, cast to ValidationPath
                ValidationPath<String, String> flatMapResult =
                    (ValidationPath<String, String>) path.flatMap(f);

                assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
              }),
          DynamicTest.dynamicTest(
              "via and flatMap produce same result for Invalid",
              () -> {
                ValidationPath<String, Integer> path = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

                ValidationPath<String, String> viaResult = path.via(f);
                // flatMap returns Chainable<B>, cast to ValidationPath
                ValidationPath<String, String> flatMapResult =
                    (ValidationPath<String, String>) path.flatMap(f);

                assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
              }));
    }

    @TestFactory
    @DisplayName("short-circuit vs accumulating mode")
    Stream<DynamicTest> shortCircuitVsAccumulating() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "via short-circuits on first error",
              () -> {
                ValidationPath<String, Integer> path = Path.invalid("first", STRING_SEMIGROUP);
                ValidationPath<String, Integer> path2 = Path.invalid("second", STRING_SEMIGROUP);

                // via short-circuits - only first error
                ValidationPath<String, Integer> result =
                    path.via(x -> Path.valid(x, STRING_SEMIGROUP)).via(x -> path2);

                assertThat(result.isInvalid()).isTrue();
                assertThat(result.run().getError()).isEqualTo("first");
              }),
          DynamicTest.dynamicTest(
              "zipWithAccum accumulates errors",
              () -> {
                ValidationPath<String, Integer> path1 = Path.invalid("first", STRING_SEMIGROUP);
                ValidationPath<String, Integer> path2 = Path.invalid("second", STRING_SEMIGROUP);

                // zipWithAccum accumulates both errors
                ValidationPath<String, Integer> result = path1.zipWithAccum(path2, Integer::sum);

                assertThat(result.isInvalid()).isTrue();
                assertThat(result.run().getError()).isEqualTo("first, second");
              }));
    }
  }
}
