// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.PathOps;

/**
 * Examples demonstrating parallel execution with IOPath and CompletableFuturePath.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>{@code parZipWith} - combine two computations in parallel
 *   <li>{@code parZip3} / {@code parZip4} - combine 3 or 4 computations
 *   <li>{@code parSequenceIO} / {@code parSequenceFuture} - run many in parallel
 *   <li>{@code race} - first one to complete wins
 *   <li>Performance comparison: sequential vs parallel
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ParallelExecutionExample}
 */
public class ParallelExecutionExample {

  // Simulated latency for "network" operations
  private static final long SIMULATED_LATENCY_MS = 100;

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Parallel Execution ===\n");

    parZipWithExample();
    parZip3And4Example();
    parSequenceExample();
    raceExample();
    performanceComparisonExample();
    completableFuturePathExample();
  }

  private static void parZipWithExample() {
    System.out.println("--- parZipWith: Parallel Combination ---");

    // Two independent API calls that can run in parallel
    IOPath<String> fetchUser = simulateApiCall("user-service", "Alice");
    IOPath<Integer> fetchAge = simulateApiCallInt("age-service", 30);

    // Sequential: ~200ms (100ms + 100ms)
    System.out.println("Sequential execution:");
    long seqStart = System.currentTimeMillis();
    String seqUser = fetchUser.unsafeRun();
    Integer seqAge = fetchAge.unsafeRun();
    long seqTime = System.currentTimeMillis() - seqStart;
    System.out.println("  Result: " + seqUser + ", " + seqAge + " (" + seqTime + "ms)");

    // Parallel: ~100ms (both run simultaneously)
    System.out.println("\nParallel execution with parZipWith:");
    long parStart = System.currentTimeMillis();
    IOPath<String> combined =
        fetchUser.parZipWith(fetchAge, (user, age) -> user + " is " + age + " years old");
    String result = combined.unsafeRun();
    long parTime = System.currentTimeMillis() - parStart;
    System.out.println("  Result: " + result + " (" + parTime + "ms)");

    System.out.println("  Speedup: " + String.format("%.1fx", (double) seqTime / parTime));
    System.out.println();
  }

  private static void parZip3And4Example() {
    System.out.println("--- parZip3 and parZip4: Multiple Parallel Ops ---");

    // Three independent API calls
    IOPath<String> fetchName = simulateApiCall("name-service", "Bob");
    IOPath<String> fetchEmail = simulateApiCall("email-service", "bob@example.com");
    IOPath<String> fetchRole = simulateApiCall("role-service", "Admin");

    // Combine three in parallel
    System.out.println("parZip3 - Three parallel operations:");
    long start = System.currentTimeMillis();

    IOPath<String> user3 =
        PathOps.parZip3(
            fetchName,
            fetchEmail,
            fetchRole,
            (name, email, role) ->
                "User{name=" + name + ", email=" + email + ", role=" + role + "}");
    String result3 = user3.unsafeRun();
    long time3 = System.currentTimeMillis() - start;
    System.out.println("  Result: " + result3);
    System.out.println("  Time: " + time3 + "ms (vs ~300ms sequential)");

    // Four independent API calls
    IOPath<String> fetchDept = simulateApiCall("dept-service", "Engineering");

    System.out.println("\nparZip4 - Four parallel operations:");
    start = System.currentTimeMillis();

    IOPath<String> user4 =
        PathOps.parZip4(
            fetchName,
            fetchEmail,
            fetchRole,
            fetchDept,
            (name, email, role, dept) ->
                "User{name="
                    + name
                    + ", email="
                    + email
                    + ", role="
                    + role
                    + ", dept="
                    + dept
                    + "}");
    String result4 = user4.unsafeRun();
    long time4 = System.currentTimeMillis() - start;
    System.out.println("  Result: " + result4);
    System.out.println("  Time: " + time4 + "ms (vs ~400ms sequential)");

    System.out.println();
  }

  private static void parSequenceExample() {
    System.out.println("--- parSequenceIO: Many Parallel Operations ---");

    // Create 10 independent API calls
    List<IOPath<String>> apiCalls =
        List.of(
            simulateApiCall("service-1", "Result-1"),
            simulateApiCall("service-2", "Result-2"),
            simulateApiCall("service-3", "Result-3"),
            simulateApiCall("service-4", "Result-4"),
            simulateApiCall("service-5", "Result-5"),
            simulateApiCall("service-6", "Result-6"),
            simulateApiCall("service-7", "Result-7"),
            simulateApiCall("service-8", "Result-8"),
            simulateApiCall("service-9", "Result-9"),
            simulateApiCall("service-10", "Result-10"));

    System.out.println("Running 10 API calls in parallel:");
    long start = System.currentTimeMillis();

    IOPath<List<String>> allResults = PathOps.parSequenceIO(apiCalls);
    List<String> results = allResults.unsafeRun();

    long time = System.currentTimeMillis() - start;
    System.out.println("  Results: " + results.size() + " items");
    System.out.println("  Time: " + time + "ms (vs ~1000ms sequential)");
    System.out.println("  Speedup: " + String.format("%.1fx", 1000.0 / time));

    System.out.println();
  }

  private static void raceExample() {
    System.out.println("--- race: First One Wins ---");

    // Race between fast and slow services
    // Useful for: timeout fallbacks, redundant services, fastest mirror

    IOPath<String> slowService =
        Path.io(
            () -> {
              sleep(200);
              return "Slow result";
            });

    IOPath<String> fastService =
        Path.io(
            () -> {
              sleep(50);
              return "Fast result";
            });

    IOPath<String> mediumService =
        Path.io(
            () -> {
              sleep(100);
              return "Medium result";
            });

    System.out.println("Racing three services:");
    long start = System.currentTimeMillis();

    // Race between two
    IOPath<String> twoRace = fastService.race(slowService);
    String winner2 = twoRace.unsafeRun();
    long time2 = System.currentTimeMillis() - start;
    System.out.println("  Two-way race winner: " + winner2 + " (" + time2 + "ms)");

    // Race between multiple using raceIO
    System.out.println("\nRacing with raceIO (list of paths):");
    start = System.currentTimeMillis();

    List<IOPath<String>> racers = List.of(slowService, mediumService, fastService);
    IOPath<String> multiRace = PathOps.raceIO(racers);
    String winnerMulti = multiRace.unsafeRun();
    long timeMulti = System.currentTimeMillis() - start;
    System.out.println("  Winner: " + winnerMulti + " (" + timeMulti + "ms)");

    System.out.println();
  }

  private static void performanceComparisonExample() {
    System.out.println("--- Performance: Sequential vs Parallel ---");

    int numCalls = 5;
    List<IOPath<Integer>> computations =
        IntStream.range(0, numCalls)
            .mapToObj(i -> simulateApiCallInt("compute-" + i, i * 10))
            .toList();

    // Sequential
    System.out.println("Sequential execution of " + numCalls + " operations:");
    long seqStart = System.currentTimeMillis();
    int seqSum = 0;
    for (IOPath<Integer> io : computations) {
      seqSum += io.unsafeRun();
    }
    long seqTime = System.currentTimeMillis() - seqStart;
    System.out.println("  Sum: " + seqSum + " in " + seqTime + "ms");

    // Parallel
    System.out.println("\nParallel execution of " + numCalls + " operations:");
    long parStart = System.currentTimeMillis();
    IOPath<List<Integer>> parResults = PathOps.parSequenceIO(computations);
    int parSum = parResults.unsafeRun().stream().mapToInt(Integer::intValue).sum();
    long parTime = System.currentTimeMillis() - parStart;
    System.out.println("  Sum: " + parSum + " in " + parTime + "ms");

    System.out.println("\nSpeedup: " + String.format("%.1fx faster", (double) seqTime / parTime));
    System.out.println();
  }

  private static void completableFuturePathExample() {
    System.out.println("--- CompletableFuturePath Parallel Ops ---");

    // CompletableFuturePath also supports parallel operations
    ExecutorService executor = Executors.newFixedThreadPool(4);

    try {
      // Create async paths
      CompletableFuturePath<String> fetchA =
          CompletableFuturePath.fromFuture(
              CompletableFuture.supplyAsync(
                  () -> {
                    sleep(SIMULATED_LATENCY_MS);
                    return "A";
                  },
                  executor));

      CompletableFuturePath<String> fetchB =
          CompletableFuturePath.fromFuture(
              CompletableFuture.supplyAsync(
                  () -> {
                    sleep(SIMULATED_LATENCY_MS);
                    return "B";
                  },
                  executor));

      // Combine in parallel
      System.out.println("parZipWith on CompletableFuturePath:");
      long start = System.currentTimeMillis();

      CompletableFuturePath<String> combined = fetchA.parZipWith(fetchB, (a, b) -> a + " + " + b);

      String result = combined.join();
      long time = System.currentTimeMillis() - start;
      System.out.println("  Result: " + result + " (" + time + "ms)");

      // Race example with CompletableFuturePath
      System.out.println("\nRacing CompletableFuturePaths:");
      CompletableFuturePath<String> fast =
          CompletableFuturePath.fromFuture(
              CompletableFuture.supplyAsync(
                  () -> {
                    sleep(50);
                    return "Fast!";
                  },
                  executor));

      CompletableFuturePath<String> slow =
          CompletableFuturePath.fromFuture(
              CompletableFuture.supplyAsync(
                  () -> {
                    sleep(200);
                    return "Slow!";
                  },
                  executor));

      start = System.currentTimeMillis();
      String winner = fast.race(slow).join();
      time = System.currentTimeMillis() - start;
      System.out.println("  Winner: " + winner + " (" + time + "ms)");

      // parSequence for multiple futures
      System.out.println("\nparSequenceFuture:");
      List<CompletableFuturePath<String>> futures =
          List.of(
              createAsyncPath(executor, "Item-1"),
              createAsyncPath(executor, "Item-2"),
              createAsyncPath(executor, "Item-3"),
              createAsyncPath(executor, "Item-4"),
              createAsyncPath(executor, "Item-5"));

      start = System.currentTimeMillis();
      List<String> results = PathOps.parSequenceFuture(futures).join();
      time = System.currentTimeMillis() - start;
      System.out.println("  Results: " + results);
      System.out.println("  Time: " + time + "ms (vs ~500ms sequential)");

    } finally {
      executor.shutdown();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    System.out.println();
  }

  // Helper methods

  private static IOPath<String> simulateApiCall(String service, String result) {
    return Path.io(
        () -> {
          sleep(SIMULATED_LATENCY_MS);
          return result;
        });
  }

  private static IOPath<Integer> simulateApiCallInt(String service, int result) {
    return Path.io(
        () -> {
          sleep(SIMULATED_LATENCY_MS);
          return result;
        });
  }

  private static CompletableFuturePath<String> createAsyncPath(
      ExecutorService executor, String value) {
    return CompletableFuturePath.fromFuture(
        CompletableFuture.supplyAsync(
            () -> {
              sleep(SIMULATED_LATENCY_MS);
              return value;
            },
            executor));
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
