// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link TrampolineMonad}.
 *
 * <p>Verifies that the Monad instance for Trampoline correctly implements functor, applicative, and
 * monadic operations while maintaining stack safety. The Monad laws are driven by the shipped
 * {@link MonadLaws} over {@link TrampolineLawFixtures}.
 */
@DisplayName("TrampolineMonad Tests")
class TrampolineMonadTest extends TrampolineTestBase {

  private final Monad<TrampolineKind.Witness> monad = Instances.monad(trampoline());

  private final Function<Integer, Kind<TrampolineKind.Witness, Integer>> testFunction =
      x -> monad.of(x * 2);
  private final Function<Integer, Kind<TrampolineKind.Witness, Integer>> chainFunction =
      x -> monad.of(x + 10);

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}/{@code
   * flatMap} <em>propagate</em> a thrown function exception immediately, but {@code Trampoline}
   * defers — both build a {@code FlatMap} structure, so a thrown function only surfaces at {@link
   * Trampoline#run()} time. The deferred null-result behaviour is exercised in {@link FlatMapTests}
   * and {@link EdgeCasesAndCoverage}. The Monad laws are verified in the {@code Laws} block below.
   */
  @Test
  @DisplayName("Monad contract — operations & validations")
  void monadContract() {
    TypeClassContract.<TrampolineKind.Witness>monad(TrampolineMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(TRAMPOLINE.widen(Trampoline.done(DEFAULT_VALUE)))
        .withMonadOperations(
            TRAMPOLINE.widen(Trampoline.done(ALTERNATIVE_VALUE)),
            Object::toString,
            x -> monad.of("flat:" + x),
            TRAMPOLINE.widen(Trampoline.done(Object::toString)),
            (a, b) -> "Result:" + a + "," + b)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, TrampolineLawFixtures.EQ);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#kinds")
    void rightIdentity(String label, Kind<TrampolineKind.Witness, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, TrampolineLawFixtures.EQ);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#kinds")
    void associativity(String label, Kind<TrampolineKind.Witness, Integer> ma) {
      MonadLaws.assertAssociativity(
          monad, ma, testFunction, chainFunction, TrampolineLawFixtures.EQ);
    }
  }

  @Nested
  @DisplayName("of() Tests")
  class OfTests {

    @Test
    @DisplayName("of() creates a completed trampoline")
    void ofCreatesCompletedTrampoline() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(42);

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(kind);
      assertThat(trampoline.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("of() accepts null values")
    @SuppressWarnings("DataFlowIssue") // a Trampoline may legitimately hold a null value
    void ofAcceptsNull() {
      Kind<TrampolineKind.Witness, String> kind = monad.of(null);

      Trampoline<String> trampoline = TRAMPOLINE.narrow(kind);
      assertThat(trampoline.run()).isNull();
    }
  }

  @Nested
  @DisplayName("map() Tests")
  class MapTests {

    @Test
    @DisplayName("map() transforms the result")
    void mapTransformsResult() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(42);
      Kind<TrampolineKind.Witness, Integer> mapped = monad.map(x -> x * 2, kind);

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(mapped);
      assertThat(trampoline.run()).isEqualTo(84);
    }

    @Test
    @DisplayName("map() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mapWithNullFunctionThrows() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(42);

      assertThatThrownBy(() -> monad.map(null, kind)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map() with null kind throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mapWithNullKindThrows() {
      assertThatThrownBy(() -> monad.map((Integer x) -> x * 2, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map() preserves stack safety")
    void mapPreservesStackSafety() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(0);

      // Chain 10,000 maps
      for (int i = 0; i < 10_000; i++) {
        kind = monad.map(x -> x + 1, kind);
      }

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(kind);
      assertThat(trampoline.run()).isEqualTo(10_000);
    }
  }

  @Nested
  @DisplayName("flatMap() Tests")
  class FlatMapTests {

    @Test
    @DisplayName("flatMap() sequences computations")
    void flatMapSequencesComputations() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(42);
      Kind<TrampolineKind.Witness, Integer> flatMapped = monad.flatMap(x -> monad.of(x * 2), kind);

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(flatMapped);
      assertThat(trampoline.run()).isEqualTo(84);
    }

    @Test
    @DisplayName("flatMap() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void flatMapWithNullFunctionThrows() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(42);

      assertThatThrownBy(() -> monad.flatMap(null, kind)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap() with null kind throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void flatMapWithNullKindThrows() {
      assertThatThrownBy(() -> monad.flatMap((Integer x) -> monad.of(x * 2), null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap() with function returning null throws KindUnwrapException")
    @SuppressWarnings("DataFlowIssue") // the mapper deliberately returns null
    void flatMapWithNullReturningFunctionThrows() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(42);
      Function<Integer, Kind<TrampolineKind.Witness, Integer>> nullFlatMapper = _ -> null;
      Kind<TrampolineKind.Witness, Integer> flatMapped = monad.flatMap(nullFlatMapper, kind);

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(flatMapped);
      assertThatThrownBy(trampoline::run).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("flatMap() preserves stack safety")
    void flatMapPreservesStackSafety() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(0);

      // Chain 10,000 flatMaps
      for (int i = 0; i < 10_000; i++) {
        kind = monad.flatMap(x -> monad.of(x + 1), kind);
      }

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(kind);
      assertThat(trampoline.run()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("flatMap() with deeply nested computations is stack-safe")
    void flatMapWithDeeplyNestedComputationsIsStackSafe() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(0);

      // Create a deeply nested computation
      kind =
          monad.flatMap(
              x ->
                  monad.flatMap(
                      y -> monad.flatMap(z -> monad.of(x + y + z + 1), monad.of(0)), monad.of(0)),
              kind);

      // Wrap in 1000 more flatMaps
      for (int i = 0; i < 1_000; i++) {
        kind = monad.flatMap(x -> monad.of(x + 1), kind);
      }

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(kind);
      assertThat(trampoline.run()).isEqualTo(1_001);
    }
  }

  @Nested
  @DisplayName("ap() Tests")
  class ApTests {

    @Test
    @DisplayName("ap() applies function to value")
    void apAppliesFunctionToValue() {
      Kind<TrampolineKind.Witness, Integer> value = monad.of(42);
      Kind<TrampolineKind.Witness, Function<Integer, Integer>> func = monad.of(x -> x * 2);

      Kind<TrampolineKind.Witness, Integer> result = monad.ap(func, value);

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(result);
      assertThat(trampoline.run()).isEqualTo(84);
    }

    @Test
    @DisplayName("ap() with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void apWithNullFunctionThrows() {
      Kind<TrampolineKind.Witness, Integer> value = monad.of(42);

      assertThatThrownBy(() -> monad.ap(null, value)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ap() with null value throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void apWithNullValueThrows() {
      Kind<TrampolineKind.Witness, Function<Integer, Integer>> func = monad.of(x -> x * 2);

      assertThatThrownBy(() -> monad.ap(func, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ap() preserves stack safety")
    void apPreservesStackSafety() {
      Kind<TrampolineKind.Witness, Integer> value = monad.of(0);

      // Chain 1,000 ap operations
      for (int i = 0; i < 1_000; i++) {
        Kind<TrampolineKind.Witness, Function<Integer, Integer>> func = monad.of(x -> x + 1);
        value = monad.ap(func, value);
      }

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(value);
      assertThat(trampoline.run()).isEqualTo(1_000);
    }
  }

  @Nested
  @DisplayName("Instance and Inheritance Tests")
  class InstanceTests {

    @Test
    @DisplayName("INSTANCE singleton is accessible")
    void instanceSingletonAccessible() {
      assertThat(Instances.monad(trampoline())).isNotNull();
      assertThat(Instances.monad(trampoline())).isSameAs(monad);
    }

    @Test
    @DisplayName("TrampolineMonad extends TrampolineFunctor")
    void extendsFunctor() {
      assertThat(monad).isInstanceOf(TrampolineFunctor.class);
    }

    @Test
    @DisplayName("Inherited map method works correctly")
    void inheritedMapWorks() {
      // map is inherited from TrampolineFunctor
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(42);
      Kind<TrampolineKind.Witness, Integer> mapped = monad.map(x -> x * 2, kind);

      assertThat(TRAMPOLINE.narrow(mapped).run()).isEqualTo(84);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Coverage")
  class EdgeCasesAndCoverage {

    @Test
    @DisplayName("of() with null creates trampoline with null value")
    @SuppressWarnings("DataFlowIssue") // a Trampoline may legitimately hold a null value
    void ofWithNullCreatesNullTrampoline() {
      Kind<TrampolineKind.Witness, String> kind = monad.of(null);

      Trampoline<String> trampoline = TRAMPOLINE.narrow(kind);
      assertThat(trampoline.run()).isNull();
    }

    @Test
    @DisplayName("flatMap with deferred computation")
    void flatMapWithDeferredComputation() {
      Kind<TrampolineKind.Witness, Integer> kind =
          monad.flatMap(
              x -> TRAMPOLINE.widen(Trampoline.defer(() -> Trampoline.done(x * 2))), monad.of(21));

      assertThat(TRAMPOLINE.narrow(kind).run()).isEqualTo(42);
    }

    @Test
    @DisplayName("ap with null values")
    @SuppressWarnings("ConstantValue") // the value Trampoline deliberately carries a null
    void apWithNullValues() {
      Kind<TrampolineKind.Witness, String> value = monad.of(null);
      Kind<TrampolineKind.Witness, Function<String, String>> func =
          monad.of(x -> x == null ? "was null" : x);

      Kind<TrampolineKind.Witness, String> result = monad.ap(func, value);

      assertThat(TRAMPOLINE.narrow(result).run()).isEqualTo("was null");
    }

    @Test
    @DisplayName("Complex chain of monad operations")
    void complexChainOfOperations() {
      Kind<TrampolineKind.Witness, Integer> result =
          monad.flatMap(
              x -> monad.flatMap(y -> monad.map(z -> x + y + z, monad.of(3)), monad.of(2)),
              monad.of(1));

      assertThat(TRAMPOLINE.narrow(result).run()).isEqualTo(6); // 1 + 2 + 3
    }

    @Test
    @DisplayName("flatMap result validation catches null")
    @SuppressWarnings("DataFlowIssue") // the mapper deliberately returns null
    void flatMapResultValidationCatchesNull() {
      Kind<TrampolineKind.Witness, Integer> kind = monad.of(42);
      Function<Integer, Kind<TrampolineKind.Witness, Integer>> nullFlatMapper = _ -> null;
      Kind<TrampolineKind.Witness, Integer> mapped = monad.flatMap(nullFlatMapper, kind);

      Trampoline<Integer> trampoline = TRAMPOLINE.narrow(mapped);
      assertThatThrownBy(trampoline::run).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("ap implementation uses flatMap internally")
    void apUsesFlatMap() {
      // Test that ap works correctly (it uses flatMap under the hood)
      Kind<TrampolineKind.Witness, Integer> value = monad.of(10);
      Kind<TrampolineKind.Witness, Function<Integer, String>> func = monad.of(x -> "Value: " + x);

      Kind<TrampolineKind.Witness, String> result = monad.ap(func, value);

      assertThat(TRAMPOLINE.narrow(result).run()).isEqualTo("Value: 10");
    }
  }
}
