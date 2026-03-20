// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 20: Custom Container Navigation - Navigating Container Types with Affines
 *
 * <p>Types like {@code Either}, {@code Try}, and {@code Validated} hold zero or one value depending
 * on their state (Right/Left, Success/Failure, Valid/Invalid). These container types can be
 * navigated using the {@code some(Affine)} method on FocusPath, AffinePath, and TraversalPath.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@code Affines.eitherRight()} - Affine focusing on the Right value of an Either
 *   <li>{@code Affines.trySuccess()} - Affine focusing on the Success value of a Try
 *   <li>{@code Affines.validatedValid()} - Affine focusing on the Valid value of a Validated
 *   <li>{@code some(Affine)} on FocusPath/AffinePath/TraversalPath - widens to AffinePath
 *   <li>Composing SPI-widened paths with standard lens paths using {@code via(Lens)}
 * </ul>
 *
 * <p>Prerequisites: Complete Tutorials 12-13 (Focus DSL) before this one.
 *
 * <p>Estimated time: ~12 minutes
 *
 * <p>Replace each {@code answerRequired()} placeholder with the correct code to make the tests
 * pass.
 */
@DisplayName("Tutorial 18: SPI Type Navigation")
public class Tutorial20_ContainerNavigation {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Domain Records and Lenses
  // ===========================================================================

  record ApiResult(String endpoint, Either<String, Integer> response) {}

  static final Lens<ApiResult, Either<String, Integer>> responseLens =
      Lens.of(ApiResult::response, (r, v) -> new ApiResult(r.endpoint(), v));

  record ComputeResult(String task, Try<Double> output) {}

  static final Lens<ComputeResult, Try<Double>> outputLens =
      Lens.of(ComputeResult::output, (c, v) -> new ComputeResult(c.task(), v));

  record Payment(int amount, String currency) {}

  static final Lens<Payment, Integer> amountLens =
      Lens.of(Payment::amount, (p, a) -> new Payment(a, p.currency()));

  record Order(String id, Either<String, Payment> paymentResult) {}

  static final Lens<Order, Either<String, Payment>> paymentResultLens =
      Lens.of(Order::paymentResult, (o, pr) -> new Order(o.id(), pr));

  record FormField(String label, Validated<List<String>, String> value) {}

  static final Lens<FormField, Validated<List<String>, String>> valueLens =
      Lens.of(FormField::value, (f, v) -> new FormField(f.label(), v));

  // ===========================================================================
  // Exercise 1: Navigate through an Either field using Affines.eitherRight()
  // ===========================================================================

  /**
   * Exercise 1: Navigate through an Either field using Affines.eitherRight()
   *
   * <p>When a record contains an {@code Either<L, R>} field, you can navigate to the Right value
   * using {@code FocusPath.of(lens).some(Affines.eitherRight())}. This produces an AffinePath that
   * focuses on the Right value when present, or returns empty for Left values.
   *
   * <p>Task: Create an AffinePath from ApiResult to its response value (the Right side of the
   * Either), then test it on both Right and Left values.
   */
  @Test
  @DisplayName("Exercise 1: Navigate through Either with eitherRight()")
  void exercise1_navigateEitherRight() {
    ApiResult successResult = new ApiResult("/api/count", Either.right(42));
    ApiResult errorResult = new ApiResult("/api/count", Either.left("Not found"));

    // TODO: Create an AffinePath<ApiResult, Integer> by composing
    // FocusPath.of(responseLens) with .some(Affines.eitherRight())
    AffinePath<ApiResult, Integer> responsePath = answerRequired();

    // TODO: Use getOptional on the success result
    Optional<Integer> successValue = answerRequired();

    // TODO: Use getOptional on the error result
    Optional<Integer> errorValue = answerRequired();

    assertThat(successValue).contains(42);
    assertThat(errorValue).isEmpty();
  }

  // ===========================================================================
  // Exercise 2: Navigate through a Try field using Affines.trySuccess()
  // ===========================================================================

