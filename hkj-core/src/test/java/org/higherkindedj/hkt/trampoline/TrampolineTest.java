// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.assertj.core.api.Assertions.*;

import java.math.BigInteger;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link Trampoline}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Basic operations (done, defer, map, flatMap)
 *   <li>Stack safety with deep recursion (100,000+ iterations)
 *   <li>Mutual recursion
 *   <li>Edge cases and error handling
 * </ul>
 */
@DisplayName("Trampoline Tests")
class TrampolineTest extends TrampolineTestBase {

  @Nested
  @DisplayName("Construction Tests")
  class ConstructionTests {

    @Test
    @DisplayName("done() creates a completed trampoline")
    void doneCreatesCompletedTrampoline() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      assertThat(trampoline).isInstanceOf(Trampoline.Done.class);
      assertThat(trampoline.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("done() accepts null values")
    void doneAcceptsNull() {
      Trampoline<String> trampoline = Trampoline.done(null);

      assertThat(trampoline).isInstanceOf(Trampoline.Done.class);
      assertThat(trampoline.run()).isNull();
    }

    @Test
    @DisplayName("defer() creates a suspended trampoline")
    void deferCreatesSuspendedTrampoline() {
      Trampoline<Integer> trampoline = Trampoline.defer(() -> Trampoline.done(42));

      assertThat(trampoline).isInstanceOf(Trampoline.More.class);
      assertThat(trampoline.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("defer() with null supplier throws NullPointerException")
    void deferWithNullThrows() {
      assertThatThrownBy(() -> Trampoline.defer(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Map Tests")
  class MapTests {

    @Test
    @DisplayName("map() transforms the result")
    void mapTransformsResult() {
      Trampoline<Integer> trampoline = Trampoline.done(42);
      Trampoline<Integer> mapped = trampoline.map(x -> x * 2);

      assertThat(mapped.run()).isEqualTo(84);
    }

    @Test
    @DisplayName("map() is lazy")
    void mapIsLazy() {
      Trampoline<Integer> trampoline = Trampoline.done(42);
      Trampoline<String> mapped = trampoline.map(Object::toString);

      // No exception thrown until run()
      assertThat(mapped).isNotNull();
      assertThat(mapped.run()).isEqualTo("42");
    }

    @Test
    @DisplayName("map() with null function throws NullPointerException")
    void mapWithNullThrows() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      assertThatThrownBy(() -> trampoline.map(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map() preserves stack safety")
    void mapPreservesStackSafety() {
      Trampoline<Integer> trampoline = Trampoline.done(0);

      // Chain 10,000 maps
      for (int i = 0; i < 10_000; i++) {
        trampoline = trampoline.map(x -> x + 1);
      }

      assertThat(trampoline.run()).isEqualTo(10_000);
    }
  }

  @Nested
  @DisplayName("FlatMap Tests")
  class FlatMapTests {

    @Test
    @DisplayName("flatMap() sequences computations")
    void flatMapSequencesComputations() {
      Trampoline<Integer> trampoline = Trampoline.done(42);
      Trampoline<Integer> flatMapped = trampoline.flatMap(x -> Trampoline.done(x * 2));

      assertThat(flatMapped.run()).isEqualTo(84);
    }

    @Test
    @DisplayName("flatMap() is lazy")
    void flatMapIsLazy() {
      Trampoline<Integer> trampoline = Trampoline.done(42);
      Trampoline<Integer> flatMapped = trampoline.flatMap(x -> Trampoline.done(x * 2));

      // No exception thrown until run()
      assertThat(flatMapped).isNotNull();
      assertThat(flatMapped.run()).isEqualTo(84);
    }

    @Test
    @DisplayName("flatMap() with null function throws NullPointerException")
    void flatMapWithNullFunctionThrows() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      assertThatThrownBy(() -> trampoline.flatMap(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap() with function returning null throws KindUnwrapException")
    void flatMapWithNullReturningFunctionThrows() {
      Trampoline<Integer> trampoline = Trampoline.done(42);
      Trampoline<Integer> flatMapped = trampoline.flatMap(x -> null);

      assertThatThrownBy(flatMapped::run).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("flatMap() preserves stack safety")
    void flatMapPreservesStackSafety() {
      Trampoline<Integer> trampoline = Trampoline.done(0);

      // Chain 10,000 flatMaps
      for (int i = 0; i < 10_000; i++) {
        trampoline = trampoline.flatMap(x -> Trampoline.done(x + 1));
      }

      assertThat(trampoline.run()).isEqualTo(10_000);
    }
  }

  @Nested
  @DisplayName("Stack Safety Tests")
  class StackSafetyTests {

    @Test
    @DisplayName("Factorial with 10,000 iterations is stack-safe")
    void factorialTenThousandIterations() {
      // This would overflow the stack with naive recursion
      Trampoline<Long> result = factorial(10_000, 1L);

      // Just verify it completes without StackOverflowError
      assertThat(result.run()).isNotNull();
    }

    @Test
    @DisplayName("Factorial with 100,000 iterations is stack-safe")
    void factorialOneHundredThousandIterations() {
      // This is a stress test for stack safety
      Trampoline<Long> result = factorial(100_000, 1L);

      // Just verify it completes without StackOverflowError
      assertThat(result.run()).isNotNull();
    }

    @Test
    @DisplayName("BigInteger factorial with large numbers is stack-safe")
    void bigIntegerFactorialIsStackSafe() {
      Trampoline<BigInteger> result =
          bigIntegerFactorial(BigInteger.valueOf(10_000), BigInteger.ONE);

      // Verify it completes and produces a valid result
      BigInteger factorialResult = result.run();
      assertThat(factorialResult).isNotNull();
      assertThat(factorialResult).isGreaterThan(BigInteger.ZERO);
    }

    @Test
    @DisplayName("Mutual recursion with 100,000 iterations is stack-safe")
    void mutualRecursionOneHundredThousandIterations() {
      Trampoline<Boolean> result = isEven(100_000);

      assertThat(result.run()).isTrue();
    }

    @Test
    @DisplayName("Mutual recursion with odd number is stack-safe")
    void mutualRecursionOddNumber() {
      Trampoline<Boolean> result = isEven(99_999);

      assertThat(result.run()).isFalse();
    }

    @Test
    @DisplayName("Deep chain of defer() is stack-safe")
    void deepChainOfDeferIsStackSafe() {
      Trampoline<Integer> trampoline = buildDeeplyNestedDefer(100_000, 0);

      assertThat(trampoline.run()).isEqualTo(100_000);
    }

    @Test
    @DisplayName("Deep chain of flatMap with defer is stack-safe")
    void deepChainOfFlatMapWithDeferIsStackSafe() {
      Trampoline<Integer> trampoline = Trampoline.done(0);

      for (int i = 0; i < 100_000; i++) {
        int currentI = i;
        trampoline = trampoline.flatMap(x -> Trampoline.defer(() -> Trampoline.done(currentI + 1)));
      }

      assertThat(trampoline.run()).isEqualTo(100_000);
    }

    /**
     * Helper to create a deeply nested defer chain.
     *
     * @param depth The depth of nesting.
     * @param current The current counter value.
     * @return A deeply nested trampoline.
     */
    private Trampoline<Integer> buildDeeplyNestedDefer(int depth, int current) {
      if (depth <= 0) {
        return Trampoline.done(current);
      }
      return Trampoline.defer(() -> buildDeeplyNestedDefer(depth - 1, current + 1));
    }

    /**
     * Helper for BigInteger factorial.
     *
     * @param n The number.
     * @param acc The accumulator.
     * @return The factorial as a Trampoline.
     */
    private Trampoline<BigInteger> bigIntegerFactorial(BigInteger n, BigInteger acc) {
      if (n.compareTo(BigInteger.ZERO) <= 0) {
        return Trampoline.done(acc);
      }
      return Trampoline.defer(
          () -> bigIntegerFactorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
    }
  }

  @Nested
  @DisplayName("runT Tests")
  class RunTTests {

    @Test
    @DisplayName("runT() is an alias for run()")
    void runTIsAliasForRun() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      assertThat(trampoline.runT()).isEqualTo(trampoline.run());
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Empty defer chain returns initial value")
    void emptyDeferChainReturnsInitialValue() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      assertThat(trampoline.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("Single defer unwraps correctly")
    void singleDeferUnwrapsCorrectly() {
      Trampoline<Integer> trampoline = Trampoline.defer(() -> Trampoline.done(42));

      assertThat(trampoline.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("Multiple nested defers unwrap correctly")
    void multipleNestedDefersUnwrapCorrectly() {
      Trampoline<Integer> trampoline =
          Trampoline.defer(
              () -> Trampoline.defer(() -> Trampoline.defer(() -> Trampoline.done(42))));

      assertThat(trampoline.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("Combining map and flatMap works correctly")
    void combiningMapAndFlatMapWorks() {
      Trampoline<Integer> trampoline =
          Trampoline.done(10)
              .map(x -> x * 2) // 20
              .flatMap(x -> Trampoline.done(x + 5)) // 25
              .map(x -> x * 3); // 75

      assertThat(trampoline.run()).isEqualTo(75);
    }
  }
}
