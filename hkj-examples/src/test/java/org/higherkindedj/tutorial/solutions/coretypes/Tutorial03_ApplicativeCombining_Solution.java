// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 03: Applicative - Combining Independent Values
 *
 * <p>Applicative extends Functor and allows you to combine multiple independent values in context.
 * Unlike flatMap (which we'll see later), Applicative operations don't depend on previous results.
 *
 * <p>Key Concepts: - of (pure): lifts a plain value into the context - map2/map3/map4/map5:
 * combines 2/3/4/5 independent values - Perfect for validation where you want to collect all errors
 *
 * <p>When to use: - Combining independent computations - Validating multiple fields - Parallel
 * operations (all inputs are known upfront)
 */
public class Tutorial03_ApplicativeCombining_Solution {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Lifting values with 'of'
   *
   * <p>The 'of' method (also called 'pure') lifts a plain value into the context.
   *
   * <p>Task: Lift a plain integer into Either
   */
  @Test
  void exercise1_liftingValues() {
    // SOLUTION: Use Either.right() to lift the value into the Right context
    Either<String, Integer> result = Either.right(42);

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo(42);
  }

  /**
   * Exercise 2: Combining two values with map2
   *
   * <p>map2 combines two independent values in context using a combining function.
   *
   * <p>Task: Add two numbers that are both wrapped in Either
   */
  @Test
  void exercise2_combiningWithMap2() {
    Either<String, Integer> value1 = Either.right(10);
    Either<String, Integer> value2 = Either.right(20);

    // SOLUTION: Use EitherMonad typeclass to access map2
    EitherMonad<String> applicative = EitherMonad.instance();
    Either<String, Integer> result =
        EITHER.narrow(
            applicative.map2(EITHER.widen(value1), EITHER.widen(value2), (a, b) -> a + b));

    assertThat(result.getRight()).isEqualTo(30);
  }

  /**
   * Exercise 3: map2 short-circuits on Left
   *
   * <p>If any value is Left (error), map2 returns that error without calling the combining
   * function.
   *
   * <p>Task: Observe that combining with an error produces an error
   */
  @Test
  void exercise3_map2WithError() {
    Either<String, Integer> value1 = Either.right(10);
    Either<String, Integer> error = Either.left("Error occurred");

    // SOLUTION: Attempt to combine value1 and error using EitherMonad
    EitherMonad<String> applicative = EitherMonad.instance();
    Either<String, Integer> result =
        EITHER.narrow(applicative.map2(EITHER.widen(value1), EITHER.widen(error), (a, b) -> a + b));

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Error occurred");
  }

  /**
   * Exercise 4: Validating a form with map3
   *
   * <p>map3 combines three independent values. This is perfect for validating multiple fields.
   *
   * <p>Task: Create a Person record from three validated fields
   */
  @Test
  void exercise4_formValidationWithMap3() {
    record Person(String name, int age, String email) {}

    Either<String, String> name = Either.right("Alice");
    Either<String, Integer> age = Either.right(30);
    Either<String, String> email = Either.right("alice@example.com");

    // SOLUTION: Use EitherMonad typeclass to access map3
    EitherMonad<String> applicative = EitherMonad.instance();
    Either<String, Person> result =
        EITHER.narrow(
            applicative.map3(
                EITHER.widen(name),
                EITHER.widen(age),
                EITHER.widen(email),
                (n, a, e) -> new Person(n, a, e)));

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().name()).isEqualTo("Alice");
    assertThat(result.getRight().age()).isEqualTo(30);
    assertThat(result.getRight().email()).isEqualTo("alice@example.com");
  }

  /**
   * Exercise 5: Validation with Validated (accumulating errors)
   *
   * <p>Validated is like Either but accumulates ALL errors instead of stopping at the first one.
   * This is perfect for form validation.
   *
   * <p>Task: Validate multiple fields and see all errors accumulated
   */
  @Test
  void exercise5_accumulatingErrors() {
    record FormData(String name, int age, String email) {}

    // Simulate field validations (all invalid)
    Validated<String, String> name = Validated.invalid("Name is required");
    Validated<String, Integer> age = Validated.invalid("Age must be positive");
    Validated<String, String> email = Validated.invalid("Email is invalid");

    // SOLUTION: Use ValidatedMonad typeclass to access map3
    // Validated will accumulate errors instead of short-circuiting
    Semigroup<String> stringSemigroup = Semigroups.string(", ");
    ValidatedMonad<String> applicative = ValidatedMonad.instance(stringSemigroup);
    Validated<String, FormData> result =
        VALIDATED.narrow(
            applicative.map3(
                VALIDATED.widen(name),
                VALIDATED.widen(age),
                VALIDATED.widen(email),
                (n, a, e) -> new FormData(n, a, e)));

    assertThat(result.isInvalid()).isTrue();
    // Validated accumulates errors - in this case, it will contain one of the errors
    // (The exact behavior depends on the Semigroup instance for String)
  }

  /**
   * Exercise 6: Successful validation with map4
   *
   * <p>When all validations succeed, we get the combined result.
   *
   * <p>Task: Validate and create an Order
   */
  @Test
  void exercise6_successfulValidation() {
    record Order(String id, String product, int quantity, double price) {}

    Either<String, String> id = Either.right("ORD-001");
    Either<String, String> product = Either.right("Laptop");
    Either<String, Integer> quantity = Either.right(2);
    Either<String, Double> price = Either.right(999.99);

    // SOLUTION: Use EitherMonad typeclass to access map4
    EitherMonad<String> applicative = EitherMonad.instance();
    Either<String, Order> result =
        EITHER.narrow(
            applicative.map4(
                EITHER.widen(id),
                EITHER.widen(product),
                EITHER.widen(quantity),
                EITHER.widen(price),
                (i, p, q, pr) -> new Order(i, p, q, pr)));

    assertThat(result.isRight()).isTrue();
    Order order = result.getRight();
    assertThat(order.id()).isEqualTo("ORD-001");
    assertThat(order.product()).isEqualTo("Laptop");
    assertThat(order.quantity()).isEqualTo(2);
    assertThat(order.price()).isEqualTo(999.99);
  }

  /**
   * Exercise 7: Using Applicative typeclass with map5
   *
   * <p>For combining 5+ values, use map5 or the Applicative typeclass instance.
   *
   * <p>Task: Combine 5 values to create a complete address
   */
  @Test
  void exercise7_combiningFiveValues() {
    record Address(String street, String city, String state, String zip, String country) {}

    Either<String, String> street = Either.right("123 Main St");
    Either<String, String> city = Either.right("Springfield");
    Either<String, String> state = Either.right("IL");
    Either<String, String> zip = Either.right("62701");
    Either<String, String> country = Either.right("USA");

    // SOLUTION: Use EitherMonad typeclass to access map5
    EitherMonad<String> applicative = EitherMonad.instance();
    Either<String, Address> result =
        EITHER.narrow(
            applicative.map5(
                EITHER.widen(street),
                EITHER.widen(city),
                EITHER.widen(state),
                EITHER.widen(zip),
                EITHER.widen(country),
                (s, c, st, z, co) -> new Address(s, c, st, z, co)));

    assertThat(result.isRight()).isTrue();
    Address address = result.getRight();
    assertThat(address.street()).isEqualTo("123 Main St");
    assertThat(address.city()).isEqualTo("Springfield");
    assertThat(address.country()).isEqualTo("USA");
  }

  /**
   * Congratulations! You've completed Tutorial 03: Applicative Combining
   *
   * <p>You now understand: ✓ How to lift values into context with 'of' ✓ How to combine independent
   * values with map2/map3/map4/map5 ✓ That Applicative operations don't depend on previous results
   * ✓ How to validate forms with multiple fields ✓ The difference between Either (fail-fast) and
   * Validated (accumulate errors)
   *
   * <p>Next: Tutorial 04 - Monad Chaining
   */
}
