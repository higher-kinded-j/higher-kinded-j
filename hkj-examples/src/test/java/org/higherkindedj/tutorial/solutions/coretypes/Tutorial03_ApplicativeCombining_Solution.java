// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Solution for Tutorial 03: Applicative Combining — teaching-solution format. */
@DisplayName("Tutorial 03 Solution: Applicative Combining")
public class Tutorial03_ApplicativeCombining_Solution {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code Either.right(x)} is the literal "lift a value into success" call.
   * The Applicative typeclass spelling is {@code app.of(x)}; the concrete-type spelling is shorter
   * when we already know we want an {@code Either}.
   *
   * <p>Alternative: {@code app.of(42)} via {@code Instances.monadError(either())}. Useful when the
   * surrounding code is generic in {@code F}.
   *
   * <p>Common wrong attempt: writing {@code Either.<String, Integer>right(42)} when the type can be
   * inferred — works, but adds noise. Reach for explicit type witnesses only when inference
   * actually fails.
   */
  @Test
  @DisplayName("Exercise 1: lift a value with Either.right (= Applicative.of)")
  void exercise1_liftingValues() {
    Either<String, Integer> result = Either.right(42);

    assertThatEither(result).isRight().hasRight(42);
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: combinators across multiple containers live on the {@code Applicative}
   * typeclass instance, not on the concrete type. Widen the inputs, call the combinator, narrow the
   * output. Three steps, no surprises.
   *
   * <p>Alternative: {@code value1.flatMap(a -> value2.map(b -> a + b))}. Same answer, but uses the
   * heavier Monad capability and forces a sequential mental model where the values are actually
   * independent.
   *
   * <p>Common wrong attempt: calling {@code value1.map2(value2, ...)}. {@code Either} does not
   * carry {@code map2} as an instance method; the compiler error points us at the Applicative
   * typeclass instead.
   */
  @Test
  @DisplayName("Exercise 2: map2 sums two Right values")
  void exercise2_combiningWithMap2() {
    Either<String, Integer> value1 = Either.right(10);
    Either<String, Integer> value2 = Either.right(20);
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Integer> result =
        EITHER.narrow(app.map2(EITHER.widen(value1), EITHER.widen(value2), (a, b) -> a + b));

    assertThatEither(result).hasRight(30);
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: identical shape to exercise 2 — the only thing that changes is which
   * inputs are {@code Right} vs {@code Left}. Either's Applicative is fail-fast: the first {@code
   * Left} wins.
   *
   * <p>Alternative: explicit branching with {@code if (value1.isLeft())}. Strictly more code; the
   * {@code map2} form expresses intent in one call.
   *
   * <p>Common wrong attempt: assuming the combiner runs even when one input is Left. It does not.
   * If we need both errors when both fail, that is what {@link Validated} (exercise 5) is for.
   */
  @Test
  @DisplayName("Exercise 3: map2 short-circuits on the first Left")
  void exercise3_map2WithError() {
    Either<String, Integer> value1 = Either.right(10);
    Either<String, Integer> error = Either.left("Error occurred");
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Integer> result =
        EITHER.narrow(app.map2(EITHER.widen(value1), EITHER.widen(error), (a, b) -> a + b));

    assertThatEither(result).isLeft().hasLeft("Error occurred");
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code Person::new} is a method reference to the canonical record
   * constructor. Three positional fields in, one record out. Reads as "make a Person".
   *
   * <p>Alternative: an explicit lambda {@code (n, a, e) -> new Person(n, a, e)}. Equivalent;
   * verbose where the constructor reference suffices.
   *
   * <p>Common wrong attempt: nested {@code flatMap} chains across the three fields. Works, but
   * loses the parallel-friendly semantics: an {@code Applicative} can in principle evaluate the
   * inputs concurrently, a {@code Monad} cannot.
   */
  @Test
  @DisplayName("Exercise 4: map3 builds a Person from three Rights")
  void exercise4_formValidationWithMap3() {
    record Person(String name, int age, String email) {}

    Either<String, String> name = Either.right("Alice");
    Either<String, Integer> age = Either.right(30);
    Either<String, String> email = Either.right("alice@example.com");
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Person> result =
        EITHER.narrow(
            app.map3(EITHER.widen(name), EITHER.widen(age), EITHER.widen(email), Person::new));

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            person -> {
              assertThat(person.name()).isEqualTo("Alice");
              assertThat(person.age()).isEqualTo(30);
              assertThat(person.email()).isEqualTo("alice@example.com");
            });
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code Instances.validated(semigroup)} parameterises the Applicative by
   * how to combine errors. The Semigroup is the only thing that changes between "comma-separated
   * string" and "list of typed errors"; the surrounding code is unchanged.
   *
   * <p>Alternative: a different {@link Semigroup} — for example {@code Semigroups.list()} when the
   * error type is {@code List<DomainError>}. Same call shape, different accumulation behaviour.
   *
   * <p>Common wrong attempt: trying to use {@code EitherMonad} for accumulation. Either is
   * fail-fast by definition; for accumulation we want Validated.
   */
  @Test
  @DisplayName("Exercise 5: ValidatedMonad accumulates errors via a Semigroup")
  void exercise5_accumulatingErrors() {
    record FormData(String name, int age, String email) {}

    Validated<String, String> name = Validated.invalid("Name is required");
    Validated<String, Integer> age = Validated.invalid("Age must be positive");
    Validated<String, String> email = Validated.invalid("Email is invalid");

    Semigroup<String> stringSemigroup = Semigroups.string(", ");
    MonadError<ValidatedKind.Witness<String>, String> app = Instances.validated(stringSemigroup);

    Validated<String, FormData> result =
        VALIDATED.narrow(
            app.map3(
                VALIDATED.widen(name),
                VALIDATED.widen(age),
                VALIDATED.widen(email),
                FormData::new));

    assertThatValidated(result).isInvalid();
  }

  // ─── Exercise 6 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: same shape as map3, one more input. {@code Order::new} as a method
   * reference is the cleanest possible spelling.
   *
   * <p>Alternative: an explicit four-arg lambda. No advantage.
   *
   * <p>Common wrong attempt: trying to extend the chain past five inputs. {@code map5} is the
   * largest fixed-arity combinator; for more, see the for-comprehension in the Expression journey.
   */
  @Test
  @DisplayName("Exercise 6: map4 builds an Order from four Rights")
  void exercise6_successfulValidation() {
    record Order(String id, String product, int quantity, double price) {}

    Either<String, String> id = Either.right("ORD-001");
    Either<String, String> product = Either.right("Laptop");
    Either<String, Integer> quantity = Either.right(2);
    Either<String, Double> price = Either.right(999.99);
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Order> result =
        EITHER.narrow(
            app.map4(
                EITHER.widen(id),
                EITHER.widen(product),
                EITHER.widen(quantity),
                EITHER.widen(price),
                Order::new));

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            order -> {
              assertThat(order.id()).isEqualTo("ORD-001");
              assertThat(order.product()).isEqualTo("Laptop");
              assertThat(order.quantity()).isEqualTo(2);
              assertThat(order.price()).isEqualTo(999.99);
            });
  }

  // ─── Exercise 7 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code map5} caps the fixed-arity ladder. Beyond that, switch to a
   * for-comprehension; conceptually it is the same idea desugared.
   *
   * <p>Alternative: nested {@code map4} + {@code map2} compositions. Possible; not an improvement.
   *
   * <p>Common wrong attempt: hoping for {@code map6}. There isn't one (deliberately) — five is
   * already the threshold where a for-comprehension reads better.
   */
  @Test
  @DisplayName("Exercise 7: map5 builds an Address from five Rights")
  void exercise7_combiningFiveValues() {
    record Address(String street, String city, String state, String zip, String country) {}

    Either<String, String> street = Either.right("123 Main St");
    Either<String, String> city = Either.right("Springfield");
    Either<String, String> state = Either.right("IL");
    Either<String, String> zip = Either.right("62701");
    Either<String, String> country = Either.right("USA");
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Address> result =
        EITHER.narrow(
            app.map5(
                EITHER.widen(street),
                EITHER.widen(city),
                EITHER.widen(state),
                EITHER.widen(zip),
                EITHER.widen(country),
                Address::new));

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            address -> {
              assertThat(address.street()).isEqualTo("123 Main St");
              assertThat(address.city()).isEqualTo("Springfield");
              assertThat(address.country()).isEqualTo("USA");
            });
  }

  // ─── Diagnostic ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code Pair::new} as the combiner; same widen/narrow pattern. The same
   * call works whether the inputs are Valid or Invalid — the typeclass picks the right semantics.
   *
   * <p>Alternative: write the diagnostic as nested {@code flatMap} on Validated to see that the
   * Monad instance does <em>not</em> accumulate. Useful exercise for the Advanced journey.
   *
   * <p>Common wrong attempt: assuming {@code Validated.flatMap} would also accumulate. It does not
   * — Monad is sequential by definition (the second step depends on the first), and "depends on"
   * precludes "ran independently then combined errors". When we want accumulation, use the
   * Applicative form.
   */
  @Test
  @DisplayName("Diagnostic: prefer Applicative when inputs are independent")
  void diagnostic_applicativeVsMonad() {
    record Pair(String name, int age) {}

    Validated<String, String> nameOk = Validated.valid("Alice");
    Validated<String, Integer> ageOk = Validated.valid(30);
    Validated<String, Integer> ageBad = Validated.invalid("Age must be positive");

    Semigroup<String> stringSemigroup = Semigroups.string(", ");
    MonadError<ValidatedKind.Witness<String>, String> app = Instances.validated(stringSemigroup);

    Validated<String, Pair> bothValid =
        VALIDATED.narrow(app.map2(VALIDATED.widen(nameOk), VALIDATED.widen(ageOk), Pair::new));
    assertThatValidated(bothValid)
        .isValid()
        .hasValueSatisfying(pair -> "Alice".equals(pair.name()), "name is Alice");

    Validated<String, Pair> oneInvalid =
        VALIDATED.narrow(app.map2(VALIDATED.widen(nameOk), VALIDATED.widen(ageBad), Pair::new));
    assertThatValidated(oneInvalid).isInvalid();
  }
}
