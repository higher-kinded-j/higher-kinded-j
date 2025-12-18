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
 * Law verification tests for OptionalPath.
 *
 * <p>Verifies that OptionalPath satisfies Functor and Monad laws:
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
 *   <li>Left Identity: {@code Path.present(a).via(f) == f(a)}
 *   <li>Right Identity: {@code path.via(Path::present) == path}
 *   <li>Associativity: {@code path.via(f).via(g) == path.via(x -> f(x).via(g))}
 * </ul>
 */
@DisplayName("OptionalPath Law Verification Tests")
class OptionalPathLawsTest {

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
              "Identity law holds for Present",
              () -> {
                OptionalPath<Integer> path = Path.present(TEST_VALUE);
                OptionalPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for Absent",
              () -> {
                OptionalPath<Integer> path = Path.absent();
                OptionalPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for Present",
              () -> {
                OptionalPath<Integer> path = Path.present(TEST_VALUE);

                // Left side: path.map(f).map(g)
                OptionalPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);

                // Right side: path.map(g.compose(f))
                OptionalPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for Absent",
              () -> {
                OptionalPath<Integer> path = Path.absent();

                OptionalPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                OptionalPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                OptionalPath<Integer> path = Path.present(TEST_VALUE);

                // map Integer -> String -> Integer
                OptionalPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                OptionalPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    // Monadic functions for testing
    private final Function<Integer, OptionalPath<String>> intToOptionalString =
        x -> x > 0 ? Path.present("positive:" + x) : Path.absent();

    private final Function<String, OptionalPath<Integer>> stringToOptionalInt =
        s -> s.length() > 5 ? Path.present(s.length()) : Path.absent();

    private final Function<Integer, OptionalPath<Integer>> safeDouble =
        x -> x < 1000 ? Path.present(x * 2) : Path.absent();

    @TestFactory
    @DisplayName("Left Identity Law: Path.present(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity when f returns Present",
              () -> {
                int value = 10;

                // Left side: Path.present(a).via(f)
                OptionalPath<String> leftSide = Path.present(value).via(intToOptionalString);

                // Right side: f(a)
                OptionalPath<String> rightSide = intToOptionalString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity when f returns Absent",
              () -> {
                int value = -5;

                OptionalPath<String> leftSide = Path.present(value).via(intToOptionalString);
                OptionalPath<String> rightSide = intToOptionalString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(Path::present) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for Present",
              () -> {
                OptionalPath<Integer> path = Path.present(TEST_VALUE);

                OptionalPath<Integer> result = path.via(Path::present);

                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for Absent",
              () -> {
                OptionalPath<Integer> path = Path.absent();

                OptionalPath<Integer> result = path.via(Path::present);

                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for Present with successful chain",
              () -> {
                OptionalPath<Integer> path = Path.present(10);

                // Left side: path.via(f).via(g)
                OptionalPath<Integer> leftSide =
                    path.via(intToOptionalString).via(stringToOptionalInt);

                // Right side: path.via(x -> f(x).via(g))
                OptionalPath<Integer> rightSide =
                    path.via(x -> intToOptionalString.apply(x).via(stringToOptionalInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when first function returns Absent",
              () -> {
                OptionalPath<Integer> path = Path.present(-5);

                OptionalPath<Integer> leftSide =
                    path.via(intToOptionalString).via(stringToOptionalInt);
                OptionalPath<Integer> rightSide =
                    path.via(x -> intToOptionalString.apply(x).via(stringToOptionalInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when second function returns Absent",
              () -> {
                OptionalPath<Integer> path = Path.present(1); // produces "positive:1" (length 10)

                OptionalPath<Integer> leftSide =
                    path.via(intToOptionalString).via(stringToOptionalInt);
                OptionalPath<Integer> rightSide =
                    path.via(x -> intToOptionalString.apply(x).via(stringToOptionalInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for Absent",
              () -> {
                OptionalPath<Integer> path = Path.absent();

                OptionalPath<Integer> leftSide =
                    path.via(intToOptionalString).via(stringToOptionalInt);
                OptionalPath<Integer> rightSide =
                    path.via(x -> intToOptionalString.apply(x).via(stringToOptionalInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity with same-type chain",
              () -> {
                OptionalPath<Integer> path = Path.present(100);
                Function<Integer, OptionalPath<Integer>> addTen = x -> Path.present(x + 10);

                OptionalPath<Integer> leftSide = path.via(safeDouble).via(addTen);
                OptionalPath<Integer> rightSide = path.via(x -> safeDouble.apply(x).via(addTen));

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
              "map over Present produces Present",
              () -> {
                OptionalPath<Integer> path = Path.present(TEST_VALUE);
                OptionalPath<String> result = path.map(Object::toString);
                assertThat(result.run().isPresent()).isTrue();
              }),
          DynamicTest.dynamicTest(
              "map over Absent produces Absent",
              () -> {
                OptionalPath<Integer> path = Path.absent();
                OptionalPath<String> result = path.map(Object::toString);
                assertThat(result.run().isEmpty()).isTrue();
              }));
    }

    @TestFactory
    @DisplayName("via is consistent with flatMap")
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> viaConsistentWithFlatMap() {
      Function<Integer, OptionalPath<String>> f = x -> Path.present("value:" + x);

      return Stream.of(
          DynamicTest.dynamicTest(
              "via and flatMap produce same result for Present",
              () -> {
                OptionalPath<Integer> path = Path.present(TEST_VALUE);

                OptionalPath<String> viaResult = path.via(f);
                // flatMap returns Chainable<B>, cast to OptionalPath
                OptionalPath<String> flatMapResult = (OptionalPath<String>) path.flatMap(f);

                assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
              }),
          DynamicTest.dynamicTest(
              "via and flatMap produce same result for Absent",
              () -> {
                OptionalPath<Integer> path = Path.absent();

                OptionalPath<String> viaResult = path.via(f);
                // flatMap returns Chainable<B>, cast to OptionalPath
                OptionalPath<String> flatMapResult = (OptionalPath<String>) path.flatMap(f);

                assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
              }));
    }
  }
}
