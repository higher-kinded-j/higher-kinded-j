// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for LazyPath.
 *
 * <p>Verifies that LazyPath satisfies Functor and Monad laws while preserving laziness.
 */
@DisplayName("LazyPath Law Verification Tests")
class LazyPathLawsTest {

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
              "Identity law holds for pure value",
              () -> {
                LazyPath<Integer> path = LazyPath.now(TEST_VALUE);
                LazyPath<Integer> result = path.map(Function.identity());
                assertThat(result.get()).isEqualTo(path.get());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for deferred value",
              () -> {
                LazyPath<Integer> path = LazyPath.defer(() -> TEST_VALUE);
                LazyPath<Integer> result = path.map(Function.identity());
                assertThat(result.get()).isEqualTo(path.get());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for pure value",
              () -> {
                LazyPath<Integer> path = LazyPath.now(TEST_VALUE);
                LazyPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                LazyPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.get()).isEqualTo(rightSide.get());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                LazyPath<Integer> path = LazyPath.now(TEST_VALUE);
                LazyPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                LazyPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(leftSide.get()).isEqualTo(rightSide.get());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, LazyPath<String>> intToLazyString =
        x -> LazyPath.now("result:" + x);

    private final Function<String, LazyPath<Integer>> stringToLazyInt =
        s -> LazyPath.now(s.length());

    @TestFactory
    @DisplayName("Left Identity Law: LazyPath.now(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with pure function",
              () -> {
                int value = 10;
                LazyPath<String> leftSide = LazyPath.now(value).via(intToLazyString);
                LazyPath<String> rightSide = intToLazyString.apply(value);
                assertThat(leftSide.get()).isEqualTo(rightSide.get());
              }),
          DynamicTest.dynamicTest(
              "Left identity with deferred function",
              () -> {
                int value = 10;
                Function<Integer, LazyPath<Integer>> deferredDouble =
                    x -> LazyPath.defer(() -> x * 2);
                LazyPath<Integer> leftSide = LazyPath.now(value).via(deferredDouble);
                LazyPath<Integer> rightSide = deferredDouble.apply(value);
                assertThat(leftSide.get()).isEqualTo(rightSide.get());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> LazyPath.now(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for pure value",
              () -> {
                LazyPath<Integer> path = LazyPath.now(TEST_VALUE);
                LazyPath<Integer> result = path.via(x -> LazyPath.now(x));
                assertThat(result.get()).isEqualTo(path.get());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for deferred value",
              () -> {
                LazyPath<Integer> path = LazyPath.defer(() -> TEST_VALUE);
                LazyPath<Integer> result = path.via(x -> LazyPath.now(x));
                assertThat(result.get()).isEqualTo(path.get());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for pure value",
              () -> {
                LazyPath<Integer> path = LazyPath.now(10);
                LazyPath<Integer> leftSide = path.via(intToLazyString).via(stringToLazyInt);
                LazyPath<Integer> rightSide =
                    path.via(x -> intToLazyString.apply(x).via(stringToLazyInt));
                assertThat(leftSide.get()).isEqualTo(rightSide.get());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for deferred value",
              () -> {
                LazyPath<Integer> path = LazyPath.defer(() -> 10);
                LazyPath<Integer> leftSide = path.via(intToLazyString).via(stringToLazyInt);
                LazyPath<Integer> rightSide =
                    path.via(x -> intToLazyString.apply(x).via(stringToLazyInt));
                assertThat(leftSide.get()).isEqualTo(rightSide.get());
              }));
    }
  }

  @Nested
  @DisplayName("Laziness Preservation")
  class LazinessPreservationTests {

    @TestFactory
    @DisplayName("Operations preserve laziness")
    Stream<DynamicTest> operationsPreserveLaziness() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "map is lazy",
              () -> {
                AtomicInteger evalCount = new AtomicInteger(0);
                LazyPath<Integer> path =
                    LazyPath.defer(
                        () -> {
                          evalCount.incrementAndGet();
                          return 42;
                        });

                LazyPath<Integer> mapped = path.map(x -> x * 2);
                assertThat(evalCount.get()).isEqualTo(0);

                mapped.get();
                assertThat(evalCount.get()).isEqualTo(1);
              }),
          DynamicTest.dynamicTest(
              "via is lazy",
              () -> {
                AtomicInteger evalCount = new AtomicInteger(0);
                LazyPath<Integer> path =
                    LazyPath.defer(
                        () -> {
                          evalCount.incrementAndGet();
                          return 42;
                        });

                LazyPath<String> viaMapped = path.via(x -> LazyPath.now("value:" + x));
                assertThat(evalCount.get()).isEqualTo(0);

                viaMapped.get();
                assertThat(evalCount.get()).isEqualTo(1);
              }),
          DynamicTest.dynamicTest(
              "memoization caches result",
              () -> {
                AtomicInteger evalCount = new AtomicInteger(0);
                LazyPath<Integer> path =
                    LazyPath.defer(
                        () -> {
                          evalCount.incrementAndGet();
                          return 42;
                        });

                // First access
                path.get();
                assertThat(evalCount.get()).isEqualTo(1);

                // Second access should use cached value
                path.get();
                assertThat(evalCount.get()).isEqualTo(1);

                // Third access should still use cached value
                path.get();
                assertThat(evalCount.get()).isEqualTo(1);
              }));
    }
  }
}
