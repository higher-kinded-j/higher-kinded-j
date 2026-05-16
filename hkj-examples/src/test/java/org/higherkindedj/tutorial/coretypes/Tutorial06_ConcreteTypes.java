// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 06: Concrete Types — choosing the right type for the job.
 *
 * <p>Pain → Promise. Java has at least four ad-hoc ways to model "might fail or might be absent":
 * {@code null}, {@code Optional}, checked exceptions, custom {@code Result} types. Each application
 * picks one (or three) inconsistently. The library's concrete types map the problem shape to the
 * right tool:
 *
 * <ul>
 *   <li>{@link Maybe} — value may be absent; no information about why.
 *   <li>{@link Either} — value or a typed error; fail-fast.
 *   <li>{@link Validated} — value or accumulated typed errors; for forms / batch validations.
 *   <li>{@link List} — zero-or-many values, often used as a Monad for Cartesian-product
 *       combinations.
 * </ul>
 *
 * <p>This tutorial walks through the decision: when to reach for which type.
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
   * Exercise 1: Either for typed errors.
   *
   * <pre>
   *   // Nudge:    Three branches: invalid range, too young, valid.
   *   // Strategy: if age &lt; 0 or &gt; 150 -&gt; Either.left("Invalid age");
   *   //           if age &lt; 18 -&gt; Either.left("Too young");
   *   //           else Either.right(age).
   *   // Spoiler:  see the conditional shape above.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: Either with typed errors for age validation")
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
   * Exercise 2: Maybe for optional values.
   *
   * <pre>
   *   // Nudge:    Two branches: known key returns Just; anything else returns Nothing.
   *   // Strategy: key.equals("key1") ? Maybe.just("value") : Maybe.nothing()
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: Maybe.just / Maybe.nothing for optional lookup")
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
   * Exercise 3: Maybe.getOrElse for defaults.
   *
   * <pre>
   *   // Nudge:    getOrElse takes the default and returns it on Nothing.
   *   // Strategy: present.getOrElse("Default") and absent.getOrElse("Default")
   *   // Spoiler:  same call shape for both.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: Maybe.getOrElse provides defaults for the absent case")
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
   * Exercise 4: List operations via Stream.
   *
   * <pre>
   *   // Nudge:    Three operations: filter even, multiply by 10, collect.
   *   // Strategy: numbers.stream().filter(n -&gt; n % 2 == 0).map(n -&gt; n * 10).toList()
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: filter / map / collect for List operations")
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
   * Exercise 5: Validated accumulates every error.
   *
   * <pre>
   *   // Nudge:    Use ValidatedMonad with a Semigroup; map3 across the three validations.
   *   // Strategy: var app = Instances.validated(Semigroups.string(", "));
   *   //           VALIDATED.narrow(app.map3(VALIDATED.widen(name), VALIDATED.widen(age),
   *   //                                     VALIDATED.widen(email), User::new))
   *   // Spoiler:  see hint above. Same call for valid and invalid inputs.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: Validated map3 accumulates errors via Semigroup")
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
   * Exercise 6: Converting Maybe to Either.
   *
   * <pre>
   *   // Nudge:    Pattern: present -&gt; Either.right; absent -&gt; Either.left("Not found").
   *   // Strategy: present.isJust() ? Either.right(present.get()) : Either.left("Not found")
   *   // Spoiler:  same shape for both.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: convert Maybe to Either")
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
   * Exercise 7: Choosing the right type for safe division.
   *
   * <pre>
   *   // Nudge:    Two implementations: Either when the error has meaning, Maybe when it doesn't.
   *   // Strategy: Either: b == 0 ? Either.left("Division by zero") : Either.right(a / b);
   *   //           Maybe:  b == 0 ? Maybe.nothing() : Maybe.just(a / b)
   *   // Spoiler:  see hint above.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: pick Either or Maybe by whether the error carries information")
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
