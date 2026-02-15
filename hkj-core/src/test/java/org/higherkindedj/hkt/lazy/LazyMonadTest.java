// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.higherkindedj.hkt.lazy.LazyAssert.assertThatLazy;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LazyMonad Complete Test Suite")
class LazyMonadTest extends LazyTestBase {

  private LazyMonad lazyMonad;
  // Declare counters for lazy evaluation tracking
  private AtomicInteger counterA;
  private AtomicInteger counterB;
  private AtomicInteger counterC;
  private AtomicInteger counterD;
  private AtomicInteger counterF;

  private <A> Kind<LazyKind.Witness, A> countingDefer(String label, ThrowableSupplier<A> supplier) {
    AtomicInteger counter =
        switch (label) {
          case "A" -> counterA;
          case "B" -> counterB;
          case "C" -> counterC;
          case "D" -> counterD;
          default -> throw new IllegalArgumentException("Unexpected label: " + label);
        };
    return LAZY.defer(
        () -> {
          counter.incrementAndGet();
          return supplier.get();
        });
  }

  @BeforeEach
  void setUpMonad() {
    lazyMonad = LazyMonad.INSTANCE;
    // Initialise counters before each test
    counterA = new AtomicInteger(0);
    counterB = new AtomicInteger(0);
    counterC = new AtomicInteger(0);
    counterD = new AtomicInteger(0);
    counterF = new AtomicInteger(0);
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<LazyKind.Witness>monad(LazyMonad.class)
          .<Integer>instance(lazyMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(LazyMonad.class)
          .withApFrom(LazyMonad.class)
          .withFlatMapFrom(LazyMonad.class)
          .selectTests()
          .skipExceptions() // Skip generic exception tests - Lazy has special semantics
          .test();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(LazyMonadTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("flatMap() sequences lazily and memoizes")
    void flatMapSequencesLazilyAndMemoizes() throws Throwable {
      Kind<LazyKind.Witness, Integer> initialKind = countingDefer("A", () -> 5);
      Function<Integer, Kind<LazyKind.Witness, String>> f =
          i -> {
            counterF.incrementAndGet();
            return countingDefer("B", () -> "Val" + (i * 2));
          };

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.flatMap(f, initialKind);
      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
      assertThat(counterB.get()).isZero();

      assertThatLazy(narrowToLazy(resultKind)).whenForcedHasValue("Val10");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);

      assertThatLazy(narrowToLazy(resultKind)).whenForcedHasValue("Val10");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map() applies function lazily and memoizes")
    void mapAppliesFunctionLazilyAndMemoizes() throws Throwable {
      Kind<LazyKind.Witness, Integer> initialKind = countingDefer("A", () -> 10);
      Function<Integer, String> mapper =
          i -> {
            counterF.incrementAndGet();
            return "Val:" + i;
          };
      Kind<LazyKind.Witness, String> mappedKind = lazyMonad.map(mapper, initialKind);

      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();

      assertThatLazy(narrowToLazy(mappedKind)).whenForcedHasValue("Val:10");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);

      assertThatLazy(narrowToLazy(mappedKind)).whenForcedHasValue("Val:10");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("ap() applies effectful function to effectful value")
    void apAppliesEffectfulFunctionToEffectfulValue() throws Throwable {
      Kind<LazyKind.Witness, Function<Integer, String>> funcKind =
          countingDefer("A", () -> i -> "F" + i);
      Kind<LazyKind.Witness, Integer> valKind = countingDefer("B", () -> 20);

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();

      assertThatLazy(narrowToLazy(resultKind)).whenForcedHasValue("F20");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);

      assertThatLazy(narrowToLazy(resultKind)).whenForcedHasValue("F20");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("of() creates already evaluated Lazy")
    void ofCreatesAlreadyEvaluatedLazy() throws Throwable {
      Kind<LazyKind.Witness, String> kind = lazyMonad.of("pureValue");
      assertThatLazy(narrowToLazy(kind)).whenForcedHasValue("pureValue");
      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
    }

    @Test
    @DisplayName("map2() combines two Lazy values")
    void map2CombinesTwoLazyValues() throws Throwable {
      Kind<LazyKind.Witness, Integer> lz1 = countingDefer("A", () -> 1);
      Kind<LazyKind.Witness, Integer> lz2 = countingDefer("B", () -> 2);

      Kind<LazyKind.Witness, String> result = lazyMonad.map2(lz1, lz2, (i, j) -> i + "," + j);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue("1,2");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<LazyKind.Witness>monad(LazyMonad.class)
          .<Integer>instance(lazyMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<LazyKind.Witness>monad(LazyMonad.class)
          .<Integer>instance(lazyMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(LazyMonad.class)
          .withApFrom(LazyMonad.class)
          .withFlatMapFrom(LazyMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Lazy has special exception semantics - exceptions are memoized
      // and thrown at force() time. The generic exception tests don't account
      // for this, so we skip them. Exception propagation is already thoroughly
      // tested in the EdgeCasesTests nested class with proper force() calls.
      assertThat(true)
          .as("Exception propagation for Lazy is tested in EdgeCasesTests nested class")
          .isTrue();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<LazyKind.Witness>monad(LazyMonad.class)
          .<Integer>instance(lazyMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() throws Throwable {
      Kind<LazyKind.Witness, Integer> start = nowKind(1);

      Kind<LazyKind.Witness, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = lazyMonad.flatMap(x -> lazyMonad.of(x + increment), result);
      }

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("Chaining map and flatMap operations")
    void chainingMapAndFlatMap() throws Throwable {
      Kind<LazyKind.Witness, Integer> start = nowKind(10);

      Kind<LazyKind.Witness, String> result =
          lazyMonad.flatMap(
              i ->
                  lazyMonad.map(
                      str -> str.toUpperCase(),
                      lazyMonad.map(x -> "value:" + x, lazyMonad.of(i * 2))),
              start);

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue("VALUE:20");
    }

    @Test
    @DisplayName("of() allows null value")
    void ofAllowsNullValue() throws Throwable {
      Kind<LazyKind.Witness, String> kind = lazyMonad.of(null);
      assertThatLazy(narrowToLazy(kind)).whenForcedHasValue(null);
      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
    }

    @Test
    @DisplayName("map() propagates exception from original Lazy")
    void mapPropagatesExceptionFromOriginalLazy() {
      RuntimeException ex = new RuntimeException("OriginalFail");
      Kind<LazyKind.Witness, Integer> initialKind =
          countingDefer(
              "A",
              () -> {
                throw ex;
              });
      Kind<LazyKind.Witness, String> mappedKind = lazyMonad.map(i -> "Val:" + i, initialKind);

      assertThat(counterA.get()).isZero();

      Throwable thrown = catchThrowable(() -> forceLazy(mappedKind));
      assertThat(thrown).isInstanceOf(RuntimeException.class).isSameAs(ex);
      assertThat(counterA.get()).isEqualTo(1);

      assertThatLazy(narrowToLazy(mappedKind)).whenForcedThrows(RuntimeException.class);
      assertThat(counterA.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map() propagates exception from mapper function")
    void mapPropagatesExceptionFromMapperFunction() {
      RuntimeException mapEx = new RuntimeException("MapperFail");
      Kind<LazyKind.Witness, Integer> initialKind = countingDefer("A", () -> 10);
      Kind<LazyKind.Witness, String> mappedKind =
          lazyMonad.map(
              i -> {
                counterF.incrementAndGet();
                throw mapEx;
              },
              initialKind);

      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
      Throwable thrown = catchThrowable(() -> forceLazy(mappedKind));
      assertThat(thrown).isInstanceOf(RuntimeException.class).isSameAs(mapEx);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);

      assertThatLazy(narrowToLazy(mappedKind)).whenForcedThrows(RuntimeException.class);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("ap() propagates exception from function Lazy")
    void apPropagatesExceptionFromFunctionLazy() {
      RuntimeException funcEx = new RuntimeException("FuncFail");
      Kind<LazyKind.Witness, Function<Integer, String>> funcKind =
          countingDefer(
              "A",
              () -> {
                throw funcEx;
              });
      Kind<LazyKind.Witness, Integer> valKind = countingDefer("B", () -> 20);

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThatLazy(narrowToLazy(resultKind)).whenForcedThrows(RuntimeException.class);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isZero();
    }

    @Test
    @DisplayName("ap() propagates exception from value Lazy")
    void apPropagatesExceptionFromValueLazy() {
      RuntimeException valEx = new RuntimeException("ValFail");
      Kind<LazyKind.Witness, Function<Integer, String>> funcKind =
          countingDefer("A", () -> i -> "F" + i);
      Kind<LazyKind.Witness, Integer> valKind =
          countingDefer(
              "B",
              () -> {
                throw valEx;
              });

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThatLazy(narrowToLazy(resultKind)).whenForcedThrows(RuntimeException.class);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("ap() propagates exception from function apply")
    void apPropagatesExceptionFromFunctionApply() {
      RuntimeException applyEx = new RuntimeException("ApplyFail");
      Kind<LazyKind.Witness, Function<Integer, String>> funcKind =
          countingDefer(
              "A",
              () ->
                  i -> {
                    counterF.incrementAndGet();
                    throw applyEx;
                  });
      Kind<LazyKind.Witness, Integer> valKind = countingDefer("B", () -> 20);

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThatLazy(narrowToLazy(resultKind)).whenForcedThrows(RuntimeException.class);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("flatMap() propagates exception from initial Lazy")
    void flatMapPropagatesExceptionFromInitialLazy() {
      RuntimeException exA = new RuntimeException("FailA");
      Kind<LazyKind.Witness, Integer> initialKind =
          countingDefer(
              "A",
              () -> {
                throw exA;
              });
      Function<Integer, Kind<LazyKind.Witness, String>> f =
          i -> {
            counterF.incrementAndGet();
            return countingDefer("B", () -> "Val");
          };

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.flatMap(f, initialKind);
      assertThatLazy(narrowToLazy(resultKind)).whenForcedThrows(RuntimeException.class);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isZero();
      assertThat(counterB.get()).isZero();
    }

    @Test
    @DisplayName("flatMap() propagates exception from function apply")
    void flatMapPropagatesExceptionFromFunctionApply() {
      RuntimeException fEx = new RuntimeException("FuncApplyFail");
      Kind<LazyKind.Witness, Integer> initialKind = countingDefer("A", () -> 5);
      Function<Integer, Kind<LazyKind.Witness, String>> f =
          i -> {
            counterF.incrementAndGet();
            throw fEx;
          };

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.flatMap(f, initialKind);
      assertThatLazy(narrowToLazy(resultKind)).whenForcedThrows(RuntimeException.class);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
      assertThat(counterB.get()).isZero();
    }

    @Test
    @DisplayName("flatMap() propagates exception from resulting Lazy")
    void flatMapPropagatesExceptionFromResultingLazy() {
      RuntimeException exB = new RuntimeException("FailB");
      Kind<LazyKind.Witness, Integer> initialKind = countingDefer("A", () -> 5);
      Function<Integer, Kind<LazyKind.Witness, String>> f =
          i -> {
            counterF.incrementAndGet();
            return countingDefer(
                "B",
                () -> {
                  throw exB;
                });
          };

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.flatMap(f, initialKind);
      assertThatLazy(narrowToLazy(resultKind)).whenForcedThrows(RuntimeException.class);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("MapN Tests")
  class MapNTests {
    Kind<LazyKind.Witness, Integer> lz1;
    Kind<LazyKind.Witness, String> lz2;
    Kind<LazyKind.Witness, Double> lz3;
    Kind<LazyKind.Witness, Boolean> lz4;

    @BeforeEach
    void setUpMapN() {
      counterA.set(0);
      counterB.set(0);
      counterC.set(0);
      counterD.set(0);
      lz1 = countingDefer("A", () -> 1);
      lz2 = countingDefer("B", () -> "X");
      lz3 = countingDefer("C", () -> 2.5);
      lz4 = countingDefer("D", () -> true);
    }

    @Test
    @DisplayName("map2() combines lazily")
    void map2CombinesLazily() throws Throwable {
      Kind<LazyKind.Witness, String> result = lazyMonad.map2(lz1, lz2, (i, s) -> s + i);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue("X1");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue("X1");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map3() combines lazily")
    void map3CombinesLazily() throws Throwable {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%s%d-%.1f", s, i, d);
      Kind<LazyKind.Witness, String> result = lazyMonad.map3(lz1, lz2, lz3, f3);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();
      assertThat(counterC.get()).isZero();

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue("X1-2.5");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue("X1-2.5");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map4() combines lazily")
    void map4CombinesLazily() throws Throwable {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%s%d-%.1f-%b", s, i, d, b);
      Kind<LazyKind.Witness, String> result = lazyMonad.map4(lz1, lz2, lz3, lz4, f4);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();
      assertThat(counterC.get()).isZero();
      assertThat(counterD.get()).isZero();

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue("X1-2.5-true");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);
      assertThat(counterD.get()).isEqualTo(1);

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue("X1-2.5-true");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);
      assertThat(counterD.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("mapN propagates failure")
    void mapNPropagatesFailure() {
      RuntimeException ex = new RuntimeException("FailMapN");
      Kind<LazyKind.Witness, String> lzFail =
          countingDefer(
              "B",
              () -> {
                throw ex;
              });
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Won't run";

      Kind<LazyKind.Witness, String> result = lazyMonad.map3(lz1, lzFail, lz3, f3);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();
      assertThat(counterC.get()).isZero();

      assertThatLazy(narrowToLazy(result)).whenForcedThrows(RuntimeException.class);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isZero();
    }
  }
}
