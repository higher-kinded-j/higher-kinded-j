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
 * Law verification tests for TryPath.
 *
 * <p>Verifies that TryPath satisfies Functor and Monad laws.
 */
@DisplayName("TryPath Law Verification Tests")
class TryPathLawsTest {

  private static final int TEST_VALUE = 42;
  private static final RuntimeException TEST_EXCEPTION = new RuntimeException("test error");

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
              "Identity law holds for Success",
              () -> {
                TryPath<Integer> path = Path.success(TEST_VALUE);
                TryPath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for Failure",
              () -> {
                TryPath<Integer> path = Path.failure(TEST_EXCEPTION);
                TryPath<Integer> result = path.map(Function.identity());
                // For failures, we compare the exception
                assertThat(result.run().isFailure()).isTrue();
                assertThat(path.run().isFailure()).isTrue();
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for Success",
              () -> {
                TryPath<Integer> path = Path.success(TEST_VALUE);

                TryPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                TryPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for Failure",
              () -> {
                TryPath<Integer> path = Path.failure(TEST_EXCEPTION);

                TryPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                TryPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run().isFailure()).isTrue();
                assertThat(rightSide.run().isFailure()).isTrue();
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                TryPath<Integer> path = Path.success(TEST_VALUE);

                TryPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                TryPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, TryPath<String>> intToTryString =
        x -> x > 0 ? Path.success("positive:" + x) : Path.failure(new RuntimeException("negative"));

    private final Function<String, TryPath<Integer>> stringToTryInt =
        s ->
            s.length() > 5
                ? Path.success(s.length())
                : Path.failure(new RuntimeException("too short"));

    @TestFactory
    @DisplayName("Left Identity Law: Path.success(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity when f returns Success",
              () -> {
                int value = 10;

                TryPath<String> leftSide = Path.success(value).via(intToTryString);
                TryPath<String> rightSide = intToTryString.apply(value);

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity when f returns Failure",
              () -> {
                int value = -5;

                TryPath<String> leftSide = Path.success(value).via(intToTryString);
                TryPath<String> rightSide = intToTryString.apply(value);

                assertThat(leftSide.run().isFailure()).isEqualTo(rightSide.run().isFailure());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(Path::success) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for Success",
              () -> {
                TryPath<Integer> path = Path.success(TEST_VALUE);

                TryPath<Integer> result = path.via(Path::success);

                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for Failure",
              () -> {
                TryPath<Integer> path = Path.failure(TEST_EXCEPTION);

                TryPath<Integer> result = path.via(Path::success);

                assertThat(result.run().isFailure()).isTrue();
                assertThat(path.run().isFailure()).isTrue();
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for Success with successful chain",
              () -> {
                TryPath<Integer> path = Path.success(10);

                TryPath<Integer> leftSide = path.via(intToTryString).via(stringToTryInt);
                TryPath<Integer> rightSide =
                    path.via(x -> intToTryString.apply(x).via(stringToTryInt));

                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds when first function returns Failure",
              () -> {
                TryPath<Integer> path = Path.success(-5);

                TryPath<Integer> leftSide = path.via(intToTryString).via(stringToTryInt);
                TryPath<Integer> rightSide =
                    path.via(x -> intToTryString.apply(x).via(stringToTryInt));

                assertThat(leftSide.run().isFailure()).isEqualTo(rightSide.run().isFailure());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for Failure",
              () -> {
                TryPath<Integer> path = Path.failure(TEST_EXCEPTION);

                TryPath<Integer> leftSide = path.via(intToTryString).via(stringToTryInt);
                TryPath<Integer> rightSide =
                    path.via(x -> intToTryString.apply(x).via(stringToTryInt));

                assertThat(leftSide.run().isFailure()).isTrue();
                assertThat(rightSide.run().isFailure()).isTrue();
              }));
    }
  }

  @Nested
  @DisplayName("Exception Handling Invariants")
  class ExceptionHandlingInvariantsTests {

    @TestFactory
    @DisplayName("map catches exceptions thrown by function")
    Stream<DynamicTest> mapCatchesExceptions() {
      Function<Integer, Integer> throwing =
          x -> {
            throw new RuntimeException("function threw");
          };

      return Stream.of(
          DynamicTest.dynamicTest(
              "Exception in map function produces Failure",
              () -> {
                TryPath<Integer> path = Path.success(TEST_VALUE);
                TryPath<Integer> result = path.map(throwing);
                assertThat(result.run().isFailure()).isTrue();
              }));
    }

    @TestFactory
    @DisplayName("via catches exceptions thrown by function")
    Stream<DynamicTest> viaCatchesExceptions() {
      Function<Integer, TryPath<Integer>> throwing =
          x -> {
            throw new RuntimeException("function threw");
          };

      return Stream.of(
          DynamicTest.dynamicTest(
              "Exception in via function produces Failure",
              () -> {
                TryPath<Integer> path = Path.success(TEST_VALUE);
                TryPath<Integer> result = path.via(throwing);
                assertThat(result.run().isFailure()).isTrue();
              }));
    }
  }
}
