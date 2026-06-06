// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.higherkindedj.hkt.assertions.LazyAssert.assertThatLazy;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("LazyMonad Complete Test Suite")
class LazyMonadTest extends LazyTestBase {

  private Monad<LazyKind.Witness> lazyMonad;
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
    lazyMonad = Instances.monad(lazy());
    // Initialise counters before each test
    counterA = new AtomicInteger(0);
    counterB = new AtomicInteger(0);
    counterC = new AtomicInteger(0);
    counterD = new AtomicInteger(0);
    counterF = new AtomicInteger(0);
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.lazy.LazyLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(lazyMonad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.lazy.LazyLawFixtures#kinds")
    void rightIdentity(String label, Kind<LazyKind.Witness, Integer> ma) {
      MonadLaws.assertRightIdentity(lazyMonad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.lazy.LazyLawFixtures#kinds")
    void associativity(String label, Kind<LazyKind.Witness, Integer> ma) {
      MonadLaws.assertAssociativity(lazyMonad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}/{@code
   * flatMap} <em>propagate</em> a thrown function exception immediately, but {@code Lazy} is lazy —
   * the exception surfaces only when the result is forced. That deferral is exercised throughout
   * {@link EdgeCasesTests}.
   */
  @Test
  @DisplayName(
      "Monad contract — operations & validations (laws verified above; Lazy defers exceptions,"
          + " verified below)")
  void monadContract() {
    TypeClassContract.<LazyKind.Witness>monad(LazyMonad.class)
        .<Integer>instance(lazyMonad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
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
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() throws Throwable {
      Kind<LazyKind.Witness, Integer> result = nowKind(1);
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
                      String::toUpperCase, lazyMonad.map(x -> "value:" + x, lazyMonad.of(i * 2))),
              start);

      assertThatLazy(narrowToLazy(result)).whenForcedHasValue("VALUE:20");
    }

    @Test
    @DisplayName("of() allows null value")
    @SuppressWarnings("DataFlowIssue") // Lazy may legitimately hold a null value
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
              _ -> {
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
                  _ -> {
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
          _ -> {
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
          _ -> {
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
          _ -> {
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
      Function3<Integer, String, Double, String> f3 = (_, _, _) -> "Won't run";

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
