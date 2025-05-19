// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.lazy;

import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.lazy.LazyKind;
import org.higherkindedj.hkt.lazy.LazyMonad;

public class LazyExample {

  public static void main(String[] args) {
    // Creating Lazy Instances
    // 1. Deferring a computation (that might throw checked exception)
    java.util.concurrent.atomic.AtomicInteger counter =
        new java.util.concurrent.atomic.AtomicInteger(0);
    Kind<LazyKind.Witness, String> deferredLazy =
        defer(
            () -> {
              System.out.println("Executing expensive computation...");
              counter.incrementAndGet();
              // Simulate potential failure
              if (System.currentTimeMillis() % 2 == 0) {
                // Throwing a checked exception is allowed by ThrowableSupplier
                throw new java.io.IOException("Simulated IO failure");
              }
              Thread.sleep(50); // Simulate work
              return "Computed Value";
            });

    // 2. Creating an already evaluated Lazy
    Kind<LazyKind.Witness, String> nowLazy = now("Precomputed Value");

    // 3. Using the underlying Lazy type directly (less common when using HKT)
    Lazy<String> directLazy =
        Lazy.defer(
            () -> {
              counter.incrementAndGet();
              return "Direct Lazy";
            });

    // Forcing Evaluation
    System.out.println("Lazy instances created. Counter: " + counter.get()); // Output: 0

    try {
      // Force the deferred computation
      String result1 = force(deferredLazy); // force() throws Throwable
      System.out.println("Result 1: " + result1);
      System.out.println("Counter after first force: " + counter.get()); // Output: 1

      // Force again - uses memoised result
      String result2 = force(deferredLazy);
      System.out.println("Result 2: " + result2);
      System.out.println(
          "Counter after second force: " + counter.get()); // Output: 1 (not re-computed)

      // Force the 'now' instance
      String resultNow = force(nowLazy);
      System.out.println("Result Now: " + resultNow);
      System.out.println(
          "Counter after forcing 'now': "
              + counter.get()); // Output: 1 (no computation ran for 'now')

    } catch (Throwable t) { // Catch Throwable because force() can re-throw anything
      System.err.println("Caught exception during force: " + t);
      // Exception is also memoized:
      try {
        force(deferredLazy);
      } catch (Throwable t2) {
        System.err.println("Caught memoized exception: " + t2);
        System.out.println("Counter after failed force: " + counter.get()); // Output: 1
      }
    }

    // Using LazyMonad (map and flatMap)
    LazyMonad lazyMonad = new LazyMonad();
    counter.set(0); // Reset counter for this example

    Kind<LazyKind.Witness, Integer> initialLazy =
        defer(
            () -> {
              counter.incrementAndGet();
              return 10;
            });

    // --- map ---
    // Apply a function lazily
    Function<Integer, String> toStringMapper = i -> "Value: " + i;
    Kind<LazyKind.Witness, String> mappedLazy = lazyMonad.map(toStringMapper, initialLazy);

    System.out.println("Mapped Lazy created. Counter: " + counter.get()); // Output: 0

    try {
      System.out.println(
          "Mapped Result: " + force(mappedLazy)); // Triggers evaluation of initialLazy & map
      // Output: Mapped Result: Value: 10
      System.out.println("Counter after forcing mapped: " + counter.get()); // Output: 1
    } catch (Throwable t) {
      /* ... */
    }

    // --- flatMap ---
    // Sequence lazy computations
    Function<Integer, Kind<LazyKind.Witness, String>> multiplyAndStringifyLazy =
        i ->
            defer(
                () -> { // Inner computation is also lazy
                  int result = i * 5;
                  return "Multiplied: " + result;
                });

    Kind<LazyKind.Witness, String> flatMappedLazy =
        lazyMonad.flatMap(multiplyAndStringifyLazy, initialLazy);

    System.out.println(
        "FlatMapped Lazy created. Counter: "
            + counter.get()); // Output: 1 (map already forced initialLazy)

    try {
      System.out.println(
          "FlatMapped Result: " + force(flatMappedLazy)); // Triggers evaluation of inner lazy
      // Output: FlatMapped Result: Multiplied: 50
    } catch (Throwable t) {
      /* ... */
    }

    // --- Chaining ---
    Kind<LazyKind.Witness, String> chainedLazy =
        lazyMonad.flatMap(
            value1 ->
                lazyMonad.map(
                    value2 -> "Combined: " + value1 + " & " + value2, // Combine results
                    defer(() -> value1 * 2) // Second lazy step, depends on result of first
                    ),
            defer(() -> 5) // First lazy step
            );

    try {
      System.out.println("Chained Result: " + force(chainedLazy)); // Output: Combined: 5 & 10
    } catch (Throwable t) {
      /* ... */
    }
  }
}
