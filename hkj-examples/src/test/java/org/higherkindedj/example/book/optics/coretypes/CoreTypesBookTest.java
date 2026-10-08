// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.coretypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.example.book.optics.cast.CastFixtures;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Working with Core Types and Optics page makes about its prisms. */
@DisplayName("the Core Types page: prisms and traversals over Maybe, Either, Validated and Try")
class CoreTypesBookTest {

  @Test
  @DisplayName("the Maybe prism reads a Just, misses a Nothing, builds a Just and matches it")
  void maybePrisms() {
    CoreTypesBook.MaybePrismResults results = CoreTypesBook.maybePrisms();

    assertThat(results.value()).contains("Hello").hasToString("Optional[Hello]");
    assertThat(results.empty()).isEmpty().hasToString("Optional.empty");
    assertThat(results.built()).isEqualTo(Maybe.just("World")).hasToString("Just(World)");
    assertThat(results.isJust()).isTrue();
  }

  @Test
  @DisplayName("the Either prisms read their own case only, and build a Right")
  void eitherPrisms() {
    CoreTypesBook.EitherPrismResults results = CoreTypesBook.eitherPrisms();

    assertThat(results.value()).contains(42).hasToString("Optional[42]");
    assertThat(results.noValue()).isEmpty();
    assertThat(results.error()).contains("AppError").hasToString("Optional[AppError]");
    assertThat(results.newSuccess()).isEqualTo(Either.right(100)).hasToString("Right(100)");
  }

  @Test
  @DisplayName("the Validated prisms read the valid value and the invalid error")
  void validatedPrisms() {
    CoreTypesBook.ValidatedPrismResults results = CoreTypesBook.validatedPrisms();

    assertThat(results.age()).contains(30).hasToString("Optional[30]");
    assertThat(results.error())
        .contains("Age must be positive")
        .hasToString("Optional[Age must be positive]");
  }

  @Test
  @DisplayName("the Try prisms read the success value and the failure's exception")
  void tryPrisms() {
    CoreTypesBook.TryPrismResults results = CoreTypesBook.tryPrisms();

    assertThat(results.value()).contains(42).hasToString("Optional[42]");
    assertThat(results.ex())
        .hasValueSatisfying(
            ex -> assertThat(ex).isInstanceOf(RuntimeException.class).hasMessage("Database error"))
        .hasToString("Optional[java.lang.RuntimeException: Database error]");
  }

  @Test
  @DisplayName("MaybeTraversals.just changes a Just and leaves a Nothing as it was")
  void maybeTraversals() {
    CoreTypesBook.MaybeTraversalResults results = CoreTypesBook.maybeTraversals();

    assertThat(results.modified()).isEqualTo(Maybe.just("HELLO")).hasToString("Just(HELLO)");
    assertThat(results.unchanged()).isEqualTo(Maybe.nothing()).hasToString("Nothing");
  }

  @Test
  @DisplayName("EitherTraversals change the Right or the Left they reach")
  void eitherTraversals() {
    CoreTypesBook.EitherTraversalResults results = CoreTypesBook.eitherTraversals();

    assertThat(results.doubled()).isEqualTo(Either.right(200)).hasToString("Right(200)");
    assertThat(results.enriched())
        .isEqualTo(Either.left("[ERROR] Connection failed"))
        .hasToString("Left([ERROR] Connection failed)");
  }

  @Test
  @DisplayName("the composed path reads the customer's email, or nothing when there is no order")
  void compositionReadsThroughTheMaybe() {
    assertThat(
            CoreTypesBook.customerEmails(
                new ApiResponse(200, Maybe.just(CastFixtures.ORDER), List.of())))
        .containsExactly("ada@example.com")
        .hasToString("[ada@example.com]");
    assertThat(CoreTypesBook.customerEmails(new ApiResponse(404, Maybe.nothing(), List.of())))
        .isEmpty();
  }
}
