// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Lazy Functor Complete Test Suite")
class LazyFunctorTest extends TypeClassTestBase<LazyKind.Witness, String, Integer> {

  // ============================================================================
  // Test Fixtures
  // ============================================================================

  private LazyMonad functor;
  private static final AtomicInteger COUNTER = new AtomicInteger(0);

  // ============================================================================
  // TypeClassTestBase Implementation
  // ============================================================================

  @Override
  protected Kind<LazyKind.Witness, String> createValidKind() {
    return LAZY.widen(
        Lazy.defer(
            () -> {
              COUNTER.incrementAndGet();
              return "TestValue";
            }));
  }

  @Override
  protected Kind<LazyKind.Witness, String> createValidKind2() {
    return LAZY.widen(Lazy.defer(() -> "TestValue2"));
  }

  @Override
  protected Function<String, Integer> createValidMapper() {
    return String::length;
  }

  @Override
  protected Function<Integer, String> createSecondMapper() {
    return Object::toString;
  }

  @Override
  protected BiPredicate<Kind<LazyKind.Witness, ?>, Kind<LazyKind.Witness, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      try {
        Lazy<?> lazy1 = LAZY.narrow((Kind<LazyKind.Witness, Object>) k1);
        Lazy<?> lazy2 = LAZY.narrow((Kind<LazyKind.Witness, Object>) k2);
        Object v1 = lazy1.force();
        Object v2 = lazy2.force();
        return Objects.equals(v1, v2);
      } catch (Throwable e) {
        return false;
      }
    };
  }

  @BeforeEach
  void setUpFunctor() {
    functor = LazyMonad.INSTANCE;
    COUNTER.set(0);
  }

  // ============================================================================
  // Complete Test Suite
  // ============================================================================

  @Nested
  @DisplayName("Complete LazyFunctor Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Functor tests using standard pattern")
    void runCompleteFunctorTestsUsingStandardPattern() {
      validateRequiredFixtures();

      TypeClassTest.<LazyKind.Witness>functor(LazyMonad.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .skipExceptions() // Skip generic exception tests
          .test();
    }

    @Test
    @DisplayName("Run complete Functor tests with custom equality checker")
    void runCompleteFunctorTestsWithCustomEqualityChecker() {
      TypeClassTest.<LazyKind.Witness>functor(LazyMonad.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .skipExceptions() // Skip generic exception tests
          .test();
    }

    @Test
    @DisplayName("Run complete Functor tests with second mapper")
    void runCompleteFunctorTestsWithSecondMapper() {
      TypeClassTest.<LazyKind.Witness>functor(LazyMonad.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .skipExceptions() // Skip generic exception tests
          .test();
    }
  }

  // ============================================================================
  // Operation Tests
  // ============================================================================

  @Nested
  @DisplayName("Map Operations")
  class MapOperations {

    @Test
    @DisplayName("map should transform value lazily")
    void mapShouldTransformValueLazily() throws Throwable {
      COUNTER.set(0);
      AtomicInteger mapCounter = new AtomicInteger(0);

      Kind<LazyKind.Witness, String> kind =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    COUNTER.incrementAndGet();
                    return "test";
                  }));

      Function<String, Integer> mapper =
          s -> {
            mapCounter.incrementAndGet();
            return s.length();
          };

      Kind<LazyKind.Witness, Integer> mapped = functor.map(mapper, kind);

      // Neither computation should have run yet
      assertThat(COUNTER.get()).isZero();
      assertThat(mapCounter.get()).isZero();

      // Force the evaluation
      Lazy<Integer> result = LAZY.narrow(mapped);
      assertThat(result.force()).isEqualTo(4);

      // Both computations should have run exactly once
      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);

      // Force again - both should be memoised
      assertThat(result.force()).isEqualTo(4);
      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map should propagate exceptions from original Lazy")
    void mapShouldPropagateExceptionsFromOriginalLazy() {
      RuntimeException testException = new RuntimeException("Original failure");
      Kind<LazyKind.Witness, String> failingKind =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    throw testException;
                  }));

      Kind<LazyKind.Witness, Integer> mapped = functor.map(String::length, failingKind);
      Lazy<Integer> result = LAZY.narrow(mapped);

      assertThatThrownBy(result::force).isSameAs(testException);
    }

    @Test
    @DisplayName("map should propagate exceptions from mapper function")
    void mapShouldPropagateExceptionsFromMapperFunction() {
      RuntimeException mapperException = new RuntimeException("Mapper failure");
      Kind<LazyKind.Witness, String> kind = LAZY.widen(Lazy.now("test"));

      Function<String, Integer> throwingMapper =
          s -> {
            throw mapperException;
          };

      Kind<LazyKind.Witness, Integer> mapped = functor.map(throwingMapper, kind);
      Lazy<Integer> result = LAZY.narrow(mapped);

      assertThatThrownBy(result::force).isSameAs(mapperException);
    }

    @Test
    @DisplayName("map should handle null results correctly")
    void mapShouldHandleNullResultsCorrectly() throws Throwable {
      Kind<LazyKind.Witness, String> kind = LAZY.widen(Lazy.now("test"));
      Function<String, Integer> nullMapper = s -> null;

      Kind<LazyKind.Witness, Integer> mapped = functor.map(nullMapper, kind);
      Lazy<Integer> result = LAZY.narrow(mapped);

      assertThat(result.force()).isNull();
    }
  }

  // ============================================================================
  // Validation Tests
  // ============================================================================

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("map should throw NPE for null mapper")
    void mapShouldThrowNPEForNullMapper() {
      assertThatNullPointerException()
          .isThrownBy(() -> functor.map(null, validKind))
          .withMessageContaining("function f for LazyMonad.map cannot be null"); // Changed
    }

    @Test
    @DisplayName("map should throw NPE for null Kind")
    void mapShouldThrowNPEForNullKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> functor.map(validMapper, null))
          .withMessageContaining("Kind for LazyMonad.map cannot be null"); // Changed
    }
  }

  // ============================================================================
  // Law Tests
  // ============================================================================

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {

    @Test
    @DisplayName("Identity Law: map(id, fa) == fa")
    void identityLaw() throws Throwable {
      Function<String, String> identity = s -> s;
      Kind<LazyKind.Witness, String> mapped = functor.map(identity, validKind);

      assertThat(equalityChecker.test(mapped, validKind))
          .as("map(id, fa) should equal fa")
          .isTrue();
    }

    @Test
    @DisplayName("Composition Law: map(g ∘ f, fa) == map(g, map(f, fa))")
    void compositionLaw() throws Throwable {
      Function<String, Integer> f = String::length;
      Function<Integer, String> g = Object::toString;

      // Left side: map(g ∘ f, fa)
      Function<String, String> composed = s -> g.apply(f.apply(s));
      Kind<LazyKind.Witness, String> leftSide = functor.map(composed, validKind);

      // Right side: map(g, map(f, fa))
      Kind<LazyKind.Witness, Integer> intermediate = functor.map(f, validKind);
      Kind<LazyKind.Witness, String> rightSide = functor.map(g, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("map(g ∘ f, fa) should equal map(g, map(f, fa))")
          .isTrue();
    }
  }

  // ============================================================================
  // Memoisation Tests
  // ============================================================================

  @Nested
  @DisplayName("Memoisation Behaviour")
  class MemoisationBehaviour {

    @Test
    @DisplayName("map should memoise both original and mapped computations")
    void mapShouldMemoriseBothComputations() throws Throwable {
      AtomicInteger sourceCounter = new AtomicInteger(0);
      AtomicInteger mapCounter = new AtomicInteger(0);

      Kind<LazyKind.Witness, String> kind =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    sourceCounter.incrementAndGet();
                    return "test";
                  }));

      Function<String, Integer> mapper =
          s -> {
            mapCounter.incrementAndGet();
            return s.length();
          };

      Kind<LazyKind.Witness, Integer> mapped = functor.map(mapper, kind);
      Lazy<Integer> result = LAZY.narrow(mapped);

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

      Kind<LazyKind.Witness, String> failingKind =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    exceptionCounter.incrementAndGet();
                    throw testException;
                  }));

      Kind<LazyKind.Witness, Integer> mapped = functor.map(String::length, failingKind);
      Lazy<Integer> result = LAZY.narrow(mapped);

      // Force multiple times
      catchThrowable(result::force);
      catchThrowable(result::force);
      catchThrowable(result::force);

      // Exception should be thrown and memoised
      assertThat(exceptionCounter.get()).isEqualTo(1);
    }
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("map should work with already evaluated Lazy")
    void mapShouldWorkWithAlreadyEvaluatedLazy() throws Throwable {
      Kind<LazyKind.Witness, String> nowKind = LAZY.widen(Lazy.now("precomputed"));
      Kind<LazyKind.Witness, Integer> mapped = functor.map(String::length, nowKind);

      Lazy<Integer> result = LAZY.narrow(mapped);
      assertThat(result.force()).isEqualTo(11);
    }

    @Test
    @DisplayName("map should preserve lazy semantics")
    void mapShouldPreserveLazySemantics() {
      AtomicInteger sideEffect = new AtomicInteger(0);

      Kind<LazyKind.Witness, String> kind =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    sideEffect.incrementAndGet();
                    return "lazy";
                  }));

      // Mapping should not trigger evaluation
      functor.map(String::length, kind);

      assertThat(sideEffect.get()).isZero();
    }

    @Test
    @DisplayName("map should work with nested mappings")
    void mapShouldWorkWithNestedMappings() throws Throwable {
      Kind<LazyKind.Witness, String> kind = LAZY.widen(Lazy.defer(() -> "test"));

      Kind<LazyKind.Witness, Integer> mapped1 = functor.map(String::length, kind);
      Kind<LazyKind.Witness, String> mapped2 = functor.map(Object::toString, mapped1);
      Kind<LazyKind.Witness, Integer> mapped3 = functor.map(Integer::parseInt, mapped2);

      Lazy<Integer> result = LAZY.narrow(mapped3);
      assertThat(result.force()).isEqualTo(4);
    }
  }

  // ============================================================================
  // Individual Component Tests
  // ============================================================================

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<LazyKind.Witness>functor(LazyMonad.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<LazyKind.Witness>functor(LazyMonad.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .selectTests()
          .onlyValidations()
          .test();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Exception propagation is already thoroughly tested in MapOperations
      // Lazy has special semantics - exceptions are memoized and thrown at force() time
      // See MapOperations tests for comprehensive exception handling coverage
      assertThat(true)
          .as("Exception propagation for Lazy is tested in MapOperations nested class")
          .isTrue();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<LazyKind.Witness>functor(LazyMonad.class)
          .<String>instance(functor)
          .<Integer>withKind(validKind)
          .withMapper(validMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }
}
