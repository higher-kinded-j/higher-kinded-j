package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LazyMonad Tests")
class LazyMonadTest {

  private LazyMonad lazyMonad;
  // Declare counters
  private AtomicInteger counterA;
  private AtomicInteger counterB;
  private AtomicInteger counterC;
  private AtomicInteger counterD; // Added counter for map4
  private AtomicInteger counterF;

  private <A> Kind<LazyKind.Witness, A> countingDefer(String label, ThrowableSupplier<A> supplier) {
    AtomicInteger counter =
        switch (label) {
          case "A" -> counterA;
          case "B" -> counterB;
          case "C" -> counterC;
          case "D" -> counterD; // Handle label "D"
          default -> throw new IllegalArgumentException("Unexpected label: " + label);
        };
    // Return a LazyKind that increments the selected counter upon evaluation
    return LazyKindHelper.defer(
        () -> {
          counter.incrementAndGet();
          // System.out.println("Evaluating " + label + " (Count: " + counter.get() + ")");
          return supplier.get();
        });
  }

  @BeforeEach
  void setUp() {
    lazyMonad = new LazyMonad();
    // Initialize counters before each test
    counterA = new AtomicInteger(0);
    counterB = new AtomicInteger(0);
    counterC = new AtomicInteger(0);
    counterD = new AtomicInteger(0); // Initialize counterD
    counterF = new AtomicInteger(0);
  }

