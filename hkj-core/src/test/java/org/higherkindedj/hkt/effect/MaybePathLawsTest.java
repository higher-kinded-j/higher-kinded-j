// Copyright (c) 2025 Magnus Smith
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
 * Law verification tests for MaybePath.
 *
 * <p>Verifies that MaybePath satisfies Functor and Monad laws:
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
 *   <li>Left Identity: {@code Path.just(a).via(f) == f(a)}
 *   <li>Right Identity: {@code path.via(Path::just) == path}
 *   <li>Associativity: {@code path.via(f).via(g) == path.via(x -> f(x).via(g))}
 * </ul>
 */
@DisplayName("MaybePath Law Verification Tests")
class MaybePathLawsTest {

  // Test values
  private static final int TEST_VALUE = 42;
  private static final String TEST_STRING = "test";

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
              "Identity law holds for Just",
              () -> {
                MaybePath<Integer> path = Path.just(TEST_VALUE);
                MaybePath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for Nothing",
              () -> {
                MaybePath<Integer> path = Path.nothing();
                MaybePath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for Just",
              () -> {
                MaybePath<Integer> path = Path.just(TEST_VALUE);

                // Left side: path.map(f).map(g)
                MaybePath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);

                // Right side: path.map(g.compose(f))
                MaybePath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for Nothing",
              () -> {
                MaybePath<Integer> path = Path.nothing();

                MaybePath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                MaybePath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                MaybePath<Integer> path = Path.just(TEST_VALUE);

                // map Integer -> String -> Integer
                MaybePath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                MaybePath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    // Monadic functions for testing
    private final Function<Integer, MaybePath<String>> intToMaybeString =
        x -> x > 0 ? Path.just("positive:" + x) : Path.nothing();

    private final Function<String, MaybePath<Integer>> stringToMaybeInt =
        s -> s.length() > 5 ? Path.just(s.length()) : Path.nothing();

    private final Function<Integer, MaybePath<Integer>> safeDouble =
        x -> x < 1000 ? Path.just(x * 2) : Path.nothing();

    @TestFactory
    @DisplayName("Left Identity Law: Path.just(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity when f returns Just",
              () -> {
                int value = 10;

                // Left side: Path.just(a).via(f)
                MaybePath<String> leftSide = Path.just(value).via(intToMaybeString);

                // Right side: f(a)
                MaybePath<String> rightSide = intToMaybeString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity when f returns Nothing",
              () -> {
                int value = -5;

                MaybePath<String> leftSide = Path.just(value).via(intToMaybeString);
                MaybePath<String> rightSide = intToMaybeString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(Path::just) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for Just",
              () -> {
                MaybePath<Integer> path = Path.just(TEST_VALUE);

                MaybePath<Integer> result = path.via(Path::just);

                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for Nothing",
              () -> {
                MaybePath<Integer> path = Path.nothing();

                MaybePath<Integer> result = path.via(Path::just);

                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for Just with successful chain",
              () -> {
                MaybePath<Integer> path = Path.just(10);

                // Left side: path.via(f).via(g)
                MaybePath<Integer> leftSide = path.via(intToMaybeString).via(stringToMaybeInt);

                // Right side: path.via(x -> f(x).via(g))
                MaybePath<Integer> rightSide =
                    path.via(x -> intToMaybeString.apply(x).via(stringToMaybeInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when first function returns Nothing",
              () -> {
                MaybePath<Integer> path = Path.just(-5);

                MaybePath<Integer> leftSide = path.via(intToMaybeString).via(stringToMaybeInt);
                MaybePath<Integer> rightSide =
                    path.via(x -> intToMaybeString.apply(x).via(stringToMaybeInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when second function returns Nothing",
              () -> {
                MaybePath<Integer> path = Path.just(1); // produces "positive:1" (length 10)

                MaybePath<Integer> leftSide = path.via(intToMaybeString).via(stringToMaybeInt);
                MaybePath<Integer> rightSide =
                    path.via(x -> intToMaybeString.apply(x).via(stringToMaybeInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for Nothing",
              () -> {
                MaybePath<Integer> path = Path.nothing();

                MaybePath<Integer> leftSide = path.via(intToMaybeString).via(stringToMaybeInt);
                MaybePath<Integer> rightSide =
                    path.via(x -> intToMaybeString.apply(x).via(stringToMaybeInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity with same-type chain",
              () -> {
                MaybePath<Integer> path = Path.just(100);
                Function<Integer, MaybePath<Integer>> addTen = x -> Path.just(x + 10);

                MaybePath<Integer> leftSide = path.via(safeDouble).via(addTen);
                MaybePath<Integer> rightSide = path.via(x -> safeDouble.apply(x).via(addTen));

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
              "map over Just produces Just",
              () -> {
                MaybePath<Integer> path = Path.just(TEST_VALUE);
                MaybePath<String> result = path.map(Object::toString);
                assertThat(result.run().isJust()).isTrue();
              }),
          DynamicTest.dynamicTest(
              "map over Nothing produces Nothing",
              () -> {
                MaybePath<Integer> path = Path.nothing();
                MaybePath<String> result = path.map(Object::toString);
                assertThat(result.run().isNothing()).isTrue();
              }));
    }

    @TestFactory
    @DisplayName("via is consistent with flatMap")
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> viaConsistentWithFlatMap() {
      Function<Integer, MaybePath<String>> f = x -> Path.just("value:" + x);

      return Stream.of(
          DynamicTest.dynamicTest(
              "via and flatMap produce same result for Just",
              () -> {
                MaybePath<Integer> path = Path.just(TEST_VALUE);

                MaybePath<String> viaResult = path.via(f);
                // flatMap returns Chainable<B>, cast to MaybePath
                MaybePath<String> flatMapResult = (MaybePath<String>) path.flatMap(f);

                assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
              }),
          DynamicTest.dynamicTest(
              "via and flatMap produce same result for Nothing",
              () -> {
                MaybePath<Integer> path = Path.nothing();

                MaybePath<String> viaResult = path.via(f);
                // flatMap returns Chainable<B>, cast to MaybePath
                MaybePath<String> flatMapResult = (MaybePath<String>) path.flatMap(f);

                assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
              }));
    }
  }
}
