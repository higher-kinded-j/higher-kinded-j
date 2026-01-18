// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Benchmarks for Scope and Resource - Phase 3 structured concurrency types.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Scope creation and join overhead
 *   <li>Different joiner strategies (allSucceed, anySucceed, accumulating)
 *   <li>Resource bracket pattern overhead
 *   <li>Comparison with direct Par combinators
 * </ul>
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*ScopeBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
public class ScopeBenchmark {

  @Param({"3", "10", "50"})
  private int taskCount;

  private List<VTask<Integer>> tasks;

  @Setup
  public void setup() {
    tasks = IntStream.range(0, taskCount).mapToObj(i -> VTask.succeed(i)).toList();
  }

  // ========== Scope AllSucceed ==========

  @Benchmark
  public List<Integer> scope_allSucceed() throws Throwable {
    Scope<Integer, List<Integer>> scope = Scope.allSucceed();
    for (VTask<Integer> task : tasks) {
      scope = scope.fork(task);
    }
    return scope.join().run();
  }

  @Benchmark
  public List<Integer> scope_allSucceed_forkAll() throws Throwable {
    return Scope.<Integer>allSucceed().forkAll(tasks).join().run();
  }

  // ========== Scope AnySucceed ==========

  @Benchmark
  public Integer scope_anySucceed() throws Throwable {
    Scope<Integer, Integer> scope = Scope.anySucceed();
    for (VTask<Integer> task : tasks) {
      scope = scope.fork(task);
    }
    return scope.join().run();
  }

  // ========== Scope Accumulating ==========

  @Benchmark
  public Validated<List<String>, List<Integer>> scope_accumulating() throws Throwable {
    Scope<Integer, Validated<List<String>, List<Integer>>> scope =
        Scope.accumulating(Throwable::getMessage);
    for (VTask<Integer> task : tasks) {
      scope = scope.fork(task);
    }
    return scope.join().run();
  }

  // ========== Comparison with Par.all ==========

  @Benchmark
  public List<Integer> par_all_direct() throws Throwable {
    return Par.all(tasks).run();
  }

  // ========== Resource Benchmarks ==========

  @Benchmark
  public String resource_simple_use() throws Throwable {
    Resource<String> resource = Resource.make(() -> "test", s -> {});
    return resource.useSync(s -> s.toUpperCase()).run();
  }

  @Benchmark
  public String resource_autoCloseable() throws Throwable {
    Resource<AutoCloseable> resource = Resource.fromAutoCloseable(() -> () -> {});
    return resource.useSync(r -> "result").run();
  }

  @Benchmark
  public String resource_combined_two() throws Throwable {
    Resource<String> first = Resource.make(() -> "first", s -> {});
    Resource<String> second = Resource.make(() -> "second", s -> {});

    return first.and(second).useSync(tuple -> tuple.first() + tuple.second()).run();
  }

  @Benchmark
  public String resource_nested_use() throws Throwable {
    Resource<String> outer = Resource.make(() -> "outer", s -> {});
    Resource<String> inner = Resource.make(() -> "inner", s -> {});

    return outer.use(o -> inner.useSync(i -> o + i)).run();
  }

  // ========== Scope with Computation ==========

  @Benchmark
  public List<Integer> scope_with_computation() throws Throwable {
    List<VTask<Integer>> computeTasks =
        IntStream.range(0, taskCount).mapToObj(i -> VTask.of(() -> i * 2)).toList();

    return Scope.<Integer>allSucceed().forkAll(computeTasks).join().run();
  }

  // ========== Safe Join Variants ==========

  @Benchmark
  public Object scope_joinSafe() throws Throwable {
    return Scope.<Integer>allSucceed().forkAll(tasks).joinSafe().run();
  }

  @Benchmark
  public Object scope_joinEither() throws Throwable {
    return Scope.<Integer>allSucceed().forkAll(tasks).joinEither().run();
  }

  @Benchmark
  public Object scope_joinMaybe() throws Throwable {
    return Scope.<Integer>allSucceed().forkAll(tasks).joinMaybe().run();
  }
}
