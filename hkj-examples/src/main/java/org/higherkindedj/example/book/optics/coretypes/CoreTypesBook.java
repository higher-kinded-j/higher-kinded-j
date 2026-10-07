// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.coretypes;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddressLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.EitherTraversals;
import org.higherkindedj.optics.util.MaybeTraversals;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/core_type_integration.html">Working with
 * Core Types and Optics</a> page. The page {@code {{#include}}}s the anchored regions, and {@code
 * CoreTypesBookTest} holds the claims the page makes about this code.
 */
public final class CoreTypesBook {

  private CoreTypesBook() {}

  /** What the Maybe prism reads, builds and matches. */
  record MaybePrismResults(
      Optional<String> value, Optional<String> empty, Maybe<String> built, boolean isJust) {}

  static MaybePrismResults maybePrisms() {
    // ANCHOR: maybe_prisms
    // Extract value from Just, returns empty Optional for Nothing
    Prism<Maybe<String>, String> justPrism = Prisms.just();

    Maybe<String> present = Maybe.just("Hello");
    Maybe<String> absent = Maybe.nothing();

    Optional<String> value = justPrism.getOptional(present);
    // Optional[Hello]

    Optional<String> empty = justPrism.getOptional(absent);
    // Optional.empty

    // Construct Maybe.just() from a value
    Maybe<String> built = justPrism.build("World");
    // Just(World)

    // Check if it's a Just
    boolean isJust = justPrism.matches(present);
    // true
    // ANCHOR_END: maybe_prisms
    return new MaybePrismResults(value, empty, built, isJust);
  }

  /** What the Either prisms read from each case, and what they build. */
  record EitherPrismResults(
      Optional<Integer> value,
      Optional<Integer> noValue,
      Optional<String> error,
      Either<String, Integer> newSuccess) {}

  static EitherPrismResults eitherPrisms() {
    // ANCHOR: either_prisms
    // Extract from Left and Right cases
    Prism<Either<String, Integer>, String> leftPrism = Prisms.left();
    Prism<Either<String, Integer>, Integer> rightPrism = Prisms.right();

    Either<String, Integer> success = Either.right(42);
    Either<String, Integer> failure = Either.left("AppError");

    // Extract success value
    Optional<Integer> value = rightPrism.getOptional(success);
    // Optional[42]

    Optional<Integer> noValue = rightPrism.getOptional(failure);
    // Optional.empty

    // Extract error value
    Optional<String> error = leftPrism.getOptional(failure);
    // Optional[AppError]

    // Construct Either values
    Either<String, Integer> newSuccess = rightPrism.build(100);
    // Right(100)
    // ANCHOR_END: either_prisms
    return new EitherPrismResults(value, noValue, error, newSuccess);
  }

  /** What the Validated prisms read from each case. */
  record ValidatedPrismResults(Optional<Integer> age, Optional<String> error) {}

  static ValidatedPrismResults validatedPrisms() {
    // ANCHOR: validated_prisms
    // Extract from Valid and Invalid cases
    Prism<Validated<String, Integer>, Integer> validPrism = Prisms.valid();
    Prism<Validated<String, Integer>, String> invalidPrism = Prisms.invalid();

    Validated<String, Integer> valid = Validated.valid(30);
    Validated<String, Integer> invalid = Validated.invalid("Age must be positive");

    // Extract valid value
    Optional<Integer> age = validPrism.getOptional(valid);
    // Optional[30]

    // Extract validation error
    Optional<String> error = invalidPrism.getOptional(invalid);
    // Optional[Age must be positive]
    // ANCHOR_END: validated_prisms
    return new ValidatedPrismResults(age, error);
  }

  /** What the Try prisms read from each case. */
  record TryPrismResults(Optional<Integer> value, Optional<Throwable> ex) {}

  static TryPrismResults tryPrisms() {
    // ANCHOR: try_prisms
    // Extract from Success and Failure cases
    Prism<Try<Integer>, Integer> successPrism = Prisms.success();
    Prism<Try<Integer>, Throwable> failurePrism = Prisms.failure();

    Try<Integer> success = Try.success(42);
    Try<Integer> failure = Try.failure(new RuntimeException("Database error"));

    // Extract success value
    Optional<Integer> value = successPrism.getOptional(success);
    // Optional[42]

    // Extract exception
    Optional<Throwable> ex = failurePrism.getOptional(failure);
    // Optional[java.lang.RuntimeException: Database error]
    // ANCHOR_END: try_prisms
    return new TryPrismResults(value, ex);
  }

  /** A Just changed through the traversal, and a Nothing it leaves alone. */
  record MaybeTraversalResults(Maybe<String> modified, Maybe<String> unchanged) {}

  static MaybeTraversalResults maybeTraversals() {
    // ANCHOR: maybe_traversals
    Traversal<Maybe<String>, String> justTraversal = MaybeTraversals.just();

    // Modify value inside Just
    Maybe<String> original = Maybe.just("hello");
    Maybe<String> modified = Traversals.modify(justTraversal, String::toUpperCase, original);
    // Result: Just(HELLO)

    // No effect on Nothing
    Maybe<String> nothing = Maybe.nothing();
    Maybe<String> unchanged = Traversals.modify(justTraversal, String::toUpperCase, nothing);
    // Result: Nothing
    // ANCHOR_END: maybe_traversals
    return new MaybeTraversalResults(modified, unchanged);
  }

  /** A Right changed through one traversal, and a Left enriched through the other. */
  record EitherTraversalResults(
      Either<String, Integer> doubled, Either<String, Integer> enriched) {}

  static EitherTraversalResults eitherTraversals() {
    // ANCHOR: either_traversals
    Traversal<Either<String, Integer>, Integer> rightTraversal = EitherTraversals.right();
    Traversal<Either<String, Integer>, String> leftTraversal = EitherTraversals.left();

    // Modify Right value
    Either<String, Integer> success = Either.right(100);
    Either<String, Integer> doubled = Traversals.modify(rightTraversal, n -> n * 2, success);
    // Result: Right(200)

    // AppError enrichment with Left traversal
    Either<String, Integer> error = Either.left("Connection failed");
    Either<String, Integer> enriched =
        Traversals.modify(leftTraversal, msg -> "[ERROR] " + msg, error);
    // Result: Left([ERROR] Connection failed)
    // ANCHOR_END: either_traversals
    return new EitherTraversalResults(doubled, enriched);
  }

  static List<String> customerEmails(ApiResponse response) {
    // ANCHOR: composition
    // Full composition: ApiResponse -> Maybe<Order> -> Order -> Customer -> email
    Lens<ApiResponse, Maybe<Order>> dataLens = ApiResponseLenses.data();
    Traversal<Maybe<Order>, Order> orderTraversal = MaybeTraversals.just();
    Lens<Order, Customer> customerLens = OrderLenses.customer();
    Lens<Customer, String> emailLens = CustomerLenses.email().andThen(EmailAddressLenses.value());

    Traversal<ApiResponse, String> emailPath =
        dataLens.andThen(orderTraversal).andThen(customerLens).andThen(emailLens);

    List<String> emails = Traversals.getAll(emailPath, response);
    // Result: [ada@example.com] for Ada's order, or [] when the response carries no order
    // ANCHOR_END: composition
    return emails;
  }
}

// ANCHOR: composition_records
@GenerateLenses
record ApiResponse(int statusCode, Maybe<Order> data, List<String> warnings) {}
// ANCHOR_END: composition_records
