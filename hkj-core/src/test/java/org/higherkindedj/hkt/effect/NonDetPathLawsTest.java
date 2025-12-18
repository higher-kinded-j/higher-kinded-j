// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for NonDetPath.
 *
 * <p>Verifies that NonDetPath satisfies Functor and Monad laws. NonDetPath uses Cartesian product
 * semantics for zipWith (different from ListPath's positional).
 */
@DisplayName("NonDetPath Law Verification Tests")
class NonDetPathLawsTest {

  private static final int TEST_VALUE = 42;
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
              "Identity law holds for single element",
              () -> {
                NonDetPath<Integer> path = NonDetPath.pure(TEST_VALUE);
                NonDetPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for multiple elements",
              () -> {
                NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3, 4, 5));
                NonDetPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for empty",
              () -> {
                NonDetPath<Integer> path = NonDetPath.empty();
                NonDetPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for single element",
              () -> {
                NonDetPath<Integer> path = NonDetPath.pure(TEST_VALUE);
                NonDetPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                NonDetPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for multiple elements",
              () -> {
                NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));
                NonDetPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                NonDetPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                NonDetPath<Integer> path = NonDetPath.pure(TEST_VALUE);
                NonDetPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                NonDetPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, NonDetPath<String>> intToNonDetString =
        x -> NonDetPath.of(List.of("a:" + x, "b:" + x));

    private final Function<String, NonDetPath<Integer>> stringToNonDetInt =
        s -> NonDetPath.pure(s.length());

    @TestFactory
    @DisplayName("Left Identity Law: NonDetPath.pure(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with list-returning function",
              () -> {
                int value = 10;
                NonDetPath<String> leftSide = NonDetPath.pure(value).via(intToNonDetString);
                NonDetPath<String> rightSide = intToNonDetString.apply(value);
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity with single-element function",
              () -> {
                int value = 10;
                Function<Integer, NonDetPath<Integer>> doubleIt = x -> NonDetPath.pure(x * 2);
                NonDetPath<Integer> leftSide = NonDetPath.pure(value).via(doubleIt);
                NonDetPath<Integer> rightSide = doubleIt.apply(value);
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> NonDetPath.pure(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for single element",
              () -> {
                NonDetPath<Integer> path = NonDetPath.pure(TEST_VALUE);
                NonDetPath<Integer> result = path.via(x -> NonDetPath.pure(x));
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for multiple elements",
              () -> {
                NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));
                NonDetPath<Integer> result = path.via(x -> NonDetPath.pure(x));
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for empty",
              () -> {
                NonDetPath<Integer> path = NonDetPath.empty();
                NonDetPath<Integer> result = path.via(x -> NonDetPath.pure(x));
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for single element",
              () -> {
                NonDetPath<Integer> path = NonDetPath.pure(10);
                NonDetPath<Integer> leftSide = path.via(intToNonDetString).via(stringToNonDetInt);
                NonDetPath<Integer> rightSide =
                    path.via(x -> intToNonDetString.apply(x).via(stringToNonDetInt));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for multiple elements",
              () -> {
                NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));
                NonDetPath<Integer> leftSide = path.via(intToNonDetString).via(stringToNonDetInt);
                NonDetPath<Integer> rightSide =
                    path.via(x -> intToNonDetString.apply(x).via(stringToNonDetInt));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Cartesian Product Semantics")
  class CartesianProductSemanticsTests {

    @TestFactory
    @DisplayName("zipWith uses Cartesian product semantics")
    Stream<DynamicTest> zipWithUsesCartesianProductSemantics() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "zipWith produces all combinations",
              () -> {
                NonDetPath<Integer> a = NonDetPath.of(List.of(1, 2));
                NonDetPath<String> b = NonDetPath.of(List.of("a", "b"));
                NonDetPath<String> result = a.zipWith(b, (i, s) -> i + s);

                // Cartesian product: 2 * 2 = 4 combinations
                assertThat(result.run()).containsExactly("1a", "1b", "2a", "2b");
              }),
          DynamicTest.dynamicTest(
              "zipWith with different sizes",
              () -> {
                NonDetPath<Integer> a = NonDetPath.of(List.of(1, 2, 3));
                NonDetPath<String> b = NonDetPath.of(List.of("x", "y"));
                NonDetPath<String> result = a.zipWith(b, (i, s) -> i + s);

                // Cartesian product: 3 * 2 = 6 combinations
                assertThat(result.run()).hasSize(6);
                assertThat(result.run()).containsExactly("1x", "1y", "2x", "2y", "3x", "3y");
              }),
          DynamicTest.dynamicTest(
              "zipWith with empty returns empty",
              () -> {
                NonDetPath<Integer> a = NonDetPath.of(List.of(1, 2, 3));
                NonDetPath<String> b = NonDetPath.empty();
                NonDetPath<String> result = a.zipWith(b, (i, s) -> i + s);

                assertThat(result.run()).isEmpty();
              }));
    }

    @TestFactory
    @DisplayName("NonDetPath differs from ListPath semantics")
    Stream<DynamicTest> differsFromListPathSemantics() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "NonDetPath produces more results than ListPath for same input",
              () -> {
                List<Integer> ints = List.of(1, 2, 3);
                List<String> strs = List.of("a", "b", "c");

                NonDetPath<Integer> nonDetA = NonDetPath.of(ints);
                NonDetPath<String> nonDetB = NonDetPath.of(strs);
                NonDetPath<String> nonDetResult = nonDetA.zipWith(nonDetB, (i, s) -> i + s);

                ListPath<Integer> listA = ListPath.of(ints);
                ListPath<String> listB = ListPath.of(strs);
                ListPath<String> listResult = listA.zipWith(listB, (i, s) -> i + s);

                // NonDetPath: Cartesian product (3 * 3 = 9 elements)
                assertThat(nonDetResult.run()).hasSize(9);

                // ListPath: Positional (3 elements)
                assertThat(listResult.run()).hasSize(3);
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("NonDetPath operations work correctly")
    Stream<DynamicTest> nonDetOperationsWorkCorrectly() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "filter removes non-matching elements",
              () -> {
                NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3, 4, 5));
                NonDetPath<Integer> result = path.filter(x -> x % 2 == 0);
                assertThat(result.run()).containsExactly(2, 4);
              }),
          DynamicTest.dynamicTest(
              "headOption returns first element",
              () -> {
                NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));
                assertThat(path.headOption().isPresent()).isTrue();
                assertThat(path.headOption().orElse(-1)).isEqualTo(1);
              }),
          DynamicTest.dynamicTest(
              "headOption on empty returns Nothing",
              () -> {
                NonDetPath<Integer> path = NonDetPath.empty();
                assertThat(path.headOption().isEmpty()).isTrue();
              }),
          DynamicTest.dynamicTest(
              "via flattens nested non-determinism",
              () -> {
                NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2));
                NonDetPath<String> result = path.via(x -> NonDetPath.of(List.of("a" + x, "b" + x)));

                // Each element maps to 2 alternatives: 2 * 2 = 4 total
                assertThat(result.run()).containsExactly("a1", "b1", "a2", "b2");
              }));
    }
  }
}
