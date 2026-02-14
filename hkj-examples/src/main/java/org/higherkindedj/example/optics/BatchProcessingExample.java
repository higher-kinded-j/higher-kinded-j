// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.ListTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * A real-world example demonstrating limiting traversals for batch processing workflows.
 *
 * <p>This example simulates a data pipeline where:
 *
 * <ul>
 *   <li>Records are processed in chunks (like Spring Batch)
 *   <li>Different processing stages apply transformations to specific chunks
 *   <li>Priority processing handles high-priority items first
 *   <li>Failure recovery skips already-processed chunks
 * </ul>
 *
 * <p>Key patterns demonstrated:
 *
 * <ul>
 *   <li>Chunk-based processing using {@code slicing(from, to)}
 *   <li>Priority handling using {@code taking(n)} and {@code dropping(n)}
 *   <li>Progressive processing with state tracking
 *   <li>Composing limiting traversals with domain transformations
 * </ul>
 */
public class BatchProcessingExample {

  // Domain models for a data processing pipeline
  public record DataRecord(
      String id,
      String payload,
      ProcessingStatus status,
      int retryCount,
      LocalDateTime processedAt) {
    DataRecord markProcessed() {
      return new DataRecord(
          id, payload, ProcessingStatus.PROCESSED, retryCount, LocalDateTime.now());
    }

    DataRecord markFailed() {
      return new DataRecord(id, payload, ProcessingStatus.FAILED, retryCount + 1, processedAt);
    }

    DataRecord markPending() {
      return new DataRecord(id, payload, ProcessingStatus.PENDING, retryCount, processedAt);
    }

    DataRecord markProcessing() {
      return new DataRecord(id, payload, ProcessingStatus.PROCESSING, retryCount, processedAt);
    }

    DataRecord transformPayload(String prefix) {
      return new DataRecord(id, prefix + "_" + payload, status, retryCount, processedAt);
    }
  }

