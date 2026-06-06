// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.LazyAssert.assertThatLazy;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Lazy Applicative Complete Test Suite")
class LazyApplicativeTest extends LazyTestBase {

  private Monad<LazyKind.Witness> applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = Instances.monad(lazy());
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.lazy.LazyLawFixtures#kinds")
    void identity(String label, Kind<LazyKind.Witness, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.lazy.LazyLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.lazy.LazyLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.lazy.LazyLawFixtures#kinds")
    void composition(String label, Kind<LazyKind.Witness, Integer> w) {
      Kind<LazyKind.Witness, Function<String, String>> u =
          LAZY.widen(Lazy.defer(() -> s -> "u(" + s + ")"));
      Kind<LazyKind.Witness, Function<Integer, String>> v =
          LAZY.widen(Lazy.defer(() -> i -> "v" + i));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }
  }

  // No separate Applicative contract smoke: this instance is the Lazy Monad, so its map/ap/map2
  // null-argument validation is already covered by the contract in LazyMonadTest. (EXCEPTIONS does
  // not apply to Lazy at all — exceptions surface only when a result is forced, which the deferred
  // ap/map2 exception tests below exercise.)

  @Nested
  @DisplayName("Of Operation")
  class OfOperation {

    @Test
    @DisplayName("of should create already evaluated Lazy")
    void ofShouldCreateAlreadyEvaluatedLazy() throws Throwable {
      Kind<LazyKind.Witness, Integer> kind = applicative.of(DEFAULT_LAZY_VALUE);
      Lazy<Integer> lazy = narrowToLazy(kind);

      assertThatLazy(lazy).whenForcedHasValue(DEFAULT_LAZY_VALUE);
      assertThatLazy(lazy).isEvaluated();
    }

    @Test
    @DisplayName("of should handle null values")
    void ofShouldHandleNullValues() throws Throwable {
      Kind<LazyKind.Witness, Integer> kind = applicative.of(null);
      Lazy<Integer> lazy = narrowToLazy(kind);

      assertThat(lazy.force()).isNull();
    }
  }

  @Nested
  @DisplayName("Ap Operation")
  class ApOperation {

