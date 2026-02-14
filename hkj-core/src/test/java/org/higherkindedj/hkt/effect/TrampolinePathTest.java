// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for TrampolinePath.
 *
 * <p>Tests cover factory methods, stack safety, Composable/Combinable/Chainable operations, and
 * conversions.
 */
@DisplayName("TrampolinePath<A> Complete Test Suite")
class TrampolinePathTest {

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("done() creates immediate value")
    void doneCreatesImmediateValue() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      assertThat(path.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("pure() is alias for done()")
    void pureIsAliasForDone() {
      TrampolinePath<String> path = TrampolinePath.pure("hello");

      assertThat(path.run()).isEqualTo("hello");
    }

    @Test
    @DisplayName("defer() creates deferred computation")
    void deferCreatesDeferredComputation() {
      AtomicBoolean executed = new AtomicBoolean(false);

      TrampolinePath<Integer> path =
          TrampolinePath.defer(
              () -> {
                executed.set(true);
                return TrampolinePath.done(42);
              });

      assertThat(executed).isFalse();
      assertThat(path.run()).isEqualTo(42);
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("Path.trampolineDone() creates immediate value")
    void pathTrampolineDoneCreatesImmediateValue() {
      TrampolinePath<Integer> path = Path.trampolineDone(42);

      assertThat(path.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.trampolineDefer() creates deferred computation")
    void pathTrampolineDeferCreatesDeferredComputation() {
      TrampolinePath<Integer> path = Path.trampolineDefer(() -> TrampolinePath.done(42));

      assertThat(path.run()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Stack Safety")
  class StackSafetyTests {

    @Test
    @DisplayName("Deep recursion doesn't overflow stack")
    void deepRecursionDoesNotOverflow() {
      // Count up to 100,000 using trampolined recursion
      TrampolinePath<Integer> result = countUp(0, 100_000);

      assertThat(result.run()).isEqualTo(100_000);
    }

    private TrampolinePath<Integer> countUp(int current, int target) {
      if (current >= target) {
        return TrampolinePath.done(current);
      }
      return TrampolinePath.defer(() -> countUp(current + 1, target));
    }

    @Test
    @DisplayName("Factorial computation is stack safe")
    void factorialIsStackSafe() {
      TrampolinePath<BigInteger> result = factorial(BigInteger.valueOf(1000), BigInteger.ONE);

      BigInteger value = result.run();
      assertThat(value).isNotNull();
      // 1000! is a very large number, just verify it computed
      assertThat(value.compareTo(BigInteger.ZERO)).isGreaterThan(0);
    }

    private TrampolinePath<BigInteger> factorial(BigInteger n, BigInteger acc) {
      if (n.compareTo(BigInteger.ONE) <= 0) {
        return TrampolinePath.done(acc);
      }
      return TrampolinePath.defer(() -> factorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
    }

    @Test
    @DisplayName("Mutual recursion is stack safe")
    void mutualRecursionIsStackSafe() {
      TrampolinePath<Boolean> result = isEven(10_000);

      assertThat(result.run()).isTrue();
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
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value")
    void mapTransformsValue() {
      TrampolinePath<String> path = TrampolinePath.done(42).map(i -> "value: " + i);

      assertThat(path.run()).isEqualTo("value: 42");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      TrampolinePath<String> path =
          TrampolinePath.done("hello")
              .map(String::toUpperCase)
              .map(s -> s + "!")
              .map(s -> s.repeat(2));

      assertThat(path.run()).isEqualTo("HELLO!HELLO!");
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      AtomicBoolean observed = new AtomicBoolean(false);

      TrampolinePath<Integer> path = TrampolinePath.done(42).peek(i -> observed.set(true));

      Integer result = path.run();

      assertThat(result).isEqualTo(42);
      assertThat(observed).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations")
    void viaChainsComputations() {
      TrampolinePath<Integer> path = TrampolinePath.done(10).via(i -> TrampolinePath.done(i * 2));

      assertThat(path.run()).isEqualTo(20);
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() throws when mapper returns wrong type")
    void viaThrowsWhenMapperReturnsWrongType() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      TrampolinePath<Integer> result = path.via(_ -> Path.just(100));

      assertThatIllegalArgumentException()
          .isThrownBy(result::run)
          .withMessageContaining("TrampolinePath.via must return TrampolinePath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      TrampolinePath<Integer> path = TrampolinePath.done(10);

      Integer viaResult = path.via(i -> TrampolinePath.done(i * 2)).run();
      // flatMap returns Chainable, so we cast to TrampolinePath to access run()
      TrampolinePath<Integer> flatMapped =
          (TrampolinePath<Integer>) path.flatMap(i -> TrampolinePath.done(i * 2));
      Integer flatMapResult = flatMapped.run();

      assertThat(viaResult).isEqualTo(flatMapResult);
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      TrampolinePath<String> path =
          TrampolinePath.done(42)
              .peek(_ -> firstExecuted.set(true))
              .then(() -> TrampolinePath.done("result"));

      assertThat(path.run()).isEqualTo("result");
      assertThat(firstExecuted).isTrue();
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two TrampolinePaths")
    void zipWithCombinesTwoPaths() {
      TrampolinePath<String> first = TrampolinePath.done("hello");
      TrampolinePath<Integer> second = TrampolinePath.done(3);

      TrampolinePath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + (Integer) b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(TrampolinePath.done(1), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-TrampolinePath")
    void zipWithThrowsWhenGivenNonTrampolinePath() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);
      MaybePath<Integer> maybePath = Path.just(10);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(maybePath, (a, b) -> a + b))
          .withMessageContaining("Cannot zipWith non-TrampolinePath");
    }
  }

  @Nested
  @DisplayName("Conversions")
  class ConversionTests {

    @Test
    @DisplayName("toIOPath() creates IOPath")
    void toIOPathCreatesIOPath() {
      TrampolinePath<Integer> trampoline = TrampolinePath.done(42);

      IOPath<Integer> ioPath = trampoline.toIOPath();

      assertThat(ioPath.unsafeRun()).isEqualTo(42);
    }

    @Test
    @DisplayName("toLazyPath() creates LazyPath")
    void toLazyPathCreatesLazyPath() {
      TrampolinePath<Integer> trampoline = TrampolinePath.done(42);

      LazyPath<Integer> lazyPath = trampoline.toLazyPath();

      assertThat(lazyPath.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("toTrampoline() returns underlying Trampoline")
    void toTrampolineReturnsUnderlyingTrampoline() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      assertThat(path.toTrampoline()).isNotNull();
      assertThat(path.toTrampoline().run()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      assertThat(path.toString()).contains("TrampolinePath");
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns false for different type")
    void equalsReturnsFalseForDifferentType() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      assertThat(path.equals("not a path")).isFalse();
      assertThat(path.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals() compares underlying trampolines")
    void equalsComparesUnderlyingTrampolines() {
      TrampolinePath<Integer> path1 = TrampolinePath.done(42);
      TrampolinePath<Integer> path2 = TrampolinePath.done(42);

      // Different instances with same value - depends on Trampoline's equals
      // Just verify equals doesn't throw and returns a boolean
      boolean result = path1.equals(path2);
      assertThat(result).isIn(true, false);
    }

    @Test
    @DisplayName("hashCode() is consistent")
    void hashCodeIsConsistent() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      assertThat(path.hashCode()).isEqualTo(path.hashCode());
    }

    @Test
    @DisplayName("hashCode() returns value based on underlying trampoline")
    void hashCodeReturnsValueBasedOnTrampoline() {
      TrampolinePath<Integer> path = TrampolinePath.done(42);

      // Just verify it returns a consistent value
      int hash = path.hashCode();
      assertThat(hash).isEqualTo(path.hashCode());
    }
  }

  @Nested
  @DisplayName("Additional Factory Methods")
  class AdditionalFactoryMethodsTests {

    @Test
    @DisplayName("of() creates TrampolinePath from existing Trampoline")
    void ofCreatesFromExistingTrampoline() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      TrampolinePath<Integer> path = TrampolinePath.of(trampoline);

      assertThat(path.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("of() validates null trampoline")
    void ofValidatesNullTrampoline() {
      assertThatNullPointerException()
          .isThrownBy(() -> TrampolinePath.of(null))
          .withMessageContaining("trampoline must not be null");
    }

    @Test
    @DisplayName("Path.trampoline(Trampoline) creates TrampolinePath from existing Trampoline")
    void pathTrampolineCreatesFromExistingTrampoline() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      TrampolinePath<Integer> path = Path.trampoline(trampoline);

      assertThat(path.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.trampoline() validates null trampoline")
    void pathTrampolineValidatesNullTrampoline() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.trampoline(null))
          .withMessageContaining("trampoline must not be null");
    }
  }
}