  public enum ProcessingStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
  }

  public record BatchResult(int chunkNumber, int processed, int failed, long durationMs) {}

  // Constants
  private static final int CHUNK_SIZE = 10;

  public static void main(String[] args) {
    List<DataRecord> records = createRecords(35);

    System.out.println("=== Batch Processing with Limiting Traversals ===\n");
    System.out.println("Total records: " + records.size());
    System.out.println("Chunk size: " + CHUNK_SIZE);
    System.out.println();

    demonstrateChunkProcessing(records);
    demonstratePriorityProcessing(records);
    demonstrateProgressiveTransformation(records);
    demonstrateFailureRecovery(records);
    demonstrateParallelChunkPreparation(records);
  }

  private static void demonstrateChunkProcessing(List<DataRecord> records) {
    System.out.println("--- Scenario 1: Chunk-Based Processing ---");

    int totalChunks = (int) Math.ceil(records.size() / (double) CHUNK_SIZE);
    System.out.printf("Processing %d records in %d chunks%n", records.size(), totalChunks);

    List<DataRecord> processed = records;
    List<BatchResult> results = new ArrayList<>();

    for (int chunk = 0; chunk < totalChunks; chunk++) {
      int start = chunk * CHUNK_SIZE;
      int end = Math.min((chunk + 1) * CHUNK_SIZE, records.size());

      Traversal<List<DataRecord>, DataRecord> chunkTraversal = ListTraversals.slicing(start, end);

      long startTime = System.currentTimeMillis();

      // Process this chunk - mark all as processed
      processed = Traversals.modify(chunkTraversal, DataRecord::markProcessed, processed);

      long duration = System.currentTimeMillis() - startTime;
      int chunkSize = end - start;

      results.add(new BatchResult(chunk + 1, chunkSize, 0, duration));
      System.out.printf(
          "  Chunk %d: Processed %d records (indices %d-%d) in %dms%n",
          chunk + 1, chunkSize, start, end - 1, duration);
    }

    // Verify all processed
    long processedCount =
        processed.stream().filter(r -> r.status() == ProcessingStatus.PROCESSED).count();
    System.out.printf("%nTotal processed: %d/%d records%n", processedCount, records.size());

    System.out.println();
  }

  private static void demonstratePriorityProcessing(List<DataRecord> records) {
    System.out.println("--- Scenario 2: Priority-Based Processing ---");

    // High priority: First 5 records (urgent)
    Traversal<List<DataRecord>, DataRecord> highPriority = ListTraversals.taking(5);

    // Medium priority: Next 10 records
    Traversal<List<DataRecord>, DataRecord> mediumPriority = ListTraversals.slicing(5, 15);

    // Low priority: Remaining records
    Traversal<List<DataRecord>, DataRecord> lowPriority = ListTraversals.dropping(15);

    System.out.println("Priority distribution:");
    System.out.println("  HIGH (first 5): " + Traversals.getAll(highPriority, records).size());
    System.out.println("  MEDIUM (next 10): " + Traversals.getAll(mediumPriority, records).size());
    System.out.println("  LOW (remaining): " + Traversals.getAll(lowPriority, records).size());

    // Transform payloads based on priority
    List<DataRecord> step1 =
        Traversals.modify(highPriority, r -> r.transformPayload("URGENT"), records);
    List<DataRecord> step2 =
        Traversals.modify(mediumPriority, r -> r.transformPayload("NORMAL"), step1);
    List<DataRecord> step3 =
        Traversals.modify(lowPriority, r -> r.transformPayload("BATCH"), step2);

    System.out.println("\nSample records after priority tagging:");
    System.out.printf("  Record 1 (HIGH): %s%n", step3.get(0).payload());
    System.out.printf("  Record 10 (MEDIUM): %s%n", step3.get(9).payload());
    System.out.printf("  Record 20 (LOW): %s%n", step3.get(19).payload());

    System.out.println();
  }

  private static void demonstrateProgressiveTransformation(List<DataRecord> records) {
    System.out.println("--- Scenario 3: Progressive Transformation Pipeline ---");

    // Simulate a multi-stage ETL pipeline
    System.out.println("ETL Pipeline stages:");

    // Stage 1: Extract - Mark first batch as processing
    Traversal<List<DataRecord>, DataRecord> extractBatch = ListTraversals.taking(20);
    List<DataRecord> afterExtract =
        Traversals.modify(extractBatch, DataRecord::markProcessing, records);
    System.out.println("  1. EXTRACT: First 20 records marked as PROCESSING");

    // Stage 2: Transform - Apply transformation to extracted records
    List<DataRecord> afterTransform =
        Traversals.modify(extractBatch, r -> r.transformPayload("ETL"), afterExtract);
    System.out.println("  2. TRANSFORM: Applied ETL prefix to payloads");

    // Stage 3: Load - Mark as processed
    List<DataRecord> afterLoad =
        Traversals.modify(extractBatch, DataRecord::markProcessed, afterTransform);
    System.out.println("  3. LOAD: Marked as PROCESSED with timestamp");

    // Verify pipeline results
    List<DataRecord> processed = Traversals.getAll(extractBatch, afterLoad);
    boolean allProcessed =
        processed.stream().allMatch(r -> r.status() == ProcessingStatus.PROCESSED);
    boolean allTransformed = processed.stream().allMatch(r -> r.payload().startsWith("ETL_"));

    System.out.println("\nPipeline verification:");
    System.out.println("  All 20 records processed: " + allProcessed);
    System.out.println("  All 20 records transformed: " + allTransformed);

    // Check remaining records unchanged
    Traversal<List<DataRecord>, DataRecord> remaining = ListTraversals.dropping(20);
    List<DataRecord> unprocessed = Traversals.getAll(remaining, afterLoad);
    boolean remainingPending =
        unprocessed.stream().allMatch(r -> r.status() == ProcessingStatus.PENDING);
    System.out.println(
        "  Remaining " + unprocessed.size() + " records still PENDING: " + remainingPending);

    System.out.println();
  }

  private static void demonstrateFailureRecovery(List<DataRecord> records) {
    System.out.println("--- Scenario 4: Failure Recovery and Retry ---");

    // Simulate partial processing with failures
    // First 10: Successfully processed
    // Next 5: Failed
    // Remaining: Not yet attempted

    Traversal<List<DataRecord>, DataRecord> successBatch = ListTraversals.taking(10);
    Traversal<List<DataRecord>, DataRecord> failedBatch = ListTraversals.slicing(10, 15);
    Traversal<List<DataRecord>, DataRecord> pendingBatch = ListTraversals.dropping(15);

    List<DataRecord> step1 = Traversals.modify(successBatch, DataRecord::markProcessed, records);
    List<DataRecord> step2 = Traversals.modify(failedBatch, DataRecord::markFailed, step1);

    System.out.println("After initial processing attempt:");
    System.out.printf(
        "  PROCESSED: %d records%n",
        step2.stream().filter(r -> r.status() == ProcessingStatus.PROCESSED).count());
    System.out.printf(
        "  FAILED: %d records%n",
        step2.stream().filter(r -> r.status() == ProcessingStatus.FAILED).count());
    System.out.printf(
        "  PENDING: %d records%n",
        step2.stream().filter(r -> r.status() == ProcessingStatus.PENDING).count());

    // Recovery: Retry failed records
    System.out.println("\nRetrying failed records...");

    // Only modify the failed slice - attempt to process them again
    List<DataRecord> afterRetry =
        Traversals.modify(
            failedBatch,
            r -> {
              // Simulate 80% success on retry
              if (r.id().hashCode() % 5 != 0) {
                return r.markProcessed();
              } else {
                return r.markFailed(); // Still fails
              }
            },
            step2);

    System.out.println("After retry:");
    System.out.printf(
        "  PROCESSED: %d records%n",
        afterRetry.stream().filter(r -> r.status() == ProcessingStatus.PROCESSED).count());
    System.out.printf(
        "  FAILED: %d records (max retries exceeded)%n",
        afterRetry.stream().filter(r -> r.status() == ProcessingStatus.FAILED).count());

    // Show retry counts for failed records
    List<DataRecord> stillFailed = Traversals.getAll(failedBatch, afterRetry);
    stillFailed.stream()
        .filter(r -> r.status() == ProcessingStatus.FAILED)
        .forEach(r -> System.out.printf("    Record %s: %d retries%n", r.id(), r.retryCount()));

    System.out.println();
  }

  private static void demonstrateParallelChunkPreparation(List<DataRecord> records) {
    System.out.println("--- Scenario 5: Parallel Chunk Preparation ---");

    // Prepare multiple chunks for parallel processing
    System.out.println("Preparing 4 chunks for parallel execution:");

    Traversal<List<DataRecord>, DataRecord> chunk1 = ListTraversals.slicing(0, 10);
    Traversal<List<DataRecord>, DataRecord> chunk2 = ListTraversals.slicing(10, 20);
    Traversal<List<DataRecord>, DataRecord> chunk3 = ListTraversals.slicing(20, 30);
    Traversal<List<DataRecord>, DataRecord> chunk4 = ListTraversals.dropping(30);

    List<List<DataRecord>> chunks =
        List.of(
            Traversals.getAll(chunk1, records),
            Traversals.getAll(chunk2, records),
            Traversals.getAll(chunk3, records),
            Traversals.getAll(chunk4, records));

    for (int i = 0; i < chunks.size(); i++) {
      System.out.printf(
          "  Chunk %d: %d records ready for Worker-%d%n", i + 1, chunks.get(i).size(), i + 1);
    }

    // Simulate parallel processing results
    System.out.println("\nSimulated parallel processing results:");

    List<DataRecord> finalResult = records;
    String[] workerIds = {"WORKER_A", "WORKER_B", "WORKER_C", "WORKER_D"};
    List<Traversal<List<DataRecord>, DataRecord>> chunkTraversals =
        List.of(chunk1, chunk2, chunk3, chunk4);

    for (int i = 0; i < chunkTraversals.size(); i++) {
      String workerId = workerIds[i];
      finalResult =
          Traversals.modify(
              chunkTraversals.get(i),
              r -> r.transformPayload(workerId).markProcessed(),
              finalResult);
      System.out.printf("  %s completed chunk %d%n", workerId, i + 1);
    }

    // Verify all processed
    long totalProcessed =
        finalResult.stream().filter(r -> r.status() == ProcessingStatus.PROCESSED).count();
    System.out.printf("%nAll %d records processed by parallel workers%n", totalProcessed);

    // Sample verification
    System.out.println("Sample transformed payloads:");
    System.out.printf("  Record 5 (Chunk 1): %s%n", finalResult.get(4).payload());
    System.out.printf("  Record 15 (Chunk 2): %s%n", finalResult.get(14).payload());
    System.out.printf("  Record 25 (Chunk 3): %s%n", finalResult.get(24).payload());
    System.out.printf("  Record 32 (Chunk 4): %s%n", finalResult.get(31).payload());

    System.out.println();
    System.out.println("=== Batch Processing Examples Complete ===");
  }

  // Helper method to create test records
  private static List<DataRecord> createRecords(int count) {
    List<DataRecord> records = new ArrayList<>();
    IntStream.range(0, count)
        .forEach(
            i -> {
              String id = UUID.randomUUID().toString().substring(0, 8);
              String payload = "DATA_" + String.format("%03d", i + 1);
              records.add(new DataRecord(id, payload, ProcessingStatus.PENDING, 0, null));
            });
    return records;
  }
}
