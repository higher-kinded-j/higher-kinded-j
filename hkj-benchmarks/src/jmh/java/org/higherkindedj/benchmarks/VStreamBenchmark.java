// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.vstream.VStream;
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
 * JMH benchmarks for VStream operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Cost of VStream construction from various sources
 *   <li>Lazy combinator construction overhead (map, filter, flatMap)
 *   <li>Terminal operation execution (toList, foldLeft, count)
 *   <li>Pipeline composition with chained combinators
 *   <li>Comparison of VStream vs raw Java Stream for equivalent operations
 * </ul>
 *
 * <p>Note: VStream is lazy and pull-based, executing on virtual threads. Most combinators build up
 * a description; only terminal operations (toList, foldLeft, etc.) materialise the stream.
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*VStreamBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class VStreamBenchmark {

  @Param({"50"})
  private int streamSize;

  @Param({"50"})
  private int chainDepth;

  private VStream<Integer> prebuiltStream;
  private List<Integer> sourceList;

  @Setup
  public void setup() {
    sourceList = new java.util.ArrayList<>(streamSize);
    for (int i = 0; i < streamSize; i++) {
      sourceList.add(i);
    }
    prebuiltStream = VStream.fromList(sourceList);
  }

  // ========== Construction ==========

  /** Construction cost of VStream.empty. */
  @Benchmark
  public VStream<Integer> constructEmpty() {
    return VStream.empty();
  }

  /** Construction cost of VStream.of with a single value. */
  @Benchmark
  public VStream<Integer> constructSingle() {
    return VStream.of(42);
  }

  /** Construction cost of VStream.fromList. */
  @Benchmark
  public VStream<Integer> constructFromList() {
    return VStream.fromList(sourceList);
  }

  /** Construction cost of VStream.range. */
  @Benchmark
  public VStream<Integer> constructRange() {
    return VStream.range(0, streamSize);
  }

  // ========== Combinator Construction (lazy - doesn't execute) ==========

  /** Map combinator construction. */
  @Benchmark
  public VStream<Integer> mapConstruction() {
    return prebuiltStream.map(x -> x + 1);
  }

  /** Filter combinator construction. */
  @Benchmark
  public VStream<Integer> filterConstruction() {
    return prebuiltStream.filter(x -> x % 2 == 0);
  }

  /** FlatMap combinator construction. */
  @Benchmark
  public VStream<Integer> flatMapConstruction() {
    return prebuiltStream.flatMap(x -> VStream.of(x, x * 2));
  }

  /** Take combinator construction. */
  @Benchmark
  public VStream<Integer> takeConstruction() {
    return prebuiltStream.take(streamSize / 2);
  }

  /** Concat combinator construction. */
  @Benchmark
  public VStream<Integer> concatConstruction() {
    return prebuiltStream.concat(prebuiltStream);
  }

  // ========== Single Combinator Execution ==========

  /** Map execution - materialises the entire stream. */
  @Benchmark
  public List<Integer> mapExecution() {
    return prebuiltStream.map(x -> x + 1).toList().run();
  }

  /** Filter execution - materialises with predicate evaluation. */
  @Benchmark
  public List<Integer> filterExecution() {
    return prebuiltStream.filter(x -> x % 2 == 0).toList().run();
  }

  /** FlatMap execution - each element expands to two. */
  @Benchmark
  public List<Integer> flatMapExecution() {
    return prebuiltStream.flatMap(x -> VStream.of(x, x * 2)).toList().run();
  }

  /** Take execution - early termination after n elements. */
  @Benchmark
  public List<Integer> takeExecution() {
    return prebuiltStream.take(streamSize / 2).toList().run();
  }

  /** Concat execution - two streams merged. */
  @Benchmark
  public List<Integer> concatExecution() {
    return prebuiltStream.concat(prebuiltStream).toList().run();
  }

  // ========== Terminal Operations ==========

  /** toList terminal operation. */
  @Benchmark
  public List<Integer> toListExecution() {
    return prebuiltStream.toList().run();
  }

  /** foldLeft terminal operation - sum all elements. */
  @Benchmark
  public Integer foldLeftExecution() {
    return prebuiltStream.foldLeft(0, Integer::sum).run();
  }

  /** count terminal operation. */
  @Benchmark
  public Long countExecution() {
    return prebuiltStream.count().run();
  }

  /** exists terminal operation with short-circuit (match at start). */
  @Benchmark
  public Boolean existsEarlyMatch() {
    return prebuiltStream.exists(x -> x == 0).run();
  }

  /** exists terminal operation without short-circuit (no match). */
  @Benchmark
  public Boolean existsNoMatch() {
    return prebuiltStream.exists(x -> x < 0).run();
  }

  /** find terminal operation with early match. */
  @Benchmark
  public Object findEarlyMatch() {
    return prebuiltStream.find(x -> x == 0).run();
  }

  // ========== Pipeline Composition ==========

  /** Multi-stage pipeline: filter then map. */
  @Benchmark
  public List<Integer> pipelineFilterMap() {
    return prebuiltStream.filter(x -> x % 2 == 0).map(x -> x * 3).toList().run();
  }

  /** Multi-stage pipeline: map, filter, take. */
  @Benchmark
  public List<Integer> pipelineMapFilterTake() {
    return prebuiltStream.map(x -> x * 2).filter(x -> x % 3 == 0).take(10).toList().run();
  }

  /** Deep map chain construction (lazy). */
  @Benchmark
  public VStream<Integer> deepMapChainConstruction() {
    VStream<Integer> result = prebuiltStream;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /** Deep map chain execution. */
  @Benchmark
  public List<Integer> deepMapChainExecution() {
    VStream<Integer> result = prebuiltStream;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result.toList().run();
  }

  /** Deep flatMap chain for stack safety validation. */
  @Benchmark
  public List<Integer> deepFlatMapChainExecution() {
    VStream<Integer> result = VStream.of(0);
    for (int i = 0; i < chainDepth; i++) {
      result = result.flatMap(x -> VStream.of(x + 1));
    }
    return result.toList().run();
  }

  // ========== Comparison Baselines (Raw Java Stream) ==========

  /** Baseline: raw Java Stream map + collect. */
  @Benchmark
  public List<Integer> baselineJavaStreamMap() {
    return sourceList.stream().map(x -> x + 1).toList();
  }

  /** Baseline: raw Java Stream filter + collect. */
  @Benchmark
  public List<Integer> baselineJavaStreamFilter() {
    return sourceList.stream().filter(x -> x % 2 == 0).toList();
  }

  /** Baseline: raw Java Stream pipeline (filter, map, limit). */
  @Benchmark
  public List<Integer> baselineJavaStreamPipeline() {
    return sourceList.stream().map(x -> x * 2).filter(x -> x % 3 == 0).limit(10).toList();
  }

  /** Baseline: raw Java Stream fold (reduce). */
  @Benchmark
  public Integer baselineJavaStreamFold() {
    return sourceList.stream().reduce(0, Integer::sum);
  }

  // ========== Real-World Patterns ==========

  /**
   * Real-world pattern: data transformation pipeline.
   *
   * <p>Simulates a typical ETL-style pipeline: generate, transform, filter, collect.
   */
  @Benchmark
  public List<String> realWorldTransformPipeline(Blackhole blackhole) {
    return VStream.range(0, streamSize)
        .map(x -> x * x)
        .filter(x -> x % 2 == 0)
        .map(x -> "item-" + x)
        .take(20)
        .toList()
        .run();
  }

  /**
   * Real-world pattern: flatMap expansion with filtering.
   *
   * <p>Simulates expanding each item into sub-items and selecting relevant ones.
   */
  @Benchmark
  public List<Integer> realWorldFlatMapFilter() {
    return VStream.range(0, streamSize / 5)
        .flatMap(x -> VStream.of(x, x * 10, x * 100))
        .filter(x -> x > 0 && x < 500)
        .toList()
        .run();
  }
}
