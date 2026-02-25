// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.List;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Examples demonstrating VStream parallel operations and chunking.
 *
 * <p>These examples demonstrate the Stage 5 capabilities of VStream: bounded-concurrency parallel
 * processing via VStreamPar, chunking for batch operations, and their integration with VStreamPath.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>parEvalMap for bounded-concurrency parallel processing (order-preserving)
 *   <li>parEvalMapUnordered for maximum throughput
 *   <li>parEvalFlatMap for parallel stream expansion
 *   <li>merge for concurrent multi-source consumption
 *   <li>chunk, chunkWhile, and mapChunked for batch operations
 *   <li>Combined pipelines: chunk then parallel process
 *   <li>VStreamPath integration with parallel methods
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.VStreamParallelExample}
 *
 * @see org.higherkindedj.hkt.vstream.VStreamPar
 * @see org.higherkindedj.hkt.vstream.VStream
 * @see org.higherkindedj.hkt.effect.VStreamPath
 */
public class VStreamParallelExample {

  public static void main(String[] args) {
    System.out.println("=== VStream Parallel Operations ===\n");

    parallelApiCalls();
    unorderedParallel();
    batchDatabaseInsert();
    mergeMultipleSources();
    chunkingBasics();
    chunkWhileGrouping();
    parallelBatchPipeline();
    vstreamPathParallel();
  }

  // ============================================================
  // Parallel API Calls
  // ============================================================

  /**
   * Demonstrates fetching data concurrently with bounded concurrency.
   *
   * <p>parEvalMap processes elements in parallel, with at most N elements in flight at a time.
   * Results are emitted in the same order as the input, despite parallel execution.
   */
  private static void parallelApiCalls() {
    System.out.println("--- Parallel API Calls ---");

    VStream<String> userIds = VStream.of("user-1", "user-2", "user-3", "user-4", "user-5");

    // Simulate fetching user profiles concurrently, max 2 at a time
    VStream<String> profiles =
        VStreamPar.parEvalMap(
            userIds,
            2,
            userId ->
                VTask.of(
                    () -> {
                      // Simulate API call latency
                      Thread.sleep(50);
                      return "Profile(" + userId + ")";
                    }));

    List<String> result = profiles.toList().run();
    System.out.println("Profiles: " + result);
    System.out.println("Order preserved: first=" + result.getFirst());
    System.out.println();
  }

  // ============================================================
  // Unordered Parallel for Throughput
  // ============================================================

  /**
   * Demonstrates unordered parallel processing for maximum throughput.
   *
   * <p>parEvalMapUnordered emits results as they complete, without waiting for earlier elements.
   * This is faster when output order does not matter.
   */
  private static void unorderedParallel() {
    System.out.println("--- Unordered Parallel (Throughput) ---");

    VStream<Integer> numbers = VStream.fromList(List.of(50, 10, 40, 20, 30));

    // Process with varying delays; unordered emits faster completions first
    VStream<String> result =
        VStreamPar.parEvalMapUnordered(
            numbers,
            3,
            n ->
                VTask.of(
                    () -> {
                      Thread.sleep(n);
                      return "done-" + n;
                    }));

    List<String> resultList = result.toList().run();
    System.out.println("Unordered results: " + resultList);
    System.out.println();
  }

  // ============================================================
  // Batch Database Insert
  // ============================================================

  /**
   * Demonstrates chunking for efficient batch operations.
   *
   * <p>chunk(n) groups elements into lists of at most n elements, which can then be processed as
   * batches. mapChunked combines chunk + map + flatten into a single operation.
   */
  private static void batchDatabaseInsert() {
    System.out.println("--- Batch Database Insert ---");

    VStream<String> records = VStream.of("rec-1", "rec-2", "rec-3", "rec-4", "rec-5", "rec-6");

    // Simulate batch insert: chunks of 3, each batch processed as a unit
    VStream<String> insertResults =
        records.mapChunked(
            3,
            batch -> {
              // Simulate bulk insert returning confirmation for each record
              System.out.println("  Inserting batch of " + batch.size() + ": " + batch);
              return batch.stream().map(r -> "inserted:" + r).toList();
            });

    List<String> allResults = insertResults.toList().run();
    System.out.println("Insert results: " + allResults);
    System.out.println();
  }

