// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 06: Concrete Types - Choosing the Right Type for the Job
 *
 * <p>Now that you understand Functor, Applicative, and Monad, let's explore the concrete types and
 * when to use each one.
 *
 * <p>Common Types: - Either<L, R>: Represents a value that can be Left (error) or Right (success) -
 * Maybe<A>: Represents a value that might be absent - List<A>: Represents multiple values -
 * Validated<E, A>: Like Either, but accumulates all errors
 */
public class Tutorial06_ConcreteTypes {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Either for error handling
   *
   * <p>Use Either when you want explicit error types and fail-fast behavior.
   *
   * <p>Task: Create a function that validates a user's age
   */
  @Test
  void exercise1_eitherForErrorHandling() {
    Function<Integer, Either<String, Integer>> validateAge =
        age -> {
          // TODO: Replace null with validation logic
          // Return Either.left("Too young") if age < 18
          // Return Either.left("Invalid age") if age < 0 or age > 150
          // Otherwise return Either.right(age)
          return answerRequired();
        };

    assertThat(validateAge.apply(25).isRight()).isTrue();
    assertThat(validateAge.apply(25).getRight()).isEqualTo(25);

    assertThat(validateAge.apply(15).isLeft()).isTrue();
    assertThat(validateAge.apply(15).getLeft()).isEqualTo("Too young");

    assertThat(validateAge.apply(-5).isLeft()).isTrue();
    assertThat(validateAge.apply(-5).getLeft()).isEqualTo("Invalid age");
  }

  /**
   * Exercise 2: Maybe for optional values
   *
   * <p>Use Maybe when a value might be absent, but you don't need to explain why.
   *
   * <p>Task: Look up a value in a simple key-value store
   */
  @Test
  void exercise2_maybeForOptionalValues() {
    Function<String, Maybe<String>> lookup =
        key -> {
          // TODO: Replace null with lookup logic
          // Return Maybe.just("value") if key equals "key1"
          // Return Maybe.nothing() otherwise
          return answerRequired();
        };

    Maybe<String> found = lookup.apply("key1");
    assertThat(found.isJust()).isTrue();
    assertThat(found.get()).isEqualTo("value");

    Maybe<String> notFound = lookup.apply("key2");
    assertThat(notFound.isNothing()).isTrue();
  }

  /**
   * Exercise 3: Maybe with getOrElse
   *
   * <p>Maybe provides convenient methods for handling the absent case.
   *
   * <p>Task: Use getOrElse to provide a default value
   */
  @Test
  void exercise3_maybeWithDefault() {
    Maybe<String> present = Maybe.just("Hello");
    Maybe<String> absent = Maybe.nothing();

    // TODO: Replace null with code that gets the value or returns "Default"
    // Hint: Use .getOrElse(...)
    String result1 = answerRequired();
    String result2 = answerRequired();

    assertThat(result1).isEqualTo("Hello");
    assertThat(result2).isEqualTo("Default");
  }

  /**
   * Exercise 4: List for multiple values
   *
   * <p>Use List when you have zero or more values and want to work with all of them.
   *
   * <p>Task: Filter and transform a list of numbers using Java streams
   */
  @Test
  void exercise4_listOperations() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    // TODO: Replace null with code that:
    // 1. Creates a stream from numbers
    // 2. Filters to only even numbers
    // 3. Maps to multiply each by 10
    // 4. Collects back to a list
    // Hint: numbers.stream().filter(n -> n % 2 == 0).map(n -> n * 10).collect(Collectors.toList())
    List<Integer> result = answerRequired();

