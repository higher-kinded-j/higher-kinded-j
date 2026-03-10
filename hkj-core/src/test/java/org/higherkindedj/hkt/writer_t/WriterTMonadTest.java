// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WriterTMonad Test Suite")
class WriterTMonadTest {

  /** String monoid with concatenation. */
  private static final Monoid<String> STRING_MONOID =
      new Monoid<>() {
        @Override
        public String empty() {
          return "";
        }

        @Override
        public String combine(String a, String b) {
          return a + b;
        }
      };

  private Monad<IdKind.Witness> idMonad;
  private MonadWriter<WriterTKind.Witness<IdKind.Witness, String>, String> writerMonad;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
    writerMonad = new WriterTMonad<>(idMonad, STRING_MONOID);
  }

  /** Extract the Pair<A, W> from a WriterT wrapped in Id. */
  private <A> Pair<A, String> run(Kind<WriterTKind.Witness<IdKind.Witness, String>, A> kind) {
    WriterT<IdKind.Witness, String, A> writerT = WRITER_T.narrow(kind);
    Id<Pair<A, String>> id = IdKindHelper.ID.narrow(writerT.run());
    return id.value();
  }

  @Nested
  @DisplayName("Of Tests")
  class OfTests {

    @Test
    @DisplayName("of should lift value with empty output")
    void of_shouldLiftValueWithEmptyOutput() {
      var result = writerMonad.of(42);
      var pair = run(result);
      assertThat(pair.first()).isEqualTo(42);
      assertThat(pair.second()).isEmpty();
    }

    @Test
    @DisplayName("of with null should work")
    void of_withNull() {
      var result = writerMonad.of(null);
      var pair = run(result);
      assertThat(pair.first()).isNull();
      assertThat(pair.second()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Map Tests")
  class MapTests {

    @Test
    @DisplayName("map should transform value and preserve output")
    void map_shouldTransformValue() {
      var initial = writerMonad.of(10);
      var result = writerMonad.map(x -> x * 2, initial);
      var pair = run(result);
      assertThat(pair.first()).isEqualTo(20);
      assertThat(pair.second()).isEmpty();
    }

    @Test
    @DisplayName("map should preserve existing output")
    void map_shouldPreserveOutput() {
      var told = writerMonad.flatMap(_ -> writerMonad.of(42), writerMonad.tell("log:"));
      var result = writerMonad.map(x -> x + 1, told);
      var pair = run(result);
      assertThat(pair.first()).isEqualTo(43);
      assertThat(pair.second()).isEqualTo("log:");
    }
  }

  @Nested
  @DisplayName("FlatMap Tests")
  class FlatMapTests {

    @Test
    @DisplayName("flatMap should sequence computations and combine output")
    void flatMap_shouldSequenceAndCombineOutput() {
      var step1 = writerMonad.flatMap(_ -> writerMonad.of(10), writerMonad.tell("step1,"));
      var step2 = writerMonad.flatMap(_ -> writerMonad.of(20), writerMonad.tell("step2,"));
      var composed = writerMonad.flatMap(_ -> step2, step1);

      var pair = run(composed);
      assertThat(pair.first()).isEqualTo(20);
      assertThat(pair.second()).isEqualTo("step1,step2,");
    }

    @Test
    @DisplayName("flatMap should thread value through")
    void flatMap_shouldThreadValue() {
      var result =
          writerMonad.flatMap(
              x -> writerMonad.flatMap(_ -> writerMonad.of(x * 2), writerMonad.tell("doubled,")),
              writerMonad.of(5));

      var pair = run(result);
      assertThat(pair.first()).isEqualTo(10);
      assertThat(pair.second()).isEqualTo("doubled,");
    }
  }

  @Nested
  @DisplayName("Ap Tests")
  class ApTests {

    @Test
    @DisplayName("ap should apply function and combine output")
    void ap_shouldApplyAndCombine() {
      Kind<WriterTKind.Witness<IdKind.Witness, String>, Function<Integer, String>> ff =
          writerMonad.flatMap(
              _ -> writerMonad.of((Function<Integer, String>) (x -> "v" + x)),
              writerMonad.tell("func,"));
      var fa = writerMonad.flatMap(_ -> writerMonad.of(42), writerMonad.tell("arg,"));

      var result = writerMonad.ap(ff, fa);
      var pair = run(result);
      assertThat(pair.first()).isEqualTo("v42");
      assertThat(pair.second()).isEqualTo("func,arg,");
    }
  }

  @Nested
  @DisplayName("Tell Tests")
  class TellTests {

    @Test
    @DisplayName("tell should append to output and return Unit")
    void tell_shouldAppendOutput() {
      var result = writerMonad.tell("hello");
      var pair = run(result);
      assertThat(pair.first()).isEqualTo(Unit.INSTANCE);
      assertThat(pair.second()).isEqualTo("hello");
    }

    @Test
    @DisplayName("tell should accumulate in order")
    void tell_shouldAccumulateInOrder() {
      var composed =
          writerMonad.flatMap(_ -> writerMonad.tell("world"), writerMonad.tell("hello "));
      var pair = run(composed);
      assertThat(pair.second()).isEqualTo("hello world");
    }
  }

  @Nested
  @DisplayName("Listen Tests")
  class ListenTests {

    @Test
    @DisplayName("listen should pair value with accumulated output")
    void listen_shouldPairValueWithOutput() {
      var computation = writerMonad.flatMap(_ -> writerMonad.of(42), writerMonad.tell("log"));
      var result = writerMonad.listen(computation);
      var pair = run(result);

      Pair<Integer, String> innerPair = pair.first();
      assertThat(innerPair.first()).isEqualTo(42);
      assertThat(innerPair.second()).isEqualTo("log");
      // Output is preserved
      assertThat(pair.second()).isEqualTo("log");
    }

    @Test
    @DisplayName("listen with empty output")
    void listen_withEmptyOutput() {
      var computation = writerMonad.of(10);
      var result = writerMonad.listen(computation);
      var pair = run(result);

      assertThat(pair.first().first()).isEqualTo(10);
      assertThat(pair.first().second()).isEmpty();
      assertThat(pair.second()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Pass Tests")
  class PassTests {

    @Test
    @DisplayName("pass should apply function to output")
    void pass_shouldApplyFunctionToOutput() {
      // Create a computation that returns (value, function-to-transform-output)
      var computation =
          writerMonad.flatMap(
              _ ->
                  writerMonad.of(
                      Pair.<Integer, Function<String, String>>of(42, String::toUpperCase)),
              writerMonad.tell("hello"));

      var result = writerMonad.pass(computation);
      var pair = run(result);
      assertThat(pair.first()).isEqualTo(42);
      assertThat(pair.second()).isEqualTo("HELLO");
    }
  }

  @Nested
  @DisplayName("Listens Tests")
  class ListensTests {

    @Test
    @DisplayName("listens should transform the output in the pair")
    void listens_shouldTransformOutputInPair() {
      var computation = writerMonad.flatMap(_ -> writerMonad.of(42), writerMonad.tell("hello"));
      var result = writerMonad.listens(String::length, computation);
      var pair = run(result);

      assertThat(pair.first().first()).isEqualTo(42);
      assertThat(pair.first().second()).isEqualTo(5); // "hello".length()
      assertThat(pair.second()).isEqualTo("hello"); // output preserved
    }
  }

  @Nested
  @DisplayName("Censor Tests")
  class CensorTests {

    @Test
    @DisplayName("censor should modify output")
    void censor_shouldModifyOutput() {
      var computation = writerMonad.flatMap(_ -> writerMonad.of(42), writerMonad.tell("hello"));
      var result = writerMonad.censor(String::toUpperCase, computation);
      var pair = run(result);

      assertThat(pair.first()).isEqualTo(42);
      assertThat(pair.second()).isEqualTo("HELLO");
    }
  }

  @Nested
  @DisplayName("MonadWriter Laws")
  class LawTests {

    @Test
    @DisplayName("Tell-empty: tell(empty) == of(Unit.INSTANCE)")
    void tellEmpty() {
      var leftSide = writerMonad.tell("");
      var rightSide = writerMonad.of(Unit.INSTANCE);

      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }

    @Test
    @DisplayName("Tell-combine: flatMap(_ -> tell(b), tell(a)) == tell(a <> b)")
    void tellCombine() {
      var leftSide = writerMonad.flatMap(_ -> writerMonad.tell("b"), writerMonad.tell("a"));
      var rightSide = writerMonad.tell("ab");

      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }

    @Test
    @DisplayName("Listen-tell: listen(tell(w)) returns (Unit, w) with output w")
    void listenTell() {
      var listened = writerMonad.listen(writerMonad.tell("log"));
      var pair = run(listened);

      assertThat(pair.first().first()).isEqualTo(Unit.INSTANCE);
      assertThat(pair.first().second()).isEqualTo("log");
      assertThat(pair.second()).isEqualTo("log");
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawTests {

    @Test
    @DisplayName("Left Identity: flatMap(f, of(a)) == f(a)")
    void leftIdentity() {
      Function<Integer, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>> f =
          x -> {
            var told = writerMonad.tell("f(" + x + ")");
            return writerMonad.flatMap(_ -> writerMonad.of("result:" + x), told);
          };

      var leftSide = writerMonad.flatMap(f, writerMonad.of(5));
      var rightSide = f.apply(5);
      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }

    @Test
    @DisplayName("Right Identity: flatMap(of, m) == m")
    void rightIdentity() {
      var m = writerMonad.flatMap(_ -> writerMonad.of(42), writerMonad.tell("log"));
      var result = writerMonad.flatMap(x -> writerMonad.of(x), m);
      assertThat(run(result)).isEqualTo(run(m));
    }

    @Test
    @DisplayName("Associativity")
    void associativity() {
      Function<Integer, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>> f =
          x -> writerMonad.flatMap(_ -> writerMonad.of("s" + x), writerMonad.tell("f,"));
      Function<String, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>> g =
          s -> writerMonad.flatMap(_ -> writerMonad.of(s + "!"), writerMonad.tell("g,"));

      var m = writerMonad.of(5);
      var leftSide = writerMonad.flatMap(g, writerMonad.flatMap(f, m));
      var rightSide = writerMonad.flatMap(a -> writerMonad.flatMap(g, f.apply(a)), m);
      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }
  }

  @Nested
  @DisplayName("LiftF Tests")
  class LiftFTests {

    @Test
    @DisplayName("liftF should lift outer monad value with empty output")
    void liftF_shouldLiftWithEmptyOutput() {
      Kind<IdKind.Witness, Integer> idValue = IdKindHelper.ID.widen(new Id<>(42));
      var writerT = WriterT.liftF(idMonad, STRING_MONOID, idValue);
      var pair = run(WRITER_T.widen(writerT));
      assertThat(pair.first()).isEqualTo(42);
      assertThat(pair.second()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Writer Factory Tests")
  class WriterFactoryTests {

    @Test
    @DisplayName("writer should create with explicit value and output")
    void writer_shouldCreateWithValueAndOutput() {
      var writerT = WriterT.writer(idMonad, 42, "initial-log");
      var pair = run(WRITER_T.widen(writerT));
      assertThat(pair.first()).isEqualTo(42);
      assertThat(pair.second()).isEqualTo("initial-log");
    }
  }

  @Nested
  @DisplayName("Null Tests")
  class NullTests {

    @Test
    @DisplayName("constructor should reject null outer monad")
    void constructor_shouldRejectNullOuterMonad() {
      assertThatThrownBy(() -> new WriterTMonad<>(null, STRING_MONOID))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor should reject null monoid")
    void constructor_shouldRejectNullMonoid() {
      assertThatThrownBy(() -> new WriterTMonad<>(idMonad, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("tell should reject null output")
    void tell_shouldRejectNullOutput() {
      assertThatThrownBy(() -> writerMonad.tell(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map should reject null function")
    void map_shouldRejectNullFunction() {
      assertThatThrownBy(() -> writerMonad.map(null, writerMonad.of(1)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap should reject null function")
    void flatMap_shouldRejectNullFunction() {
      assertThatThrownBy(() -> writerMonad.flatMap(null, writerMonad.of(1)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("WriterT.fromKind should reject null")
    void fromKind_shouldRejectNull() {
      assertThatThrownBy(() -> WriterT.fromKind(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("FromKind Tests")
  class FromKindTests {

    @Test
    @DisplayName("fromKind should create WriterT from Kind")
    void fromKind_shouldCreateWriterT() {
      Kind<IdKind.Witness, Pair<Integer, String>> inner =
          IdKindHelper.ID.widen(new Id<>(Pair.of(42, "log")));
      WriterT<IdKind.Witness, String, Integer> writerT = WriterT.fromKind(inner);
      var pair = run(WRITER_T.widen(writerT));
      assertThat(pair.first()).isEqualTo(42);
      assertThat(pair.second()).isEqualTo("log");
    }

    @Test
    @DisplayName("fromKind should preserve empty output")
    void fromKind_shouldPreserveEmptyOutput() {
      Kind<IdKind.Witness, Pair<String, String>> inner =
          IdKindHelper.ID.widen(new Id<>(Pair.of("value", "")));
      WriterT<IdKind.Witness, String, String> writerT = WriterT.fromKind(inner);
      var pair = run(WRITER_T.widen(writerT));
      assertThat(pair.first()).isEqualTo("value");
      assertThat(pair.second()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("multi-step workflow with output accumulation")
    void multiStepWorkflow() {
      // Simulate: validate input, process, record audit trail
      var workflow =
          writerMonad.flatMap(
              _ ->
                  writerMonad.flatMap(
                      _ ->
                          writerMonad.flatMap(
                              _ -> writerMonad.of("done"), writerMonad.tell("completed. ")),
                      writerMonad.tell("processing. ")),
              writerMonad.tell("started. "));

      var pair = run(workflow);
      assertThat(pair.first()).isEqualTo("done");
      assertThat(pair.second()).isEqualTo("started. processing. completed. ");
    }
  }
}
