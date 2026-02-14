// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for TrampolinePath.
 *
 * <p>Verifies that TrampolinePath satisfies Functor and Monad laws while maintaining stack safety.
 */
@DisplayName("TrampolinePath Law Verification Tests")
class TrampolinePathLawsTest {

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
              "Identity law holds for done value",
              () -> {
                TrampolinePath<Integer> path = TrampolinePath.done(TEST_VALUE);
                TrampolinePath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for deferred value",
              () -> {
                TrampolinePath<Integer> path =
                    TrampolinePath.defer(() -> TrampolinePath.done(TEST_VALUE));
                TrampolinePath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for nested defer",
              () -> {
                TrampolinePath<Integer> path =
                    TrampolinePath.defer(
                        () -> TrampolinePath.defer(() -> TrampolinePath.done(TEST_VALUE)));
                TrampolinePath<Integer> result = path.map(Function.identity());
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for done value",
              () -> {
                TrampolinePath<Integer> path = TrampolinePath.done(TEST_VALUE);
                TrampolinePath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                TrampolinePath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                TrampolinePath<Integer> path = TrampolinePath.done(TEST_VALUE);
                TrampolinePath<Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                TrampolinePath<Integer> rightSide = path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for deferred value",
              () -> {
                TrampolinePath<Integer> path =
                    TrampolinePath.defer(() -> TrampolinePath.done(TEST_VALUE));
                TrampolinePath<Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                TrampolinePath<Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, TrampolinePath<String>> intToTrampolineString =
        x -> TrampolinePath.done("result:" + x);

    private final Function<String, TrampolinePath<Integer>> stringToTrampolineInt =
        s -> TrampolinePath.done(s.length());

    private final Function<Integer, TrampolinePath<Integer>> deferredDouble =
        x -> TrampolinePath.defer(() -> TrampolinePath.done(x * 2));

    @TestFactory
    @DisplayName("Left Identity Law: TrampolinePath.done(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with done function",
              () -> {
                int value = 10;
                TrampolinePath<String> leftSide =
                    TrampolinePath.done(value).via(intToTrampolineString);
                TrampolinePath<String> rightSide = intToTrampolineString.apply(value);
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Left identity with deferred function",
              () -> {
                int value = 10;
                TrampolinePath<Integer> leftSide = TrampolinePath.done(value).via(deferredDouble);
                TrampolinePath<Integer> rightSide = deferredDouble.apply(value);
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> TrampolinePath.done(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for done value",
              () -> {
                TrampolinePath<Integer> path = TrampolinePath.done(TEST_VALUE);
                TrampolinePath<Integer> result = path.via(x -> TrampolinePath.done(x));
                assertThat(result.run()).isEqualTo(path.run());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for deferred value",
              () -> {
                TrampolinePath<Integer> path =
                    TrampolinePath.defer(() -> TrampolinePath.done(TEST_VALUE));
                TrampolinePath<Integer> result = path.via(x -> TrampolinePath.done(x));
                assertThat(result.run()).isEqualTo(path.run());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for done value",
              () -> {
                TrampolinePath<Integer> path = TrampolinePath.done(10);
                TrampolinePath<Integer> leftSide =
                    path.via(intToTrampolineString).via(stringToTrampolineInt);
                TrampolinePath<Integer> rightSide =
                    path.via(x -> intToTrampolineString.apply(x).via(stringToTrampolineInt));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for deferred value",
              () -> {
                TrampolinePath<Integer> path = TrampolinePath.defer(() -> TrampolinePath.done(10));
                TrampolinePath<Integer> leftSide =
                    path.via(intToTrampolineString).via(stringToTrampolineInt);
                TrampolinePath<Integer> rightSide =
                    path.via(x -> intToTrampolineString.apply(x).via(stringToTrampolineInt));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }),
          DynamicTest.dynamicTest(
              "Associativity with deferred functions",
              () -> {
                TrampolinePath<Integer> path = TrampolinePath.done(5);
                Function<Integer, TrampolinePath<Integer>> f =
                    x -> TrampolinePath.defer(() -> TrampolinePath.done(x + 1));
                Function<Integer, TrampolinePath<Integer>> g =
                    x -> TrampolinePath.defer(() -> TrampolinePath.done(x * 2));

                TrampolinePath<Integer> leftSide = path.via(f).via(g);
                TrampolinePath<Integer> rightSide = path.via(x -> f.apply(x).via(g));
                assertThat(leftSide.run()).isEqualTo(rightSide.run());
              }));
    }
  }

  @Nested
  @DisplayName("Stack Safety")
  class StackSafetyTests {

    @TestFactory
    @DisplayName("Operations are stack safe")
    Stream<DynamicTest> operationsAreStackSafe() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Deep recursion with defer",
              () -> {
                int depth = 100_000;
                TrampolinePath<Integer> path = countTo(0, depth);
                assertThat(path.run()).isEqualTo(depth);
              }),
          DynamicTest.dynamicTest(
              "Deep map chains are stack safe",
              () -> {
                TrampolinePath<Integer> path = TrampolinePath.done(0);
                for (int i = 0; i < 10000; i++) {
                  path = path.map(x -> x + 1);
                }
                assertThat(path.run()).isEqualTo(10000);
              }),
          DynamicTest.dynamicTest(
              "Deep via chains are stack safe",
              () -> {
                TrampolinePath<Integer> path = TrampolinePath.done(0);
                for (int i = 0; i < 10000; i++) {
                  path = path.via(x -> TrampolinePath.done(x + 1));
                }
                assertThat(path.run()).isEqualTo(10000);
              }),
          DynamicTest.dynamicTest(
              "Factorial of large number is stack safe",
              () -> {
                BigInteger n = BigInteger.valueOf(5000);
                TrampolinePath<BigInteger> result = factorial(n, BigInteger.ONE);
                BigInteger value = result.run();
                // Just verify it completes without stack overflow
                assertThat(value).isPositive();
              }),
          DynamicTest.dynamicTest(
              "Mutual recursion is stack safe",
              () -> {
                int n = 100_000;
                TrampolinePath<Boolean> result = isEven(n);
                assertThat(result.run()).isTrue();
              }));
    }

    private TrampolinePath<Integer> countTo(int current, int target) {
      if (current >= target) {
        return TrampolinePath.done(current);
      }
      return TrampolinePath.defer(() -> countTo(current + 1, target));
    }

    private TrampolinePath<BigInteger> factorial(BigInteger n, BigInteger acc) {
      if (n.compareTo(BigInteger.ONE) <= 0) {
        return TrampolinePath.done(acc);
      }
      return TrampolinePath.defer(() -> factorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
    }

    private TrampolinePath<Boolean> isEven(int n) {
      if (n == 0) return TrampolinePath.done(true);
      return TrampolinePath.defer(() -> isOdd(n - 1));
    }

    private TrampolinePath<Boolean> isOdd(int n) {
      if (n == 0) return TrampolinePath.done(false);
      return TrampolinePath.defer(() -> isEven(n - 1));
    }
  }

  @Nested
  @DisplayName("Combinable Laws")
  class CombinableLawsTests {

    @TestFactory
    @DisplayName("zipWith behaves correctly")
    Stream<DynamicTest> zipWithTests() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "zipWith combines done values",
              () -> {
                TrampolinePath<Integer> a = TrampolinePath.done(10);
                TrampolinePath<Integer> b = TrampolinePath.done(20);
                TrampolinePath<Integer> result = a.zipWith(b, Integer::sum);
                assertThat(result.run()).isEqualTo(30);
              }),
          DynamicTest.dynamicTest(
              "zipWith combines deferred values",
              () -> {
                TrampolinePath<Integer> a = TrampolinePath.defer(() -> TrampolinePath.done(10));
                TrampolinePath<Integer> b = TrampolinePath.defer(() -> TrampolinePath.done(20));
                TrampolinePath<Integer> result = a.zipWith(b, Integer::sum);
                assertThat(result.run()).isEqualTo(30);
              }),
          DynamicTest.dynamicTest(
              "zipWith with type-changing combiner",
              () -> {
                TrampolinePath<String> a = TrampolinePath.done("hello");
                TrampolinePath<Integer> b = TrampolinePath.done(3);
                TrampolinePath<String> result = a.zipWith(b, (s, n) -> s.repeat(n));
                assertThat(result.run()).isEqualTo("hellohellohello");
              }));
    }
  }

  @Nested
  @DisplayName("Conversion Laws")
  class ConversionLawsTests {

    @TestFactory
    @DisplayName("Conversions preserve values")
    Stream<DynamicTest> conversionTests() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "toIOPath preserves value",
              () -> {
                TrampolinePath<Integer> trampoline = TrampolinePath.done(TEST_VALUE);
                IOPath<Integer> io = trampoline.toIOPath();
                assertThat(io.unsafeRun()).isEqualTo(TEST_VALUE);
              }),
          DynamicTest.dynamicTest(
              "toIOPath preserves deferred computation",
              () -> {
                TrampolinePath<Integer> trampoline =
                    TrampolinePath.defer(() -> TrampolinePath.done(TEST_VALUE));
                IOPath<Integer> io = trampoline.toIOPath();
                assertThat(io.unsafeRun()).isEqualTo(TEST_VALUE);
              }),
          DynamicTest.dynamicTest(
              "toLazyPath preserves value",
              () -> {
                TrampolinePath<Integer> trampoline = TrampolinePath.done(TEST_VALUE);
                LazyPath<Integer> lazy = trampoline.toLazyPath();
                assertThat(lazy.get()).isEqualTo(TEST_VALUE);
              }),
          DynamicTest.dynamicTest(
              "toLazyPath preserves deferred computation",
              () -> {
                TrampolinePath<Integer> trampoline =
                    TrampolinePath.defer(() -> TrampolinePath.done(TEST_VALUE));
                LazyPath<Integer> lazy = trampoline.toLazyPath();
                assertThat(lazy.get()).isEqualTo(TEST_VALUE);
              }));
    }
  }
}
