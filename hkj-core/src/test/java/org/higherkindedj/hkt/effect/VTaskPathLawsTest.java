// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.test.assertions.VTaskPathAssert.assertThatVTaskPath;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for VTaskPath.
 *
 * <p>Verifies that VTaskPath satisfies Functor and Monad laws. Since VTaskPath represents deferred
 * computations that execute on virtual threads, laws are verified by executing the effects and
 * comparing results.
 */
@DisplayName("VTaskPath Law Verification Tests")
class VTaskPathLawsTest {

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
                VTaskPath<Integer> path = Path.vtaskPure(TEST_VALUE);
                VTaskPath<Integer> result = path.map(Function.identity());

                assertThatVTaskPath(result).isEquivalentTo(path);
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for deferred computation",
              () -> {
                VTaskPath<Integer> path = Path.vtask(() -> TEST_VALUE * 2);
                VTaskPath<Integer> result = path.map(Function.identity());

                assertThatVTaskPath(result).isEquivalentTo(path);
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds",
              () -> {
                VTaskPath<Integer> path = Path.vtaskPure(TEST_VALUE);

                VTaskPath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                VTaskPath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThatVTaskPath(leftSide).isEquivalentTo(rightSide);
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                VTaskPath<Integer> path = Path.vtaskPure(TEST_VALUE);

                VTaskPath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                VTaskPath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThatVTaskPath(leftSide).isEquivalentTo(rightSide);
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, VTaskPath<String>> intToVTaskString =
        x -> Path.vtask(() -> "computed:" + x);

    private final Function<String, VTaskPath<Integer>> stringToVTaskInt =
        s -> Path.vtask(s::length);

    @TestFactory
    @DisplayName("Left Identity Law: Path.vtaskPure(a).via(f).unsafeRun() == f(a).unsafeRun()")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity holds",
              () -> {
                int value = 10;

                VTaskPath<String> leftSide = Path.vtaskPure(value).via(intToVTaskString);
                VTaskPath<String> rightSide = intToVTaskString.apply(value);

                assertThatVTaskPath(leftSide).isEquivalentTo(rightSide);
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(Path::vtaskPure).unsafeRun() == path.unsafeRun()")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for pure value",
              () -> {
                VTaskPath<Integer> path = Path.vtaskPure(TEST_VALUE);

                VTaskPath<Integer> result = path.via(Path::vtaskPure);

                assertThatVTaskPath(result).isEquivalentTo(path);
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for deferred computation",
              () -> {
                VTaskPath<Integer> path = Path.vtask(() -> TEST_VALUE * 3);

                VTaskPath<Integer> result = path.via(Path::vtaskPure);

                assertThatVTaskPath(result).isEquivalentTo(path);
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds",
              () -> {
                VTaskPath<Integer> path = Path.vtaskPure(10);

                VTaskPath<Integer> leftSide = path.via(intToVTaskString).via(stringToVTaskInt);
                VTaskPath<Integer> rightSide =
                    path.via(x -> intToVTaskString.apply(x).via(stringToVTaskInt));

                assertThatVTaskPath(leftSide).isEquivalentTo(rightSide);
              }),
          DynamicTest.dynamicTest(
              "Associativity with same-type chain",
              () -> {
                VTaskPath<Integer> path = Path.vtaskPure(5);
                Function<Integer, VTaskPath<Integer>> addTen = x -> Path.vtaskPure(x + 10);
                Function<Integer, VTaskPath<Integer>> multiplyTwo = x -> Path.vtaskPure(x * 2);

                VTaskPath<Integer> leftSide = path.via(addTen).via(multiplyTwo);
                VTaskPath<Integer> rightSide = path.via(x -> addTen.apply(x).via(multiplyTwo));

                assertThatVTaskPath(leftSide).isEquivalentTo(rightSide);
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
                VTaskPath<Integer> path =
                    Path.vtask(
                        () -> {
                          counter[0]++;
                          return TEST_VALUE;
                        });

                VTaskPath<Integer> mapped = path.map(ADD_ONE);

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
                VTaskPath<Integer> path =
                    Path.vtask(
                        () -> {
                          counter[0]++;
                          return TEST_VALUE;
                        });

                VTaskPath<String> viaMapped = path.via(x -> Path.vtaskPure("value:" + x));

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
                VTaskPath<Integer> path =
                    Path.vtask(
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
      Function<Integer, VTaskPath<String>> f = x -> Path.vtaskPure("value:" + x);

      return Stream.of(
          DynamicTest.dynamicTest(
              "via and flatMap produce same result",
              () -> {
                VTaskPath<Integer> path = Path.vtaskPure(TEST_VALUE);

                VTaskPath<String> viaResult = path.via(f);
                VTaskPath<String> flatMapResult = path.flatMap(f);

                assertThatVTaskPath(viaResult).isEquivalentTo(flatMapResult);
              }));
    }
  }

  @Nested
  @DisplayName("Virtual Thread Execution")
  class VirtualThreadExecutionTests {

    @TestFactory
    @DisplayName("VTaskPath runAsync() executes on virtual threads")
    Stream<DynamicTest> executesOnVirtualThreads() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Async computation runs on virtual thread",
              () -> {
                VTaskPath<Boolean> path = Path.vtask(() -> Thread.currentThread().isVirtual());

                // runAsync() uses virtual threads, unsafeRun() runs on calling thread
                Boolean result = path.runAsync().get();
                assertThat(result).isTrue();
              }));
    }
  }
}
