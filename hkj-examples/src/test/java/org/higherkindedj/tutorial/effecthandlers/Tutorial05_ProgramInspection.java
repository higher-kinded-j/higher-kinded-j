// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 05: Program Inspection
 *
 * <p>Because Free programs are data structures, they can be traversed and analysed before
 * execution. {@code ProgramAnalyser} counts instructions, recovery points, and parallel scopes
 * without running the program.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@code ProgramAnalyser.analyse(program)}: traverses the program tree
 *   <li>{@code ProgramAnalysis}: record with counts for suspend, recovery, parallel, flatMap
 *   <li>Counts are lower bounds: FlatMapped continuations are opaque
 *   <li>{@code hasOpaqueRegions()}: true when the analysis cannot see into flatMap continuations
 * </ul>
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial05_ProgramInspection {

  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  private static final Functor<IdKind.Witness> ID_FUNCTOR =
      new Functor<>() {
        @Override
        public <A, B> Kind<IdKind.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<IdKind.Witness, A> fa) {
          return IdMonad.instance().map(f, fa);
        }
      };

  /**
   * Exercise 1: Counting instructions
   *
   * <p>{@code Free.liftF} creates a Suspend node (one instruction). Chaining with flatMap adds a
   * FlatMapped node.
   *
   * <p>Task: Analyse a program with one instruction and verify the count
   */
  @Test
  void exercise1_countInstructions() {
    Free<IdKind.Witness, String> program = Free.liftF(new Id<>("hello"), ID_FUNCTOR);

    // TODO: Use ProgramAnalyser.analyse to get the analysis
    ProgramAnalysis analysis = answerRequired();

    assertThat(analysis.suspendCount()).isEqualTo(1);
    assertThat(analysis.totalInstructions()).isEqualTo(1);
  }

  /**
   * Exercise 2: Detecting opaque regions
   *
   * <p>When a program uses flatMap, the continuation is a function that cannot be inspected. The
   * analyser marks this with {@code hasOpaqueRegions = true}.
   *
   * <p>Task: Create a program with a flatMap and verify opaque regions are detected
   */
  @Test
  void exercise2_detectOpaqueRegions() {
    // TODO: Create a program with liftF followed by flatMap
    // Hint: Free.liftF(new Id<>("a"), ID_FUNCTOR).flatMap(s -> Free.pure(s + "b"))
    Free<IdKind.Witness, String> program = answerRequired();

    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.hasOpaqueRegions()).isTrue();
    assertThat(analysis.flatMapDepth()).isGreaterThan(0);
  }

  /**
   * Exercise 3: Counting parallel scopes
   *
   * <p>{@code Free.Ap} wraps a {@code FreeAp} for applicative (parallel) composition. The analyser
   * counts these as parallel scopes.
   *
   * <p>Task: Create a program with an Ap node and verify the parallel scope count
   */
  @Test
  void exercise3_countParallelScopes() {
    // TODO: Create a Free.Ap node wrapping a FreeAp.pure
    // Hint: new Free.Ap<>(FreeAp.pure("parallel"))
    Free<IdKind.Witness, String> program = answerRequired();

    ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

    assertThat(analysis.parallelScopes()).isEqualTo(1);
  }
}
