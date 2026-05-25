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
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Lazy Applicative Complete Test Suite")
class LazyApplicativeTest extends LazyTestBase {

  private Monad<LazyKind.Witness> applicative;
  private static final AtomicInteger COUNTER = new AtomicInteger(0);

  @BeforeEach
  void setUpApplicative() {
    applicative = Instances.monad(lazy());
    COUNTER.set(0);
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("fixtures")
    void identity(String label, Kind<LazyKind.Witness, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("fixtures")
    void composition(String label, Kind<LazyKind.Witness, Integer> w) {
      Kind<LazyKind.Witness, Function<String, String>> u =
          LAZY.widen(Lazy.defer(() -> (Function<String, String>) s -> "u(" + s + ")"));
      Kind<LazyKind.Witness, Function<Integer, String>> v =
          LAZY.widen(Lazy.defer(() -> (Function<Integer, String>) i -> "v" + i));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of("Lazy.defer(0)", LAZY.widen(Lazy.defer(() -> 0))),
          Arguments.of("Lazy.defer(42)", LAZY.widen(Lazy.defer(() -> 42))),
          Arguments.of("Lazy.defer(-1)", LAZY.widen(Lazy.defer(() -> -1))));
    }

    static Stream<Arguments> values() {
      return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
    }
  }

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

    @Test
    @DisplayName("of should not trigger side effects")
    void ofShouldNotTriggerSideEffects() {
      AtomicInteger sideEffect = new AtomicInteger(0);
      Kind<LazyKind.Witness, Integer> kind = applicative.of(DEFAULT_LAZY_VALUE);

      // Creating the Kind should not cause evaluation
      assertThat(sideEffect.get()).isZero();
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
          i -> {
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
          (i1, i2) -> {
            throw combinerException;
          };

      Kind<LazyKind.Witness, String> result = applicative.map2(kind1, kind2, throwingCombiner);
      Lazy<String> lazy = narrowToLazy(result);

      assertThatLazy(lazy).whenForcedThrows(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("ap should throw NPE for null function Kind")
    void apShouldThrowNPEForNullFunctionKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.ap(null, validKind))
          .withMessageContaining("Kind for ap")
          .withMessageContaining("function");
    }

    @Test
    @DisplayName("ap should throw NPE for null argument Kind")
    void apShouldThrowNPEForNullArgumentKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.ap(validFunctionKind, null))
          .withMessageContaining("Kind for ap (argument) cannot be null");
    }

    @Test
    @DisplayName("map2 should throw NPE for null first Kind")
    void map2ShouldThrowNPEForNullFirstKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.map2(null, validKind2, validCombiningFunction))
          .withMessageContaining("Kind<F, A> fa for map2 cannot be null");
    }

    @Test
    @DisplayName("map2 should throw NPE for null second Kind")
    void map2ShouldThrowNPEForNullSecondKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.map2(validKind, null, validCombiningFunction))
          .withMessageContaining("Kind<F, B> fb for map2 cannot be null");
    }

    @Test
    @DisplayName("map2 should throw NPE for null combining function")
    void map2ShouldThrowNPEForNullCombiningFunction() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  applicative.map2(
                      validKind, validKind2, (BiFunction<Integer, Integer, String>) null))
          .withMessageContaining("combining function")
          .withMessageContaining("map2");
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
