// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.resilience.RetryExhaustedException;
import org.higherkindedj.hkt.resilience.RetryPolicy;

/**
 * Examples demonstrating resilience patterns with IOPath and RetryPolicy.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>{@link RetryPolicy} - configurable retry strategies
 *   <li>Fixed delay, exponential backoff, exponential with jitter
 *   <li>{@code IOPath.withRetry} and {@code IOPath.retry} methods
 *   <li>Selective retry based on exception type
 *   <li>Handling {@link RetryExhaustedException}
 *   <li>Real-world patterns: flaky services, network failures
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ResilienceExample}
 */
public class ResilienceExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Resilience Patterns ===\n");

    basicRetryExample();
    retryPolicyTypesExample();
    selectiveRetryExample();
    exhaustedRetriesExample();
    flakyServiceSimulation();
    completableFutureRetryExample();
    combinedPatternsExample();
  }

  private static void basicRetryExample() {
    System.out.println("--- Basic Retry Usage ---");

    AtomicInteger attempts = new AtomicInteger(0);

    // An operation that fails the first 2 times, then succeeds
    IOPath<String> flakyOperation =
        Path.io(
            () -> {
              int attempt = attempts.incrementAndGet();
              System.out.println("  Attempt " + attempt);
              if (attempt < 3) {
                throw new RuntimeException("Transient failure!");
              }
              return "Success on attempt " + attempt;
            });

    // Simple retry: 5 attempts with 100ms initial delay (exponential backoff + jitter)
    System.out.println("Using retry(5, 100ms):");
    IOPath<String> withRetry = flakyOperation.retry(5, Duration.ofMillis(100));

    String result = withRetry.unsafeRun();
    System.out.println("Result: " + result);
    System.out.println();
  }

  private static void retryPolicyTypesExample() {
    System.out.println("--- RetryPolicy Types ---");

    // 1. Fixed delay - same wait between each attempt
    System.out.println("1. Fixed delay (3 attempts, 200ms delay):");
    RetryPolicy fixed = RetryPolicy.fixed(3, Duration.ofMillis(200));
    demonstratePolicy(fixed);

    // 2. Exponential backoff - delay doubles each time
    System.out.println("\n2. Exponential backoff (4 attempts, 100ms initial):");
    RetryPolicy exponential = RetryPolicy.exponentialBackoff(4, Duration.ofMillis(100));
    // Delays: 100ms, 200ms, 400ms
    demonstratePolicy(exponential);

    // 3. Exponential with jitter - randomized to prevent thundering herd
    System.out.println("\n3. Exponential with jitter (4 attempts, 100ms initial):");
    RetryPolicy jitter = RetryPolicy.exponentialBackoffWithJitter(4, Duration.ofMillis(100));
    demonstratePolicy(jitter);

    // 4. No retry - fail immediately
    System.out.println("\n4. No retry:");
    RetryPolicy noRetry = RetryPolicy.noRetry();
    System.out.println("  Max attempts: " + noRetry.maxAttempts());

    System.out.println();
  }

  private static void demonstratePolicy(RetryPolicy policy) {
    System.out.println("  Max attempts: " + policy.maxAttempts());
    System.out.println("  Initial delay: " + policy.initialDelay());
    System.out.println("  Sample delays:");
    for (int i = 0; i < Math.min(4, policy.maxAttempts()); i++) {
      System.out.println("    Attempt " + (i + 1) + " delay: " + policy.delayForAttempt(i));
    }
  }

  private static void selectiveRetryExample() {
    System.out.println("--- Selective Retry (Exception Filtering) ---");

    AtomicInteger attempts = new AtomicInteger(0);

    // Only retry on UncheckedIOException, not on IllegalArgumentException
    RetryPolicy policy =
        RetryPolicy.fixed(5, Duration.ofMillis(50)).retryOn(UncheckedIOException.class);

    // Operation that throws UncheckedIOException (should retry)
    IOPath<String> ioErrorOp =
        Path.io(
            () -> {
              int attempt = attempts.incrementAndGet();
              System.out.println("  Attempt " + attempt + " (UncheckedIOException case)");
              if (attempt < 3) {
                throw new UncheckedIOException(new IOException("Network error"));
              }
              return "Recovered from UncheckedIOException";
            });

    System.out.println("UncheckedIOException (should retry):");
    String result = ioErrorOp.withRetry(policy).unsafeRun();
    System.out.println("Result: " + result);

    // Operation that throws IllegalArgumentException (should NOT retry)
    attempts.set(0);
    IOPath<String> illegalArgOp =
        Path.io(
            () -> {
              int attempt = attempts.incrementAndGet();
              System.out.println("  Attempt " + attempt + " (IllegalArgumentException case)");
              throw new IllegalArgumentException("Invalid input");
            });

    System.out.println("\nIllegalArgumentException (should NOT retry):");
    try {
      illegalArgOp.withRetry(policy).unsafeRun();
    } catch (IllegalArgumentException e) {
      System.out.println("Caught immediately (no retry): " + e.getMessage());
    }

    // Custom predicate: only retry on errors with "transient" in message
    System.out.println("\nCustom predicate (only 'transient' errors):");
    attempts.set(0);
    RetryPolicy customPolicy =
        RetryPolicy.fixed(5, Duration.ofMillis(50))
            .retryIf(ex -> ex.getMessage() != null && ex.getMessage().contains("transient"));

    IOPath<String> transientOp =
        Path.io(
            () -> {
              int attempt = attempts.incrementAndGet();
              System.out.println("  Attempt " + attempt);
              if (attempt < 3) {
                throw new RuntimeException("transient failure");
              }
              return "Recovered";
            });

    result = transientOp.withRetry(customPolicy).unsafeRun();
    System.out.println("Result: " + result);

    System.out.println();
  }

  private static void exhaustedRetriesExample() {
    System.out.println("--- Handling Exhausted Retries ---");

    AtomicInteger attempts = new AtomicInteger(0);

    // Operation that always fails
    IOPath<String> alwaysFails =
        Path.io(
            () -> {
              attempts.incrementAndGet();
              throw new RuntimeException("Persistent failure");
            });

    RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(50));

    System.out.println("Attempting operation with 3 retries:");
    try {
      alwaysFails.withRetry(policy).unsafeRun();
    } catch (RetryExhaustedException e) {
      System.out.println("Caught RetryExhaustedException!");
      System.out.println("  Attempts made: " + e.getAttempts());
      System.out.println("  Last error: " + e.getCause().getMessage());
      System.out.println("  Message: " + e.getMessage());
    }

    System.out.println("Total attempts: " + attempts.get());
    System.out.println();
  }

  private static void flakyServiceSimulation() {
    System.out.println("--- Flaky Service Simulation ---");

    // Simulate a service that has 60% chance of failure
    AtomicInteger totalCalls = new AtomicInteger(0);
    AtomicInteger failures = new AtomicInteger(0);

    IOPath<String> flakyService =
        Path.io(
            () -> {
              totalCalls.incrementAndGet();
              // 60% failure rate
              if (Math.random() < 0.6) {
                failures.incrementAndGet();
                throw new RuntimeException("Service unavailable");
              }
              return "Service response: OK";
            });

    // With retry, we should almost always succeed
    RetryPolicy resilientPolicy =
        RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(50))
            .withMaxDelay(Duration.ofMillis(500));

    System.out.println("Making 10 requests to a 60% failure-rate service:");
    int successes = 0;
    for (int i = 0; i < 10; i++) {
      try {
        flakyService.withRetry(resilientPolicy).unsafeRun();
        successes++;
      } catch (RetryExhaustedException e) {
        System.out.println(
            "  Request " + (i + 1) + " failed after " + e.getAttempts() + " attempts");
      }
    }

    System.out.println("\nResults:");
    System.out.println("  Successful requests: " + successes + "/10");
    System.out.println("  Total API calls: " + totalCalls.get());
    System.out.println("  Failures: " + failures.get());
    System.out.println("  Success rate improved from 40% to " + (successes * 10) + "%");

    System.out.println();
  }

  private static void completableFutureRetryExample() {
    System.out.println("--- CompletableFuturePath Retry ---");

    AtomicInteger attempts = new AtomicInteger(0);

    // CompletableFuturePath also supports retry
    CompletableFuturePath<String> asyncOp =
        CompletableFuturePath.supplyAsync(
            () -> {
              int attempt = attempts.incrementAndGet();
              System.out.println("  Async attempt " + attempt);
              if (attempt < 3) {
                throw new RuntimeException("Async failure");
              }
              return "Async success!";
            });

    RetryPolicy policy = RetryPolicy.fixed(5, Duration.ofMillis(100));

    System.out.println("Retrying async operation:");
    String result = asyncOp.withRetry(policy).join();
    System.out.println("Result: " + result);

    System.out.println();
  }

  private static void combinedPatternsExample() {
    System.out.println("--- Combined Patterns: Retry + Fallback ---");

    AtomicInteger primaryCalls = new AtomicInteger(0);
    AtomicInteger fallbackCalls = new AtomicInteger(0);

    // Primary service - fails sometimes
    IOPath<String> primaryService =
        Path.io(
            () -> {
              primaryCalls.incrementAndGet();
              if (Math.random() < 0.7) {
                throw new RuntimeException("Primary service down");
              }
              return "Response from PRIMARY";
            });

    // Fallback service - always works but slower
    IOPath<String> fallbackService =
        Path.io(
            () -> {
              fallbackCalls.incrementAndGet();
              try {
                Thread.sleep(50); // Simulate slower response
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return "Response from FALLBACK";
            });

    // Strategy: Try primary with retries, fall back if exhausted
    RetryPolicy quickRetry = RetryPolicy.fixed(2, Duration.ofMillis(50));

    IOPath<String> resilientCall =
        Path.io(
            () -> {
              try {
                return primaryService.withRetry(quickRetry).unsafeRun();
              } catch (RetryExhaustedException e) {
                System.out.println("  Primary exhausted, using fallback");
                return fallbackService.unsafeRun();
              }
            });

    System.out.println("Making 5 resilient requests:");
    int primarySuccesses = 0;
    int fallbackSuccesses = 0;

    for (int i = 0; i < 5; i++) {
      primaryCalls.set(0);
      fallbackCalls.set(0);

      String result = resilientCall.unsafeRun();
      if (result.contains("PRIMARY")) {
        primarySuccesses++;
      } else {
        fallbackSuccesses++;
      }
      System.out.println("  Request " + (i + 1) + ": " + result);
    }

    System.out.println("\nSummary:");
    System.out.println("  Primary successes: " + primarySuccesses);
    System.out.println("  Fallback successes: " + fallbackSuccesses);
    System.out.println("  Overall success rate: 100% (with resilience)");

    System.out.println();
  }
}
