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
 * Law verification tests for StreamPath.
 *
 * <p>Verifies that StreamPath satisfies Functor and Monad laws. StreamPath uses lazy stream
 * evaluation with reusable stream suppliers.
 */
@DisplayName("StreamPath Law Verification Tests")
class StreamPathLawsTest {

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
                StreamPath<Integer> path = StreamPath.pure(TEST_VALUE);
                StreamPath<Integer> result = path.map(Function.identity());
                assertThat(result.toList()).isEqualTo(path.toList());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for multiple elements",
              () -> {
                StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4, 5));
                StreamPath<Integer> result = path.map(Function.identity());
                assertThat(result.toList()).isEqualTo(path.toList());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for empty stream",
              () -> {
                StreamPath<Integer> path = StreamPath.empty();
                StreamPath<Integer> result = path.map(Function.identity());
                assertThat(result.toList()).isEqualTo(path.toList());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for single element",
              () -> {
                StreamPath<Integer> path = StreamPath.pure(TEST_VALUE);
                StreamPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                StreamPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for multiple elements",
              () -> {
                StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));
                StreamPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                StreamPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                StreamPath<Integer> path = StreamPath.pure(TEST_VALUE);
                StreamPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                StreamPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, StreamPath<String>> intToStreamString =
        x -> StreamPath.fromList(List.of("a:" + x, "b:" + x));

    private final Function<String, StreamPath<Integer>> stringToStreamInt =
        s -> StreamPath.pure(s.length());

    @TestFactory
    @DisplayName("Left Identity Law: StreamPath.pure(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with stream-returning function",
              () -> {
                int value = 10;
                StreamPath<String> leftSide = StreamPath.pure(value).via(intToStreamString);
                StreamPath<String> rightSide = intToStreamString.apply(value);
                assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
              }),
          DynamicTest.dynamicTest(
              "Left identity with single-element function",
              () -> {
                int value = 10;
                Function<Integer, StreamPath<Integer>> doubleIt = x -> StreamPath.pure(x * 2);
                StreamPath<Integer> leftSide = StreamPath.pure(value).via(doubleIt);
                StreamPath<Integer> rightSide = doubleIt.apply(value);
                assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> StreamPath.pure(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for single element",
              () -> {
                StreamPath<Integer> path = StreamPath.pure(TEST_VALUE);
                StreamPath<Integer> result = path.via(x -> StreamPath.pure(x));
                assertThat(result.toList()).isEqualTo(path.toList());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for multiple elements",
              () -> {
                StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));
                StreamPath<Integer> result = path.via(x -> StreamPath.pure(x));
                assertThat(result.toList()).isEqualTo(path.toList());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for empty stream",
              () -> {
                StreamPath<Integer> path = StreamPath.empty();
                StreamPath<Integer> result = path.via(x -> StreamPath.pure(x));
                assertThat(result.toList()).isEqualTo(path.toList());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for single element",
              () -> {
                StreamPath<Integer> path = StreamPath.pure(10);
                StreamPath<Integer> leftSide = path.via(intToStreamString).via(stringToStreamInt);
                StreamPath<Integer> rightSide =
                    path.via(x -> intToStreamString.apply(x).via(stringToStreamInt));
                assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for multiple elements",
              () -> {
                StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));
                StreamPath<Integer> leftSide = path.via(intToStreamString).via(stringToStreamInt);
                StreamPath<Integer> rightSide =
                    path.via(x -> intToStreamString.apply(x).via(stringToStreamInt));
                assertThat(leftSide.toList()).isEqualTo(rightSide.toList());
              }));
    }
  }

  @Nested
  @DisplayName("Stream Reusability")
  class StreamReusabilityTests {

    @TestFactory
    @DisplayName("StreamPath can be consumed multiple times")
    Stream<DynamicTest> streamPathIsReusable() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "toList can be called multiple times",
              () -> {
                StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

                List<Integer> first = path.toList();
                List<Integer> second = path.toList();
                List<Integer> third = path.toList();

                assertThat(first).containsExactly(1, 2, 3);
                assertThat(second).containsExactly(1, 2, 3);
                assertThat(third).containsExactly(1, 2, 3);
              }),
          DynamicTest.dynamicTest(
              "mapped stream is also reusable",
              () -> {
                StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));
                StreamPath<Integer> mapped = path.map(x -> x * 2);

                assertThat(mapped.toList()).containsExactly(2, 4, 6);
                assertThat(mapped.toList()).containsExactly(2, 4, 6);
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("Stream operations work correctly")
    Stream<DynamicTest> streamOperationsWorkCorrectly() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "filter removes non-matching elements",
              () -> {
                StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4, 5));
                StreamPath<Integer> result = path.filter(x -> x % 2 == 0);
                assertThat(result.toList()).containsExactly(2, 4);
              }),
          DynamicTest.dynamicTest(
              "take limits elements",
              () -> {
                StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4, 5));
                StreamPath<Integer> result = path.take(3);
                assertThat(result.toList()).containsExactly(1, 2, 3);
              }),
          DynamicTest.dynamicTest(
              "headOption returns first element",
              () -> {
                StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));
                assertThat(path.headOption().isPresent()).isTrue();
                assertThat(path.headOption().orElse(-1)).isEqualTo(1);
              }),
          DynamicTest.dynamicTest(
              "headOption on empty returns Nothing",
              () -> {
                StreamPath<Integer> path = StreamPath.empty();
                assertThat(path.headOption().isEmpty()).isTrue();
              }));
    }
  }
}
