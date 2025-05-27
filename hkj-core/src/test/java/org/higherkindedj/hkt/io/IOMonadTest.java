// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOKindHelper.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOMonad Tests")
class IOMonadTest {

  private IOMonad ioMonad;
  private List<String> effectsLog; // To track side effects

  // Helper to create an IO Kind that logs an effect and returns a value
  private <A> Kind<IOKind.Witness, A> effectfulKind(String effect, A value) {
    return IOKindHelper.delay(
        () -> {
          effectsLog.add(effect);
          return value;
        });
  }

  // Helper to create an IO Kind that logs an effect and throws
  private <A> Kind<IOKind.Witness, A> failingKind(String effect, RuntimeException exception) {
    return IOKindHelper.delay(
        () -> {
          effectsLog.add(effect);
          throw exception;
        });
  }

  @BeforeEach
  void setUp() {
    ioMonad = new IOMonad();
    effectsLog = new ArrayList<>(); // Reset log for each test
  }

  // --- Basic Operations ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateIOThatReturnsValueWithoutImmediateEffect() {
      Kind<IOKind.Witness, String> kind = ioMonad.of("pureValue");
      assertThat(effectsLog).isEmpty(); // No effect on creation
      assertThat(unsafeRunSync(kind)).isEqualTo("pureValue");
      assertThat(effectsLog).isEmpty(); // 'of'/'pure' shouldn't have side effects itself
    }

    @Test
    void of_shouldAllowNullValue() {
      Kind<IOKind.Witness, String> kind = ioMonad.of(null);
      assertThat(effectsLog).isEmpty();
      assertThat(unsafeRunSync(kind)).isNull();
      assertThat(effectsLog).isEmpty();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionLazilyAndPreserveEffect() {
      Kind<IOKind.Witness, Integer> initialKind = effectfulKind("Effect1", 10);
      Kind<IOKind.Witness, String> mappedKind = ioMonad.map(i -> "Val:" + i, initialKind);

      assertThat(effectsLog).isEmpty(); // No effect yet

      assertThat(unsafeRunSync(mappedKind)).isEqualTo("Val:10");
      assertThat(effectsLog).containsExactly("Effect1"); // Original effect ran

      // Run again
      effectsLog.clear();
      assertThat(unsafeRunSync(mappedKind)).isEqualTo("Val:10");
      assertThat(effectsLog).containsExactly("Effect1");
    }

    @Test
    void map_shouldPropagateExceptionFromOriginalIO() {
      RuntimeException ex = new RuntimeException("OriginalFail");
      Kind<IOKind.Witness, Integer> initialKind = failingKind("EffectFail", ex);
      Kind<IOKind.Witness, String> mappedKind = ioMonad.map(i -> "Val:" + i, initialKind);

      assertThat(effectsLog).isEmpty();
      assertThatThrownBy(() -> unsafeRunSync(mappedKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
      assertThat(effectsLog).containsExactly("EffectFail"); // Effect ran before failing
    }

    @Test
    void map_shouldPropagateExceptionFromMapperFunction() {
      RuntimeException mapEx = new RuntimeException("MapperFail");
      Kind<IOKind.Witness, Integer> initialKind = effectfulKind("EffectOK", 10);
      Kind<IOKind.Witness, String> mappedKind =
          ioMonad.map(
              i -> {
                effectsLog.add("MapperRun");
                throw mapEx;
              },
              initialKind);

      assertThat(effectsLog).isEmpty();
      assertThatThrownBy(() -> unsafeRunSync(mappedKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mapEx);
      // Effects: Original IO runs, then mapper runs and throws
      assertThat(effectsLog).containsExactly("EffectOK", "MapperRun");
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    @Test
    void ap_shouldApplyEffectfulFunctionToEffectfulValue() {
      Kind<IOKind.Witness, Function<Integer, String>> funcKind =
          effectfulKind("EffectF", i -> "F(" + i + ")");
      Kind<IOKind.Witness, Integer> valKind = effectfulKind("EffectV", 20);

      Kind<IOKind.Witness, String> resultKind = ioMonad.ap(funcKind, valKind);
      assertThat(effectsLog).isEmpty(); // Lazy

      assertThat(unsafeRunSync(resultKind)).isEqualTo("F(20)");
      // ap runs function IO first, then value IO
      assertThat(effectsLog).containsExactly("EffectF", "EffectV");
    }

    @Test
    void ap_shouldPropagateExceptionFromFunctionIO() {
      RuntimeException funcEx = new RuntimeException("FuncFail");
      Kind<IOKind.Witness, Function<Integer, String>> funcKind = failingKind("EffectF", funcEx);
      Kind<IOKind.Witness, Integer> valKind = effectfulKind("EffectV", 20); // This effect won't run

      Kind<IOKind.Witness, String> resultKind = ioMonad.ap(funcKind, valKind);
      assertThat(effectsLog).isEmpty();

      assertThatThrownBy(() -> unsafeRunSync(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(funcEx);
      assertThat(effectsLog).containsExactly("EffectF"); // Only first effect ran
    }

    @Test
    void ap_shouldPropagateExceptionFromValueIO() {
      RuntimeException valEx = new RuntimeException("ValFail");
      Kind<IOKind.Witness, Function<Integer, String>> funcKind =
          effectfulKind("EffectF", i -> "F(" + i + ")");
      Kind<IOKind.Witness, Integer> valKind = failingKind("EffectV", valEx);

      Kind<IOKind.Witness, String> resultKind = ioMonad.ap(funcKind, valKind);
      assertThat(effectsLog).isEmpty();

      assertThatThrownBy(() -> unsafeRunSync(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(valEx);
      assertThat(effectsLog).containsExactly("EffectF", "EffectV"); // Both effects ran before fail
    }

    @Test
    void ap_shouldPropagateExceptionFromFunctionApply() {
      RuntimeException applyEx = new RuntimeException("ApplyFail");
      Kind<IOKind.Witness, Function<Integer, String>> funcKind =
          effectfulKind(
              "EffectF",
              i -> {
                effectsLog.add("ApplyRun");
                throw applyEx;
              });
      Kind<IOKind.Witness, Integer> valKind = effectfulKind("EffectV", 20);

      Kind<IOKind.Witness, String> resultKind = ioMonad.ap(funcKind, valKind);
      assertThat(effectsLog).isEmpty();

      assertThatThrownBy(() -> unsafeRunSync(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(applyEx);
      // Effects: Func IO, Value IO, then Apply fails
      assertThat(effectsLog).containsExactly("EffectF", "EffectV", "ApplyRun");
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    @Test
    void flatMap_shouldSequenceEffectsCorrectly() {
      Kind<IOKind.Witness, Integer> initialKind = effectfulKind("Effect1", 5);
      Function<Integer, Kind<IOKind.Witness, String>> f =
          i -> effectfulKind("Effect2(" + i + ")", "Val" + (i * 2));

      Kind<IOKind.Witness, String> resultKind = ioMonad.flatMap(f, initialKind);
      assertThat(effectsLog).isEmpty(); // Lazy

      assertThat(unsafeRunSync(resultKind)).isEqualTo("Val10");
      assertThat(effectsLog).containsExactly("Effect1", "Effect2(5)"); // Effects in order
    }

    @Test
    void flatMap_shouldPropagateExceptionFromInitialIO() {
      RuntimeException ex1 = new RuntimeException("Fail1");
      Kind<IOKind.Witness, Integer> initialKind = failingKind("Effect1", ex1);
      Function<Integer, Kind<IOKind.Witness, String>> f =
          i -> effectfulKind("Effect2", "Val"); // This won't run

      Kind<IOKind.Witness, String> resultKind = ioMonad.flatMap(f, initialKind);
      assertThat(effectsLog).isEmpty();

      assertThatThrownBy(() -> unsafeRunSync(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex1);
      assertThat(effectsLog).containsExactly("Effect1");
    }

    @Test
    void flatMap_shouldPropagateExceptionFromFunctionApply() {
      RuntimeException fEx = new RuntimeException("FuncApplyFail");
      Kind<IOKind.Witness, Integer> initialKind = effectfulKind("Effect1", 5);
      Function<Integer, Kind<IOKind.Witness, String>> f =
          i -> {
            effectsLog.add("FuncRun");
            throw fEx;
          };

      Kind<IOKind.Witness, String> resultKind = ioMonad.flatMap(f, initialKind);
      assertThat(effectsLog).isEmpty();

      assertThatThrownBy(() -> unsafeRunSync(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(fEx);
      assertThat(effectsLog).containsExactly("Effect1", "FuncRun");
    }

    @Test
    void flatMap_shouldPropagateExceptionFromResultingIO() {
      RuntimeException ex2 = new RuntimeException("Fail2");
      Kind<IOKind.Witness, Integer> initialKind = effectfulKind("Effect1", 5);
      Function<Integer, Kind<IOKind.Witness, String>> f =
          i -> failingKind("Effect2(" + i + ")", ex2); // This IO fails

      Kind<IOKind.Witness, String> resultKind = ioMonad.flatMap(f, initialKind);
      assertThat(effectsLog).isEmpty();

      assertThatThrownBy(() -> unsafeRunSync(resultKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex2);
      assertThat(effectsLog).containsExactly("Effect1", "Effect2(5)");
    }
  }

  // --- Law Tests ---

  // Helper functions for laws
  final Function<Integer, String> intToString = Object::toString;
  final Function<String, String> appendWorld = s -> s + " world";
  final AtomicReference<String> lawEffectTracker = new AtomicReference<>("");

  // Function a -> M b (Integer -> IOKind<String>)
  final Function<Integer, Kind<IOKind.Witness, String>> fLaw =
      i -> {
        lawEffectTracker.set(lawEffectTracker.get() + "f(" + i + ")");
        return ioMonad.of("v" + i);
      };
  // Function b -> M c (String -> IOKind<String>)
  final Function<String, Kind<IOKind.Witness, String>> gLaw =
      s -> {
        lawEffectTracker.set(lawEffectTracker.get() + "g(" + s + ")");
        return ioMonad.of(s + "!");
      };

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      int value = 5;
      Kind<IOKind.Witness, Integer> ofValue = ioMonad.of(value);

      // Run left side
      lawEffectTracker.set("");
      Kind<IOKind.Witness, String> leftSide = ioMonad.flatMap(fLaw, ofValue);
      String leftResult = unsafeRunSync(leftSide);
      String leftEffects = lawEffectTracker.get();

      // Run right side
      lawEffectTracker.set("");
      Kind<IOKind.Witness, String> rightSide = fLaw.apply(value);
      String rightResult = unsafeRunSync(rightSide);
      String rightEffects = lawEffectTracker.get();

      assertThat(leftResult).isEqualTo(rightResult);
      assertThat(leftEffects).isEqualTo(rightEffects); // Check effects are the same
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Kind<IOKind.Witness, Integer> mValue =
          IOKindHelper.delay(
              () -> {
                lawEffectTracker.set(lawEffectTracker.get() + "m");
                return 10;
              });
      Function<Integer, Kind<IOKind.Witness, Integer>> ofFunc = i -> ioMonad.of(i);

      // Run left side
      lawEffectTracker.set("");
      Kind<IOKind.Witness, Integer> leftSide = ioMonad.flatMap(ofFunc, mValue);
      Integer leftResult = unsafeRunSync(leftSide);
      String leftEffects = lawEffectTracker.get();

      // Run right side
      lawEffectTracker.set("");
      Integer rightResult = unsafeRunSync(mValue);
      String rightEffects = lawEffectTracker.get();

      assertThat(leftResult).isEqualTo(rightResult);
      assertThat(leftEffects).isEqualTo(rightEffects);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<IOKind.Witness, Integer> mValue =
          IOKindHelper.delay(
              () -> {
                lawEffectTracker.set(lawEffectTracker.get() + "m");
                return 10;
              });

      // Run left side: flatMap(gLaw, flatMap(fLaw, mValue))
      lawEffectTracker.set("");
      Kind<IOKind.Witness, String> innerLeft = ioMonad.flatMap(fLaw, mValue);
      Kind<IOKind.Witness, String> leftSide = ioMonad.flatMap(gLaw, innerLeft);
      String leftResult = unsafeRunSync(leftSide);
      String leftEffects = lawEffectTracker.get();

      // Run right side: flatMap(mValue, a -> flatMap(gLaw, fLaw.apply(a)))
      lawEffectTracker.set("");
      Function<Integer, Kind<IOKind.Witness, String>> rightSideFunc =
          a -> ioMonad.flatMap(gLaw, fLaw.apply(a));
      Kind<IOKind.Witness, String> rightSide = ioMonad.flatMap(rightSideFunc, mValue);
      String rightResult = unsafeRunSync(rightSide);
      String rightEffects = lawEffectTracker.get();

      assertThat(leftResult).isEqualTo(rightResult);
      assertThat(leftEffects).isEqualTo(rightEffects); // Effects should match order: m -> f -> g
    }
  }

  // --- mapN Tests (using default Applicative implementations) ---
  @Nested
  @DisplayName("mapN tests")
  class MapNTests {
    Kind<IOKind.Witness, Integer> io1 = effectfulKind("E1", 1);
    Kind<IOKind.Witness, String> io2 = effectfulKind("E2", "A");
    Kind<IOKind.Witness, Double> io3 = effectfulKind("E3", 1.5);
    Kind<IOKind.Witness, Boolean> io4 = effectfulKind("E4", true);

    @Test
    void map2_combinesValuesAndEffects() {
      Kind<IOKind.Witness, String> result = ioMonad.map2(io1, io2, (i, s) -> s + i);
      assertThat(effectsLog).isEmpty();
      assertThat(unsafeRunSync(result)).isEqualTo("A1");
      assertThat(effectsLog).containsExactly("E1", "E2"); // Effects run in order
    }

    @Test
    void map3_combinesValuesAndEffects() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%s%d-%.1f", s, i, d);
      Kind<IOKind.Witness, String> result = ioMonad.map3(io1, io2, io3, f3);
      assertThat(effectsLog).isEmpty();
      assertThat(unsafeRunSync(result)).isEqualTo("A1-1.5");
      assertThat(effectsLog).containsExactly("E1", "E2", "E3");
    }

    @Test
    void map4_combinesValuesAndEffects() {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%s%d-%.1f-%b", s, i, d, b);
      Kind<IOKind.Witness, String> result = ioMonad.map4(io1, io2, io3, io4, f4);
      assertThat(effectsLog).isEmpty();
      assertThat(unsafeRunSync(result)).isEqualTo("A1-1.5-true");
      assertThat(effectsLog).containsExactly("E1", "E2", "E3", "E4");
    }

    @Test
    void mapN_propagatesFailure() {
      RuntimeException ex = new RuntimeException("Fail");
      Kind<IOKind.Witness, String> ioFail = failingKind("EFail", ex);
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Won't run";

      Kind<IOKind.Witness, String> result = ioMonad.map3(io1, ioFail, io3, f3);
      assertThat(effectsLog).isEmpty();
      assertThatThrownBy(() -> unsafeRunSync(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
      // Effects run up to the point of failure
      assertThat(effectsLog).containsExactly("E1", "EFail");
    }
  }
}