    assertThat(result).containsExactly(20, 40, 60, 80, 100);
  }

  /**
   * Exercise 5: Validated for accumulating errors
   *
   * <p>Use Validated when you want to collect ALL errors, not just the first one.
   *
   * <p>Task: Create field validators that accumulate errors
   */
  @Test
  void exercise5_validatedAccumulatesErrors() {
    record User(String name, int age, String email) {}

    Function<String, Validated<String, String>> validateName =
        name -> name.length() >= 2 ? Validated.valid(name) : Validated.invalid("Name too short");

    Function<Integer, Validated<String, Integer>> validateAge =
        age -> age >= 18 ? Validated.valid(age) : Validated.invalid("Must be 18+");

    Function<String, Validated<String, String>> validateEmail =
        email -> email.contains("@") ? Validated.valid(email) : Validated.invalid("Invalid email");

    // Valid case
    Validated<String, String> validName = validateName.apply("Alice");
    Validated<String, Integer> validAge = validateAge.apply(25);
    Validated<String, String> validEmail = validateEmail.apply("alice@example.com");

    // TODO: Replace null with code that combines the three validations using map3
    Validated<String, User> validUser = answerRequired();

    assertThat(validUser.isValid()).isTrue();

    // Invalid case - multiple errors
    Validated<String, String> invalidName = validateName.apply("A");
    Validated<String, Integer> invalidAge = validateAge.apply(15);
    Validated<String, String> invalidEmail = validateEmail.apply("not-an-email");

    // TODO: Replace null with code that combines the invalid validations
    Validated<String, User> invalidUser = answerRequired();

    assertThat(invalidUser.isInvalid()).isTrue();
    // Validated accumulates errors (implementation-dependent on how Semigroup works)
  }

  /**
   * Exercise 6: Converting between types
   *
   * <p>You can convert between Maybe, Either, and other types.
   *
   * <p>Task: Convert Maybe to Either
   */
  @Test
  void exercise6_convertingTypes() {
    Maybe<String> present = Maybe.just("value");
    Maybe<String> absent = Maybe.nothing();

    // TODO: Replace null with code that converts Maybe to Either
    // Hint: Use pattern: present.isJust() ? Either.right(present.get()) : Either.left("Not found")
    Either<String, String> either1 = answerRequired();
    Either<String, String> either2 = answerRequired();

    assertThat(either1.isRight()).isTrue();
    assertThat(either1.getRight()).isEqualTo("value");

    assertThat(either2.isLeft()).isTrue();
    assertThat(either2.getLeft()).isEqualTo("Not found");
  }

  /**
   * Exercise 7: Choosing the right type
   *
   * <p>Let's practice choosing the right type for different scenarios.
   *
   * <p>Task: Implement a safe division function
   */
  @Test
  void exercise7_choosingTheRightType() {
    // Option 1: Use Either to provide an error message
    Function<Integer, Function<Integer, Either<String, Integer>>> safeDivideEither =
        a ->
            b -> {
              // TODO: Replace null with Either-based division
              // Return Left("Division by zero") if b == 0
              // Otherwise return Right(a / b)
              return answerRequired();
            };

    assertThat(safeDivideEither.apply(10).apply(2).getRight()).isEqualTo(5);
    assertThat(safeDivideEither.apply(10).apply(0).getLeft()).isEqualTo("Division by zero");

    // Option 2: Use Maybe if you don't need an error message
    Function<Integer, Function<Integer, Maybe<Integer>>> safeDivideMaybe =
        a ->
            b -> {
              // TODO: Replace null with Maybe-based division
              // Return Nothing if b == 0
              // Otherwise return Just(a / b)
              return answerRequired();
            };

    assertThat(safeDivideMaybe.apply(10).apply(2).get()).isEqualTo(5);
    assertThat(safeDivideMaybe.apply(10).apply(0).isNothing()).isTrue();
  }

  /**
   * Congratulations! You've completed Tutorial 06: Concrete Types
   *
   * <p>You now understand: ✓ When to use Either (explicit error handling) ✓ When to use Maybe
   * (optional values without error details) ✓ When to use List (working with multiple values) ✓
   * When to use Validated (accumulating all errors) ✓ How to convert between different types ✓ How
   * to choose the right type for your use case
   *
   * <p>Next: Tutorial 07 - Real World Examples
   */
}
