// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.FreeFactory;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
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
 * JMH benchmarks for Free monad operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Cost of Free monad construction (pure, suspend, liftF)
 *   <li>Performance of map/flatMap chains
 *   <li>foldMap interpretation overhead using Trampoline internally
 *   <li>Stack safety for deeply nested Free structures
 *   <li>Comparison with direct function composition
 * </ul>
 *
 * <p>Note: Free monads use Trampoline internally for stack-safe foldMap interpretation. These
 * benchmarks measure the combined overhead of Free structure construction and Trampoline-based
 * execution.
 *
 * <p>Run with: {@code ./gradlew jmh} or {@code ./gradlew :hkj-benchmarks:jmh}
 *
 * <p>Run specific benchmark: {@code ./gradlew jmh --includes=".*FreeBenchmark.*"}
 *
 * <p>Run with different depths: {@code ./gradlew jmh -Pjmh.benchmarkParameters.chainDepth=1000}
 *
 * <p>Run with GC profiling: {@code ./gradlew jmh -Pjmh.profilers=gc}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class FreeBenchmark {

  @Param({"50"})
  private int chainDepth;

  private Monad<IdKind.Witness> idMonad;
  private Function<Kind<IdKind.Witness, ?>, Kind<IdKind.Witness, ?>> identityTransform;
  private FreeFactory<IdKind.Witness> FREE;

  private Free<IdKind.Witness, Integer> simplePure;
  private Free<IdKind.Witness, Integer> simpleSuspend;

  @Setup
  public void setup() {
    idMonad = IdMonad.instance();
    identityTransform = kind -> kind;
    FREE = FreeFactory.withMonad(idMonad);

    simplePure = FREE.pure(42);
    Kind<IdKind.Witness, Free<IdKind.Witness, Integer>> suspendedId =
        IdKindHelper.ID.widen(Id.of(FREE.pure(10)));
    simpleSuspend = FREE.suspend(suspendedId);
  }

  // ==================== Construction Benchmarks ====================

  /**
   * Construction cost of Free.pure with simple value.
   *
   * <p>Measures overhead of creating a pure Free monad.
   */
  @Benchmark
  public Free<IdKind.Witness, Integer> constructPure() {
    return FREE.pure(42);
  }

  /**
   * Construction cost of Free.suspend with simple value.
   *
   * <p>Measures overhead of creating a suspended Free monad.
   */
  @Benchmark
  public Free<IdKind.Witness, Integer> constructSuspend() {
    Kind<IdKind.Witness, Free<IdKind.Witness, Integer>> inner =
        IdKindHelper.ID.widen(Id.of(FREE.pure(10)));
    return FREE.suspend(inner);
  }

  // ==================== Map/FlatMap Benchmarks ====================

  /**
   * Map operation construction (lazy - builds data structure).
   *
   * <p>Measures cost of building up Free chains without executing.
   */
  @Benchmark
  public Free<IdKind.Witness, Integer> mapConstruction() {
    return simplePure.map(x -> x + 1);
  }

  /**
   * Map operation with foldMap execution.
   *
   * <p>Measures cost of interpreting a mapped Free monad using Trampoline internally.
   */
  @Benchmark
  public Integer mapExecution() {
    Free<IdKind.Witness, Integer> mapped = simplePure.map(x -> x + 1);
    Kind<IdKind.Witness, Integer> result = mapped.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(result).value();
  }

  /** FlatMap operation construction. */
  @Benchmark
  public Free<IdKind.Witness, Integer> flatMapConstruction() {
    return simplePure.flatMap(x -> FREE.pure(x * 2));
  }

  /** FlatMap operation with foldMap execution. */
  @Benchmark
  public Integer flatMapExecution() {
    Free<IdKind.Witness, Integer> flatMapped = simplePure.flatMap(x -> FREE.pure(x * 2));
    Kind<IdKind.Witness, Integer> result = flatMapped.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(result).value();
  }

  // ==================== Chain Benchmarks (Stack Safety via Trampoline) ====================

  /**
   * Deep flatMap chain construction.
   *
   * <p>Measures cost of building deeply nested Free structures.
   */
  @Benchmark
  public Free<IdKind.Witness, Integer> deepChainConstruction() {
    Free<IdKind.Witness, Integer> result = simplePure;
    for (int i = 0; i < chainDepth; i++) {
      result = result.flatMap(x -> FREE.pure(x + 1));
    }
    return result;
  }

  /**
   * Deep flatMap chain execution with Trampoline-based foldMap.
   *
   * <p>Measures cost of interpreting deeply nested Free structures. This demonstrates the
   * stack-safe interpretation that leverages Trampoline internally.
   */
  @Benchmark
  public Integer deepChainExecution() {
    Free<IdKind.Witness, Integer> result = simplePure;
    for (int i = 0; i < chainDepth; i++) {
      result = result.flatMap(x -> FREE.pure(x + 1));
    }
    Kind<IdKind.Witness, Integer> interpreted = result.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(interpreted).value();
  }

  /**
   * Deep map chain construction.
   *
   * <p>Measures cost of building many consecutive map operations.
   */
  @Benchmark
  public Free<IdKind.Witness, Integer> deepMapChainConstruction() {
    Free<IdKind.Witness, Integer> result = simplePure;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Deep map chain execution.
   *
   * <p>Measures cost of interpreting many consecutive map operations.
   */
  @Benchmark
  public Integer deepMapChainExecution() {
    Free<IdKind.Witness, Integer> result = simplePure;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    Kind<IdKind.Witness, Integer> interpreted = result.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(interpreted).value();
  }

  // ==================== Comparison Benchmarks ====================

  /**
   * Baseline: Direct function composition.
   *
   * <p>Compares Free monad overhead to direct function application.
   */
  @Benchmark
  public Integer directComposition() {
    int result = 42;
    for (int i = 0; i < chainDepth; i++) {
      result += 1;
    }
    return result;
  }

  /**
   * Direct nested function calls (no Free overhead).
   *
   * <p>Measures what the code would do without Free monad abstraction.
   */
  @Benchmark
  public Integer directFunctionChain() {
    Function<Integer, Integer> addOne = x -> x + 1;
    Integer result = 42;
    for (int i = 0; i < chainDepth; i++) {
      result = addOne.apply(result);
    }
    return result;
  }

  // ==================== Real-World Pattern Benchmarks ====================

  /**
   * Mixed map/flatMap operations.
   *
   * <p>Simulates realistic programme construction patterns.
   */
  @Benchmark
  public Integer mixedOperations() {
    Free<IdKind.Witness, Integer> program =
        simplePure
            .map(x -> x + 1)
            .flatMap(x -> FREE.pure(x * 2))
            .map(x -> x - 5)
            .flatMap(x -> FREE.pure(x / 3))
            .map(x -> x + 10);

    Kind<IdKind.Witness, Integer> result = program.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(result).value();
  }

  /**
   * Programme construction without execution.
   *
   * <p>Measures cost of building Free programmes as data structures (lazy construction).
   */
  @Benchmark
  public Free<IdKind.Witness, Integer> programmeConstruction() {
    return simplePure
        .map(x -> x + 1)
        .flatMap(x -> FREE.pure(x * 2))
        .map(x -> x - 5)
        .flatMap(x -> FREE.pure(x / 3))
        .map(x -> x + 10);
  }

  /**
   * Multiple independent Free constructions.
   *
   * <p>Measures cost of creating multiple independent Free monads.
   */
  @Benchmark
  public void independentConstruction(Blackhole blackhole) {
    Free<IdKind.Witness, Integer> f1 = FREE.pure(1);
    Free<IdKind.Witness, Integer> f2 = FREE.pure(2).map(x -> x * 2);
    Free<IdKind.Witness, Integer> f3 = FREE.pure(3).flatMap(x -> FREE.pure(x + 1));
    blackhole.consume(f1);
    blackhole.consume(f2);
    blackhole.consume(f3);
  }

  /**
   * Sequential programme interpretation.
   *
   * <p>Measures cost of interpreting multiple independent Free programmes.
   */
  @Benchmark
  public void sequentialInterpretation(Blackhole blackhole) {
    Free<IdKind.Witness, Integer> f1 = FREE.pure(1).map(x -> x + 1);
    Free<IdKind.Witness, Integer> f2 = FREE.pure(2).flatMap(x -> FREE.pure(x * 2));
    Free<IdKind.Witness, Integer> f3 = FREE.pure(3).map(x -> x + 3);

    Integer r1 = IdKindHelper.ID.narrow(f1.foldMap(identityTransform, idMonad)).value();
    Integer r2 = IdKindHelper.ID.narrow(f2.foldMap(identityTransform, idMonad)).value();
    Integer r3 = IdKindHelper.ID.narrow(f3.foldMap(identityTransform, idMonad)).value();

    blackhole.consume(r1);
    blackhole.consume(r2);
    blackhole.consume(r3);
  }

  /**
   * Deeply nested flatMap with suspend.
   *
   * <p>Tests the overhead of suspend operations combined with flatMap chains.
   */
  @Benchmark
  public Integer suspendAndFlatMapChain() {
    Free<IdKind.Witness, Integer> program = simpleSuspend;
    for (int i = 0; i < chainDepth; i++) {
      program = program.flatMap(x -> FREE.pure(x + 1));
    }
    Kind<IdKind.Witness, Integer> result = program.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(result).value();
  }

  // ==================== Trampoline Integration Benchmarks ====================

  /**
   * Demonstrates Trampoline integration: pure value interpretation.
   *
   * <p>Measures overhead of Trampoline.done path in foldMap.
   */
  @Benchmark
  public Integer pureTrampolineIntegration() {
    // Pure values should be fast - minimal Trampoline overhead
    Kind<IdKind.Witness, Integer> result = simplePure.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(result).value();
  }

  /**
   * Demonstrates Trampoline integration: suspended value interpretation.
   *
   * <p>Measures overhead of Trampoline.done path for Suspend case.
   */
  @Benchmark
  public Integer suspendTrampolineIntegration() {
    Kind<IdKind.Witness, Integer> result = simpleSuspend.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(result).value();
  }

  /**
   * Demonstrates Trampoline integration: FlatMapped interpretation.
   *
   * <p>Measures overhead of Trampoline.defer path in foldMap.
   */
  @Benchmark
  public Integer flatMappedTrampolineIntegration() {
    Free<IdKind.Witness, Integer> flatMapped =
        simplePure.flatMap(x -> FREE.pure(x + 1)).flatMap(y -> FREE.pure(y * 2));
    Kind<IdKind.Witness, Integer> result = flatMapped.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(result).value();
  }

  /**
   * Stress test: Very deep chain to verify Trampoline stack safety.
   *
   * <p>This benchmark specifically tests that the Trampoline-based foldMap implementation handles
   * deep recursion without stack overflow.
   */
  @Benchmark
  public Integer stackSafetyStressTest() {
    Free<IdKind.Witness, Integer> result = simplePure;
    // Use chainDepth parameter to test various depths
    for (int i = 0; i < chainDepth; i++) {
      result = result.flatMap(x -> FREE.pure(x + 1));
    }
    Kind<IdKind.Witness, Integer> interpreted = result.foldMap(identityTransform, idMonad);
    return IdKindHelper.ID.narrow(interpreted).value();
  }
}
