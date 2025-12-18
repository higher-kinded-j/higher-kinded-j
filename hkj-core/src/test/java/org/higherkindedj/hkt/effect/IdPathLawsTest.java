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
 * Law verification tests for IdPath.
 *
 * <p>Verifies that IdPath satisfies Functor and Monad laws. Since IdPath always contains a value
 * (it never fails), all laws should hold trivially for all values.
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
 *   <li>Left Identity: {@code Path.id(a).via(f) == f(a)}
 *   <li>Right Identity: {@code path.via(Path::id) == path}
 *   <li>Associativity: {@code path.via(f).via(g) == path.via(x -> f(x).via(g))}
 * </ul>
 */
@DisplayName("IdPath Law Verification Tests")
class IdPathLawsTest {

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
              "Identity law holds for Id",
              () -> {
                IdPath<Integer> path = Path.id(TEST_VALUE);
                IdPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for zero",
              () -> {
                IdPath<Integer> path = Path.id(0);
                IdPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for String",
              () -> {
                IdPath<String> path = Path.id(TEST_STRING);
                IdPath<String> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for Id",
              () -> {
                IdPath<Integer> path = Path.id(TEST_VALUE);

                // Left side: path.map(f).map(g)
                IdPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);

                // Right side: path.map(g.compose(f))
                IdPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                IdPath<Integer> path = Path.id(TEST_VALUE);

                // map Integer -> String -> Integer
                IdPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                IdPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for negative values",
              () -> {
                IdPath<Integer> path = Path.id(-10);

                IdPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                IdPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    // Monadic functions for testing
    private final Function<Integer, IdPath<String>> intToIdString = x -> Path.id("value:" + x);

    private final Function<String, IdPath<Integer>> stringToIdInt = s -> Path.id(s.length());

    private final Function<Integer, IdPath<Integer>> idDouble = x -> Path.id(x * 2);

    @TestFactory
    @DisplayName("Left Identity Law: Path.id(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity holds for positive value",
              () -> {
                int value = 10;

                // Left side: Path.id(a).via(f)
                IdPath<String> leftSide = Path.id(value).via(intToIdString);

                // Right side: f(a)
                IdPath<String> rightSide = intToIdString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity holds for negative value",
              () -> {
                int value = -5;

                IdPath<String> leftSide = Path.id(value).via(intToIdString);
                IdPath<String> rightSide = intToIdString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity holds for zero",
              () -> {
                int value = 0;

                IdPath<String> leftSide = Path.id(value).via(intToIdString);
                IdPath<String> rightSide = intToIdString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(Path::id) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for Id",
              () -> {
                IdPath<Integer> path = Path.id(TEST_VALUE);

                IdPath<Integer> result = path.via(Path::id);

                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for String",
              () -> {
                IdPath<String> path = Path.id(TEST_STRING);

                IdPath<String> result = path.via(Path::id);

                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for Id",
              () -> {
                IdPath<Integer> path = Path.id(10);

                // Left side: path.via(f).via(g)
                IdPath<Integer> leftSide = path.via(intToIdString).via(stringToIdInt);

                // Right side: path.via(x -> f(x).via(g))
                IdPath<Integer> rightSide =
                    path.via(x -> intToIdString.apply(x).via(stringToIdInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity with same-type chain",
              () -> {
                IdPath<Integer> path = Path.id(100);
                Function<Integer, IdPath<Integer>> addTen = x -> Path.id(x + 10);

                IdPath<Integer> leftSide = path.via(idDouble).via(addTen);
                IdPath<Integer> rightSide = path.via(x -> idDouble.apply(x).via(addTen));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity with multiple type changes",
              () -> {
                IdPath<Integer> path = Path.id(5);
                Function<Integer, IdPath<String>> toStr = x -> Path.id(String.valueOf(x));
                Function<String, IdPath<Integer>> toLen = s -> Path.id(s.length());

                IdPath<Integer> leftSide = path.via(toStr).via(toLen);
                IdPath<Integer> rightSide = path.via(x -> toStr.apply(x).via(toLen));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("IdPath always contains a value")
    Stream<DynamicTest> idPathAlwaysContainsValue() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "get() returns the value",
              () -> {
                IdPath<Integer> path = Path.id(TEST_VALUE);
                assertThat(path.get()).isEqualTo(TEST_VALUE);
              }),
          DynamicTest.dynamicTest(
              "run() returns Id containing value",
              () -> {
                IdPath<Integer> path = Path.id(TEST_VALUE);
                assertThat(path.run().value()).isEqualTo(TEST_VALUE);
              }));
    }

    @TestFactory
    @DisplayName("via is consistent with flatMap")
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> viaConsistentWithFlatMap() {
      Function<Integer, IdPath<String>> f = x -> Path.id("value:" + x);

      return Stream.of(
          DynamicTest.dynamicTest(
              "via and flatMap produce same result",
              () -> {
                IdPath<Integer> path = Path.id(TEST_VALUE);

                IdPath<String> viaResult = path.via(f);
                // flatMap returns Chainable<B>, cast to IdPath
                IdPath<String> flatMapResult = (IdPath<String>) path.flatMap(f);

                assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
              }));
    }
  }
}