    @Test
    @DisplayName("ap should apply function lazily")
    void apShouldApplyFunctionLazily() throws Throwable {
      AtomicInteger funcCounter = new AtomicInteger(0);
      AtomicInteger valueCounter = new AtomicInteger(0);

      Kind<LazyKind.Witness, Function<Integer, String>> funcKind =
          deferKind(
              () -> {
                funcCounter.incrementAndGet();
                return validMapper;
              });

      Kind<LazyKind.Witness, Integer> valueKind =
          deferKind(
              () -> {
                valueCounter.incrementAndGet();
                return DEFAULT_LAZY_VALUE;
              });

      Kind<LazyKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      // Nothing should be evaluated yet
      assertThat(funcCounter.get()).isZero();
      assertThat(valueCounter.get()).isZero();

      // Force evaluation
      Lazy<String> lazy = narrowToLazy(result);
      assertThatLazy(lazy).whenForcedHasValue(String.valueOf(DEFAULT_LAZY_VALUE));

      // Both should have been evaluated
      assertThat(funcCounter.get()).isEqualTo(1);
      assertThat(valueCounter.get()).isEqualTo(1);

      // Force again - both should be memoised
      assertThatLazy(lazy).whenForcedHasValue(String.valueOf(DEFAULT_LAZY_VALUE));
      assertThat(funcCounter.get()).isEqualTo(1);
      assertThat(valueCounter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("ap should propagate exceptions from function Lazy")
    void apShouldPropagateExceptionsFromFunctionLazy() {
      RuntimeException funcException = new RuntimeException("Function failure");
      Kind<LazyKind.Witness, Function<Integer, String>> funcKind =
          deferKind(
              () -> {
                throw funcException;
              });

      Kind<LazyKind.Witness, Integer> valueKind = nowKind(DEFAULT_LAZY_VALUE);
      Kind<LazyKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      Lazy<String> lazy = narrowToLazy(result);
      assertThatLazy(lazy).whenForcedThrows(RuntimeException.class);
    }

    @Test
    @DisplayName("ap should propagate exceptions from value Lazy")
    void apShouldPropagateExceptionsFromValueLazy() {
      RuntimeException valueException = new RuntimeException("Value failure");
      Kind<LazyKind.Witness, Function<Integer, String>> funcKind = nowKind(validMapper);

      Kind<LazyKind.Witness, Integer> valueKind =
          deferKind(
              () -> {
                throw valueException;
              });

      Kind<LazyKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      Lazy<String> lazy = narrowToLazy(result);
      assertThatLazy(lazy).whenForcedThrows(RuntimeException.class);
    }

    @Test
    @DisplayName("ap should propagate exceptions from function application")
    void apShouldPropagateExceptionsFromFunctionApplication() {
      RuntimeException applyException = new RuntimeException("Apply failure");
      Function<Integer, String> throwingFunc =
          _ -> {
            throw applyException;
          };

      Kind<LazyKind.Witness, Function<Integer, String>> funcKind = nowKind(throwingFunc);
      Kind<LazyKind.Witness, Integer> valueKind = nowKind(DEFAULT_LAZY_VALUE);

      Kind<LazyKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      Lazy<String> lazy = narrowToLazy(result);
      assertThatLazy(lazy).whenForcedThrows(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Map2 Operation")
  class Map2Operation {

    @Test
    @DisplayName("map2 should combine values lazily")
    void map2ShouldCombineValuesLazily() throws Throwable {
      AtomicInteger counter1 = new AtomicInteger(0);
      AtomicInteger counter2 = new AtomicInteger(0);

      Kind<LazyKind.Witness, Integer> kind1 =
          deferKind(
              () -> {
                counter1.incrementAndGet();
                return DEFAULT_LAZY_VALUE;
              });

      Kind<LazyKind.Witness, Integer> kind2 =
          deferKind(
              () -> {
                counter2.incrementAndGet();
                return ALTERNATIVE_LAZY_VALUE;
              });

      BiFunction<Integer, Integer, String> combiner = (i1, i2) -> i1 + "," + i2;
      Kind<LazyKind.Witness, String> result = applicative.map2(kind1, kind2, combiner);

      // Nothing evaluated yet
      assertThat(counter1.get()).isZero();
      assertThat(counter2.get()).isZero();

      // Force evaluation
      Lazy<String> lazy = narrowToLazy(result);
      assertThatLazy(lazy).whenForcedHasValue(DEFAULT_LAZY_VALUE + "," + ALTERNATIVE_LAZY_VALUE);

      // Both evaluated
      assertThat(counter1.get()).isEqualTo(1);
      assertThat(counter2.get()).isEqualTo(1);

      // Force again - both memoised
      assertThatLazy(lazy).whenForcedHasValue(DEFAULT_LAZY_VALUE + "," + ALTERNATIVE_LAZY_VALUE);
      assertThat(counter1.get()).isEqualTo(1);
      assertThat(counter2.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map2 should propagate exceptions from first Lazy")
    void map2ShouldPropagateExceptionsFromFirstLazy() {
      RuntimeException exception1 = new RuntimeException("First failure");
      Kind<LazyKind.Witness, Integer> kind1 =
          deferKind(
              () -> {
                throw exception1;
              });

      Kind<LazyKind.Witness, Integer> kind2 = nowKind(ALTERNATIVE_LAZY_VALUE);
      BiFunction<Integer, Integer, String> combiner = (i1, i2) -> i1 + "," + i2;

      Kind<LazyKind.Witness, String> result = applicative.map2(kind1, kind2, combiner);
      Lazy<String> lazy = narrowToLazy(result);

      assertThatLazy(lazy).whenForcedThrows(RuntimeException.class);
    }

    @Test
    @DisplayName("map2 should propagate exceptions from second Lazy")
    void map2ShouldPropagateExceptionsFromSecondLazy() {
      RuntimeException exception2 = new RuntimeException("Second failure");
      Kind<LazyKind.Witness, Integer> kind1 = nowKind(DEFAULT_LAZY_VALUE);
      Kind<LazyKind.Witness, Integer> kind2 =
          deferKind(
              () -> {
                throw exception2;
              });

      BiFunction<Integer, Integer, String> combiner = (i1, i2) -> i1 + "," + i2;

      Kind<LazyKind.Witness, String> result = applicative.map2(kind1, kind2, combiner);
      Lazy<String> lazy = narrowToLazy(result);

      assertThatLazy(lazy).whenForcedThrows(RuntimeException.class);
    }

    @Test
    @DisplayName("map2 should propagate exceptions from combining function")
    void map2ShouldPropagateExceptionsFromCombiningFunction() {
      RuntimeException combinerException = new RuntimeException("Combiner failure");
      Kind<LazyKind.Witness, Integer> kind1 = nowKind(DEFAULT_LAZY_VALUE);
      Kind<LazyKind.Witness, Integer> kind2 = nowKind(ALTERNATIVE_LAZY_VALUE);

      BiFunction<Integer, Integer, String> throwingCombiner =
          (_, _) -> {
            throw combinerException;
          };

      Kind<LazyKind.Witness, String> result = applicative.map2(kind1, kind2, throwingCombiner);
      Lazy<String> lazy = narrowToLazy(result);

      assertThatLazy(lazy).whenForcedThrows(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("ap should work with already evaluated Lazies")
    void apShouldWorkWithAlreadyEvaluatedLazies() throws Throwable {
      Kind<LazyKind.Witness, Function<Integer, String>> funcKind = nowKind(validMapper);
      Kind<LazyKind.Witness, Integer> valueKind = nowKind(DEFAULT_LAZY_VALUE);

      Kind<LazyKind.Witness, String> result = applicative.ap(funcKind, valueKind);
      Lazy<String> lazy = narrowToLazy(result);

      assertThatLazy(lazy).whenForcedHasValue(String.valueOf(DEFAULT_LAZY_VALUE));
    }

    @Test
    @DisplayName("map2 should preserve lazy semantics")
    void map2ShouldPreserveLazySemantics() {
      AtomicInteger sideEffect = new AtomicInteger(0);

      Kind<LazyKind.Witness, Integer> kind1 =
          deferKind(
              () -> {
                sideEffect.incrementAndGet();
                return DEFAULT_LAZY_VALUE;
              });

      Kind<LazyKind.Witness, Integer> kind2 =
          deferKind(
              () -> {
                sideEffect.incrementAndGet();
                return ALTERNATIVE_LAZY_VALUE;
              });

      // Creating map2 should not trigger evaluation
      applicative.map2(kind1, kind2, (i1, i2) -> i1 + "," + i2);

      assertThat(sideEffect.get()).isZero();
    }
  }
}