  /**
   * Exercise 2: Navigate through a Try field using Affines.trySuccess()
   *
   * <p>The {@code Try} type represents a computation that may succeed or fail with an exception.
   * Use {@code Affines.trySuccess()} to focus on the success value.
   *
   * <p>Task: Create an AffinePath from ComputeResult to its output value, then test getOptional and
   * modify on both Success and Failure values.
   */
  @Test
  @DisplayName("Exercise 2: Navigate through Try with trySuccess()")
  void exercise2_navigateTrySuccess() {
    ComputeResult successResult = new ComputeResult("sqrt", Try.success(4.0));
    ComputeResult failureResult =
        new ComputeResult("sqrt", Try.failure(new ArithmeticException("negative input")));

    // TODO: Create an AffinePath<ComputeResult, Double> using
    // FocusPath.of(outputLens).some(Affines.trySuccess())
    AffinePath<ComputeResult, Double> outputPath = answerRequired();

    // TODO: Use getOptional on the success result
    Optional<Double> successValue = answerRequired();

    // TODO: Use getOptional on the failure result
    Optional<Double> failureValue = answerRequired();

    // TODO: Use modify to double the value on the success result
    ComputeResult doubled = answerRequired();

    assertThat(successValue).contains(4.0);
    assertThat(failureValue).isEmpty();
    assertThat(outputPath.getOptional(doubled)).contains(8.0);
  }

  // ===========================================================================
  // Exercise 3: Compose SPI-widened paths with standard lens paths
  // ===========================================================================

  /**
   * Exercise 3: Compose SPI-widened paths with standard lens paths
   *
   * <p>After using {@code some(Affine)} to widen a FocusPath to an AffinePath, you can continue
   * composing with standard lenses using {@code via(Lens)}. This lets you navigate deep into nested
   * structures that contain SPI container types.
   *
   * <p>Task: Navigate from Order through the paymentResult (Either) into the Payment record and
   * down to its amount field.
   */
  @Test
  @DisplayName("Exercise 3: Compose SPI-widened paths with lens paths")
  void exercise3_composeSpiWithLens() {
    Order successOrder = new Order("ORD-1", Either.right(new Payment(5000, "GBP")));
    Order failedOrder = new Order("ORD-2", Either.left("Card declined"));

    // TODO: Create an AffinePath<Order, Integer> by chaining:
    // FocusPath.of(paymentResultLens).some(Affines.eitherRight()).via(amountLens)
    AffinePath<Order, Integer> orderAmountPath = answerRequired();

    // TODO: Use getOptional on the success order
    Optional<Integer> successAmount = answerRequired();

    // TODO: Use getOptional on the failed order
    Optional<Integer> failedAmount = answerRequired();

    // TODO: Use modify to add a 500 surcharge to the success order's amount
    Order surcharged = answerRequired();

    assertThat(successAmount).contains(5000);
    assertThat(failedAmount).isEmpty();
    assertThat(orderAmountPath.getOptional(surcharged)).contains(5500);
  }

  // ===========================================================================
  // Exercise 4: Use some(Affine) with Validated
  // ===========================================================================

  /**
   * Exercise 4: Use some(Affine) with Validated
   *
   * <p>The {@code Validated} type is similar to Either but is designed for accumulating errors. Use
   * {@code Affines.validatedValid()} to focus on the valid value.
   *
   * <p>Task: Navigate to the valid value inside a FormField's Validated field, and test with both
   * Valid and Invalid instances.
   */
  @Test
  @DisplayName("Exercise 4: Navigate through Validated with validatedValid()")
  void exercise4_navigateValidated() {
    FormField validField = new FormField("email", Validated.valid("alice@example.com"));
    FormField invalidField =
        new FormField("email", Validated.invalid(List.of("must not be empty", "invalid format")));

    // TODO: Create an AffinePath<FormField, String> using
    // FocusPath.of(valueLens).some(Affines.validatedValid())
    AffinePath<FormField, String> validValuePath = answerRequired();

    // TODO: Use getOptional on the valid field
    Optional<String> validValue = answerRequired();

    // TODO: Use getOptional on the invalid field
    Optional<String> invalidValue = answerRequired();

    // TODO: Use modify to lowercase the valid field's value
    FormField lowered = answerRequired();

    assertThat(validValue).contains("alice@example.com");
    assertThat(invalidValue).isEmpty();
    assertThat(validValuePath.getOptional(lowered)).contains("alice@example.com");
  }

  /**
   * Congratulations! You have completed Tutorial 18: SPI Type Navigation 🎉
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How {@code Affines.eitherRight()} focuses on the Right value of an Either
   *   <li>How {@code Affines.trySuccess()} focuses on the Success value of a Try
   *   <li>How {@code Affines.validatedValid()} focuses on the Valid value of a Validated
   *   <li>How {@code some(Affine)} on FocusPath widens to AffinePath
   *   <li>How to compose SPI-widened paths with standard lens paths using {@code via(Lens)}
   * </ul>
   *
   * <p>These techniques allow you to navigate through any container type that holds zero or one
   * value, composing seamlessly with the rest of the optics ecosystem.
   */
}
