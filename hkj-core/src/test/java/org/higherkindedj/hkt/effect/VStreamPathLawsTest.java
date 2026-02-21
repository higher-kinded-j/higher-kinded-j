// Copyright (c) 2025 - 2026 Magnus Smith
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
 * Law verification tests for VStreamPath.
 *
 * <p>Verifies that VStreamPath satisfies Functor and Monad laws. VStreamPath uses lazy pull-based
 * stream evaluation with virtual thread execution.
 */
@DisplayName("VStreamPath Law Verification Tests")
class VStreamPathLawsTest {

  private static final int TEST_VALUE = 42;
  private static final Function<Integer, Integer> ADD_ONE = x -> x + 1;
  private static final Function<Integer, Integer> DOUBLE = x -> x * 2;
  private static final Function<Integer, String> INT_TO_STRING = x -> "value:" + x;
  private static final Function<String, Integer> STRING_LENGTH = String::length;

  private static List<Integer> materialise(VStreamPath<Integer> path) {
    return path.toList().unsafeRun();
  }

  private static List<String> materialiseStr(VStreamPath<String> path) {
    return path.toList().unsafeRun();
  }

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
                VStreamPath<Integer> path = Path.vstreamPure(TEST_VALUE);
                VStreamPath<Integer> result = path.map(Function.identity());
                assertThat(materialise(result)).isEqualTo(materialise(path));
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for multiple elements",
              () -> {
                VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 3, 4, 5));
                VStreamPath<Integer> result = path.map(Function.identity());
                assertThat(materialise(result)).isEqualTo(materialise(path));
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for empty stream",
              () -> {
                VStreamPath<Integer> path = Path.vstreamEmpty();
                VStreamPath<Integer> result = path.map(Function.identity());
                assertThat(materialise(result)).isEqualTo(materialise(path));
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for single element",
              () -> {
                VStreamPath<Integer> path = Path.vstreamPure(TEST_VALUE);
                VStreamPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                VStreamPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(materialise(leftSide)).isEqualTo(materialise(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for multiple elements",
              () -> {
                VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 3));
                VStreamPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                VStreamPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(materialise(leftSide)).isEqualTo(materialise(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                VStreamPath<Integer> path = Path.vstreamPure(TEST_VALUE);
                VStreamPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                VStreamPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(materialise(leftSide)).isEqualTo(materialise(rightSide));
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, VStreamPath<String>> intToVStreamString =
        x -> Path.vstreamOf("a:" + x, "b:" + x);

    private final Function<String, VStreamPath<Integer>> stringToVStreamInt =
        s -> Path.vstreamPure(s.length());

    @TestFactory
    @DisplayName("Left Identity Law: VStreamPath.pure(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with stream-returning function",
              () -> {
                int value = 10;
                VStreamPath<String> leftSide = Path.vstreamPure(value).via(intToVStreamString);
                VStreamPath<String> rightSide = intToVStreamString.apply(value);
                assertThat(materialiseStr(leftSide)).isEqualTo(materialiseStr(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Left identity with single-element function",
              () -> {
                int value = 10;
                Function<Integer, VStreamPath<Integer>> doubleIt = x -> Path.vstreamPure(x * 2);
                VStreamPath<Integer> leftSide = Path.vstreamPure(value).via(doubleIt);
                VStreamPath<Integer> rightSide = doubleIt.apply(value);
                assertThat(materialise(leftSide)).isEqualTo(materialise(rightSide));
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> VStreamPath.pure(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for single element",
              () -> {
                VStreamPath<Integer> path = Path.vstreamPure(TEST_VALUE);
                VStreamPath<Integer> result = path.via(x -> Path.vstreamPure(x));
                assertThat(materialise(result)).isEqualTo(materialise(path));
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for multiple elements",
              () -> {
                VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 3));
                VStreamPath<Integer> result = path.via(x -> Path.vstreamPure(x));
                assertThat(materialise(result)).isEqualTo(materialise(path));
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for empty stream",
              () -> {
                VStreamPath<Integer> path = Path.vstreamEmpty();
                VStreamPath<Integer> result = path.via(x -> Path.vstreamPure(x));
                assertThat(materialise(result)).isEqualTo(materialise(path));
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for single element",
              () -> {
                VStreamPath<Integer> path = Path.vstreamPure(10);
                VStreamPath<Integer> leftSide =
                    path.via(intToVStreamString).via(stringToVStreamInt);
                VStreamPath<Integer> rightSide =
                    path.via(x -> intToVStreamString.apply(x).via(stringToVStreamInt));
                assertThat(materialise(leftSide)).isEqualTo(materialise(rightSide));
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for multiple elements",
              () -> {
                VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 3));
                VStreamPath<Integer> leftSide =
                    path.via(intToVStreamString).via(stringToVStreamInt);
                VStreamPath<Integer> rightSide =
                    path.via(x -> intToVStreamString.apply(x).via(stringToVStreamInt));
                assertThat(materialise(leftSide)).isEqualTo(materialise(rightSide));
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("Stream operations preserve expected semantics")
    Stream<DynamicTest> streamOperationsWorkCorrectly() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "filter removes non-matching elements",
              () -> {
                VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 3, 4, 5));
                VStreamPath<Integer> result = path.filter(x -> x % 2 == 0);
                assertThat(materialise(result)).containsExactly(2, 4);
              }),
          DynamicTest.dynamicTest(
              "take limits elements",
              () -> {
                VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 3, 4, 5));
                VStreamPath<Integer> result = path.take(3);
                assertThat(materialise(result)).containsExactly(1, 2, 3);
              }),
          DynamicTest.dynamicTest(
              "distinct removes duplicates preserving order",
              () -> {
                VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 2, 3, 1));
                VStreamPath<Integer> result = path.distinct();
                assertThat(materialise(result)).containsExactly(1, 2, 3);
              }),
          DynamicTest.dynamicTest(
              "concat preserves element order",
              () -> {
                VStreamPath<Integer> a = Path.vstreamOf(1, 2);
                VStreamPath<Integer> b = Path.vstreamOf(3, 4);
                assertThat(materialise(a.concat(b))).containsExactly(1, 2, 3, 4);
              }),
          DynamicTest.dynamicTest(
              "zipWith pairs elements positionally",
              () -> {
                VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3);
                VStreamPath<String> strs = Path.vstreamOf("a", "b", "c");
                VStreamPath<String> result = nums.zipWith(strs, (n, s) -> n + s);
                assertThat(materialiseStr(result)).containsExactly("1a", "2b", "3c");
              }));
    }
  }
}
