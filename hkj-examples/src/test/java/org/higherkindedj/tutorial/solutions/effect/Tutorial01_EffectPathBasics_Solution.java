// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 01: Effect Path Basics - The Primary API for Higher-Kinded-J
 *
 * <p>Effect Paths are the recommended way to work with functional effects in Higher-Kinded-J. They
 * wrap effect types (Maybe, Either, Try, IO) in a fluent, composable API that handles the
 * complexity of higher-kinded types for you.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Effect Paths wrap effect types in a fluent API
 *   <li>The {@code Path} class provides factory methods for creating paths
 *   <li>{@code map} transforms values; {@code via} chains dependent computations
 *   <li>Error recovery with {@code recover}, {@code recoverWith}, {@code orElse}
 *   <li>{@code zipWith} combines independent computations
 * </ul>
 *
 * <p>See the documentation: Effect Path Overview in hkj-book
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial01_EffectPathBasics_Solution {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Part 1: Creating Paths
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Creating MaybePath
   *
   * <p>MaybePath represents computations that might not have a value. It wraps the Maybe type,
   * providing Just (present) or Nothing (absent) states.
   *
   * <p>Task: Create MaybePath values using Path factory methods
   */
  @Test
  void exercise1_creatingMaybePath() {
    // Path.just wraps a present value
    // SOLUTION: Use Path.just() to create a present MaybePath
    MaybePath<String> present = Path.just("hello");

    assertThat(present.run().isJust()).isTrue();
    assertThat(present.getOrElse("default")).isEqualTo("hello");

    // Path.nothing represents absence
    // SOLUTION: Use Path.nothing() to create an absent MaybePath
    MaybePath<String> absent = Path.nothing();

    assertThat(absent.run().isNothing()).isTrue();
    assertThat(absent.getOrElse("default")).isEqualTo("default");

    // Path.maybe wraps a nullable value (null becomes Nothing)
    String nullable = null;
    // SOLUTION: Use Path.maybe() to wrap a nullable value
    MaybePath<String> fromNullable = Path.maybe(nullable);

    assertThat(fromNullable.run().isNothing()).isTrue();

    // Non-null values become Just
    MaybePath<String> fromNonNull = Path.maybe("world");
    assertThat(fromNonNull.run().isJust()).isTrue();
  }

  /**
   * Exercise 2: Creating EitherPath
   *
   * <p>EitherPath represents computations that can fail with a typed error. Left holds the error,
   * Right holds the success value. This is the workhorse for error handling.
   *
   * <p>Task: Create EitherPath values for success and failure cases
   */
  @Test
  void exercise2_creatingEitherPath() {
    // Path.right for success
    // SOLUTION: Use Path.right() to create a success EitherPath
    EitherPath<String, Integer> success = Path.right(42);

    assertThat(success.run().isRight()).isTrue();
    assertThat(success.run().getRight()).isEqualTo(42);

    // Path.left for failure
    // SOLUTION: Use Path.left() to create a failure EitherPath
    EitherPath<String, Integer> failure = Path.left("Invalid input");

    assertThat(failure.run().isLeft()).isTrue();
    assertThat(failure.run().getLeft()).isEqualTo("Invalid input");

    // Path.either wraps an existing Either value
    Either<String, Integer> either = Either.right(100);
    // SOLUTION: Use Path.either() to wrap an existing Either
    EitherPath<String, Integer> fromEither = Path.either(either);

    assertThat(fromEither.run().getRight()).isEqualTo(100);
  }

  /**
   * Exercise 3: TryPath and IOPath
   *
   * <p>TryPath handles exception-throwing code safely, wrapping the result in Success or Failure.
   * IOPath wraps side-effecting computations that are deferred until explicitly run.
   *
   * <p>Task: Use TryPath for exception handling and IOPath for deferred effects
   */
  @Test
  void exercise3_tryPathAndIOPath() {
    // Path.tryOf wraps a potentially throwing computation
    // SOLUTION: Use Path.tryOf() to safely wrap exception-throwing code
    TryPath<Integer> successTry = Path.tryOf(() -> Integer.parseInt("42"));

    assertThat(successTry.run().isSuccess()).isTrue();
    assertThat(successTry.getOrElse(-1)).isEqualTo(42);

    // Invalid input creates a failure
    TryPath<Integer> failureTry = Path.tryOf(() -> Integer.parseInt("not a number"));
    assertThat(failureTry.run().isFailure()).isTrue();

    // Path.io wraps a side-effecting computation (deferred execution)
    var counter = new int[] {0};
    // SOLUTION: Use Path.io() to defer a side-effecting computation
    IOPath<Integer> ioPath =
        Path.io(
            () -> {
              counter[0]++;
              return counter[0];
            });

    // IO is lazy - the counter is not incremented yet
    assertThat(counter[0]).isEqualTo(0);

    // unsafeRun executes the effect
    Integer result = ioPath.unsafeRun();
    assertThat(counter[0]).isEqualTo(1);
    assertThat(result).isEqualTo(1);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Part 2: Transforming and Chaining
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: Transforming with map
   *
   * <p>All Effect Paths support {@code map}, which transforms the success value while preserving
   * the effect structure. Errors pass through unchanged.
   *
   * <p>Task: Use map to transform values in different path types
   */
  @Test
  void exercise4_transformingWithMap() {
    // map transforms the value inside MaybePath
    MaybePath<String> name = Path.just("alice");
    // SOLUTION: Use map to transform the value
    MaybePath<String> upperName = name.map(String::toUpperCase);

    assertThat(upperName.getOrElse("")).isEqualTo("ALICE");

    // map on Nothing returns Nothing (no value to transform)
    MaybePath<String> absent = Path.<String>nothing().map(String::toUpperCase);
    assertThat(absent.run().isNothing()).isTrue();

    // map on EitherPath transforms Right, passes Left through unchanged
    EitherPath<String, Integer> success = Path.right(10);
    // SOLUTION: Use map to double the value
    EitherPath<String, Integer> doubled = success.map(n -> n * 2);

    assertThat(doubled.run().getRight()).isEqualTo(20);

    // Left values pass through map unchanged
    EitherPath<String, Integer> failure = Path.<String, Integer>left("error").map(n -> n * 2);
    assertThat(failure.run().getLeft()).isEqualTo("error");
  }

  /**
   * Exercise 5: Chaining with via
   *
   * <p>The {@code via} method (also available as {@code flatMap}) chains computations where each
   * step depends on the result of the previous step. If any step fails, the chain short-circuits.
   *
   * <p>Task: Chain multiple operations using via
   */
  @Test
  void exercise5_chainingWithVia() {
    // Helper functions that return paths
    Function<String, EitherPath<String, Integer>> parseNumber =
        s -> {
          try {
            return Path.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Path.left("Not a number: " + s);
          }
        };

    Function<Integer, EitherPath<String, Integer>> validatePositive =
        n -> n > 0 ? Path.right(n) : Path.left("Must be positive");

    Function<Integer, EitherPath<String, Double>> divideHundredBy =
        n -> n != 0 ? Path.right(100.0 / n) : Path.left("Division by zero");

    // Chain the operations: parse -> validate -> divide
    EitherPath<String, String> input = Path.right("25");

    // SOLUTION: Chain operations with via
    EitherPath<String, Double> result =
        input.via(parseNumber).via(validatePositive).via(divideHundredBy);

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(4.0);

    // Failure at any step short-circuits the entire chain
    EitherPath<String, String> invalidInput = Path.right("not-a-number");
    EitherPath<String, Double> failedResult =
        invalidInput.via(parseNumber).via(validatePositive).via(divideHundredBy);

    assertThat(failedResult.run().isLeft()).isTrue();
    assertThat(failedResult.run().getLeft()).isEqualTo("Not a number: not-a-number");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Part 3: Error Recovery
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: Error Recovery
   *
   * <p>Effect Paths provide several ways to recover from errors:
   *
   * <ul>
   *   <li>{@code recover}: Replace error with a success value
   *   <li>{@code recoverWith}: Replace error with another path computation
   *   <li>{@code orElse}: Provide an alternative path when this one fails
   *   <li>{@code mapError}: Transform the error type
   * </ul>
   *
   * <p>Task: Use recovery methods to handle failures
   */
  @Test
  void exercise6_errorRecovery() {
    EitherPath<String, Integer> failure = Path.left("Not found");

    // recover: Replace error with a default value
    // SOLUTION: Use recover to provide a fallback value
    EitherPath<String, Integer> recovered = failure.recover(err -> -1);

    assertThat(recovered.run().isRight()).isTrue();
    assertThat(recovered.run().getRight()).isEqualTo(-1);

    // recoverWith: Replace error with another computation
    // SOLUTION: Use recoverWith to provide a fallback path
    EitherPath<String, Integer> recoveredWith = failure.recoverWith(err -> Path.right(0));

    assertThat(recoveredWith.run().getRight()).isEqualTo(0);

    // orElse: Provide an alternative path
    EitherPath<String, Integer> primary = Path.left("Primary failed");
    EitherPath<String, Integer> fallback = Path.right(42);

    // SOLUTION: Use orElse to provide an alternative
    EitherPath<String, Integer> withFallback = primary.orElse(() -> fallback);

    assertThat(withFallback.run().getRight()).isEqualTo(42);

    // mapError: Transform the error type
    EitherPath<String, Integer> stringError = Path.left("error");
    // SOLUTION: Use mapError to transform the error
    EitherPath<Integer, Integer> intError = stringError.mapError(String::length);

    assertThat(intError.run().getLeft()).isEqualTo(5);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Part 4: Combining and Real-World Usage
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 7: Combining Paths
   *
   * <p>Use {@code zipWith} to combine independent path computations. Both paths run and their
   * results are combined with a function. If either fails, the result fails.
   *
   * <p>Task: Combine multiple paths to build composite values
   */
  @Test
  void exercise7_combiningPaths() {
    record User(String name, int age) {}

    MaybePath<String> namePath = Path.just("Alice");
    MaybePath<Integer> agePath = Path.just(30);

    // SOLUTION: Use zipWith to combine paths
    MaybePath<User> userPath = namePath.zipWith(agePath, User::new);

    User user = userPath.getOrElse(new User("unknown", 0));
    assertThat(user.name()).isEqualTo("Alice");
    assertThat(user.age()).isEqualTo(30);

    // If either path is Nothing, result is Nothing
    MaybePath<String> absentName = Path.nothing();
    MaybePath<User> partialUser = absentName.zipWith(agePath, User::new);
    assertThat(partialUser.run().isNothing()).isTrue();

    // EitherPath zipWith works similarly (first error wins)
    EitherPath<String, String> firstName = Path.right("Alice");
    EitherPath<String, String> lastName = Path.right("Smith");

    // SOLUTION: Use zipWith to combine names
    EitherPath<String, String> fullName = firstName.zipWith(lastName, (f, l) -> f + " " + l);

    assertThat(fullName.run().getRight()).isEqualTo("Alice Smith");
  }

  /**
   * Exercise 8: Real-World Workflow
   *
   * <p>Combine path creation, transformation, chaining, and error recovery in a realistic user
   * lookup and validation scenario.
   *
   * <p>Task: Build a complete workflow using Effect Paths
   */
  @Test
  void exercise8_realWorldWorkflow() {
    record User(String id, String name, String email) {}

    // Simulated database lookup
    Function<String, EitherPath<String, User>> findUser =
        id -> {
          if (id.equals("u1")) {
            return Path.right(new User("u1", "Alice", "alice@example.com"));
          } else if (id.equals("u2")) {
            return Path.right(new User("u2", "Bob", ""));
          }
          return Path.left("User not found: " + id);
        };

    // Validate email is present
    Function<User, EitherPath<String, User>> validateEmail =
        user ->
            user.email().isEmpty()
                ? Path.left("Email required for user: " + user.name())
                : Path.right(user);

    // Build the workflow: find user -> validate email -> extract email -> uppercase
    // SOLUTION: Chain all operations together
    EitherPath<String, String> workflow =
        Path.<String, String>right("u1")
            .via(findUser)
            .via(validateEmail)
            .map(User::email)
            .map(String::toUpperCase);

    String result = workflow.run().fold(e -> "Error: " + e, email -> email);
    assertThat(result).isEqualTo("ALICE@EXAMPLE.COM");

    // Test with user who has no email
    EitherPath<String, String> invalidWorkflow =
        Path.<String, String>right("u2").via(findUser).via(validateEmail).map(User::email);

    assertThat(invalidWorkflow.run().isLeft()).isTrue();
    assertThat(invalidWorkflow.run().getLeft()).contains("Email required");

    // Test with non-existent user, with recovery
    EitherPath<String, String> recoveredWorkflow =
        Path.<String, String>right("u999")
            .via(findUser)
            .recover(err -> new User("default", "Guest", "guest@example.com"))
            .map(User::email);

    assertThat(recoveredWorkflow.run().getRight()).isEqualTo("guest@example.com");
  }

  /**
   * Congratulations! You have completed Tutorial 01: Effect Path Basics
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to create paths with Path.just, Path.nothing, Path.maybe
   *   <li>✓ How to create paths with Path.right, Path.left, Path.either
   *   <li>✓ How to use TryPath for exception handling and IOPath for deferred effects
   *   <li>✓ How to transform values with map
   *   <li>✓ How to chain dependent computations with via (flatMap)
   *   <li>✓ How to recover from errors with recover, recoverWith, orElse, mapError
   *   <li>✓ How to combine independent paths with zipWith
   *   <li>✓ How to build real-world workflows combining all operations
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>Effect Paths are the primary, user-friendly API for Higher-Kinded-J
   *   <li>Use {@code map} for simple transformations, {@code via} for dependent chains
   *   <li>Errors short-circuit the chain; use recovery methods to handle them
   *   <li>The Path factory class provides all creation methods
   * </ul>
   *
   * <p>Next: Tutorial 02 - Effect Path Advanced (ForPath, Contexts, Annotations)
   */
}
