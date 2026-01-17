// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Comparison benchmarks between VTaskPath (virtual threads) and IOPath (platform threads).
 *
 * <p>These benchmarks compare the two Effect Path types across:
 *
 * <ul>
 *   <li>Simple execution overhead
 *   <li>Map chain performance
 *   <li>Via/FlatMap chain performance
 *   <li>Deep recursion performance
 *   <li>Error handling overhead
 * </ul>
 *
 * <p>This helps quantify the relative overhead of virtual thread execution in VTaskPath compared to
 * direct platform thread execution in IOPath.
 *
 * <p>Expected results:
 *
 * <ul>
 *   <li>IOPath should have lower per-operation overhead for simple operations
 *   <li>VTaskPath should scale better under concurrent load (see VTaskParBenchmark)
 *   <li>Both should have similar composition overhead
 * </ul>
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*VTaskPathVsIOPathBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class VTaskPathVsIOPathBenchmark {

  @Param({"50"})
  private int chainDepth;

  private VTaskPath<Integer> vtaskPath;
  private IOPath<Integer> ioPath;

  @Setup
  public void setup() {
    vtaskPath = Path.vtaskPure(42);
    ioPath = Path.ioPure(42);
  }

  // ========== Simple Execution ==========

  @Benchmark
  public Integer ioPath_simpleExecution() {
    return ioPath.unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_simpleExecution() {
    return vtaskPath.unsafeRun();
  }

  // ========== Map Chain ==========

  @Benchmark
  public Integer ioPath_mapChain() {
    return ioPath.map(x -> x + 1).map(x -> x * 2).map(x -> x - 5).unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_mapChain() {
    return vtaskPath.map(x -> x + 1).map(x -> x * 2).map(x -> x - 5).unsafeRun();
  }

  // ========== Via/FlatMap Chain ==========

  @Benchmark
  public Integer ioPath_viaChain() {
    return ioPath
        .via(x -> Path.ioPure(x + 1))
        .via(x -> Path.ioPure(x * 2))
        .via(x -> Path.ioPure(x - 5))
        .unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_viaChain() {
    return vtaskPath
        .via(x -> Path.vtaskPure(x + 1))
        .via(x -> Path.vtaskPure(x * 2))
        .via(x -> Path.vtaskPure(x - 5))
        .unsafeRun();
  }

  // ========== Deep Recursion ==========

  @Benchmark
  public Integer ioPath_deepRecursion() {
    IOPath<Integer> result = ioPath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.via(x -> Path.ioPure(x + 1));
    }
    return result.unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_deepRecursion() {
    VTaskPath<Integer> result = vtaskPath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.via(x -> Path.vtaskPure(x + 1));
    }
    return result.unsafeRun();
  }

  // ========== Long Map Chain ==========

  @Benchmark
  public Integer ioPath_longMapChain() {
    IOPath<Integer> result = ioPath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result.unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_longMapChain() {
    VTaskPath<Integer> result = vtaskPath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result.unsafeRun();
  }

  // ========== Mixed Operations ==========

  @Benchmark
  public Integer ioPath_mixedOperations() {
    return ioPath
        .map(x -> x + 1)
        .via(x -> Path.ioPure(x * 2))
        .map(x -> x - 5)
        .via(x -> Path.ioPure(x / 3))
        .map(x -> x + 10)
        .unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_mixedOperations() {
    return vtaskPath
        .map(x -> x + 1)
        .via(x -> Path.vtaskPure(x * 2))
        .map(x -> x - 5)
        .via(x -> Path.vtaskPure(x / 3))
        .map(x -> x + 10)
        .unsafeRun();
  }

  // ========== Error Handling ==========

  @Benchmark
  public Integer ioPath_handleError() {
    return Path.<Integer>io(
            () -> {
              throw new RuntimeException("error");
            })
        .handleError(e -> -1)
        .unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_handleError() {
    return Path.<Integer>vtask(
            () -> {
              throw new RuntimeException("error");
            })
        .handleError(e -> -1)
        .unsafeRun();
  }

  // ========== HandleErrorWith ==========

  @Benchmark
  public Integer ioPath_handleErrorWith() {
    return Path.<Integer>io(
            () -> {
              throw new RuntimeException("error");
            })
        .handleErrorWith(e -> Path.ioPure(-1))
        .unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_handleErrorWith() {
    return Path.<Integer>vtask(
            () -> {
              throw new RuntimeException("error");
            })
        .handleErrorWith(e -> Path.vtaskPure(-1))
        .unsafeRun();
  }

  // ========== Resilient Pattern ==========

  @Benchmark
  public Integer ioPath_resilientPattern() {
    return Path.io(() -> 42).handleError(e -> -1).unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_resilientPattern() {
    return Path.vtask(() -> 42).timeout(Duration.ofSeconds(1)).handleError(e -> -1).unsafeRun();
  }

  // ========== Construction Only ==========

  @Benchmark
  public IOPath<Integer> ioPath_construction() {
    return Path.ioPure(42);
  }

  @Benchmark
  public VTaskPath<Integer> vtaskPath_construction() {
    return Path.vtaskPure(42);
  }

  // ========== Deferred Construction ==========

  @Benchmark
  public IOPath<Integer> ioPath_deferredConstruction() {
    return Path.io(() -> 42);
  }

  @Benchmark
  public VTaskPath<Integer> vtaskPath_deferredConstruction() {
    return Path.vtask(() -> 42);
  }

  // ========== ZipWith ==========

  @Benchmark
  public Integer ioPath_zipWith() {
    IOPath<Integer> other = Path.ioPure(10);
    return ioPath.zipWith(other, Integer::sum).unsafeRun();
  }

  @Benchmark
  public Integer vtaskPath_zipWith() {
    VTaskPath<Integer> other = Path.vtaskPure(10);
    return vtaskPath.zipWith(other, Integer::sum).unsafeRun();
  }

  // ========== RunSafe ==========

  @Benchmark
  public Object ioPath_runSafe() {
    return ioPath.runSafe();
  }

  @Benchmark
  public Object vtaskPath_runSafe() {
    return vtaskPath.runSafe();
  }
}
