// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.boundary;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TestBoundary}.
 *
 * <p>Uses a minimal in-test DSL to avoid depending on external modules.
 */
@DisplayName("TestBoundary Tests")
class TestBoundaryTest {

  // ===== Minimal test DSL (same structure as EffectBoundaryTest) =====

  sealed interface TestOp<A> {
    record Store<A>(String value, Function<String, A> k) implements TestOp<A> {}
  }

  interface TestOpKind<A> extends Kind<TestOpKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
      private Witness() {}
    }
  }

  record TestOpHolder<A>(TestOp<A> op) implements TestOpKind<A> {}

  static <A> Kind<TestOpKind.Witness, A> widen(TestOp<A> op) {
    return new TestOpHolder<>(op);
  }

  @SuppressWarnings("unchecked")
  static <A> TestOp<A> narrow(Kind<TestOpKind.Witness, A> kind) {
    return ((TestOpHolder<A>) kind).op();
  }

  static final Functor<TestOpKind.Witness> TEST_FUNCTOR =
      new Functor<>() {
        @Override
        public <A, B> Kind<TestOpKind.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<TestOpKind.Witness, A> fa) {
          TestOp<A> op = narrow(fa);
          return switch (op) {
            case TestOp.Store<A> s -> widen(new TestOp.Store<>(s.value(), s.k().andThen(f)));
          };
        }
      };

  static Free<TestOpKind.Witness, String> store(String value) {
    return Free.liftF(widen(new TestOp.Store<>(value, Function.identity())), TEST_FUNCTOR);
  }

  // ===== Recording interpreter (Id target) =====

  static class RecordingIdInterpreter implements Natural<TestOpKind.Witness, IdKind.Witness> {
    private final List<String> stored = new ArrayList<>();

    @Override
    public <A> Kind<IdKind.Witness, A> apply(Kind<TestOpKind.Witness, A> fa) {
      TestOp<A> op = narrow(fa);
      return switch (op) {
        case TestOp.Store<A> s -> {
          stored.add(s.value());
          @SuppressWarnings("unchecked")
          A result = (A) s.k().apply(s.value().toUpperCase());
          yield Id.of(result);
        }
      };
    }

    List<String> stored() {
      return stored;
    }
  }

  // ===== Tests =====

  private final RecordingIdInterpreter interpreter = new RecordingIdInterpreter();
  private final TestBoundary<TestOpKind.Witness> boundary = TestBoundary.of(interpreter);

  @Nested
  @DisplayName("of() - Factory")
  class FactoryTests {

    @Test
    @DisplayName("Should create boundary from interpreter")
    void shouldCreateBoundary() {
      assertThat(boundary).isNotNull();
      assertThat(boundary.interpreter()).isSameAs(interpreter);
    }

    @Test
    @DisplayName("Should reject null interpreter")
    void shouldRejectNullInterpreter() {
      assertThatNullPointerException().isThrownBy(() -> TestBoundary.of(null));
    }
  }

  @Nested
  @DisplayName("run(Free) - Pure Execution")
  class RunFreeTests {

    @Test
    @DisplayName("Should execute pure program and return value")
    void shouldExecutePureProgram() {
      Free<TestOpKind.Witness, String> program = Free.pure("hello");
      assertThat(boundary.run(program)).isEqualTo("hello");
    }

    @Test
    @DisplayName("Should interpret effect operations purely")
    void shouldInterpretPurely() {
      Free<TestOpKind.Witness, String> program = store("hello");
      String result = boundary.run(program);
      assertThat(result).isEqualTo("HELLO");
      assertThat(interpreter.stored()).containsExactly("hello");
    }

    @Test
    @DisplayName("Should chain multiple effects via flatMap")
    void shouldChainEffects() {
      Free<TestOpKind.Witness, String> program =
          store("hello").flatMap(h -> store("world").map(w -> h + " " + w));
      String result = boundary.run(program);
      assertThat(result).isEqualTo("HELLO WORLD");
      assertThat(interpreter.stored()).containsExactly("hello", "world");
    }

    @Test
    @DisplayName("Should reject null program")
    void shouldRejectNullProgram() {
      assertThatNullPointerException()
          .isThrownBy(() -> boundary.run((Free<TestOpKind.Witness, String>) null));
    }
  }

  @Nested
  @DisplayName("run(FreePath) - FreePath Convenience")
  class RunFreePathTests {

    @Test
    @DisplayName("Should interpret FreePath programs")
    void shouldInterpretFreePath() {
      FreePath<TestOpKind.Witness, String> program = FreePath.of(store("test"), TEST_FUNCTOR);
      String result = boundary.run(program);
      assertThat(result).isEqualTo("TEST");
      assertThat(interpreter.stored()).containsExactly("test");
    }

    @Test
    @DisplayName("Should reject null FreePath program")
    void shouldRejectNullFreePath() {
      assertThatNullPointerException()
          .isThrownBy(() -> boundary.run((FreePath<TestOpKind.Witness, String>) null));
    }
  }

  @Nested
  @DisplayName("analyse() - Program Structure Analysis")
  class AnalyseTests {

    @Test
    @DisplayName("Should count instructions in simple program")
    void shouldCountInstructions() {
      Free<TestOpKind.Witness, String> program = store("hello");
      ProgramAnalysis analysis = boundary.analyse(program);

      assertThat(analysis.totalInstructions()).isEqualTo(1);
      assertThat(analysis.effectsUsed()).hasSize(1);
      assertThat(analysis.recoveryPoints()).isZero();
      assertThat(analysis.applicativeBlocks()).isZero();
    }

    @Test
    @DisplayName("Should count instructions in chained program")
    void shouldCountChainedInstructions() {
      Free<TestOpKind.Witness, String> program =
          store("a").flatMap(a -> store("b").flatMap(b -> store("c").map(c -> a + b + c)));
      ProgramAnalysis analysis = boundary.analyse(program);

      // FlatMapped wraps one Suspend sub, the continuation creates more at execution time
      // The static walk only sees the outer Suspend
      assertThat(analysis.totalInstructions()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should return empty analysis for pure program")
    void shouldReturnEmptyForPure() {
      Free<TestOpKind.Witness, String> program = Free.pure("pure");
      ProgramAnalysis analysis = boundary.analyse(program);

      assertThat(analysis.totalInstructions()).isZero();
      assertThat(analysis.effectsUsed()).isEmpty();
      assertThat(analysis.recoveryPoints()).isZero();
      assertThat(analysis.applicativeBlocks()).isZero();
    }

    @Test
    @DisplayName("Should count recovery points for HandleError")
    void shouldCountRecoveryPoints() {
      Free<TestOpKind.Witness, String> program =
          store("risky").handleError(RuntimeException.class, e -> Free.pure("recovered"));
      ProgramAnalysis analysis = boundary.analyse(program);

      assertThat(analysis.recoveryPoints()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should count applicative blocks for Ap nodes")
    void shouldCountApplicativeBlocks() {
      Free<TestOpKind.Witness, String> program = new Free.Ap<>(FreeAp.pure("ap-value"));
      ProgramAnalysis analysis = boundary.analyse(program);

      assertThat(analysis.applicativeBlocks()).isEqualTo(1);
      assertThat(analysis.totalInstructions()).isZero();
      assertThat(analysis.recoveryPoints()).isZero();
    }

    @Test
    @DisplayName("Should count FlatMapped sub-programs")
    void shouldCountFlatMappedSub() {
      // FlatMapped wraps a Suspend sub-program
      Free<TestOpKind.Witness, String> program = store("inner").map(s -> s + "!");
      ProgramAnalysis analysis = boundary.analyse(program);

      assertThat(analysis.totalInstructions()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject null program")
    void shouldRejectNullProgram() {
      assertThatNullPointerException().isThrownBy(() -> boundary.analyse(null));
    }
  }

  @Nested
  @DisplayName("Pure Execution Properties")
  class PureExecutionTests {

    @Test
    @DisplayName("Should execute deterministically")
    void shouldBeDeterministic() {
      var interp1 = new RecordingIdInterpreter();
      var interp2 = new RecordingIdInterpreter();
      var b1 = TestBoundary.of(interp1);
      var b2 = TestBoundary.of(interp2);

      Free<TestOpKind.Witness, String> program = store("deterministic");

      String r1 = b1.run(program);
      String r2 = b2.run(program);

      assertThat(r1).isEqualTo(r2);
      assertThat(interp1.stored()).isEqualTo(interp2.stored());
    }

    @Test
    @DisplayName("Should track all effect invocations")
    void shouldTrackEffects() {
      Free<TestOpKind.Witness, String> program =
          store("a").flatMap(a -> store("b").flatMap(b -> store("c").map(c -> a + b + c)));

      boundary.run(program);
      assertThat(interpreter.stored()).containsExactly("a", "b", "c");
    }
  }
}
