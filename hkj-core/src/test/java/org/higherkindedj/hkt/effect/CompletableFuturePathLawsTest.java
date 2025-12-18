// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for CompletableFuturePath.
 *
 * <p>Verifies that CompletableFuturePath satisfies Functor and Monad laws.
 */
@DisplayName("CompletableFuturePath Law Verification Tests")
class CompletableFuturePathLawsTest {

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
              "Identity law holds for completed future",
              () -> {
                CompletableFuturePath<Integer> path = CompletableFuturePath.completed(TEST_VALUE);
                CompletableFuturePath<Integer> result = path.map(Function.identity());
                assertThat(result.join()).isEqualTo(path.join());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for async future",
              () -> {
                CompletableFuturePath<Integer> path =
                    CompletableFuturePath.fromFuture(
                        CompletableFuture.supplyAsync(() -> TEST_VALUE));
                CompletableFuturePath<Integer> result = path.map(Function.identity());
                assertThat(result.join()).isEqualTo(path.join());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for completed future",
              () -> {
                CompletableFuturePath<Integer> path = CompletableFuturePath.completed(TEST_VALUE);
                CompletableFuturePath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                CompletableFuturePath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.join()).isEqualTo(rightSide.join());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                CompletableFuturePath<Integer> path = CompletableFuturePath.completed(TEST_VALUE);
                CompletableFuturePath<Integer> leftSide =
                    path.map(INT_TO_STRING).map(STRING_LENGTH);
                CompletableFuturePath<Integer> rightSide =
                    path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(leftSide.join()).isEqualTo(rightSide.join());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, CompletableFuturePath<String>> intToFutureString =
        x -> CompletableFuturePath.completed("result:" + x);

    private final Function<String, CompletableFuturePath<Integer>> stringToFutureInt =
        s -> CompletableFuturePath.completed(s.length());

    @TestFactory
    @DisplayName("Left Identity Law: CompletableFuturePath.completed(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with pure function",
              () -> {
                int value = 10;
                CompletableFuturePath<String> leftSide =
                    CompletableFuturePath.completed(value).via(intToFutureString);
                CompletableFuturePath<String> rightSide = intToFutureString.apply(value);
                assertThat(leftSide.join()).isEqualTo(rightSide.join());
              }),
          DynamicTest.dynamicTest(
              "Left identity with async function",
              () -> {
                int value = 10;
                Function<Integer, CompletableFuturePath<Integer>> asyncDouble =
                    x ->
                        CompletableFuturePath.fromFuture(
                            CompletableFuture.supplyAsync(() -> x * 2));
                CompletableFuturePath<Integer> leftSide =
                    CompletableFuturePath.completed(value).via(asyncDouble);
                CompletableFuturePath<Integer> rightSide = asyncDouble.apply(value);
                assertThat(leftSide.join()).isEqualTo(rightSide.join());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> CompletableFuturePath.completed(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for completed future",
              () -> {
                CompletableFuturePath<Integer> path = CompletableFuturePath.completed(TEST_VALUE);
                CompletableFuturePath<Integer> result =
                    path.via(x -> CompletableFuturePath.completed(x));
                assertThat(result.join()).isEqualTo(path.join());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for async future",
              () -> {
                CompletableFuturePath<Integer> path =
                    CompletableFuturePath.fromFuture(
                        CompletableFuture.supplyAsync(() -> TEST_VALUE));
                CompletableFuturePath<Integer> result =
                    path.via(x -> CompletableFuturePath.completed(x));
                assertThat(result.join()).isEqualTo(path.join());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for completed future",
              () -> {
                CompletableFuturePath<Integer> path = CompletableFuturePath.completed(10);
                CompletableFuturePath<Integer> leftSide =
                    path.via(intToFutureString).via(stringToFutureInt);
                CompletableFuturePath<Integer> rightSide =
                    path.via(x -> intToFutureString.apply(x).via(stringToFutureInt));
                assertThat(leftSide.join()).isEqualTo(rightSide.join());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for async chain",
              () -> {
                CompletableFuturePath<Integer> path =
                    CompletableFuturePath.fromFuture(CompletableFuture.supplyAsync(() -> 10));
                CompletableFuturePath<Integer> leftSide =
                    path.via(intToFutureString).via(stringToFutureInt);
                CompletableFuturePath<Integer> rightSide =
                    path.via(x -> intToFutureString.apply(x).via(stringToFutureInt));
                assertThat(leftSide.join()).isEqualTo(rightSide.join());
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("Async operations complete correctly")
    Stream<DynamicTest> asyncOperationsComplete() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Multiple async operations chain correctly",
              () -> {
                CompletableFuturePath<Integer> path = CompletableFuturePath.completed(1);
                CompletableFuturePath<Integer> result =
                    path.map(x -> x + 1)
                        .via(x -> CompletableFuturePath.completed(x * 2))
                        .map(x -> x + 10);

                assertThat(result.join()).isEqualTo(14); // ((1+1)*2)+10
              }),
          DynamicTest.dynamicTest(
              "zipWith combines two futures",
              () -> {
                CompletableFuturePath<Integer> a = CompletableFuturePath.completed(10);
                CompletableFuturePath<Integer> b = CompletableFuturePath.completed(20);
                CompletableFuturePath<Integer> result = a.zipWith(b, Integer::sum);

                assertThat(result.join()).isEqualTo(30);
              }));
    }
  }
}