  // --- Basic Operations ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateAlreadyEvaluatedLazy() throws Throwable {
      Kind<LazyKind.Witness, String> kind = lazyMonad.of("pureValue");
      assertThat(force(kind)).isEqualTo("pureValue"); // Line 69
      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
    }

    @Test
    void of_shouldAllowNullValue() throws Throwable {
      Kind<LazyKind.Witness, String> kind = lazyMonad.of(null);
      assertThat(force(kind)).isNull();
      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionLazilyAndMemoize() throws Throwable {
      Kind<LazyKind.Witness, Integer> initialKind = countingDefer("A", () -> 10);
      Function<Integer, String> mapper =
          i -> {
            counterF.incrementAndGet();
            return "Val:" + i;
          };
      Kind<LazyKind.Witness, String> mappedKind = lazyMonad.map(mapper, initialKind);

      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();

      assertThat(force(mappedKind)).isEqualTo("Val:10");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);

      assertThat(force(mappedKind)).isEqualTo("Val:10");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
    }

    @Test
    void map_shouldPropagateExceptionFromOriginalLazy() {
      RuntimeException ex = new RuntimeException("OriginalFail");
      Kind<LazyKind.Witness, Integer> initialKind =
          countingDefer(
              "A",
              () -> {
                throw ex;
              });
      Kind<LazyKind.Witness, String> mappedKind = lazyMonad.map(i -> "Val:" + i, initialKind);

      assertThat(counterA.get()).isZero();

      Throwable thrown = catchThrowable(() -> force(mappedKind));
      assertThat(thrown).isInstanceOf(RuntimeException.class).isSameAs(ex);
      assertThat(counterA.get()).isEqualTo(1);

      assertThatThrownBy(() -> force(mappedKind)).isInstanceOf(RuntimeException.class).isSameAs(ex);
      assertThat(counterA.get()).isEqualTo(1);
    }

    @Test
    void map_shouldPropagateExceptionFromMapperFunction() {
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
      Throwable thrown = catchThrowable(() -> force(mappedKind));
      assertThat(thrown).isInstanceOf(RuntimeException.class).isSameAs(mapEx);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);

      assertThatThrownBy(() -> force(mappedKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mapEx);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    @Test
    void ap_shouldApplyEffectfulFunctionToEffectfulValue() throws Throwable {
      Kind<LazyKind.Witness, Function<Integer, String>> funcKind =
          countingDefer("A", () -> i -> "F" + i);
      Kind<LazyKind.Witness, Integer> valKind = countingDefer("B", () -> 20);

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();

      assertThat(force(resultKind)).isEqualTo("F20");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);

      assertThat(force(resultKind)).isEqualTo("F20");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }

    @Test
    void ap_shouldPropagateExceptionFromFunctionLazy() {
      RuntimeException funcEx = new RuntimeException("FuncFail");
      Kind<LazyKind.Witness, Function<Integer, String>> funcKind =
          countingDefer(
              "A",
              () -> {
                throw funcEx;
              });
      Kind<LazyKind.Witness, Integer> valKind = countingDefer("B", () -> 20);

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(funcEx);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isZero();
    }

    @Test
    void ap_shouldPropagateExceptionFromValueLazy() {
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
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(valEx);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }

    @Test
    void ap_shouldPropagateExceptionFromFunctionApply() {
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
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(applyEx);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    @Test
    // Add 'throws Throwable' because force() is called
    void flatMap_shouldSequenceLazilyAndMemoize() throws Throwable {
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

      assertThat(force(resultKind)).isEqualTo("Val10");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);

      assertThat(force(resultKind)).isEqualTo("Val10");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }

    @Test
    void flatMap_shouldPropagateExceptionFromInitialLazy() {
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
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(exA);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isZero();
      assertThat(counterB.get()).isZero();
    }

    @Test
    void flatMap_shouldPropagateExceptionFromFunctionApply() {
      RuntimeException fEx = new RuntimeException("FuncApplyFail");
      Kind<LazyKind.Witness, Integer> initialKind = countingDefer("A", () -> 5);
      Function<Integer, Kind<LazyKind.Witness, String>> f =
          i -> {
            counterF.incrementAndGet();
            throw fEx;
          };

      Kind<LazyKind.Witness, String> resultKind = lazyMonad.flatMap(f, initialKind);
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(fEx);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
      assertThat(counterB.get()).isZero();
    }

    @Test
    void flatMap_shouldPropagateExceptionFromResultingLazy() {
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
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(exB);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }
  }

  // --- Law Tests ---

  final Function<Integer, String> intToString = Object::toString;
  final Function<String, String> appendWorld = s -> s + " world";
  // Use AtomicReference for thread safety if needed, though tests are sequential
  final AtomicReference<String> lawEffectTracker = new AtomicReference<>("");

  // Function a -> M b (Integer -> Kind<LazyKind.Witness, String>)
  // Make fLaw and gLaw methods instead of final fields to ensure fresh behavior
  private Function<Integer, Kind<LazyKind.Witness, String>> createFLaw() {
    return i -> {
      counterF.incrementAndGet(); // Track function application
      // System.out.println("Applying fLaw to " + i + " (counterF=" + counterF.get() + ")");
      return countingDefer("B", () -> "v" + i); // Lazy result
    };
  }

  // Function b -> M c (String -> Kind<LazyKind.Witness, String>)
  private Function<String, Kind<LazyKind.Witness, String>> createGLaw() {
    return s -> {
      counterF.incrementAndGet(); // Track function application
      // System.out.println("Applying gLaw to " + s + " (counterF=" + counterF.get() + ")");
      return countingDefer("C", () -> s + "!"); // Lazy result
    };
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() throws Throwable { // Add throws
      int value = 5;
      Kind<LazyKind.Witness, Integer> ofValue = lazyMonad.of(value);
      Function<Integer, Kind<LazyKind.Witness, String>> f = createFLaw();

      // Run left side: flatMap(f, ofValue)
      counterA.set(0);
      counterB.set(0);
      counterC.set(0);
      counterF.set(0); // Reset counters
      Kind<LazyKind.Witness, String> leftSide = lazyMonad.flatMap(f, ofValue);
      String leftResult = force(leftSide);
      int leftEvalA = counterA.get();
      int leftEvalB = counterB.get();
      int leftEvalC = counterC.get();
      int leftEvalF = counterF.get();

      // Run right side: f.apply(value)
      counterA.set(0);
      counterB.set(0);
      counterC.set(0);
      counterF.set(0); // Reset counters
      Kind<LazyKind.Witness, String> rightSide = f.apply(value);
      String rightResult = force(rightSide);
      int rightEvalA = counterA.get();
      int rightEvalB = counterB.get();
      int rightEvalC = counterC.get();
      int rightEvalF = counterF.get();

      assertThat(leftResult).isEqualTo(rightResult);
      // Check counts: f applied once, B evaluated once
      assertThat(leftEvalF).isEqualTo(1);
      assertThat(leftEvalB).isEqualTo(1);
      assertThat(rightEvalF).isEqualTo(1);
      assertThat(rightEvalB).isEqualTo(1);
      // A and C should not be evaluated
      assertThat(leftEvalA).isZero();
      assertThat(leftEvalC).isZero();
      assertThat(rightEvalA).isZero();
      assertThat(rightEvalC).isZero();
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() throws Throwable { // Add throws
      counterA.set(0);
      counterF.set(0);
      Kind<LazyKind.Witness, Integer> mValueLeft = countingDefer("A", () -> 10);
      Function<Integer, Kind<LazyKind.Witness, Integer>> ofFunc = i -> lazyMonad.of(i);
      Kind<LazyKind.Witness, Integer> leftSide = lazyMonad.flatMap(ofFunc, mValueLeft);
      Integer leftResult = force(leftSide);
      int leftEvalA = counterA.get();
      int leftEvalF = counterF.get();

      counterA.set(0);
      counterF.set(0);
      Kind<LazyKind.Witness, Integer> mValueRight = countingDefer("A", () -> 10);
      Integer rightResult = force(mValueRight);
      int rightEvalA = counterA.get();
      int rightEvalF = counterF.get();

      assertThat(leftResult).isEqualTo(rightResult);
      assertThat(leftEvalA).isEqualTo(1);
      assertThat(rightEvalA).isEqualTo(1);
      // Corrected: ofFunc is applied, but doesn't use counterF
      assertThat(leftEvalF).isZero();
      assertThat(rightEvalF).isZero();
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() throws Throwable { // Add throws
      Function<Integer, Kind<LazyKind.Witness, String>> f = createFLaw();
      Function<String, Kind<LazyKind.Witness, String>> g = createGLaw();

      // --- Left Side Evaluation ---
      counterA.set(0);
      counterB.set(0);
      counterC.set(0);
      counterF.set(0);
      Kind<LazyKind.Witness, Integer> mValueLeft = countingDefer("A", () -> 10);
      Kind<LazyKind.Witness, String> innerLeft = lazyMonad.flatMap(f, mValueLeft);
      Kind<LazyKind.Witness, String> leftSide = lazyMonad.flatMap(g, innerLeft);
      String leftResult = force(leftSide);
      int leftEvalA = counterA.get();
      int leftEvalB = counterB.get();
      int leftEvalC = counterC.get();
      int leftEvalF = counterF.get();

      // --- Right Side Evaluation ---
      counterA.set(0);
      counterB.set(0);
      counterC.set(0);
      counterF.set(0); // Reset counters
      Kind<LazyKind.Witness, Integer> mValueRight = countingDefer("A", () -> 10);
      // Recreate f and g for the right side's lambda to ensure counterF is tracked correctly within
      // the lambda
      Function<Integer, Kind<LazyKind.Witness, String>> fRight = createFLaw();
      Function<String, Kind<LazyKind.Witness, String>> gRight = createGLaw();
      Function<Integer, Kind<LazyKind.Witness, String>> rightSideFunc =
          a -> lazyMonad.flatMap(gRight, fRight.apply(a)); // Use fresh f/g instances
      Kind<LazyKind.Witness, String> rightSide = lazyMonad.flatMap(rightSideFunc, mValueRight);
      String rightResult = force(rightSide);
      int rightEvalA = counterA.get();
      int rightEvalB = counterB.get();
      int rightEvalC = counterC.get();
      int rightEvalF = counterF.get();

      assertThat(leftResult).isEqualTo(rightResult);
      assertThat(leftEvalA).isEqualTo(1);
      assertThat(leftEvalB).isEqualTo(1);
      assertThat(leftEvalC).isEqualTo(1);
      assertThat(rightEvalA).isEqualTo(1);
      assertThat(rightEvalB).isEqualTo(1);
      assertThat(rightEvalC).isEqualTo(1);
      // Function application count should be 2 (once for f, once for g) on both sides
      assertThat(leftEvalF).isEqualTo(2);
      assertThat(rightEvalF).isEqualTo(2);
    }
  }

  // --- mapN Tests ---
  @Nested
  @DisplayName("mapN tests")
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
    void map2_combinesLazily() throws Throwable { // Add throws
      Kind<LazyKind.Witness, String> result = lazyMonad.map2(lz1, lz2, (i, s) -> s + i);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();

      assertThat(force(result)).isEqualTo("X1");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);

      assertThat(force(result)).isEqualTo("X1");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
    }

    @Test
    void map3_combinesLazily() throws Throwable { // Add throws
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%s%d-%.1f", s, i, d);
      Kind<LazyKind.Witness, String> result = lazyMonad.map3(lz1, lz2, lz3, f3);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();
      assertThat(counterC.get()).isZero();

      assertThat(force(result)).isEqualTo("X1-2.5");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);

      assertThat(force(result)).isEqualTo("X1-2.5");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);
    }

    @Test
    void map4_combinesLazily() throws Throwable { // Add throws
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%s%d-%.1f-%b", s, i, d, b);
      Kind<LazyKind.Witness, String> result = lazyMonad.map4(lz1, lz2, lz3, lz4, f4);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();
      assertThat(counterC.get()).isZero();
      assertThat(counterD.get()).isZero();

      assertThat(force(result)).isEqualTo("X1-2.5-true");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);
      assertThat(counterD.get()).isEqualTo(1);

      assertThat(force(result)).isEqualTo("X1-2.5-true");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);
      assertThat(counterD.get()).isEqualTo(1);
    }

    @Test
    void mapN_propagatesFailure() { // No throws needed
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

      assertThatThrownBy(() -> force(result)).isInstanceOf(RuntimeException.class).isSameAs(ex);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isZero();
    }
  }
}
