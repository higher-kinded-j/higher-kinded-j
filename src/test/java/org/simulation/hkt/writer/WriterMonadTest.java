package org.simulation.hkt.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simulation.hkt.writer.WriterKindHelper.*;

import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.function.Function3;
import org.simulation.hkt.function.Function4;
import org.simulation.hkt.typeclass.Monoid;
import org.simulation.hkt.typeclass.StringMonoid;

/** Tests for WriterMonad<W, A>. Uses StringMonoid for W. */
@DisplayName("WriterMonad Tests (W=String)")
class WriterMonadTest {

  private Monoid<String> stringMonoid;
  private WriterMonad<String> writerMonad;

  @BeforeEach
  void setUp() {
    stringMonoid = new StringMonoid();
    writerMonad = new WriterMonad<>(stringMonoid);
  }

  // Helper to run and get the Writer record
  private <A> Writer<String, A> runW(Kind<WriterKind<String, ?>, A> kind) {
    return runWriter(kind);
  }

  // --- Basic Operations ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateWriterWithEmptyLog() {
      Kind<WriterKind<String, ?>, Integer> kind = writerMonad.of(10);
      Writer<String, Integer> w = runW(kind);
      assertThat(w.log()).isEqualTo(stringMonoid.empty());
      assertThat(w.value()).isEqualTo(10);
    }

    @Test
    void of_shouldAllowNullValue() {
      Kind<WriterKind<String, ?>, String> kind = writerMonad.of(null);
      Writer<String, String> w = runW(kind);
      assertThat(w.log()).isEqualTo(stringMonoid.empty());
      assertThat(w.value()).isNull();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionToValueAndKeepLog() {
      Kind<WriterKind<String, ?>, Integer> initialKind = wrap(Writer.create("Log1;", 5));
      Kind<WriterKind<String, ?>, String> mappedKind = writerMonad.map(i -> "v" + i, initialKind);
      Writer<String, String> w = runW(mappedKind);
      assertThat(w.log()).isEqualTo("Log1;");
      assertThat(w.value()).isEqualTo("v5");
    }

    @Test
    void map_shouldWorkWithEmptyLog() {
      Kind<WriterKind<String, ?>, Integer> initialKind = writerMonad.of(5); // Uses 'of'
      Kind<WriterKind<String, ?>, String> mappedKind = writerMonad.map(i -> "v" + i, initialKind);
      Writer<String, String> w = runW(mappedKind);
      assertThat(w.log()).isEqualTo("");
      assertThat(w.value()).isEqualTo("v5");
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    @Test
    void ap_shouldApplyWriterFunctionToWriterValue() {
      // Writer<String, Function<Integer, String>>
      Kind<WriterKind<String, ?>, Function<Integer, String>> funcKind =
          wrap(Writer.create("FuncLog;", i -> "Res:" + i));

      // Writer<String, Integer>
      Kind<WriterKind<String, ?>, Integer> valKind = wrap(Writer.create("ValLog;", 10));

      Kind<WriterKind<String, ?>, String> resultKind = writerMonad.ap(funcKind, valKind);
      Writer<String, String> w = runW(resultKind);

      assertThat(w.log()).isEqualTo("FuncLog;ValLog;"); // Logs combined
      assertThat(w.value()).isEqualTo("Res:10"); // Function applied
    }

    @Test
    void ap_shouldCombineLogsEvenIfValueIsNull() {
      Kind<WriterKind<String, ?>, Function<Integer, String>> funcKind =
          wrap(Writer.create("FuncLog;", i -> "Res:" + i));
      Kind<WriterKind<String, ?>, Integer> valKind =
          wrap(Writer.create("ValLog;", null)); // Null value

      Kind<WriterKind<String, ?>, String> resultKind = writerMonad.ap(funcKind, valKind);
      Writer<String, String> w = runW(resultKind);

      assertThat(w.log()).isEqualTo("FuncLog;ValLog;");
      // Result depends on how the function handles null input
      assertThat(w.value()).isEqualTo("Res:null");
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    @Test
    void flatMap_shouldSequenceComputationsAndCombineLogs() {
      Kind<WriterKind<String, ?>, Integer> initialKind = wrap(Writer.create("Start;", 3));

      // Function: Integer -> WriterKind<String, String>
      Function<Integer, Kind<WriterKind<String, ?>, String>> process =
          i -> wrap(Writer.create("Proc(" + i + ");", "Value=" + (i * 2)));

      Kind<WriterKind<String, ?>, String> resultKind = writerMonad.flatMap(process, initialKind);
      Writer<String, String> w = runW(resultKind);

      assertThat(w.log()).isEqualTo("Start;Proc(3);");
      assertThat(w.value()).isEqualTo("Value=6");
    }

    @Test
    void flatMap_withTell() {
      Kind<WriterKind<String, ?>, Integer> initialKind = writerMonad.of(5); // ("", 5)

      // Function: Integer -> WriterKind<String, Void> (using tell)
      Function<Integer, Kind<WriterKind<String, ?>, Void>> logValue =
          i -> WriterKindHelper.tell(stringMonoid, "Logged:" + i + ";"); // ("Logged:5;", null)

      // Function: Void -> WriterKind<String, String>
      Function<Void, Kind<WriterKind<String, ?>, String>> finalStep =
          ignored -> wrap(Writer.create("End;", "Complete")); // ("End;", "Complete")

      // Chain: of(5) >>= logValue >>= finalStep
      Kind<WriterKind<String, ?>, String> resultKind =
          writerMonad.flatMap(
              v -> finalStep.apply(v), // Apply finalStep to the Void result of logValue
              writerMonad.flatMap(logValue, initialKind) // Apply logValue to initial 5
              );

      Writer<String, String> w = runW(resultKind);
      assertThat(w.log()).isEqualTo("Logged:5;End;");
      assertThat(w.value()).isEqualTo("Complete");
    }
  }

  // --- Law Tests ---

  // Helper functions for laws
  final Function<Integer, String> intToString = Object::toString;
  final Function<String, String> appendWorld = s -> s + " world";

  // Kind<WriterKind<String, ?>, Integer>
  final Kind<WriterKind<String, ?>, Integer> mValue = wrap(Writer.create("mVal;", 5));
  // Function Integer -> Kind<WriterKind<String, ?>, String>
  final Function<Integer, Kind<WriterKind<String, ?>, String>> f =
      i -> wrap(Writer.create("f(" + i + ");", "v" + i));
  // Function String -> Kind<WriterKind<String, ?>, String>
  final Function<String, Kind<WriterKind<String, ?>, String>> g =
      s -> wrap(Writer.create("g(" + s + ");", s + "!"));

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      Kind<WriterKind<String, ?>, Integer> fa = wrap(Writer.create("Log;", 10));
      Kind<WriterKind<String, ?>, Integer> result = writerMonad.map(Function.identity(), fa);
      assertThat(runW(result)).isEqualTo(runW(fa));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<WriterKind<String, ?>, Integer> fa = wrap(Writer.create("Log;", 10));
      Function<Integer, String> fMap = i -> "v" + i;
      Function<String, String> gMap = s -> s + "!";
      Function<Integer, String> gComposeF = gMap.compose(fMap);

      Kind<WriterKind<String, ?>, String> leftSide = writerMonad.map(gComposeF, fa);
      Kind<WriterKind<String, ?>, String> rightSide =
          writerMonad.map(gMap, writerMonad.map(fMap, fa));

      // Logs should be the same, values should be the same
      assertThat(runW(leftSide)).isEqualTo(runW(rightSide));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {
    Kind<WriterKind<String, ?>, Integer> v = wrap(Writer.create("ValLog;", 5));
    Kind<WriterKind<String, ?>, Function<Integer, String>> fKind =
        wrap(Writer.create("FuncLog;", intToString));
    Kind<WriterKind<String, ?>, Function<String, String>> gKind =
        wrap(Writer.create("GFuncLog;", appendWorld));

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<WriterKind<String, ?>, Function<Integer, Integer>> idFuncKind =
          writerMonad.of(Function.identity()); // ("", id)
      Kind<WriterKind<String, ?>, Integer> result =
          writerMonad.ap(idFuncKind, v); // ("", id) ap ("ValLog;", 5) -> ("ValLog;", 5)
      assertThat(runW(result)).isEqualTo(runW(v));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> func = i -> "X" + i;
      Kind<WriterKind<String, ?>, Function<Integer, String>> apFunc =
          writerMonad.of(func); // ("", func)
      Kind<WriterKind<String, ?>, Integer> apVal = writerMonad.of(x); // ("", 10)

      Kind<WriterKind<String, ?>, String> leftSide = writerMonad.ap(apFunc, apVal); // ("", "X10")
      Kind<WriterKind<String, ?>, String> rightSide = writerMonad.of(func.apply(x)); // ("", "X10")

      assertThat(runW(leftSide)).isEqualTo(runW(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      // Left: ("FuncLog;", f) ap ("", 20) -> ("FuncLog;", f(20))
      Kind<WriterKind<String, ?>, String> leftSide = writerMonad.ap(fKind, writerMonad.of(y));

      // Right: ("", fn->fn(y)) ap ("FuncLog;", f) -> ("FuncLog;", f(20))
      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<WriterKind<String, ?>, Function<Function<Integer, String>, String>> evalKind =
          writerMonad.of(evalWithY);
      Kind<WriterKind<String, ?>, String> rightSide = writerMonad.ap(evalKind, fKind);

      assertThat(runW(leftSide)).isEqualTo(runW(rightSide));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          composeMap = gg -> ff -> gg.compose(ff);

      // Left side:
      // map(composeMap, gKind) -> ("GFuncLog;", f -> g.compose(f))
      Kind<WriterKind<String, ?>, Function<Function<Integer, String>, Function<Integer, String>>>
          mappedCompose = writerMonad.map(composeMap, gKind);
      // ap(mappedCompose, fKind) -> ("GFuncLog;FuncLog;", g.compose(f))
      Kind<WriterKind<String, ?>, Function<Integer, String>> ap1 =
          writerMonad.ap(mappedCompose, fKind);
      // ap(ap1, v) -> ("GFuncLog;FuncLog;ValLog;", g(f(v)))
      Kind<WriterKind<String, ?>, String> leftSide = writerMonad.ap(ap1, v);

      // Right side:
      // ap(fKind, v) -> ("FuncLog;ValLog;", f(v))
      Kind<WriterKind<String, ?>, String> innerAp = writerMonad.ap(fKind, v);
      // ap(gKind, innerAp) -> ("GFuncLog;FuncLog;ValLog;", g(f(v)))
      Kind<WriterKind<String, ?>, String> rightSide = writerMonad.ap(gKind, innerAp);

      assertThat(runW(leftSide)).isEqualTo(runW(rightSide));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      int value = 5;
      Kind<WriterKind<String, ?>, Integer> ofValue = writerMonad.of(value); // ("", 5)
      // Left: flatMap(f, ofValue) -> f(5) -> ("f(5);", "v5")
      Kind<WriterKind<String, ?>, String> leftSide = writerMonad.flatMap(f, ofValue);
      // Right: f(5) -> ("f(5);", "v5")
      Kind<WriterKind<String, ?>, String> rightSide = f.apply(value);

      assertThat(runW(leftSide)).isEqualTo(runW(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<WriterKind<String, ?>, Integer>> ofFunc =
          i -> writerMonad.of(i); // i -> ("", i)
      // flatMap(ofFunc, mValue) -> flatMap(i->("",i), ("mVal;", 5)) -> ("mVal;", 5)
      Kind<WriterKind<String, ?>, Integer> leftSide = writerMonad.flatMap(ofFunc, mValue);
      assertThat(runW(leftSide)).isEqualTo(runW(mValue));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      // Left Side: flatMap(g, flatMap(f, mValue))
      // inner = flatMap(f, mValue) -> flatMap(i->("f(i);","vi"), ("mVal;", 5)) -> ("mVal;f(5);",
      // "v5")
      Kind<WriterKind<String, ?>, String> innerFlatMap = writerMonad.flatMap(f, mValue);
      // flatMap(g, inner) -> flatMap(s->("g(s);",s+"!"), ("mVal;f(5);", "v5")) ->
      // ("mVal;f(5);g(v5);", "v5!")
      Kind<WriterKind<String, ?>, String> leftSide = writerMonad.flatMap(g, innerFlatMap);

      // Right Side: flatMap(a -> flatMap(g, f(a)), mValue)
      Function<Integer, Kind<WriterKind<String, ?>, String>> rightSideFunc =
          a -> {
            Kind<WriterKind<String, ?>, String> fa = f.apply(a); // ("f(a);", "va")
            return writerMonad.flatMap(
                g, fa); // flatMap(s->("g(s);",s+"!"), ("f(a);", "va")) -> ("f(a);g(va);", "va!")
          };
      // flatMap(rightSideFunc, mValue) -> flatMap(a -> ("f(a);g(va);", "va!"), ("mVal;", 5))
      // -> ("mVal;" + "f(5);g(v5);", "v5!")
      Kind<WriterKind<String, ?>, String> rightSide = writerMonad.flatMap(rightSideFunc, mValue);

      assertThat(runW(leftSide)).isEqualTo(runW(rightSide));
    }
  }

  // --- mapN Tests --- (Using default Applicative implementations)
  @Nested
  @DisplayName("mapN tests")
  class MapNTests {
    Kind<WriterKind<String, ?>, Integer> w1 = wrap(Writer.create("L1;", 1));
    Kind<WriterKind<String, ?>, String> w2 = wrap(Writer.create("L2;", "A"));
    Kind<WriterKind<String, ?>, Double> w3 = wrap(Writer.create("L3;", 1.5));
    Kind<WriterKind<String, ?>, Boolean> w4 = wrap(Writer.create("L4;", true));

    @Test
    void map2_combinesLogsAndValues() {
      Kind<WriterKind<String, ?>, String> result = writerMonad.map2(w1, w2, (i, s) -> s + i);
      Writer<String, String> w = runW(result);
      assertThat(w.log()).isEqualTo("L1;L2;");
      assertThat(w.value()).isEqualTo("A1");
    }

    @Test
    void map3_combinesLogsAndValues() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%s%d-%.1f", s, i, d);
      Kind<WriterKind<String, ?>, String> result = writerMonad.map3(w1, w2, w3, f3);
      Writer<String, String> w = runW(result);
      assertThat(w.log()).isEqualTo("L1;L2;L3;");
      assertThat(w.value()).isEqualTo("A1-1.5");
    }

    @Test
    void map4_combinesLogsAndValues() {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%s%d-%.1f-%b", s, i, d, b);
      Kind<WriterKind<String, ?>, String> result = writerMonad.map4(w1, w2, w3, w4, f4);
      Writer<String, String> w = runW(result);
      assertThat(w.log()).isEqualTo("L1;L2;L3;L4;");
      assertThat(w.value()).isEqualTo("A1-1.5-true");
    }
  }
}
