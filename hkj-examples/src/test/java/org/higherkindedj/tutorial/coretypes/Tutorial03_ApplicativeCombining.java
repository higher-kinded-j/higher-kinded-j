// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 03: Applicative — combining independent values.
 *
 * <p>Pain → Promise. When we want to build a single result from several independent computations,
 * imperative Java reaches for a stack of conditionals:
 *
 * <pre>
 *   ValidationResult&lt;String&gt; n = validateName(form.name);
 *   if (n.isError()) return ValidationResult.error(n.error());
 *   ValidationResult&lt;Integer&gt; a = validateAge(form.age);
 *   if (a.isError()) return ValidationResult.error(a.error());
 *   ValidationResult&lt;String&gt; e = validateEmail(form.email);
 *   if (e.isError()) return ValidationResult.error(e.error());
 *   return ValidationResult.ok(new User(n.get(), a.get(), e.get()));
 * </pre>
 *
 * <p>That code says "stop at the first error". When we instead want to <em>collect</em> every error
 * so the user fixes them in one round-trip, the imperative version becomes a manual list append per
 * branch.
 *
 * <p>{@link org.higherkindedj.hkt.Applicative Applicative} captures both shapes once. {@code map2},
 * {@code map3}, {@code map4}, {@code map5} take N independent {@code Kind} values and a combining
 * function, and return a single {@code Kind} of the combined result. The semantics of "what to do
 * on failure" are decided by the {@code Applicative} instance — {@code EitherMonad} short-circuits,
 * {@code ValidatedMonad} accumulates.
 *
 * <p>Java idiom anchor.
 *
 * <ul>
 *   <li>{@link java.util.concurrent.CompletableFuture#allOf} is the Applicative for futures: it
 *       runs N independent futures in parallel and only proceeds when all complete.
 *   <li>Bean Validation's {@code Set<ConstraintViolation>} is the same idea as {@code Validated}:
 *       gather every error, then report them.
 * </ul>
 *
 * <p>What we will do here:
 *
 * <ol>
 *   <li>Lift a plain value into context with {@code of} (also called {@code pure}).
 *   <li>Combine two values with {@code map2} via the {@link EitherMonad} typeclass.
 *   <li>Observe that {@code map2} on {@link Either} short-circuits to the first {@code Left}.
 *   <li>Validate three then four then five fields with {@code map3} / {@code map4} / {@code map5}.
 *   <li>Switch the typeclass to {@link ValidatedMonad} and watch errors accumulate.
 *   <li>Practise the diagnostic: applicative vs monadic when the steps are independent.
 * </ol>
 *
 * <p>For the typeclass deep-dive see <a
 * href="../../../../../../../../../hkj-book/src/functional/applicative.md">Applicative</a> in the
 * Foundations chapter.
 */
@DisplayName("Tutorial 03: Applicative Combining")
public class Tutorial03_ApplicativeCombining {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: Lift a plain value into Either
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Lifting plain values.
   *
   * <p>{@link Either#right(Object)} is the {@code Either} version of {@code Applicative.of}: it
   * lifts a plain value into the success channel.
   *
   * <p>Task: produce {@code Either.right(42)}.
   *
   * <pre>
   *   // Nudge:    Right is the success constructor.
   *   // Strategy: Either.right(...).
   *   // Spoiler:  Either.right(42)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: lift a value with Either.right (= Applicative.of)")
  void exercise1_liftingValues() {
    Either<String, Integer> result = answerRequired();

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo(42);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: Combine two values with map2 via EitherMonad
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: {@code map2} via the typeclass instance.
   *
   * <p>{@code Either} does not carry {@code map2} as an instance method; combinators live on the
   * typeclass instance ({@link EitherMonad}, which is also an {@code Applicative}). The pattern is:
   * get the instance, widen the inputs, call {@code map2}, narrow the output.
   *
   * <pre>
   *   // Nudge:    Combinators across multiple values live on the Applicative typeclass.
   *   // Strategy: app.map2(EITHER.widen(v1), EITHER.widen(v2), (a, b) -&gt; ...)
   *   // Spoiler:  EITHER.narrow(app.map2(EITHER.widen(value1), EITHER.widen(value2),
   *   //                                  (a, b) -&gt; a + b))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: map2 sums two Right values")
  void exercise2_combiningWithMap2() {
    Either<String, Integer> value1 = Either.right(10);
    Either<String, Integer> value2 = Either.right(20);
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Integer> result = answerRequired();

    assertThat(result.getRight()).isEqualTo(30);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: map2 short-circuits on Left
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: Either's Applicative is fail-fast.
   *
   * <p>If any input is {@code Left}, the combining function is never called and the first error is
   * returned. This mirrors what we would write by hand with nested conditionals.
   *
   * <pre>
   *   // Nudge:    Same shape as exercise 2; the second input is the Left.
   *   // Strategy: app.map2(widen(value1), widen(error), ...) returns the Left.
   *   // Spoiler:  EITHER.narrow(app.map2(EITHER.widen(value1), EITHER.widen(error),
   *   //                                  (a, b) -&gt; a + b))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: map2 short-circuits on the first Left")
  void exercise3_map2WithError() {
    Either<String, Integer> value1 = Either.right(10);
    Either<String, Integer> error = Either.left("Error occurred");
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Integer> result = answerRequired();

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Error occurred");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: Three-field validation with map3
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: Building a record from three independently-validated fields.
   *
   * <p>{@code map3} is the same shape as {@code map2}, just with one more {@code Kind} input. The
   * combining function takes one argument per input.
   *
   * <pre>
   *   // Nudge:    Same widen/narrow pattern; combiner takes three arguments.
   *   // Strategy: app.map3(widen(name), widen(age), widen(email), Person::new)
   *   // Spoiler:  EITHER.narrow(app.map3(EITHER.widen(name), EITHER.widen(age),
   *   //                                  EITHER.widen(email), Person::new))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: map3 builds a Person from three Rights")
  void exercise4_formValidationWithMap3() {
    record Person(String name, int age, String email) {}

    Either<String, String> name = Either.right("Alice");
    Either<String, Integer> age = Either.right(30);
    Either<String, String> email = Either.right("alice@example.com");
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Person> result = answerRequired();

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().name()).isEqualTo("Alice");
    assertThat(result.getRight().age()).isEqualTo(30);
    assertThat(result.getRight().email()).isEqualTo("alice@example.com");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5: Validated accumulates errors
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: Error accumulation via {@link Validated}.
   *
   * <p>Switching the {@code Applicative} instance from {@code EitherMonad} to {@link
   * ValidatedMonad} changes the failure semantics: instead of stopping at the first error,
   * Validated combines every error using a {@link Semigroup} (here: comma-separated strings).
   *
   * <p>This is the same pattern as Bean Validation's {@code Set<ConstraintViolation>}, except now
   * the accumulation is part of the type, not a hand-rolled side channel.
   *
   * <pre>
   *   // Nudge:    Same map3 shape, but with VALIDATED widen/narrow and ValidatedMonad.
   *   // Strategy: app.map3(VALIDATED.widen(name), VALIDATED.widen(age),
   *   //                    VALIDATED.widen(email), FormData::new)
   *   // Spoiler:  VALIDATED.narrow(app.map3(VALIDATED.widen(name), VALIDATED.widen(age),
   *   //                                     VALIDATED.widen(email), FormData::new))
   * </pre>
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

    Validated<String, FormData> result = answerRequired();

    assertThat(result.isInvalid()).isTrue();
    // The accumulated error contains every individual message, joined by the Semigroup.
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 6: Four-field successful validation
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: Four-field combination with {@code map4}.
   *
   * <p>{@code map4} extends the same pattern. The arity goes up to {@code map5} on the standard
   * Applicative; for more inputs, use a for-comprehension (a later journey).
   *
   * <pre>
   *   // Nudge:    Same widen/narrow pattern; combiner takes four arguments.
   *   // Strategy: app.map4(widen(id), widen(product), widen(quantity), widen(price), Order::new)
   *   // Spoiler:  EITHER.narrow(app.map4(EITHER.widen(id), EITHER.widen(product),
   *   //                                  EITHER.widen(quantity), EITHER.widen(price),
   *   //                                  Order::new))
   * </pre>
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

    Either<String, Order> result = answerRequired();

    assertThat(result.isRight()).isTrue();
    Order order = result.getRight();
    assertThat(order.id()).isEqualTo("ORD-001");
    assertThat(order.product()).isEqualTo("Laptop");
    assertThat(order.quantity()).isEqualTo(2);
    assertThat(order.price()).isEqualTo(999.99);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 7: Five-field combination with map5
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 7: Five-field combination with {@code map5}.
   *
   * <p>This is the largest fixed-arity combinator. Beyond five fields we either nest combinators or
   * use a for-comprehension; the latter is taught in the Expression journey.
   *
   * <pre>
   *   // Nudge:    Same shape, five inputs.
   *   // Strategy: app.map5(widen(street), widen(city), widen(state), widen(zip), widen(country),
   *   //                    Address::new)
   *   // Spoiler:  EITHER.narrow(app.map5(EITHER.widen(street), EITHER.widen(city),
   *   //                                  EITHER.widen(state), EITHER.widen(zip),
   *   //                                  EITHER.widen(country), Address::new))
   * </pre>
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

    Either<String, Address> result = answerRequired();

    assertThat(result.isRight()).isTrue();
    Address address = result.getRight();
    assertThat(address.street()).isEqualTo("123 Main St");
    assertThat(address.city()).isEqualTo("Springfield");
    assertThat(address.country()).isEqualTo("USA");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: Applicative vs Monad when the inputs are independent.
   *
   * <p>Both {@code map2} and a stack of {@code flatMap} calls can combine two {@code Right}s. They
   * are <em>not</em> equivalent when one of the inputs is a {@code Left}:
   *
   * <ul>
   *   <li>{@code map2} (Applicative) does not run the combining function and returns the first
   *       {@code Left}.
   *   <li>{@code flatMap} (Monad) does the same on {@code Either}, but on {@code Validated} the
   *       Monad is a different shape and would <em>not</em> accumulate. Validated is the canonical
   *       case where Applicative and Monad disagree on purpose.
   * </ul>
   *
   * <p>Rule of thumb: when the inputs <em>do not depend on each other</em>, prefer Applicative. It
   * signals intent and keeps the accumulating semantics available.
   *
   * <p>Task: combine two valid {@link Validated} values into a record using {@code map2}, then
   * confirm that combining with one invalid still produces an Invalid containing the message.
   *
   * <pre>
   *   // Nudge:    The shape is the same as exercises 5; reach for app.map2.
   *   // Strategy: VALIDATED.narrow(app.map2(VALIDATED.widen(a), VALIDATED.widen(b), Pair::new))
   *   // Spoiler:  see the solution file.
   * </pre>
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

    Validated<String, Pair> bothValid = answerRequired();
    assertThat(bothValid.isValid()).isTrue();
    assertThat(bothValid.get().name()).isEqualTo("Alice");

    Validated<String, Pair> oneInvalid = answerRequired();
    assertThat(oneInvalid.isInvalid()).isTrue();
  }

  /*
   * Where to next?
   *   • Tutorial 04 — Monad chaining. Applicative is for independent inputs; Monad is for
   *     dependent ones (each step decides what the next one looks like).
   *   • Foundations chapter — Applicative. Includes the classic "validating a form" worked
   *     example and the parallel-vs-sequential discussion.
   */
}
