// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link ProgramAnalyser} and {@link ProgramAnalysis}. */
@DisplayName("ProgramAnalyser")
class ProgramAnalyserTest {

  /** Simple functor for testing. */
  private static final Functor<IdKind.Witness> ID_FUNCTOR =
      new Functor<>() {
        @Override
        public <A, B> Kind<IdKind.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<IdKind.Witness, A> fa) {
          return IdMonad.instance().map(f, fa);
        }
      };

  @Nested
  @DisplayName("ProgramAnalyser.analyse")
  class AnalyseTests {

    @Test
    @DisplayName("should return empty analysis for Pure")
    void pureReturnsEmpty() {
      Free<IdKind.Witness, String> program = Free.pure("hello");
      ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

      assertThat(analysis.suspendCount()).isZero();
      assertThat(analysis.recoveryPoints()).isZero();
      assertThat(analysis.parallelScopes()).isZero();
      assertThat(analysis.flatMapDepth()).isZero();
      assertThat(analysis.hasOpaqueRegions()).isFalse();
    }

    @Test
    @DisplayName("should count Suspend nodes")
    void countsSuspend() {
      Free<IdKind.Witness, String> program = Free.liftF(new Id<>("hello"), ID_FUNCTOR);
      ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

      assertThat(analysis.suspendCount()).isEqualTo(1);
      assertThat(analysis.recoveryPoints()).isZero();
      assertThat(analysis.parallelScopes()).isZero();
      assertThat(analysis.flatMapDepth()).isZero();
      assertThat(analysis.hasOpaqueRegions()).isFalse();
    }

    @Test
    @DisplayName("should count FlatMapped nodes and mark opaque regions")
    void countsFlatMapped() {
      Free<IdKind.Witness, String> program =
          Free.liftF(new Id<>("hello"), ID_FUNCTOR).flatMap(s -> Free.pure(s + " world"));
      ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

      assertThat(analysis.suspendCount()).isEqualTo(1);
      assertThat(analysis.flatMapDepth()).isEqualTo(1);
      assertThat(analysis.hasOpaqueRegions()).isTrue();
    }

    @Test
    @DisplayName("should count HandleError nodes as recovery points")
    void countsHandleError() {
      Free<IdKind.Witness, String> program =
          Free.liftF(new Id<>("hello"), ID_FUNCTOR)
              .handleError(Throwable.class, _ -> Free.pure("recovered"));
      ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

      assertThat(analysis.suspendCount()).isEqualTo(1);
      assertThat(analysis.recoveryPoints()).isEqualTo(1);
    }

    @Test
    @DisplayName("should count nested HandleError correctly")
    void countsNestedHandleError() {
      Free<IdKind.Witness, String> inner =
          Free.liftF(new Id<>("hello"), ID_FUNCTOR)
              .handleError(Throwable.class, _ -> Free.pure("inner-recovered"));
      Free<IdKind.Witness, String> outer =
          inner.handleError(Throwable.class, _ -> Free.pure("outer-recovered"));

      ProgramAnalysis analysis = ProgramAnalyser.analyse(outer);

      assertThat(analysis.recoveryPoints()).isEqualTo(2);
    }

    @Test
    @DisplayName("should count Ap nodes as parallel scopes")
    void countsAp() {
      FreeAp<IdKind.Witness, String> freeAp = FreeAp.pure("parallel");
      Free<IdKind.Witness, String> program = new Free.Ap<>(freeAp);
      ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

      assertThat(analysis.parallelScopes()).isEqualTo(1);
      assertThat(analysis.suspendCount()).isZero();
      assertThat(analysis.recoveryPoints()).isZero();
      assertThat(analysis.flatMapDepth()).isZero();
      assertThat(analysis.hasOpaqueRegions()).isFalse();
    }

    @Test
    @DisplayName("should reject null program")
    void rejectsNull() {
      assertThatNullPointerException().isThrownBy(() -> ProgramAnalyser.analyse(null));
    }

    @Test
    @DisplayName("private constructor should not be instantiable")
    void privateConstructor() throws NoSuchMethodException {
      Constructor<ProgramAnalyser> constructor = ProgramAnalyser.class.getDeclaredConstructor();
      assertThat(constructor.canAccess(null)).isFalse();
      constructor.setAccessible(true);
      try {
        constructor.newInstance();
      } catch (InvocationTargetException | InstantiationException | IllegalAccessException _) {
        // Expected — private constructor
      }
    }
  }

  @Nested
  @DisplayName("ProgramAnalysis")
  class ProgramAnalysisTests {

    @Test
    @DisplayName("should combine analyses correctly")
    void combinesAnalyses() {
      ProgramAnalysis a = new ProgramAnalysis(2, 1, 0, 3, false);
      ProgramAnalysis b = new ProgramAnalysis(1, 0, 1, 1, true);

      ProgramAnalysis combined = a.combine(b);

      assertThat(combined.suspendCount()).isEqualTo(3);
      assertThat(combined.recoveryPoints()).isEqualTo(1);
      assertThat(combined.parallelScopes()).isEqualTo(1);
      assertThat(combined.flatMapDepth()).isEqualTo(4);
      assertThat(combined.hasOpaqueRegions()).isTrue();
    }

    @Test
    @DisplayName("should compute total instructions")
    void computesTotalInstructions() {
      ProgramAnalysis analysis = new ProgramAnalysis(5, 2, 3, 1, false);

      assertThat(analysis.totalInstructions()).isEqualTo(8);
    }

    @Test
    @DisplayName("EMPTY should be the identity for combine")
    void emptyIsIdentity() {
      ProgramAnalysis a = new ProgramAnalysis(2, 1, 1, 1, true);

      assertThat(a.combine(ProgramAnalysis.EMPTY)).isEqualTo(a);
      assertThat(ProgramAnalysis.EMPTY.combine(a)).isEqualTo(a);
    }

    @Test
    @DisplayName("combine should reject null")
    void combineRejectsNull() {
      ProgramAnalysis a = new ProgramAnalysis(1, 0, 0, 0, false);

      assertThatNullPointerException().isThrownBy(() -> a.combine(null));
    }

    @Test
    @DisplayName("toString should include all counts without opaque marker")
    void toStringWithoutOpaque() {
      ProgramAnalysis analysis = new ProgramAnalysis(3, 1, 2, 4, false);

      String result = analysis.toString();

      assertThat(result).isEqualTo("ProgramAnalysis[3 suspend, 1 recovery, 2 parallel, 4 flatMap]");
    }

    @Test
    @DisplayName("toString should include opaque marker when present")
    void toStringWithOpaque() {
      ProgramAnalysis analysis = new ProgramAnalysis(1, 0, 0, 1, true);

      String result = analysis.toString();

      assertThat(result)
          .isEqualTo(
              "ProgramAnalysis[1 suspend, 0 recovery, 0 parallel, 1 flatMap,"
                  + " opaque regions present]");
    }

    @Test
    @DisplayName("record equality should work correctly")
    void recordEquality() {
      ProgramAnalysis a = new ProgramAnalysis(1, 2, 3, 4, true);
      ProgramAnalysis b = new ProgramAnalysis(1, 2, 3, 4, true);
      ProgramAnalysis c = new ProgramAnalysis(1, 2, 3, 4, false);

      assertThat(a).isEqualTo(b);
      assertThat(a).hasSameHashCodeAs(b);
      assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("EMPTY should have all zero counts and zero total instructions")
    void emptyHasZeroCounts() {
      assertThat(ProgramAnalysis.EMPTY.suspendCount()).isZero();
      assertThat(ProgramAnalysis.EMPTY.recoveryPoints()).isZero();
      assertThat(ProgramAnalysis.EMPTY.parallelScopes()).isZero();
      assertThat(ProgramAnalysis.EMPTY.flatMapDepth()).isZero();
      assertThat(ProgramAnalysis.EMPTY.hasOpaqueRegions()).isFalse();
      assertThat(ProgramAnalysis.EMPTY.totalInstructions()).isZero();
    }

    @Test
    @DisplayName("combine with both hasOpaqueRegions false should remain false")
    void combineOpaqueRegionsBothFalse() {
      ProgramAnalysis a = new ProgramAnalysis(1, 0, 0, 0, false);
      ProgramAnalysis b = new ProgramAnalysis(0, 1, 0, 0, false);

      assertThat(a.combine(b).hasOpaqueRegions()).isFalse();
    }

    @Test
    @DisplayName("EMPTY toString should show all zeros")
    void emptyToString() {
      assertThat(ProgramAnalysis.EMPTY.toString())
          .isEqualTo("ProgramAnalysis[0 suspend, 0 recovery, 0 parallel, 0 flatMap]");
    }
  }
}
