// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vstream.VStreamReactive;
import org.higherkindedj.hkt.vtask.VTask;
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
 * <p>Note: VStream is lazy and pull-based. A terminal operation pulls the stream on the thread that
 * runs it, and {@code VStreamPar}'s operations start virtual threads to pull their sources or run
 * element tasks. Most combinators build up a description; only terminal operations (toList,
 * foldLeft, etc.) materialise the stream.
 *
 * <p>Run with: {@code ./gradlew jmh -Pincludes=".*VStreamBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class VStreamBenchmark {

  private static final VTask<Unit> NOTHING = VTask.succeed(Unit.INSTANCE);
  private static final String RESOURCE = "resource";
  private static final int PUBLISHER_BUFFER = 16;

  @Param({"50"})
  private int streamSize;

  @Param({"50"})
  private int chainDepth;

  private VStream<Integer> prebuiltStream;
  private List<Integer> sourceList;

  @Setup
  public void setup() {
    sourceList = new ArrayList<>(streamSize);
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

  // ========== Element Tasks and Recovery ==========

  /** mapTask execution: a task for each element, none of which fails. */
  @Benchmark
  public List<Integer> mapTaskExecution() {
    return prebuiltStream.mapTask(VTask::succeed).toList().run();
  }

  /**
   * A task that fails at every fifth element, through a map and a filter, recovered: the recovery
   * goes on through both operators.
   */
  @Benchmark
  public List<Integer> recoverThroughOperators() {
    return prebuiltStream
        .mapTask(
            x ->
                x % 5 == 0
                    ? VTask.<Integer>fail(new IllegalStateException("element"))
                    : VTask.succeed(x))
        .map(x -> x * 2)
        .filter(x -> x % 3 != 0)
        .recover(_ -> -1)
        .toList()
        .run();
  }

  // ========== Finalisers and Resources ==========

  /** onFinalize execution: every pull of the stream goes through its consumption. */
  @Benchmark
  public List<Integer> onFinalizeExecution() {
    return prebuiltStream.onFinalize(NOTHING).toList().run();
  }

  /** bracket execution: acquire, read through the release's finaliser, release. */
  @Benchmark
  public List<Integer> bracketExecution() {
    return VStream.bracket(VTask.succeed(RESOURCE), _ -> prebuiltStream, _ -> NOTHING)
        .toList()
        .run();
  }

  /** fromStream execution, which closes the Java stream through a finaliser. */
  @Benchmark
  public List<Integer> fromStreamExecution() {
    return VStream.fromStream(sourceList.stream()).toList().run();
  }

  /** fromIterator execution over the same elements, which closes nothing: fromStream's baseline. */
  @Benchmark
  public List<Integer> fromIteratorExecution() {
    return VStream.fromIterator(sourceList.iterator()).toList().run();
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

  // ========== Parallel Operations (Stage 5) ==========

  /** Parallel map with concurrency of 2. */
  @Benchmark
  public List<Integer> parEvalMapConcurrency2() {
    return VStreamPar.parEvalMap(prebuiltStream, 2, x -> VTask.succeed(x * 2)).toList().run();
  }

  /** Parallel map with concurrency of 4. */
  @Benchmark
  public List<Integer> parEvalMapConcurrency4() {
    return VStreamPar.parEvalMap(prebuiltStream, 4, x -> VTask.succeed(x * 2)).toList().run();
  }

  /** Parallel map with concurrency matching stream size (unbounded). */
  @Benchmark
  public List<Integer> parEvalMapUnbounded() {
    return VStreamPar.parEvalMap(prebuiltStream, streamSize, x -> VTask.succeed(x * 2))
        .toList()
        .run();
  }

  /** Unordered parallel map with concurrency of 4. */
  @Benchmark
  public List<Integer> parEvalMapUnorderedConcurrency4() {
    return VStreamPar.parEvalMapUnordered(prebuiltStream, 4, x -> VTask.succeed(x * 2))
        .toList()
        .run();
  }

  /** merge of two streams, each read on a thread of its own. */
  @Benchmark
  public List<Integer> mergeTwoExecution() {
    return VStreamPar.merge(prebuiltStream, prebuiltStream).toList().run();
  }

  // ========== Reactive Streams ==========

  /**
   * fromPublisher execution over a publisher that sends on request, on the thread that asks, with a
   * buffer smaller than the stream, so the stream asks for more as it reads.
   */
  @Benchmark
  public List<Integer> fromPublisherExecution() {
    return VStreamReactive.fromPublisher(new RangePublisher(streamSize), PUBLISHER_BUFFER)
        .toList()
        .run();
  }

  /**
   * A publisher of {@code 0} to {@code size - 1} that sends what is asked for on the thread that
   * asks, then completes. The stream makes its calls to the subscription one at a time, so its
   * state needs no lock.
   */
  private record RangePublisher(int size) implements Flow.Publisher<Integer> {

    @Override
    public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
      subscriber.onSubscribe(
          new Flow.Subscription() {
            private int next;
            private long demand;
            private boolean sending;
            private boolean ended;

            @Override
            public void request(long n) {
              demand += n;
              // A request made from onNext adds to the demand the loop below is sending
              if (sending) {
                return;
              }
              sending = true;
              while (demand > 0 && next < size && !ended) {
                demand--;
                subscriber.onNext(next++);
              }
              if (next == size && !ended) {
                ended = true;
                subscriber.onComplete();
              }
              sending = false;
            }

            @Override
            public void cancel() {
              ended = true;
            }
          });
    }
  }

  // ========== Chunking Operations (Stage 5) ==========

  /** Chunk then process: groups of 10. */
  @Benchmark
  public List<NonEmptyList<Integer>> chunkExecution() {
    return prebuiltStream.chunk(10).toList().run();
  }

  /** mapChunked: batch transformation with chunks of 10. */
  @Benchmark
  public List<Integer> mapChunkedExecution() {
    return prebuiltStream
        .mapChunked(10, chunk -> chunk.stream().map(x -> x * 2).toList())
        .toList()
        .run();
  }

  /** Real-world: chunk and batch process with parEvalMap. */
  @Benchmark
  public List<Integer> realWorldChunkParallel() {
    return VStreamPar.parEvalMap(
            prebuiltStream.chunk(5),
            2,
            chunk -> VTask.of(() -> chunk.stream().mapToInt(Integer::intValue).sum()))
        .toList()
        .run();
  }
}
