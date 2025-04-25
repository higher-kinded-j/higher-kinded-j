package org.simulation.hkt.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.simulation.hkt.lazy.LazyKindHelper.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.function.Function3;
import org.simulation.hkt.function.Function4;

@DisplayName("LazyMonad Tests")
class LazyMonadTest {

  private LazyMonad lazyMonad;
  // Declare counters
  private AtomicInteger counterA;
  private AtomicInteger counterB;
  private AtomicInteger counterC;
  private AtomicInteger counterD; // Added counter for map4
  private AtomicInteger counterF; // For function application tracking

  // Helper to create a Kind<LazyKind, A> that counts evaluations
  private <A> Kind<LazyKind<?>, A> countingDefer(String label, Supplier<A> supplier) {
    // Select the appropriate counter based on the label
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
          counter.incrementAndGet(); // Increment the non-null counter
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
    void of_shouldCreateAlreadyEvaluatedLazy() {
      Kind<LazyKind<?>, String> kind = lazyMonad.of("pureValue");
      // 'of' uses Lazy.now, so it's already evaluated, no computation to track
      assertThat(force(kind)).isEqualTo("pureValue");
      // Ensure no counters were accidentally incremented
      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
    }

    @Test
    void of_shouldAllowNullValue() {
      Kind<LazyKind<?>, String> kind = lazyMonad.of(null);
      assertThat(force(kind)).isNull();
      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionLazilyAndMemoize() {
      Kind<LazyKind<?>, Integer> initialKind = countingDefer("A", () -> 10);
      Function<Integer, String> mapper =
          i -> {
            counterF.incrementAndGet();
            return "Val:" + i;
          };
      Kind<LazyKind<?>, String> mappedKind = lazyMonad.map(mapper, initialKind);

      assertThat(counterA.get()).isZero(); // Not evaluated yet
      assertThat(counterF.get()).isZero();

      // First force
      assertThat(force(mappedKind)).isEqualTo("Val:10");
      assertThat(counterA.get()).isEqualTo(1); // Original evaluated once
      assertThat(counterF.get()).isEqualTo(1); // Mapper evaluated once

      // Second force
      assertThat(force(mappedKind)).isEqualTo("Val:10");
      assertThat(counterA.get()).isEqualTo(1); // Original NOT evaluated again (memoized)
      assertThat(counterF.get())
          .isEqualTo(1); // Mapper NOT evaluated again (result of map is memoized)
    }

    @Test
    void map_shouldPropagateExceptionFromOriginalLazy() {
      RuntimeException ex = new RuntimeException("OriginalFail");
      Kind<LazyKind<?>, Integer> initialKind = countingDefer("A", () -> { throw ex; });
      Kind<LazyKind<?>, String> mappedKind = lazyMonad.map(i -> "Val:" + i, initialKind);

      assertThat(counterA.get()).isZero();
      assertThatThrownBy(() -> force(mappedKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
      assertThat(counterA.get()).isEqualTo(1); // Original evaluated once (and failed)

      // Force again - should throw memoized exception
      assertThatThrownBy(() -> force(mappedKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
      assertThat(counterA.get()).isEqualTo(1); // Still 1
    }

    @Test
    void map_shouldPropagateExceptionFromMapperFunction() {
      RuntimeException mapEx = new RuntimeException("MapperFail");
      Kind<LazyKind<?>, Integer> initialKind = countingDefer("A", () -> 10);
      Kind<LazyKind<?>, String> mappedKind =
          lazyMonad.map(
              i -> {
                counterF.incrementAndGet();
                throw mapEx;
              },
              initialKind);

      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
      assertThatThrownBy(() -> force(mappedKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mapEx);
      assertThat(counterA.get()).isEqualTo(1); // Original evaluated
      assertThat(counterF.get()).isEqualTo(1); // Mapper evaluated once (and failed)

      // Force again - should throw memoized exception
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
    void ap_shouldApplyLazyFunctionToLazyValueLazily() {
      Kind<LazyKind<?>, Function<Integer, String>> funcKind =
          countingDefer("A", () -> i -> "F" + i);
      Kind<LazyKind<?>, Integer> valKind = countingDefer("B", () -> 20);

      Kind<LazyKind<?>, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThat(counterA.get()).isZero(); // Not evaluated yet
      assertThat(counterB.get()).isZero();

      // First force
      assertThat(force(resultKind)).isEqualTo("F20");
      assertThat(counterA.get()).isEqualTo(1); // Function Lazy forced
      assertThat(counterB.get()).isEqualTo(1); // Value Lazy forced

      // Second force
      assertThat(force(resultKind)).isEqualTo("F20");
      assertThat(counterA.get()).isEqualTo(1); // Not forced again
      assertThat(counterB.get()).isEqualTo(1); // Not forced again
    }

    @Test
    void ap_shouldPropagateExceptionFromFunctionLazy() {
      RuntimeException funcEx = new RuntimeException("FuncFail");
      Kind<LazyKind<?>, Function<Integer, String>> funcKind = countingDefer("A", () -> { throw funcEx; });
      Kind<LazyKind<?>, Integer> valKind = countingDefer("B", () -> 20);

      Kind<LazyKind<?>, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(funcEx);
      assertThat(counterA.get()).isEqualTo(1); // Function Lazy forced
      assertThat(counterB.get()).isZero(); // Value Lazy NOT forced
    }

    @Test
    void ap_shouldPropagateExceptionFromValueLazy() {
      RuntimeException valEx = new RuntimeException("ValFail");
      Kind<LazyKind<?>, Function<Integer, String>> funcKind =
          countingDefer("A", () -> i -> "F" + i);
      Kind<LazyKind<?>, Integer> valKind = countingDefer("B", () -> { throw valEx; });

      Kind<LazyKind<?>, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(valEx);
      assertThat(counterA.get()).isEqualTo(1); // Function Lazy forced
      assertThat(counterB.get()).isEqualTo(1); // Value Lazy forced (and failed)
    }

    @Test
    void ap_shouldPropagateExceptionFromFunctionApply() {
      RuntimeException applyEx = new RuntimeException("ApplyFail");
      Kind<LazyKind<?>, Function<Integer, String>> funcKind =
          countingDefer(
              "A",
              () ->
                  i -> {
                    counterF.incrementAndGet();
                    throw applyEx;
                  });
      Kind<LazyKind<?>, Integer> valKind = countingDefer("B", () -> 20);

      Kind<LazyKind<?>, String> resultKind = lazyMonad.ap(funcKind, valKind);
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(applyEx);
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterF.get()).isEqualTo(1); // Function application attempted
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    @Test
    void flatMap_shouldSequenceLazilyAndMemoize() {
      Kind<LazyKind<?>, Integer> initialKind = countingDefer("A", () -> 5);
      Function<Integer, Kind<LazyKind<?>, String>> f =
          i -> {
            counterF.incrementAndGet(); // Track function application
            return countingDefer("B", () -> "Val" + (i * 2));
          };

      Kind<LazyKind<?>, String> resultKind = lazyMonad.flatMap(f, initialKind);
      assertThat(counterA.get()).isZero();
      assertThat(counterF.get()).isZero();
      assertThat(counterB.get()).isZero();

      // First force
      assertThat(force(resultKind)).isEqualTo("Val10");
      assertThat(counterA.get()).isEqualTo(1); // Initial forced
      assertThat(counterF.get()).isEqualTo(1); // Function applied
      assertThat(counterB.get()).isEqualTo(1); // Resulting Lazy forced

      // Second force
      assertThat(force(resultKind)).isEqualTo("Val10");
      assertThat(counterA.get()).isEqualTo(1); // Not forced again
      assertThat(counterF.get()).isEqualTo(1); // Not applied again
      assertThat(counterB.get()).isEqualTo(1); // Not forced again
    }

    @Test
    void flatMap_shouldPropagateExceptionFromInitialLazy() {
      RuntimeException exA = new RuntimeException("FailA");
      Kind<LazyKind<?>, Integer> initialKind = countingDefer("A", () -> { throw exA; });
      Function<Integer, Kind<LazyKind<?>, String>> f =
          i -> {
            counterF.incrementAndGet();
            return countingDefer("B", () -> "Val");
          }; // This part won't run

      Kind<LazyKind<?>, String> resultKind = lazyMonad.flatMap(f, initialKind);
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(exA);
      assertThat(counterA.get()).isEqualTo(1); // Initial forced (and failed)
      assertThat(counterF.get()).isZero();
      assertThat(counterB.get()).isZero();
    }

    @Test
    void flatMap_shouldPropagateExceptionFromFunctionApply() {
      RuntimeException fEx = new RuntimeException("FuncApplyFail");
      Kind<LazyKind<?>, Integer> initialKind = countingDefer("A", () -> 5);
      Function<Integer, Kind<LazyKind<?>, String>> f =
          i -> {
            counterF.incrementAndGet();
            throw fEx;
          };

      Kind<LazyKind<?>, String> resultKind = lazyMonad.flatMap(f, initialKind);
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(fEx);
      assertThat(counterA.get()).isEqualTo(1); // Initial forced
      assertThat(counterF.get()).isEqualTo(1); // Function applied (and failed)
      assertThat(counterB.get()).isZero();
    }

    @Test
    void flatMap_shouldPropagateExceptionFromResultingLazy() {
      RuntimeException exB = new RuntimeException("FailB");
      Kind<LazyKind<?>, Integer> initialKind = countingDefer("A", () -> 5);
      Function<Integer, Kind<LazyKind<?>, String>> f =
          i -> {
            counterF.incrementAndGet();
            return countingDefer("B", () -> { throw exB; }); // Resulting Lazy fails
          };

      Kind<LazyKind<?>, String> resultKind = lazyMonad.flatMap(f, initialKind);
      assertThatThrownBy(() -> force(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(exB);
      assertThat(counterA.get()).isEqualTo(1); // Initial forced
      assertThat(counterF.get()).isEqualTo(1); // Function applied
      assertThat(counterB.get()).isEqualTo(1); // Resulting Lazy forced (and failed)
    }
  }

  // --- Law Tests ---
  // We check both the final value and that the evaluation counts match after forcing.

  // Helper functions for laws that track evaluation
  final Function<Integer, String> intToString = Object::toString;
  final Function<String, String> appendWorld = s -> s + " world";

  // Function a -> M b (Integer -> LazyKind<String>)
  final Function<Integer, Kind<LazyKind<?>, String>> fLaw =
      i -> {
        counterF.incrementAndGet(); // Track function application
        return countingDefer("B", () -> "v" + i); // Lazy result
      };
  // Function b -> M c (String -> LazyKind<String>)
  final Function<String, Kind<LazyKind<?>, String>> gLaw =
      s -> {
        counterF.incrementAndGet(); // Track function application
        return countingDefer("C", () -> s + "!"); // Lazy result
      };

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      int value = 5;
      Kind<LazyKind<?>, Integer> ofValue = lazyMonad.of(value); // Already evaluated

      // Run left side: flatMap(fLaw, ofValue)
      counterA.set(0); counterB.set(0); counterC.set(0); counterF.set(0); // Reset counters
      Kind<LazyKind<?>, String> leftSide = lazyMonad.flatMap(fLaw, ofValue);
      String leftResult = force(leftSide);
      int leftEvalA = counterA.get(); int leftEvalB = counterB.get(); int leftEvalC = counterC.get(); int leftEvalF = counterF.get();

      // Run right side: fLaw.apply(value)
      counterA.set(0); counterB.set(0); counterC.set(0); counterF.set(0); // Reset counters
      Kind<LazyKind<?>, String> rightSide = fLaw.apply(value);
      String rightResult = force(rightSide);
      int rightEvalA = counterA.get(); int rightEvalB = counterB.get(); int rightEvalC = counterC.get(); int rightEvalF = counterF.get();

      assertThat(leftResult).isEqualTo(rightResult);
      // Check counts: f applied once, B evaluated once
      assertThat(leftEvalF).isEqualTo(1); assertThat(leftEvalB).isEqualTo(1);
      assertThat(rightEvalF).isEqualTo(1); assertThat(rightEvalB).isEqualTo(1);
      // A and C should not be evaluated
      assertThat(leftEvalA).isZero(); assertThat(leftEvalC).isZero();
      assertThat(rightEvalA).isZero(); assertThat(rightEvalC).isZero();

    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      // --- Left Side Evaluation ---
      counterA.set(0); counterF.set(0); // Reset relevant counters
      Kind<LazyKind<?>, Integer> mValueLeft = countingDefer("A", () -> 10); // Instance for left side
      Function<Integer, Kind<LazyKind<?>, Integer>> ofFunc = i -> lazyMonad.of(i); // Uses Lazy.now
      Kind<LazyKind<?>, Integer> leftSide = lazyMonad.flatMap(ofFunc, mValueLeft);
      Integer leftResult = force(leftSide);
      int leftEvalA = counterA.get();
      int leftEvalF = counterF.get(); // Track function application count

      // --- Right Side Evaluation ---
      counterA.set(0); counterF.set(0); // Reset relevant counters
      Kind<LazyKind<?>, Integer> mValueRight = countingDefer("A", () -> 10); // *** Create NEW instance ***
      Integer rightResult = force(mValueRight); // Force the new instance
      int rightEvalA = counterA.get();
      int rightEvalF = counterF.get(); // Track function application count

      assertThat(leftResult).isEqualTo(rightResult);
      // Check counts: A evaluated once on both sides
      assertThat(leftEvalA).isEqualTo(1);
      assertThat(rightEvalA).isEqualTo(1);
      // Check counts: ofFunc is applied once on the left side, but doesn't use counterF
      assertThat(leftEvalF).isZero(); // <-- CORRECTED ASSERTION
      assertThat(rightEvalF).isZero(); // ofFunc not applied on the right side
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      // --- Left Side Evaluation ---
      counterA.set(0); counterB.set(0); counterC.set(0); counterF.set(0);
      Kind<LazyKind<?>, Integer> mValueLeft = countingDefer("A", () -> 10); // Instance for left side
      Kind<LazyKind<?>, String> innerLeft = lazyMonad.flatMap(fLaw, mValueLeft);
      Kind<LazyKind<?>, String> leftSide = lazyMonad.flatMap(gLaw, innerLeft);
      String leftResult = force(leftSide);
      int leftEvalA = counterA.get(); int leftEvalB = counterB.get(); int leftEvalC = counterC.get(); int leftEvalF = counterF.get();

      // --- Right Side Evaluation ---
      counterA.set(0); counterB.set(0); counterC.set(0); counterF.set(0); // Reset counters
      Kind<LazyKind<?>, Integer> mValueRight = countingDefer("A", () -> 10); // *** Create NEW instance ***
      Function<Integer, Kind<LazyKind<?>, String>> rightSideFunc =
          a -> lazyMonad.flatMap(gLaw, fLaw.apply(a));
      Kind<LazyKind<?>, String> rightSide = lazyMonad.flatMap(rightSideFunc, mValueRight);
      String rightResult = force(rightSide);
      int rightEvalA = counterA.get(); int rightEvalB = counterB.get(); int rightEvalC = counterC.get(); int rightEvalF = counterF.get();

      assertThat(leftResult).isEqualTo(rightResult);
      // Check counts: A, B, C evaluated once each.
      assertThat(leftEvalA).isEqualTo(1); assertThat(leftEvalB).isEqualTo(1); assertThat(leftEvalC).isEqualTo(1);
      assertThat(rightEvalA).isEqualTo(1); assertThat(rightEvalB).isEqualTo(1); assertThat(rightEvalC).isEqualTo(1);
      // Function application count should be 2 (once for f, once for g) on both sides
      assertThat(leftEvalF).isEqualTo(2);
      assertThat(rightEvalF).isEqualTo(2);
    }
  }

  // --- mapN Tests (using default Applicative implementations) ---
  @Nested
  @DisplayName("mapN tests")
  class MapNTests {
    // Declare fields for lazy kinds
    Kind<LazyKind<?>, Integer> lz1;
    Kind<LazyKind<?>, String> lz2;
    Kind<LazyKind<?>, Double> lz3;
    Kind<LazyKind<?>, Boolean> lz4;

    @BeforeEach
      // Reset counters and initialize lazy kinds before each mapN test
    void setUpMapN() {
      counterA.set(0); counterB.set(0); counterC.set(0); counterD.set(0);
      // Initialize lazy kinds here, AFTER counters are initialized
      lz1 = countingDefer("A", () -> 1);
      lz2 = countingDefer("B", () -> "X");
      lz3 = countingDefer("C", () -> 2.5);
      lz4 = countingDefer("D", () -> true);
    }


    @Test
    void map2_combinesLazily() {
      Kind<LazyKind<?>, String> result = lazyMonad.map2(lz1, lz2, (i, s) -> s + i);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();

      assertThat(force(result)).isEqualTo("X1");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);

      // Force again
      assertThat(force(result)).isEqualTo("X1");
      assertThat(counterA.get()).isEqualTo(1); // Memoized
      assertThat(counterB.get()).isEqualTo(1); // Memoized
    }

    @Test
    void map3_combinesLazily() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%s%d-%.1f", s, i, d);
      Kind<LazyKind<?>, String> result = lazyMonad.map3(lz1, lz2, lz3, f3);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();
      assertThat(counterC.get()).isZero();

      assertThat(force(result)).isEqualTo("X1-2.5");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);

      // Force again
      assertThat(force(result)).isEqualTo("X1-2.5");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);
    }

    @Test
    void map4_combinesLazily() {
      // Use the lz4 defined in setUpMapN
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%s%d-%.1f-%b", s, i, d, b);
      Kind<LazyKind<?>, String> result = lazyMonad.map4(lz1, lz2, lz3, lz4, f4);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();
      assertThat(counterC.get()).isZero();
      assertThat(counterD.get()).isZero(); // Check counterD

      assertThat(force(result)).isEqualTo("X1-2.5-true");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);
      assertThat(counterD.get()).isEqualTo(1); // Check counterD evaluation

      // Force again
      assertThat(force(result)).isEqualTo("X1-2.5-true");
      assertThat(counterA.get()).isEqualTo(1);
      assertThat(counterB.get()).isEqualTo(1);
      assertThat(counterC.get()).isEqualTo(1);
      assertThat(counterD.get()).isEqualTo(1); // Check counterD memoization
    }

    @Test
    void mapN_propagatesFailure() {
      RuntimeException ex = new RuntimeException("FailMapN");
      Kind<LazyKind<?>, String> lzFail = countingDefer("B", () -> { throw ex; });
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Won't run";

      Kind<LazyKind<?>, String> result = lazyMonad.map3(lz1, lzFail, lz3, f3);
      assertThat(counterA.get()).isZero();
      assertThat(counterB.get()).isZero();
      assertThat(counterC.get()).isZero();

      assertThatThrownBy(() -> force(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
      // Check which computations ran
      assertThat(counterA.get()).isEqualTo(1); // lz1 ran
      assertThat(counterB.get()).isEqualTo(1); // lzFail ran (and failed)
      assertThat(counterC.get()).isZero();    // lz3 did not run
    }
  }
}
