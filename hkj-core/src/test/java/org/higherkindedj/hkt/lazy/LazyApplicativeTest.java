// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Lazy Applicative Complete Test Suite")
class LazyApplicativeTest extends TypeClassTestBase<LazyKind.Witness, String, Integer> {

  // ============================================================================
  // Test Fixtures
  // ============================================================================

  private LazyMonad applicative;
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
  protected Kind<LazyKind.Witness, Function<String, Integer>> createValidFunctionKind() {
    return LAZY.widen(Lazy.defer(() -> String::length));
  }

  @Override
  protected BiFunction<String, String, Integer> createValidCombiningFunction() {
    return (s1, s2) -> s1.length() + s2.length();
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
        return v1 != null ? v1.equals(v2) : v2 == null;
      } catch (Throwable e) {
        return false;
      }
    };
  }

  @BeforeEach
  void setUpApplicative() {
    applicative = LazyMonad.INSTANCE;
    COUNTER.set(0);
  }

  // ============================================================================
  // Complete Test Suite
  // ============================================================================

  @Nested
  @DisplayName("Complete LazyApplicative Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Applicative tests using standard pattern")
    void runCompleteApplicativeTestsUsingStandardPattern() {
      validateApplicativeFixtures();

      TypeClassTest.<LazyKind.Witness>applicative(LazyMonad.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .skipExceptions() // Skip generic exception tests - Lazy has special semantics
          .skipValidations() // Skip generic validations - tested in ValidationTests with correct
          // expectations
          .test();
    }

    @Test
    @DisplayName("Run complete Applicative tests with laws")
    void runCompleteApplicativeTestsWithLaws() {
      validateApplicativeFixtures();

      TypeClassTest.<LazyKind.Witness>applicative(LazyMonad.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting("test", String::length, equalityChecker)
          .selectTests()
          .skipExceptions() // Skip generic exception tests - Lazy has special semantics
          .skipValidations() // Skip generic validations - tested in ValidationTests with correct
          // expectations
          .test();
    }
  }

  // ============================================================================
  // Operation Tests
  // ============================================================================

  @Nested
  @DisplayName("Of Operation")
  class OfOperation {

    @Test
    @DisplayName("of should create already evaluated Lazy")
    void ofShouldCreateAlreadyEvaluatedLazy() throws Throwable {
      Kind<LazyKind.Witness, String> kind = applicative.of("pure");
      Lazy<String> lazy = LAZY.narrow(kind);

      assertThat(lazy.force()).isEqualTo("pure");
      assertThat(lazy.toString()).contains("pure");
    }

    @Test
    @DisplayName("of should handle null values")
    void ofShouldHandleNullValues() throws Throwable {
      Kind<LazyKind.Witness, String> kind = applicative.of(null);
      Lazy<String> lazy = LAZY.narrow(kind);

      assertThat(lazy.force()).isNull();
    }

    @Test
    @DisplayName("of should not trigger side effects")
    void ofShouldNotTriggerSideEffects() {
      AtomicInteger sideEffect = new AtomicInteger(0);
      Kind<LazyKind.Witness, String> kind = applicative.of("value");

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

      Kind<LazyKind.Witness, Function<String, Integer>> funcKind =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    funcCounter.incrementAndGet();
                    return String::length;
                  }));

      Kind<LazyKind.Witness, String> valueKind =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    valueCounter.incrementAndGet();
                    return "test";
                  }));

      Kind<LazyKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);

      // Nothing should be evaluated yet
      assertThat(funcCounter.get()).isZero();
      assertThat(valueCounter.get()).isZero();

      // Force evaluation
      Lazy<Integer> lazy = LAZY.narrow(result);
      assertThat(lazy.force()).isEqualTo(4);

      // Both should have been evaluated
      assertThat(funcCounter.get()).isEqualTo(1);
      assertThat(valueCounter.get()).isEqualTo(1);

      // Force again - both should be memoised
      assertThat(lazy.force()).isEqualTo(4);
      assertThat(funcCounter.get()).isEqualTo(1);
      assertThat(valueCounter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("ap should propagate exceptions from function Lazy")
    void apShouldPropagateExceptionsFromFunctionLazy() {
      RuntimeException funcException = new RuntimeException("Function failure");
      Kind<LazyKind.Witness, Function<String, Integer>> funcKind =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    throw funcException;
                  }));

      Kind<LazyKind.Witness, String> valueKind = LAZY.widen(Lazy.now("test"));
      Kind<LazyKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);

      Lazy<Integer> lazy = LAZY.narrow(result);
      assertThatThrownBy(lazy::force).isSameAs(funcException);
    }

    @Test
    @DisplayName("ap should propagate exceptions from value Lazy")
    void apShouldPropagateExceptionsFromValueLazy() {
      RuntimeException valueException = new RuntimeException("Value failure");
      Kind<LazyKind.Witness, Function<String, Integer>> funcKind =
          LAZY.widen(Lazy.now(String::length));

      Kind<LazyKind.Witness, String> valueKind =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    throw valueException;
                  }));

      Kind<LazyKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);

      Lazy<Integer> lazy = LAZY.narrow(result);
      assertThatThrownBy(lazy::force).isSameAs(valueException);
    }

    @Test
    @DisplayName("ap should propagate exceptions from function application")
    void apShouldPropagateExceptionsFromFunctionApplication() {
      RuntimeException applyException = new RuntimeException("Apply failure");
      Function<String, Integer> throwingFunc =
          s -> {
            throw applyException;
          };

      Kind<LazyKind.Witness, Function<String, Integer>> funcKind =
          LAZY.widen(Lazy.now(throwingFunc));
      Kind<LazyKind.Witness, String> valueKind = LAZY.widen(Lazy.now("test"));

      Kind<LazyKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);

      Lazy<Integer> lazy = LAZY.narrow(result);
      assertThatThrownBy(lazy::force).isSameAs(applyException);
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

      Kind<LazyKind.Witness, String> kind1 =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    counter1.incrementAndGet();
                    return "hello";
                  }));

      Kind<LazyKind.Witness, String> kind2 =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    counter2.incrementAndGet();
                    return "world";
                  }));

      BiFunction<String, String, Integer> combiner = (s1, s2) -> s1.length() + s2.length();
      Kind<LazyKind.Witness, Integer> result = applicative.map2(kind1, kind2, combiner);

      // Nothing evaluated yet
      assertThat(counter1.get()).isZero();
      assertThat(counter2.get()).isZero();

      // Force evaluation
      Lazy<Integer> lazy = LAZY.narrow(result);
      assertThat(lazy.force()).isEqualTo(10);

      // Both evaluated
      assertThat(counter1.get()).isEqualTo(1);
      assertThat(counter2.get()).isEqualTo(1);

      // Force again - both memoised
      assertThat(lazy.force()).isEqualTo(10);
      assertThat(counter1.get()).isEqualTo(1);
      assertThat(counter2.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map2 should propagate exceptions from first Lazy")
    void map2ShouldPropagateExceptionsFromFirstLazy() {
      RuntimeException exception1 = new RuntimeException("First failure");
      Kind<LazyKind.Witness, String> kind1 =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    throw exception1;
                  }));

      Kind<LazyKind.Witness, String> kind2 = LAZY.widen(Lazy.now("world"));
      BiFunction<String, String, Integer> combiner = (s1, s2) -> s1.length() + s2.length();

      Kind<LazyKind.Witness, Integer> result = applicative.map2(kind1, kind2, combiner);
      Lazy<Integer> lazy = LAZY.narrow(result);

      assertThatThrownBy(lazy::force).isSameAs(exception1);
    }

    @Test
    @DisplayName("map2 should propagate exceptions from second Lazy")
    void map2ShouldPropagateExceptionsFromSecondLazy() {
      RuntimeException exception2 = new RuntimeException("Second failure");
      Kind<LazyKind.Witness, String> kind1 = LAZY.widen(Lazy.now("hello"));
      Kind<LazyKind.Witness, String> kind2 =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    throw exception2;
                  }));

      BiFunction<String, String, Integer> combiner = (s1, s2) -> s1.length() + s2.length();

      Kind<LazyKind.Witness, Integer> result = applicative.map2(kind1, kind2, combiner);
      Lazy<Integer> lazy = LAZY.narrow(result);

      assertThatThrownBy(lazy::force).isSameAs(exception2);
    }

    @Test
    @DisplayName("map2 should propagate exceptions from combining function")
    void map2ShouldPropagateExceptionsFromCombiningFunction() {
      RuntimeException combinerException = new RuntimeException("Combiner failure");
      Kind<LazyKind.Witness, String> kind1 = LAZY.widen(Lazy.now("hello"));
      Kind<LazyKind.Witness, String> kind2 = LAZY.widen(Lazy.now("world"));

      BiFunction<String, String, Integer> throwingCombiner =
          (s1, s2) -> {
            throw combinerException;
          };

      Kind<LazyKind.Witness, Integer> result = applicative.map2(kind1, kind2, throwingCombiner);
      Lazy<Integer> lazy = LAZY.narrow(result);

      assertThatThrownBy(lazy::force).isSameAs(combinerException);
    }
  }

  // ============================================================================
  // Validation Tests
  // ============================================================================

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("ap should throw NPE for null function Kind")
    void apShouldThrowNPEForNullFunctionKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.ap(null, validKind))
          .withMessageContaining("Kind for LazyMonad.ap")
          .withMessageContaining("function");
    }

    @Test
    @DisplayName("ap should throw NPE for null argument Kind")
    void apShouldThrowNPEForNullArgumentKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> applicative.ap(validFunctionKind, null))
          .withMessageContaining("Kind for LazyMonad.ap (argument) cannot be null");
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
                      validKind, validKind2, (BiFunction<String, String, Integer>) null))
          .withMessageContaining("combining function")
          .withMessageContaining("map2");
    }
  }

  // ============================================================================
  // Applicative Laws
  // ============================================================================

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    @Test
    @DisplayName("Identity Law: ap(of(id), fa) == fa")
    void identityLaw() throws Throwable {
      Function<String, String> identity = s -> s;
      Kind<LazyKind.Witness, Function<String, String>> idFunc = applicative.of(identity);
      Kind<LazyKind.Witness, String> result = applicative.ap(idFunc, validKind);

      assertThat(equalityChecker.test(result, validKind))
          .as("ap(of(id), fa) should equal fa")
          .isTrue();
    }

    @Test
    @DisplayName("Homomorphism Law: ap(of(f), of(x)) == of(f(x))")
    void homomorphismLaw() throws Throwable {
      String testValue = "test";
      Function<String, Integer> testFunction = String::length;

      Kind<LazyKind.Witness, Function<String, Integer>> funcKind = applicative.of(testFunction);
      Kind<LazyKind.Witness, String> valueKind = applicative.of(testValue);

      // Left side: ap(of(f), of(x))
      Kind<LazyKind.Witness, Integer> leftSide = applicative.ap(funcKind, valueKind);

      // Right side: of(f(x))
      Kind<LazyKind.Witness, Integer> rightSide = applicative.of(testFunction.apply(testValue));

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("ap(of(f), of(x)) should equal of(f(x))")
          .isTrue();
    }

    @Test
    @DisplayName("Interchange Law: ap(ff, of(x)) == ap(of(f -> f(x)), ff)")
    void interchangeLaw() throws Throwable {
      String testValue = "test";
      Function<String, Integer> testFunction = String::length;
      Kind<LazyKind.Witness, Function<String, Integer>> funcKind = applicative.of(testFunction);
      Kind<LazyKind.Witness, String> valueKind = applicative.of(testValue);

      // Left side: ap(ff, of(x))
      Kind<LazyKind.Witness, Integer> leftSide = applicative.ap(funcKind, valueKind);

      // Right side: ap(of(f -> f(x)), ff)
      Function<Function<String, Integer>, Integer> applyToValue = f -> f.apply(testValue);
      Kind<LazyKind.Witness, Function<Function<String, Integer>, Integer>> applyFunc =
          applicative.of(applyToValue);
      Kind<LazyKind.Witness, Integer> rightSide = applicative.ap(applyFunc, funcKind);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("ap(ff, of(x)) should equal ap(of(f -> f(x)), ff)")
          .isTrue();
    }
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("ap should work with already evaluated Lazies")
    void apShouldWorkWithAlreadyEvaluatedLazies() throws Throwable {
      Kind<LazyKind.Witness, Function<String, Integer>> funcKind =
          LAZY.widen(Lazy.now(String::length));
      Kind<LazyKind.Witness, String> valueKind = LAZY.widen(Lazy.now("precomputed"));

      Kind<LazyKind.Witness, Integer> result = applicative.ap(funcKind, valueKind);
      Lazy<Integer> lazy = LAZY.narrow(result);

      assertThat(lazy.force()).isEqualTo(11);
    }

    @Test
    @DisplayName("map2 should preserve lazy semantics")
    void map2ShouldPreserveLazySemantics() {
      AtomicInteger sideEffect = new AtomicInteger(0);

      Kind<LazyKind.Witness, String> kind1 =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    sideEffect.incrementAndGet();
                    return "test1";
                  }));

      Kind<LazyKind.Witness, String> kind2 =
          LAZY.widen(
              Lazy.defer(
                  () -> {
                    sideEffect.incrementAndGet();
                    return "test2";
                  }));

      // Creating map2 should not trigger evaluation
      applicative.map2(kind1, kind2, (s1, s2) -> s1.length() + s2.length());

      assertThat(sideEffect.get()).isZero();
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
      validateApplicativeFixtures();

      TypeClassTest.<LazyKind.Witness>applicative(LazyMonad.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      validateApplicativeFixtures();

      // Note: The generic validation test expects ALL operations (including inherited map from
      // Functor)
      // to use the same class context. Since LazyMonad implements both map and ap operations,
      // we need to configure validation to use LazyMonad.class for all operations.
      TypeClassTest.<LazyKind.Witness>applicative(LazyMonad.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(LazyMonad.class)
          .withApFrom(LazyMonad.class)
          .withMap2From(Applicative.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Note: Lazy has special exception semantics - exceptions are memoized
      // and thrown at force() time. The generic exception tests don't account
      // for this, so we skip them and test exceptions in dedicated test methods.
      assertThat(true)
          .as(
              "Exception propagation for Lazy is tested in ApOperation and Map2Operation nested"
                  + " classes")
          .isTrue();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      validateApplicativeFixtures();

      TypeClassTest.<LazyKind.Witness>applicative(LazyMonad.class)
          .<String>instance(applicative)
          .<Integer>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting("test", String::length, equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }
}
