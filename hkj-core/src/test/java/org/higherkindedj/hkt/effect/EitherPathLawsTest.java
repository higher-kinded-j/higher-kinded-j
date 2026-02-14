// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for EitherPath.
 *
 * <p>Verifies that EitherPath satisfies Functor and Monad laws.
 */
@DisplayName("EitherPath Law Verification Tests")
class EitherPathLawsTest {

  private static final int TEST_VALUE = 42;
  private static final String TEST_ERROR = "error";

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
              "Identity law holds for Right",
              () -> {
                EitherPath<String, Integer> path = Path.right(TEST_VALUE);
                EitherPath<String, Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for Left",
              () -> {
                EitherPath<String, Integer> path = Path.left(TEST_ERROR);
                EitherPath<String, Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for Right",
              () -> {
                EitherPath<String, Integer> path = Path.right(TEST_VALUE);

                EitherPath<String, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                EitherPath<String, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for Left",
              () -> {
                EitherPath<String, Integer> path = Path.left(TEST_ERROR);

                EitherPath<String, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                EitherPath<String, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                EitherPath<String, Integer> path = Path.right(TEST_VALUE);

                EitherPath<String, Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                EitherPath<String, Integer> rightSide =
                    path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, EitherPath<String, String>> intToEitherString =
        x -> x > 0 ? Path.right("positive:" + x) : Path.left("negative");

    private final Function<String, EitherPath<String, Integer>> stringToEitherInt =
        s -> s.length() > 5 ? Path.right(s.length()) : Path.left("too short");

    @TestFactory
    @DisplayName("Left Identity Law: Path.right(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity when f returns Right",
              () -> {
                int value = 10;

                EitherPath<String, String> leftSide =
                    Path.<String, Integer>right(value).via(intToEitherString);
                EitherPath<String, String> rightSide = intToEitherString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity when f returns Left",
              () -> {
                int value = -5;

                EitherPath<String, String> leftSide =
                    Path.<String, Integer>right(value).via(intToEitherString);
                EitherPath<String, String> rightSide = intToEitherString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(Path::right) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for Right",
              () -> {
                EitherPath<String, Integer> path = Path.right(TEST_VALUE);

                EitherPath<String, Integer> result = path.via(Path::right);

                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for Left",
              () -> {
                EitherPath<String, Integer> path = Path.left(TEST_ERROR);

                EitherPath<String, Integer> result = path.via(Path::right);

                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for Right with successful chain",
              () -> {
                EitherPath<String, Integer> path = Path.right(10);

                EitherPath<String, Integer> leftSide =
                    path.via(intToEitherString).via(stringToEitherInt);
                EitherPath<String, Integer> rightSide =
                    path.via(x -> intToEitherString.apply(x).via(stringToEitherInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when first function returns Left",
              () -> {
                EitherPath<String, Integer> path = Path.right(-5);

                EitherPath<String, Integer> leftSide =
                    path.via(intToEitherString).via(stringToEitherInt);
                EitherPath<String, Integer> rightSide =
                    path.via(x -> intToEitherString.apply(x).via(stringToEitherInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for Left",
              () -> {
                EitherPath<String, Integer> path = Path.left(TEST_ERROR);

                EitherPath<String, Integer> leftSide =
                    path.via(intToEitherString).via(stringToEitherInt);
                EitherPath<String, Integer> rightSide =
                    path.via(x -> intToEitherString.apply(x).via(stringToEitherInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Bifunctor Laws")
  class BifunctorLawsTests {

    @TestFactory
    @DisplayName("mapError identity: path.mapError(id) preserves structure")
    Stream<DynamicTest> mapErrorIdentity() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "mapError identity for Right preserves value",
              () -> {
                EitherPath<String, Integer> path = Path.right(TEST_VALUE);
                EitherPath<String, Integer> result = path.mapError(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "mapError identity for Left preserves error",
              () -> {
                EitherPath<String, Integer> path = Path.left(TEST_ERROR);
                EitherPath<String, Integer> result = path.mapError(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }
  }
}
