// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.vstream;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Demonstrates core VStream operations: factory methods, transformation pipelines, terminal
 * operations, lazy evaluation, effectful unfold, and error recovery.
 *
 * <p>VStream is a lazy, pull-based streaming abstraction that executes element production on
 * virtual threads via VTask. Unlike Java's Stream, VStream is reusable and integrates with the
 * Higher-Kinded-J ecosystem.
 *
 * <p>See <a href="https://higher-kinded-j.github.io/usage-guide.html">Usage Guide</a>
 */
public class VStreamBasicExample {

  public static void main(String[] args) {
    VStreamBasicExample example = new VStreamBasicExample();

    System.out.println("=== Creating Streams ===");
    example.creatingStreams();

    System.out.println("\n=== Transformation Pipeline ===");
    example.transformationPipeline();

    System.out.println("\n=== Terminal Operations ===");
    example.terminalOperations();

    System.out.println("\n=== Lazy Evaluation ===");
    example.lazyEvaluation();

    System.out.println("\n=== Effectful Unfold (Simulated Pagination) ===");
    example.effectfulUnfold();

    System.out.println("\n=== Error Recovery ===");
    example.errorRecovery();

    System.out.println("\n=== Infinite Streams with take ===");
    example.infiniteStreamsWithTake();
  }

  /** Demonstrates creating VStreams from various sources. */
  public void creatingStreams() {
    // From values
    List<Integer> fromValues = VStream.of(1, 2, 3).toList().run();
    System.out.println("From values: " + fromValues);

    // From a list
    List<String> fromList = VStream.fromList(List.of("a", "b", "c")).toList().run();
    System.out.println("From list: " + fromList);

    // Range
    List<Integer> range = VStream.range(1, 6).toList().run();
    System.out.println("Range [1, 6): " + range);

    // Empty
    List<Integer> empty = VStream.<Integer>empty().toList().run();
    System.out.println("Empty: " + empty);

    // Single element
    List<String> single = VStream.of("hello").toList().run();
    System.out.println("Single: " + single);
  }

  /** Demonstrates chaining map, filter, flatMap in a pipeline. */
  public void transformationPipeline() {
    List<String> result =
        VStream.range(1, 20)
            .filter(n -> n % 2 == 0) // Keep evens: 2, 4, 6, ...
            .map(n -> n * 10) // Multiply: 20, 40, 60, ...
            .flatMap(n -> VStream.of(n, n + 1)) // Expand: 20, 21, 40, 41, ...
            .take(8)
            .map(n -> "item-" + n)
            .toList()
            .run();
    System.out.println("Pipeline result: " + result);
  }

  /** Demonstrates terminal operations: fold, exists, forAll, find. */
  public void terminalOperations() {
    VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

    // Fold (sum)
    Integer sum = numbers.fold(0, Integer::sum).run();
    System.out.println("Sum: " + sum);

    // Fold left (string concatenation)
    String concat = VStream.of("a", "b", "c").foldLeft("", (acc, s) -> acc + s).run();
    System.out.println("Concatenated: " + concat);

    // Exists (short-circuits)
    Boolean hasEven = VStream.of(1, 3, 4, 7).exists(n -> n % 2 == 0).run();
    System.out.println("Has even: " + hasEven);

    // ForAll
    Boolean allPositive = VStream.of(1, 2, 3).forAll(n -> n > 0).run();
    System.out.println("All positive: " + allPositive);

    // Find
    Optional<Integer> firstBig = VStream.range(1, 100).find(n -> n > 50).run();
    System.out.println("First > 50: " + firstBig);

    // Count
    Long count = VStream.range(0, 1000).count().run();
    System.out.println("Count of range(0, 1000): " + count);
  }

  /** Demonstrates that VStream operations are lazy - nothing executes until a terminal op. */
  public void lazyEvaluation() {
    AtomicInteger evaluations = new AtomicInteger(0);

    // Build a pipeline - no elements are produced yet
    VStream<Integer> pipeline =
        VStream.generate(evaluations::incrementAndGet)
            .filter(n -> n % 2 == 0)
            .map(n -> n * 100)
            .take(5);

    System.out.println("Pipeline built. Evaluations so far: " + evaluations.get());

    // Now trigger evaluation
    List<Integer> result = pipeline.toList().run();
    System.out.println("After toList(). Evaluations: " + evaluations.get());
    System.out.println("Result: " + result);
  }

  /** Demonstrates effectful unfold simulating a paginated API. */
  public void effectfulUnfold() {
    // Simulate an API that returns pages of data
    VStream<String> pages =
        VStream.unfold(
            1,
            page ->
                VTask.of(
                    () -> {
                      System.out.println("  Fetching page " + page + "...");
                      if (page > 3) {
                        return Optional.empty(); // No more pages
                      }
                      String data = "Page " + page + " data";
                      return Optional.of(new VStream.Seed<>(data, page + 1));
                    }));

    List<String> allPages = pages.toList().run();
    System.out.println("All pages: " + allPages);
  }

  /** Demonstrates error recovery patterns. */
  public void errorRecovery() {
    // recover: replace error with a default value
    VStream<String> recovered =
        VStream.<String>fail(new RuntimeException("connection error"))
            .recover(e -> "default-value");
    System.out.println("Recovered: " + recovered.toList().run());

    // recoverWith: replace error with a fallback stream
    VStream<String> fallback =
        VStream.<String>fail(new RuntimeException("primary failed"))
            .recoverWith(e -> VStream.of("fallback-1", "fallback-2"));
    System.out.println("Fallback: " + fallback.toList().run());

    // Chained recovery
    VStream<String> chained =
        VStream.<String>fail(new RuntimeException("first"))
            .recoverWith(
                e1 ->
                    VStream.<String>fail(new RuntimeException("second"))
                        .recoverWith(e2 -> VStream.of("final-fallback")));
    System.out.println("Chained: " + chained.toList().run());
  }

  /** Demonstrates working with infinite streams using take and takeWhile. */
  public void infiniteStreamsWithTake() {
    // Fibonacci sequence
    record FibState(int a, int b) {}
    VStream<Integer> fibs =
        VStream.unfold(
            new FibState(0, 1),
            state ->
                VTask.succeed(
                    Optional.of(
                        new VStream.Seed<>(
                            state.a(), new FibState(state.b(), state.a() + state.b())))));

    List<Integer> first10Fibs = fibs.take(10).toList().run();
    System.out.println("First 10 Fibonacci: " + first10Fibs);

    // Powers of 2, while < 1000
    List<Integer> powers = VStream.iterate(1, n -> n * 2).takeWhile(n -> n < 1000).toList().run();
    System.out.println("Powers of 2 < 1000: " + powers);

    // Repeat and distinct
    List<String> unique = VStream.of("a", "b", "a", "c", "b", "d").distinct().toList().run();
    System.out.println("Distinct: " + unique);
  }
}
