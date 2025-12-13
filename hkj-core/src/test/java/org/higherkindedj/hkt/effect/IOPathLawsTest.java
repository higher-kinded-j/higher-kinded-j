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
 * Law verification tests for IOPath.
 *
 * <p>Verifies that IOPath satisfies Functor and Monad laws. Since IOPath represents deferred
 * computations, laws are verified by executing the effects and comparing results.
 */
@DisplayName("IOPath Law Verification Tests")
class IOPathLawsTest {

  private static final int TEST_VALUE = 42;
  private static final String TEST_STRING = "test";

  private static final Function<Integer, Integer> ADD_ONE = x -> x + 1;
  private static final Function<Integer, Integer> DOUBLE = x -> x * 2;
  private static final Function<Integer, String> INT_TO_STRING = x -> "value:" + x;
  private static final Function<String, Integer> STRING_LENGTH = String::length;

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawsTests {

    @TestFactory
    @DisplayName("Functor Identity Law: path.map(id).unsafeRun() == path.unsafeRun()")
    Stream<DynamicTest> functorIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Identity law holds for pure value",
              () -> {
                IOPath<Integer> path = Path.ioPure(TEST_VALUE);
                IOPath<Integer> result = path.map(Function.identity());

                assertThat(result.unsafeRun()).isEqualTo(path.unsafeRun());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for deferred computation",
              () -> {
                IOPath<Integer> path = Path.io(() -> TEST_VALUE * 2);
                IOPath<Integer> result = path.map(Function.identity());

                assertThat(result.unsafeRun()).isEqualTo(path.unsafeRun());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds",
              () -> {
                IOPath<Integer> path = Path.ioPure(TEST_VALUE);

                IOPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                IOPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.unsafeRun()).isEqualTo(rightSide.unsafeRun());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                IOPath<Integer> path = Path.ioPure(TEST_VALUE);

                IOPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                IOPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThat(leftSide.unsafeRun()).isEqualTo(rightSide.unsafeRun());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, IOPath<String>> intToIOString =
        x -> Path.io(() -> "computed:" + x);

    private final Function<String, IOPath<Integer>> stringToIOInt =
        s -> Path.io(s::length);

    @TestFactory
    @DisplayName("Left Identity Law: Path.ioPure(a).via(f).unsafeRun() == f(a).unsafeRun()")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity holds",
              () -> {
                int value = 10;

                IOPath<String> leftSide = Path.ioPure(value).via(intToIOString);
                IOPath<String> rightSide = intToIOString.apply(value);

                assertThat(leftSide.unsafeRun()).isEqualTo(rightSide.unsafeRun());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(Path::ioPure).unsafeRun() == path.unsafeRun()")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for pure value",
              () -> {
                IOPath<Integer> path = Path.ioPure(TEST_VALUE);

                IOPath<Integer> result = path.via(Path::ioPure);

                assertThat(result.unsafeRun()).isEqualTo(path.unsafeRun());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for deferred computation",
              () -> {
                IOPath<Integer> path = Path.io(() -> TEST_VALUE * 3);

                IOPath<Integer> result = path.via(Path::ioPure);

                assertThat(result.unsafeRun()).isEqualTo(path.unsafeRun());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds",
              () -> {
                IOPath<Integer> path = Path.ioPure(10);

                IOPath<Integer> leftSide = path.via(intToIOString).via(stringToIOInt);
                IOPath<Integer> rightSide =
                    path.via(x -> intToIOString.apply(x).via(stringToIOInt));

                assertThat(leftSide.unsafeRun()).isEqualTo(rightSide.unsafeRun());
              }),
          DynamicTest.dynamicTest(
              "Associativity with same-type chain",
              () -> {
                IOPath<Integer> path = Path.ioPure(5);
                Function<Integer, IOPath<Integer>> addTen = x -> Path.ioPure(x + 10);
                Function<Integer, IOPath<Integer>> multiplyTwo = x -> Path.ioPure(x * 2);

                IOPath<Integer> leftSide = path.via(addTen).via(multiplyTwo);
                IOPath<Integer> rightSide = path.via(x -> addTen.apply(x).via(multiplyTwo));

                assertThat(leftSide.unsafeRun()).isEqualTo(rightSide.unsafeRun());
              }));
    }
  }

  @Nested
  @DisplayName("Deferred Execution Invariants")
  class DeferredExecutionInvariantsTests {

    @TestFactory
    @DisplayName("Effects are not executed until unsafeRun")
    Stream<DynamicTest> effectsAreDeferred() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "map does not execute effects",
              () -> {
                int[] counter = {0};
                IOPath<Integer> path =
                    Path.io(
                        () -> {
                          counter[0]++;
                          return TEST_VALUE;
                        });

                IOPath<Integer> mapped = path.map(ADD_ONE);

                // Effect not executed yet
                assertThat(counter[0]).isEqualTo(0);

                // Execute
                mapped.unsafeRun();
                assertThat(counter[0]).isEqualTo(1);
              }),
          DynamicTest.dynamicTest(
              "via does not execute effects",
              () -> {
                int[] counter = {0};
                IOPath<Integer> path =
                    Path.io(
                        () -> {
                          counter[0]++;
                          return TEST_VALUE;
                        });

                IOPath<String> viaMapped = path.via(x -> Path.ioPure("value:" + x));

                // Effect not executed yet
                assertThat(counter[0]).isEqualTo(0);

                // Execute
                viaMapped.unsafeRun();
                assertThat(counter[0]).isEqualTo(1);
              }));
    }

    @TestFactory
    @DisplayName("Multiple runs execute effects multiple times")
    Stream<DynamicTest> multipleRunsExecuteMultipleTimes() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Each unsafeRun executes the effect",
              () -> {
                int[] counter = {0};
                IOPath<Integer> path =
                    Path.io(
                        () -> {
                          counter[0]++;
                          return counter[0];
                        });

                assertThat(path.unsafeRun()).isEqualTo(1);
                assertThat(path.unsafeRun()).isEqualTo(2);
                assertThat(path.unsafeRun()).isEqualTo(3);
              }));
    }
  }

  @Nested
  @DisplayName("via and flatMap consistency")
  class ViaFlatMapConsistencyTests {

    @TestFactory
    @DisplayName("via and flatMap produce same results")
    Stream<DynamicTest> viaAndFlatMapConsistent() {
      Function<Integer, IOPath<String>> f = x -> Path.ioPure("value:" + x);

      return Stream.of(
          DynamicTest.dynamicTest(
              "via and flatMap produce same result",
              () -> {
                IOPath<Integer> path = Path.ioPure(TEST_VALUE);

                IOPath<String> viaResult = path.via(f);
                IOPath<String> flatMapResult = path.flatMap(f);

                assertThat(viaResult.unsafeRun()).isEqualTo(flatMapResult.unsafeRun());
              }));
    }
  }
}
