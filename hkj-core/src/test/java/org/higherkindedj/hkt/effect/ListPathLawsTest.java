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
 * Law verification tests for ListPath.
 *
 * <p>Verifies that ListPath satisfies Functor and Monad laws. ListPath uses positional (zipWith)
 * semantics for Applicative.
 */
@DisplayName("ListPath Law Verification Tests")
class ListPathLawsTest {

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
                ListPath<Integer> path = ListPath.pure(TEST_VALUE);
                ListPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for multiple elements",
              () -> {
                ListPath<Integer> path = ListPath.of(List.of(1, 2, 3, 4, 5));
                ListPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for empty list",
              () -> {
                ListPath<Integer> path = ListPath.empty();
                ListPath<Integer> result = path.map(Function.identity());
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
                ListPath<Integer> path = ListPath.pure(TEST_VALUE);
                ListPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                ListPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for multiple elements",
              () -> {
                ListPath<Integer> path = ListPath.of(List.of(1, 2, 3));
                ListPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                ListPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                ListPath<Integer> path = ListPath.pure(TEST_VALUE);
                ListPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                ListPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, ListPath<String>> intToListString =
        x -> ListPath.of(List.of("a:" + x, "b:" + x));

    private final Function<String, ListPath<Integer>> stringToListInt =
        s -> ListPath.pure(s.length());

    @TestFactory
    @DisplayName("Left Identity Law: ListPath.pure(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with list-returning function",
              () -> {
                int value = 10;
                ListPath<String> leftSide = ListPath.pure(value).via(intToListString);
                ListPath<String> rightSide = intToListString.apply(value);
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity with single-element function",
              () -> {
                int value = 10;
                Function<Integer, ListPath<Integer>> doubleIt = x -> ListPath.pure(x * 2);
                ListPath<Integer> leftSide = ListPath.pure(value).via(doubleIt);
                ListPath<Integer> rightSide = doubleIt.apply(value);
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> ListPath.pure(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for single element",
              () -> {
                ListPath<Integer> path = ListPath.pure(TEST_VALUE);
                ListPath<Integer> result = path.via(x -> ListPath.pure(x));
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for multiple elements",
              () -> {
                ListPath<Integer> path = ListPath.of(List.of(1, 2, 3));
                ListPath<Integer> result = path.via(x -> ListPath.pure(x));
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for empty list",
              () -> {
                ListPath<Integer> path = ListPath.empty();
                ListPath<Integer> result = path.via(x -> ListPath.pure(x));
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
                ListPath<Integer> path = ListPath.pure(10);
                ListPath<Integer> leftSide = path.via(intToListString).via(stringToListInt);
                ListPath<Integer> rightSide =
                    path.via(x -> intToListString.apply(x).via(stringToListInt));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for multiple elements",
              () -> {
                ListPath<Integer> path = ListPath.of(List.of(1, 2, 3));
                ListPath<Integer> leftSide = path.via(intToListString).via(stringToListInt);
                ListPath<Integer> rightSide =
                    path.via(x -> intToListString.apply(x).via(stringToListInt));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Positional Semantics")
  class PositionalSemanticsTests {

    @TestFactory
    @DisplayName("zipWith uses positional semantics")
    Stream<DynamicTest> zipWithUsesPositionalSemantics() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "zipWith pairs elements by position",
              () -> {
                ListPath<Integer> a = ListPath.of(List.of(1, 2, 3));
                ListPath<String> b = ListPath.of(List.of("a", "b", "c"));
                ListPath<String> result = a.zipWith(b, (i, s) -> i + s);

                assertThat(result.run()).containsExactly("1a", "2b", "3c");
              }),
          DynamicTest.dynamicTest(
              "zipWith truncates to shorter list",
              () -> {
                ListPath<Integer> a = ListPath.of(List.of(1, 2, 3, 4, 5));
                ListPath<String> b = ListPath.of(List.of("a", "b"));
                ListPath<String> result = a.zipWith(b, (i, s) -> i + s);

                assertThat(result.run()).containsExactly("1a", "2b");
              }),
          DynamicTest.dynamicTest(
              "zipWith with empty returns empty",
              () -> {
                ListPath<Integer> a = ListPath.of(List.of(1, 2, 3));
                ListPath<String> b = ListPath.empty();
                ListPath<String> result = a.zipWith(b, (i, s) -> i + s);

                assertThat(result.run()).isEmpty();
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("List operations preserve structure")
    Stream<DynamicTest> listOperationsPreserveStructure() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "map preserves order",
              () -> {
                ListPath<Integer> path = ListPath.of(List.of(3, 1, 4, 1, 5));
                ListPath<Integer> result = path.map(x -> x * 2);
                assertThat(result.run()).containsExactly(6, 2, 8, 2, 10);
              }),
          DynamicTest.dynamicTest(
              "filter preserves order",
              () -> {
                ListPath<Integer> path = ListPath.of(List.of(1, 2, 3, 4, 5));
                ListPath<Integer> result = path.filter(x -> x % 2 == 0);
                assertThat(result.run()).containsExactly(2, 4);
              }),
          DynamicTest.dynamicTest(
              "headOption returns first element",
              () -> {
                ListPath<Integer> path = ListPath.of(List.of(1, 2, 3));
                assertThat(path.headOption().isPresent()).isTrue();
                assertThat(path.headOption().orElse(-1)).isEqualTo(1);
              }),
          DynamicTest.dynamicTest(
              "headOption on empty returns Nothing",
              () -> {
                ListPath<Integer> path = ListPath.empty();
                assertThat(path.headOption().isEmpty()).isTrue();
              }));
    }
  }
}
