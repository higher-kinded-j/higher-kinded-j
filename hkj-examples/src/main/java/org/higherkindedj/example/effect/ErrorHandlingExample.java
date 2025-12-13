// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;

/**
 * Examples demonstrating error handling patterns with the Effect Path API.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Using {@code recover} to provide fallback values
 *   <li>Using {@code recoverWith} for fallback computations
 *   <li>Using {@code orElse} for alternative paths
 *   <li>Error transformation with {@code mapError} and {@code mapException}
 *   <li>Retry patterns with IOPath
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ErrorHandlingExample}
 */
public class ErrorHandlingExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Error Handling ===\n");

    maybeRecovery();
    eitherRecovery();
    tryRecovery();
    errorTransformation();
    retryPattern();
  }

  private static void maybeRecovery() {
    System.out.println("--- MaybePath Recovery ---");

    // recover() provides a fallback value when Nothing
    MaybePath<Integer> nothing = Path.nothing();
    MaybePath<Integer> recovered = nothing.recover(unit -> 42);
    System.out.println("Recovered from Nothing: " + recovered.getOrElse(-1)); // 42

    // recover() doesn't affect Just values
    MaybePath<Integer> just = Path.just(10);
    MaybePath<Integer> notRecovered = just.recover(unit -> 42);
    System.out.println("Just value unchanged: " + notRecovered.getOrElse(-1)); // 10

    // orElse() provides an alternative path
    MaybePath<String> primary = Path.nothing();
    MaybePath<String> secondary = Path.nothing();
    MaybePath<String> tertiary = Path.just("fallback");

    MaybePath<String> result = primary.orElse(() -> secondary).orElse(() -> tertiary);

    System.out.println("Chained orElse: " + result.getOrElse("none")); // fallback

    // recoverWith() for more complex recovery
    MaybePath<Integer> complexRecovery =
        Path.<Integer>nothing()
            .recoverWith(
                unit -> {
                  System.out.println("Performing complex recovery...");
                  return Path.just(100);
                });

    System.out.println("Complex recovery result: " + complexRecovery.getOrElse(-1)); // 100

    System.out.println();
  }

  private static void eitherRecovery() {
    System.out.println("--- EitherPath Recovery ---");

    // recover() handles Left (error) case
    EitherPath<String, Integer> error = Path.left("Database connection failed");
    EitherPath<String, Integer> recovered = error.recover(err -> -1);
    System.out.println("Recovered from error: " + recovered.run().getRight()); // -1

    // recoverWith() for fallback computation
    EitherPath<String, Integer> primary = Path.left("Primary failed");
    EitherPath<String, Integer> fallbackResult =
        primary.recoverWith(
            err -> {
              System.out.println("Primary failed with: " + err);
              return Path.right(42);
            });

    System.out.println("Fallback result: " + fallbackResult.run().getRight()); // 42

    // orElse() for alternative computation
    EitherPath<String, String> mainService = Path.left("Service unavailable");
    EitherPath<String, String> backupService = Path.right("Backup response");

    EitherPath<String, String> result = mainService.orElse(() -> backupService);

    System.out.println("Service result: " + result.run().getRight()); // Backup response

    System.out.println();
  }

  private static void tryRecovery() {
    System.out.println("--- TryPath Recovery ---");

    // recover() handles exceptions
    TryPath<Integer> failing =
        Path.tryOf(
            () -> {
              throw new RuntimeException("Computation failed");
            });
    TryPath<Integer> recovered = failing.recover(ex -> -1);
    System.out.println("Recovered from exception: " + recovered.getOrElse(0)); // -1

    // recover() with exception inspection
    TryPath<String> withInspection =
        Path.<String>tryOf(
                () -> {
                  throw new IllegalArgumentException("Invalid input: foo");
                })
            .recover(ex -> "Error: " + ex.getMessage());

    System.out.println("With inspection: " + withInspection.getOrElse("")); // Error: Invalid input:
    // foo

    // recoverWith() for fallback computation
    TryPath<Integer> complex =
        Path.<Integer>tryOf(
                () -> {
                  throw new RuntimeException("Primary failed");
                })
            .recoverWith(
                ex -> {
                  System.out.println("Caught: " + ex.getMessage());
                  return Path.success(100);
                });

    System.out.println("Complex recovery: " + complex.getOrElse(0)); // 100

    System.out.println();
  }

  private static void errorTransformation() {
    System.out.println("--- Error Transformation ---");

    // Define a typed error
    record AppError(String code, String message) {}

    // EitherPath mapError transforms error type
    EitherPath<String, Integer> rawError = Path.left("Connection timeout");
    EitherPath<AppError, Integer> typedError =
        rawError.mapError(msg -> new AppError("ERR_TIMEOUT", msg));

    typedError
        .run()
        .fold(
            err -> {
              System.out.println("Typed error code: " + err.code());
              System.out.println("Typed error message: " + err.message());
              return null;
            },
            val -> null);

    // TryPath mapException transforms exception type
    TryPath<Integer> original =
        Path.tryOf(
            () -> {
              throw new RuntimeException("Low-level error");
            });

    TryPath<Integer> wrapped =
        original.mapException(ex -> new IllegalStateException("Wrapped: " + ex.getMessage(), ex));

    wrapped
        .run()
        .fold(
            val -> null,
            ex -> {
              System.out.println("Wrapped exception type: " + ex.getClass().getSimpleName());
              System.out.println("Wrapped exception message: " + ex.getMessage());
              return null;
            });

    System.out.println();
  }

  private static void retryPattern() {
    System.out.println("--- Retry Pattern with IOPath ---");

    java.util.concurrent.atomic.AtomicInteger attempts =
        new java.util.concurrent.atomic.AtomicInteger(0);

    // Simulating an operation that fails twice then succeeds
    IOPath<String> unreliableOperation =
        Path.io(
            () -> {
              int attempt = attempts.incrementAndGet();
              System.out.println("Attempt " + attempt);
              if (attempt < 3) {
                throw new RuntimeException("Temporary failure");
              }
              return "Success on attempt " + attempt;
            });

    // Manual retry with handleErrorWith
    IOPath<String> withRetry =
        unreliableOperation
            .handleErrorWith(ex1 -> unreliableOperation)
            .handleErrorWith(ex2 -> unreliableOperation)
            .handleError(ex -> "All retries failed: " + ex.getMessage());

    String result = withRetry.unsafeRun();
    System.out.println("Final result: " + result); // Success on attempt 3
    System.out.println("Total attempts: " + attempts.get()); // 3

    System.out.println();
  }
}
