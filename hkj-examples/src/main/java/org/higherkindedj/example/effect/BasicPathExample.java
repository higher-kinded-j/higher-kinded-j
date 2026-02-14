// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;

/**
 * Basic examples of the Effect Path API.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Creating paths from values
 *   <li>Transforming values with map
 *   <li>Chaining computations with via
 *   <li>Extracting values with terminal operations
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.BasicPathExample}
 */
public class BasicPathExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Basic Examples ===\n");

    maybePathBasics();
    eitherPathBasics();
    tryPathBasics();
    conversionsBetweenTypes();
  }

  private static void maybePathBasics() {
    System.out.println("--- MaybePath Basics ---");

    // Creating paths
    MaybePath<String> greeting = Path.just("Hello");
    MaybePath<String> empty = Path.nothing();

    // Transforming with map
    MaybePath<Integer> length = greeting.map(String::length);
    System.out.println("Length of 'Hello': " + length.getOrElse(0)); // 5

    // Chaining with via
    MaybePath<String> result =
        greeting
            .map(String::toUpperCase)
            .via(s -> s.length() > 3 ? Path.just(s) : Path.nothing())
            .map(s -> s + "!");

    System.out.println("Transformed greeting: " + result.getOrElse("(empty)")); // HELLO!

    // Working with empty paths
    String emptyResult = empty.map(String::toUpperCase).getOrElse("default");
    System.out.println("Empty result: " + emptyResult); // default

    // Using peek for side effects (debugging)
    Path.just(42)
        .peek(v -> System.out.println("Value before transform: " + v))
        .map(v -> v * 2)
        .peek(v -> System.out.println("Value after transform: " + v));

    System.out.println();
  }

  private static void eitherPathBasics() {
    System.out.println("--- EitherPath Basics ---");

    // Creating paths
    EitherPath<String, Integer> success = Path.right(42);
    EitherPath<String, Integer> failure = Path.left("Something went wrong");

    // Transforming right values
    EitherPath<String, String> mapped = success.map(v -> "Value is: " + v);
    System.out.println(
        "Mapped success: " + mapped.run().fold(err -> "Error: " + err, val -> val)); // Value is: 42

    // Errors short-circuit
    EitherPath<String, String> mappedFailure = failure.map(v -> "Value is: " + v);
    System.out.println(
        "Mapped failure: "
            + mappedFailure
                .run()
                .fold(err -> "Error: " + err, val -> val)); // Error: Something went wrong

    // Recovery
    EitherPath<String, Integer> recovered = failure.recover(err -> -1);
    System.out.println("Recovered value: " + recovered.run().getRight()); // -1

    // Error transformation
    EitherPath<Integer, Integer> errorMapped = failure.mapError(String::length);
    System.out.println("Error code (length): " + errorMapped.run().getLeft()); // 20

    System.out.println();
  }

  private static void tryPathBasics() {
    System.out.println("--- TryPath Basics ---");

    // Creating from computation that may throw
    TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt("42"));
    System.out.println("Parsed '42': " + parsed.getOrElse(-1)); // 42

    TryPath<Integer> failed = Path.tryOf(() -> Integer.parseInt("not a number"));
    System.out.println("Parsed 'not a number': " + failed.getOrElse(-1)); // -1

    // Chaining operations that may throw
    TryPath<Double> calculation =
        Path.success(10)
            .map(x -> x * 2)
            .via(x -> Path.tryOf(() -> 100.0 / x)) // Safe division
            .map(x -> x + 1);

    System.out.println("Calculation result: " + calculation.getOrElse(0.0)); // 6.0

    // Recovery from specific exceptions
    TryPath<Integer> withRecovery =
        Path.<Integer>tryOf(
                () -> {
                  throw new IllegalArgumentException("Invalid input");
                })
            .recover(ex -> -1);

    System.out.println("Recovered from exception: " + withRecovery.getOrElse(0)); // -1

    System.out.println();
  }

  private static void conversionsBetweenTypes() {
    System.out.println("--- Conversions Between Types ---");

    // MaybePath to EitherPath
    MaybePath<String> maybe = Path.just("hello");
    EitherPath<String, String> either = maybe.toEitherPath("was nothing");
    System.out.println("Maybe to Either: " + either.run()); // Right[hello]

    MaybePath<String> nothing = Path.nothing();
    EitherPath<String, String> eitherFromNothing = nothing.toEitherPath("was nothing");
    System.out.println("Nothing to Either: " + eitherFromNothing.run()); // Left[was nothing]

    // TryPath to MaybePath
    TryPath<Integer> trySuccess = Path.success(42);
    MaybePath<Integer> maybeFromTry = trySuccess.toMaybePath();
    System.out.println(
        "Try(success) to Maybe: " + (maybeFromTry.run().isJust() ? "Just" : "Nothing")); // Just

    TryPath<Integer> tryFailure = Path.failure(new RuntimeException("error"));
    MaybePath<Integer> maybeFromFailure = tryFailure.toMaybePath();
    System.out.println(
        "Try(failure) to Maybe: "
            + (maybeFromFailure.run().isJust() ? "Just" : "Nothing")); // Nothing

    // TryPath to EitherPath
    EitherPath<Throwable, Integer> eitherFromTry = trySuccess.toEitherPath(ex -> ex);
    System.out.println("Try to Either: " + eitherFromTry.run()); // Right[42]

    System.out.println();
  }
}
