// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.eitherf.EitherFFunctor;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.inject.Inject;
import org.higherkindedj.hkt.inject.InjectInstances;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for effect handler infrastructure.
 *
 * <p>Measures the overhead introduced by the effect handler layers:
 *
 * <ul>
 *   <li>EitherF dispatch cost during interpretation
 *   <li>Free.translate overhead for lifting programs into composed types
 *   <li>HandleError wrapping overhead
 *   <li>Interpreters.combine vs single-effect interpretation
 *   <li>ProgramAnalyser traversal cost
 * </ul>
 *
 * <p>Run with: {@code ./gradlew jmh -Pincludes=".*FreeEffectBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class FreeEffectBenchmark {

  @Param({"1", "10", "50"})
  private int chainDepth;

  // Single-effect infrastructure
  private IdMonad idMonad;
  private Functor<IdKind.Witness> idFunctor;

  // Two-effect composition infrastructure
  private Functor<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>> composedFunctor;
  private Natural<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>, IdKind.Witness>
      combinedInterpreter;
  private Inject<IdKind.Witness, EitherFKind.Witness<IdKind.Witness, IdKind.Witness>> leftInject;

  // Shared interpreter
  private Natural<IdKind.Witness, IdKind.Witness> identityInterp;

  // Pre-built programs
  private Free<IdKind.Witness, Integer> singleEffectProgram;
  private Free<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>, Integer> composedProgram;
  private Free<IdKind.Witness, Integer> programWithHandleError;

  @Setup
  public void setup() {
    idMonad = IdMonad.instance();
    idFunctor =
        new Functor<>() {
          @Override
          public <A, B> Kind<IdKind.Witness, B> map(
              Function<? super A, ? extends B> f, Kind<IdKind.Witness, A> fa) {
            return idMonad.map(f, fa);
          }
        };

    composedFunctor = EitherFFunctor.of(idFunctor, idFunctor);

    identityInterp =
        new Natural<>() {
          @Override
          public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
            return fa;
          }
        };
    combinedInterpreter = Interpreters.combine(identityInterp, identityInterp);
    leftInject = InjectInstances.injectLeft();

    // Build single-effect program: chain of liftF + flatMap
    singleEffectProgram = buildSingleEffectProgram(chainDepth);
    composedProgram = buildComposedProgram(chainDepth);
    programWithHandleError = buildProgramWithHandleError(chainDepth);
  }

  @SuppressWarnings("unchecked")
  private Free<IdKind.Witness, Integer> buildSingleEffectProgram(int depth) {
    Free<IdKind.Witness, Integer> program = Free.liftF(new Id<>(0), idFunctor);
    for (int i = 0; i < depth; i++) {
      program = program.flatMap(n -> Free.liftF(new Id<>(n + 1), idFunctor));
    }
    return program;
  }

  @SuppressWarnings("unchecked")
  private Free<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>, Integer> buildComposedProgram(
      int depth) {
    Free<IdKind.Witness, Integer> singleProgram = buildSingleEffectProgram(depth);
    return Free.translate(singleProgram, leftInject::inject, composedFunctor);
  }

  @SuppressWarnings("unchecked")
  private Free<IdKind.Witness, Integer> buildProgramWithHandleError(int depth) {
    Free<IdKind.Witness, Integer> program = buildSingleEffectProgram(depth);
    return program.handleError(Throwable.class, _ -> Free.pure(-1));
  }

  // ========== Benchmarks ==========

  /** Baseline: single-effect foldMap. */
  @Benchmark
  public void singleEffectFoldMap(Blackhole bh) {
    Kind<IdKind.Witness, Integer> result = singleEffectProgram.foldMap(identityInterp, idMonad);
    bh.consume(result);
  }

  /** EitherF dispatch: composed effect foldMap via Interpreters.combine. */
  @Benchmark
  public void composedEffectFoldMap(Blackhole bh) {
    Kind<IdKind.Witness, Integer> result = composedProgram.foldMap(combinedInterpreter, idMonad);
    bh.consume(result);
  }

  /** Free.translate overhead: translating a single-effect program into a composed type. */
  @Benchmark
  public void translateProgram(Blackhole bh) {
    Free<EitherFKind.Witness<IdKind.Witness, IdKind.Witness>, Integer> translated =
        Free.translate(singleEffectProgram, leftInject::inject, composedFunctor);
    bh.consume(translated);
  }

  /** HandleError wrapping overhead. */
  @Benchmark
  public void handleErrorFoldMap(Blackhole bh) {
    Kind<IdKind.Witness, Integer> result = programWithHandleError.foldMap(identityInterp, idMonad);
    bh.consume(result);
  }

  /** ProgramAnalyser traversal cost. */
  @Benchmark
  public void programAnalysis(Blackhole bh) {
    var analysis = ProgramAnalyser.analyse(singleEffectProgram);
    bh.consume(analysis);
  }

  /** ProgramAnalyser traversal of composed program. */
  @Benchmark
  public void programAnalysisComposed(Blackhole bh) {
    var analysis = ProgramAnalyser.analyse(composedProgram);
    bh.consume(analysis);
  }

  /** Program construction cost: building a single-effect program. */
  @Benchmark
  public void programConstruction(Blackhole bh) {
    Free<IdKind.Witness, Integer> program = buildSingleEffectProgram(chainDepth);
    bh.consume(program);
  }
}
