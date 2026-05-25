// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.LazyAssert.assertThatLazy;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("LazyFunctor")
class LazyFunctorTest extends LazyTestBase {

  private Monad<LazyKind.Witness> functor;
  private static final AtomicInteger COUNTER = new AtomicInteger(0);

  @BeforeEach
  void setUpFunctor() {
    functor = Instances.monad(lazy());
    COUNTER.set(0);
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("fixtures")
    void identity(String label, Kind<LazyKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("fixtures")
    void composition(String label, Kind<LazyKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of("Lazy.defer(0)", LAZY.widen(Lazy.defer(() -> 0))),
          Arguments.of("Lazy.defer(42)", LAZY.widen(Lazy.defer(() -> 42))),
          Arguments.of("Lazy.defer(-1)", LAZY.widen(Lazy.defer(() -> -1))));
    }
  }

  @Nested
  @DisplayName("Map Operations")
  class MapOperations {

    @Test
    @DisplayName("map should transform value lazily")
    void mapShouldTransformValueLazily() throws Throwable {
      COUNTER.set(0);
      AtomicInteger mapCounter = new AtomicInteger(0);

      Kind<LazyKind.Witness, Integer> kind =
          deferKind(
              () -> {
                COUNTER.incrementAndGet();
                return DEFAULT_LAZY_VALUE;
              });

      Function<Integer, String> mapper =
          i -> {
            mapCounter.incrementAndGet();
            return "Value:" + i;
          };

      Kind<LazyKind.Witness, String> mapped = functor.map(mapper, kind);

      // Neither computation should have run yet
      assertThat(COUNTER.get()).isZero();
      assertThat(mapCounter.get()).isZero();

      // Force the evaluation
      Lazy<String> result = narrowToLazy(mapped);
      assertThatLazy(result).whenForcedHasValue("Value:" + DEFAULT_LAZY_VALUE);

      // Both computations should have run exactly once
      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);

      // Force again - both should be memoised
      assertThatLazy(result).whenForcedHasValue("Value:" + DEFAULT_LAZY_VALUE);
      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map should propagate exceptions from original Lazy")
    void mapShouldPropagateExceptionsFromOriginalLazy() {
      RuntimeException testException = new RuntimeException("Original failure");
      Kind<LazyKind.Witness, Integer> failingKind =
          deferKind(
              () -> {
                throw testException;
              });

      Kind<LazyKind.Witness, String> mapped = functor.map(validMapper, failingKind);
      Lazy<String> result = narrowToLazy(mapped);

      assertThatLazy(result).whenForcedThrows(RuntimeException.class);
    }

    @Test
    @DisplayName("map should propagate exceptions from mapper function")
    void mapShouldPropagateExceptionsFromMapperFunction() {
      RuntimeException mapperException = new RuntimeException("Mapper failure");
      Kind<LazyKind.Witness, Integer> kind = nowKind(DEFAULT_LAZY_VALUE);

      Function<Integer, String> throwingMapper =
          i -> {
            throw mapperException;
          };

      Kind<LazyKind.Witness, String> mapped = functor.map(throwingMapper, kind);
      Lazy<String> result = narrowToLazy(mapped);

      assertThatLazy(result).whenForcedThrows(RuntimeException.class);
    }

    @Test
    @DisplayName("map should handle null results correctly")
    void mapShouldHandleNullResultsCorrectly() throws Throwable {
      Kind<LazyKind.Witness, Integer> kind = nowKind(DEFAULT_LAZY_VALUE);
      Function<Integer, String> nullMapper = i -> null;

      Kind<LazyKind.Witness, String> mapped = functor.map(nullMapper, kind);
      Lazy<String> result = narrowToLazy(mapped);

      assertThat(result.force()).isNull();
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("map should throw NPE for null mapper")
    void mapShouldThrowNPEForNullMapper() {
      assertThatNullPointerException()
          .isThrownBy(() -> functor.map(null, validKind))
          .withMessageContaining("f for map cannot be null");
    }

    @Test
    @DisplayName("map should throw NPE for null Kind")
    void mapShouldThrowNPEForNullKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> functor.map(validMapper, null))
          .withMessageContaining("Kind for map cannot be null");
    }
  }

  @Nested
  @DisplayName("Memoisation Behaviour")
  class MemoisationBehaviour {

    @Test
    @DisplayName("map should memoise both original and mapped computations")
    void mapShouldMemoriseBothComputations() throws Throwable {
      AtomicInteger sourceCounter = new AtomicInteger(0);
      AtomicInteger mapCounter = new AtomicInteger(0);

      Kind<LazyKind.Witness, Integer> kind =
          deferKind(
              () -> {
                sourceCounter.incrementAndGet();
                return DEFAULT_LAZY_VALUE;
              });

      Function<Integer, String> mapper =
          i -> {
            mapCounter.incrementAndGet();
            return "Value:" + i;
          };

      Kind<LazyKind.Witness, String> mapped = functor.map(mapper, kind);
      Lazy<String> result = narrowToLazy(mapped);

      // Force multiple times
      result.force();
      result.force();
      result.force();

      // Each computation should run exactly once
      assertThat(sourceCounter.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map should memoise exceptions from original Lazy")
    void mapShouldMemoiseExceptionsFromOriginalLazy() {
      AtomicInteger exceptionCounter = new AtomicInteger(0);
      RuntimeException testException = new RuntimeException("Test");

      Kind<LazyKind.Witness, Integer> failingKind =
          deferKind(
              () -> {
                exceptionCounter.incrementAndGet();
                throw testException;
              });

      Kind<LazyKind.Witness, String> mapped = functor.map(validMapper, failingKind);
      Lazy<String> result = narrowToLazy(mapped);

      // Force multiple times
      catchThrowable(result::force);
      catchThrowable(result::force);
      catchThrowable(result::force);

      // Exception should be thrown and memoised
      assertThat(exceptionCounter.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("map should work with already evaluated Lazy")
    void mapShouldWorkWithAlreadyEvaluatedLazy() throws Throwable {
      Kind<LazyKind.Witness, Integer> nowKind = nowKind(DEFAULT_LAZY_VALUE);
      Kind<LazyKind.Witness, String> mapped = functor.map(validMapper, nowKind);

      Lazy<String> result = narrowToLazy(mapped);
      assertThatLazy(result).whenForcedHasValue(String.valueOf(DEFAULT_LAZY_VALUE));
    }

    @Test
    @DisplayName("map should preserve lazy semantics")
    void mapShouldPreserveLazySemantics() {
      AtomicInteger sideEffect = new AtomicInteger(0);

      Kind<LazyKind.Witness, Integer> kind =
          deferKind(
              () -> {
                sideEffect.incrementAndGet();
                return DEFAULT_LAZY_VALUE;
              });

      // Mapping should not trigger evaluation
      functor.map(validMapper, kind);

      assertThat(sideEffect.get()).isZero();
    }

    @Test
    @DisplayName("map should work with nested mappings")
    void mapShouldWorkWithNestedMappings() throws Throwable {
      Kind<LazyKind.Witness, Integer> kind = deferKind(() -> DEFAULT_LAZY_VALUE);

      Kind<LazyKind.Witness, String> mapped1 = functor.map(validMapper, kind);
      Kind<LazyKind.Witness, String> mapped2 = functor.map(s -> s.toUpperCase(), mapped1);
      Kind<LazyKind.Witness, Integer> mapped3 = functor.map(String::length, mapped2);

      Lazy<Integer> result = narrowToLazy(mapped3);
      assertThatLazy(result).whenForcedHasValue(String.valueOf(DEFAULT_LAZY_VALUE).length());
    }
  }
}
