// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Examples demonstrating sequential composition with the Effect Path API.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Chaining dependent computations with {@code via} and {@code flatMap}
 *   <li>Using {@code then} for sequencing with ignored results
 *   <li>Building complex pipelines
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ChainedComputationsExample}
 */
public class ChainedComputationsExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Chained Computations ===\n");

    basicChaining();
    dependentChaining();
    usingThen();
    ioChaining();
  }

  private static void basicChaining() {
    System.out.println("--- Basic Chaining with via ---");

    // via chains computations where the next step depends on the previous result
    MaybePath<String> result =
        Path.just(5)
            .via(n -> n > 0 ? Path.just(n * 2) : Path.nothing()) // 10
            .via(n -> Path.just("Result: " + n)); // "Result: 10"

    System.out.println("Chained result: " + result.getOrElse("no value")); // Result: 10

    // via with Nothing short-circuits
    MaybePath<String> shortCircuited =
        Path.just(-5)
            .via(n -> n > 0 ? Path.just(n * 2) : Path.nothing()) // Nothing
            .via(n -> Path.just("Result: " + n)); // Never executed

    System.out.println(
        "Short-circuited: " + shortCircuited.getOrElse("computation stopped")); // computation
    // stopped

    System.out.println();
  }

  private static void dependentChaining() {
    System.out.println("--- Dependent Computations ---");

    // Simulating a user lookup followed by permission check
    record User(String id, String name, String role) {}

    // User database (simulated)
    Function<String, MaybePath<User>> findUser =
        id ->
            switch (id) {
              case "1" -> Path.just(new User("1", "Alice", "admin"));
              case "2" -> Path.just(new User("2", "Bob", "user"));
              default -> Path.nothing();
            };

    // Permission check depends on user
    Function<User, MaybePath<String>> checkPermission =
        user ->
            user.role().equals("admin")
                ? Path.just("Access granted to " + user.name())
                : Path.nothing();

    // Chain: find user -> check permission
    MaybePath<String> accessResult = findUser.apply("1").via(checkPermission);

    System.out.println("Admin access: " + accessResult.getOrElse("Access denied")); // Access
    // granted to Alice

    MaybePath<String> userAccessResult = findUser.apply("2").via(checkPermission);

    System.out.println(
        "User access: " + userAccessResult.getOrElse("Access denied")); // Access denied

    MaybePath<String> unknownUserResult = findUser.apply("999").via(checkPermission);

    System.out.println(
        "Unknown user: " + unknownUserResult.getOrElse("User not found")); // User not found

    System.out.println();
  }

  private static void usingThen() {
    System.out.println("--- Using then() for Sequencing ---");

    // then() executes effects in sequence, discarding the first result
    // Useful when you want side effects but don't need the value

    AtomicInteger step = new AtomicInteger();

    MaybePath<String> sequence =
        Path.just("start")
            .peek(s -> System.out.println("Step 1: " + s))
            .then(
                () -> {
                  step.incrementAndGet();
                  return Path.just("middle");
                })
            .peek(s -> System.out.println("Step 2: " + s))
            .then(
                () -> {
                  step.incrementAndGet();
                  return Path.just("end");
                })
            .peek(s -> System.out.println("Step 3: " + s));

    System.out.println("Final value: " + sequence.getOrElse("none"));
    System.out.println("Steps executed: " + step.get());

    System.out.println();
  }

  private static void ioChaining() {
    System.out.println("--- IO Chaining (Deferred Execution) ---");

    // IOPath chains are lazy - nothing happens until unsafeRun()
    IOPath<String> deferredChain =
        Path.io(() -> "Hello")
            .map(String::toUpperCase)
            .via(s -> Path.io(() -> s + " WORLD"))
            .map(s -> s + "!");

    System.out.println("IO chain created (nothing executed yet)");
    System.out.println("Executing IO chain: " + deferredChain.unsafeRun()); // HELLO WORLD!

    // Demonstrating lazy evaluation
    System.out.println("\n--- Lazy Evaluation Demo ---");
    AtomicInteger counter = new AtomicInteger();

    IOPath<Integer> lazyChain =
        Path.io(
                () -> {
                  System.out.println("Computing first value...");
                  return counter.incrementAndGet();
                })
            .via(
                n ->
                    Path.io(
                        () -> {
                          System.out.println("Computing second value based on: " + n);
                          return n * 10;
                        }));

    System.out.println("Chain created. Counter is: " + counter.get()); // 0
    System.out.println("Running chain...");
    System.out.println("Result: " + lazyChain.unsafeRun()); // 10
    System.out.println("Counter after run: " + counter.get()); // 1

    System.out.println();
  }
}