  // ============================================================
  // Merge Multiple Sources
  // ============================================================

  /**
   * Demonstrates merging multiple streams concurrently.
   *
   * <p>merge() consumes all source streams concurrently, emitting elements as they become available
   * from any source.
   */
  private static void mergeMultipleSources() {
    System.out.println("--- Merge Multiple Sources ---");

    VStream<String> serviceA = VStream.of("a-event-1", "a-event-2");
    VStream<String> serviceB = VStream.of("b-event-1", "b-event-2", "b-event-3");
    VStream<String> serviceC = VStream.of("c-event-1");

    VStream<String> allEvents = VStreamPar.merge(List.of(serviceA, serviceB, serviceC));

    List<String> events = allEvents.toList().run();
    System.out.println("All events (" + events.size() + "): " + events);
    System.out.println();
  }

  // ============================================================
  // Chunking Basics
  // ============================================================

  /**
   * Demonstrates basic chunking operations.
   *
   * <p>chunk(n) produces a stream of lists, each containing up to n elements. The last chunk may be
   * smaller.
   */
  private static void chunkingBasics() {
    System.out.println("--- Chunking Basics ---");

    VStream<Integer> numbers = VStream.range(1, 11);

    List<List<Integer>> chunks = numbers.chunk(3).toList().run();
    System.out.println("Chunks of 3: " + chunks);
    System.out.println();
  }

  // ============================================================
  // chunkWhile for Grouping
  // ============================================================

  /**
   * Demonstrates chunkWhile for grouping consecutive elements.
   *
   * <p>chunkWhile groups elements while a predicate holds between adjacent pairs. This is useful
   * for grouping sorted data or run-length encoding.
   */
  private static void chunkWhileGrouping() {
    System.out.println("--- chunkWhile Grouping ---");

    VStream<Integer> sortedData = VStream.of(1, 1, 2, 2, 2, 3, 3, 4);

    List<List<Integer>> groups = sortedData.chunkWhile(Integer::equals).toList().run();
    System.out.println("Groups of equal elements: " + groups);
    System.out.println();
  }

  // ============================================================
  // Parallel Batch Pipeline
  // ============================================================

  /**
   * Demonstrates combining chunking with parallel processing.
   *
   * <p>This pattern is ideal for bulk API calls: chunk data into batches, then process each batch
   * in parallel.
   */
  private static void parallelBatchPipeline() {
    System.out.println("--- Parallel Batch Pipeline ---");

    VStream<Integer> data = VStream.range(1, 21);

    // Chunk into groups of 5, then parallel process each chunk
    VStream<Integer> batchSums =
        VStreamPar.parEvalMap(
            data.chunk(5),
            2,
            chunk ->
                VTask.of(
                    () -> {
                      int sum = chunk.stream().mapToInt(Integer::intValue).sum();
                      System.out.println("  Processing batch " + chunk + " -> sum=" + sum);
                      return sum;
                    }));

    List<Integer> sums = batchSums.toList().run();
    System.out.println("Batch sums: " + sums);
    int total = sums.stream().mapToInt(Integer::intValue).sum();
    System.out.println("Total: " + total);
    System.out.println();
  }

  // ============================================================
  // VStreamPath Parallel Integration
  // ============================================================

  /**
   * Demonstrates VStreamPath integration with parallel operations.
   *
   * <p>VStreamPath provides a fluent API for parallel operations, making it easy to compose complex
   * pipelines.
   */
  private static void vstreamPathParallel() {
    System.out.println("--- VStreamPath Parallel ---");

    VStreamPath<Integer> numbers = Path.vstreamRange(1, 11);

    // Fluent parallel pipeline
    List<String> result =
        numbers
            .filter(n -> n % 2 == 0)
            .parEvalMap(
                3,
                n ->
                    VTask.of(
                        () -> {
                          Thread.sleep(10);
                          return "processed-" + n;
                        }))
            .toList()
            .unsafeRun();

    System.out.println("VStreamPath parallel result: " + result);

    // Chunked batch processing via path
    List<List<Integer>> chunks = Path.vstreamRange(1, 8).chunk(3).toList().unsafeRun();
    System.out.println("VStreamPath chunks: " + chunks);
    System.out.println();
  }
}
