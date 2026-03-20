// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.util.Affines;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 20: Custom Container Navigation - Navigating Container Types with Affines
 *
 * <p>These are the reference solutions for Tutorial20_ContainerNavigation.
 */
@DisplayName("Tutorial Solution 20: Custom Container Navigation")
public class Tutorial20_ContainerNavigation_Solution {

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

  @Test
  @DisplayName("Exercise 1: Navigate through Either with eitherRight()")
  void exercise1_navigateEitherRight() {
    ApiResult successResult = new ApiResult("/api/count", Either.right(42));
    ApiResult errorResult = new ApiResult("/api/count", Either.left("Not found"));

    // SOLUTION: Create AffinePath using FocusPath.of(lens).some(Affines.eitherRight())
    AffinePath<ApiResult, Integer> responsePath =
        FocusPath.of(responseLens).some(Affines.eitherRight());

    // SOLUTION: getOptional on Right returns the value
    Optional<Integer> successValue = responsePath.getOptional(successResult);

    // SOLUTION: getOptional on Left returns empty
    Optional<Integer> errorValue = responsePath.getOptional(errorResult);

    assertThat(successValue).contains(42);
    assertThat(errorValue).isEmpty();
  }

  // ===========================================================================
  // Exercise 2: Navigate through a Try field using Affines.trySuccess()
  // ===========================================================================

  @Test
  @DisplayName("Exercise 2: Navigate through Try with trySuccess()")
  void exercise2_navigateTrySuccess() {
    ComputeResult successResult = new ComputeResult("sqrt", Try.success(4.0));
    ComputeResult failureResult =
        new ComputeResult("sqrt", Try.failure(new ArithmeticException("negative input")));

    // SOLUTION: Create AffinePath using FocusPath.of(lens).some(Affines.trySuccess())
    AffinePath<ComputeResult, Double> outputPath =
        FocusPath.of(outputLens).some(Affines.trySuccess());

    // SOLUTION: getOptional on Success returns the value
    Optional<Double> successValue = outputPath.getOptional(successResult);

    // SOLUTION: getOptional on Failure returns empty
    Optional<Double> failureValue = outputPath.getOptional(failureResult);

    // SOLUTION: modify doubles the value when present
    ComputeResult doubled = outputPath.modify(v -> v * 2, successResult);

    assertThat(successValue).contains(4.0);
    assertThat(failureValue).isEmpty();
    assertThat(outputPath.getOptional(doubled)).contains(8.0);
  }

  // ===========================================================================
  // Exercise 3: Compose SPI-widened paths with standard lens paths
  // ===========================================================================

  @Test
  @DisplayName("Exercise 3: Compose SPI-widened paths with lens paths")
  void exercise3_composeSpiWithLens() {
    Order successOrder = new Order("ORD-1", Either.right(new Payment(5000, "GBP")));
    Order failedOrder = new Order("ORD-2", Either.left("Card declined"));

    // SOLUTION: Chain FocusPath -> some(eitherRight) -> via(amountLens)
    AffinePath<Order, Integer> orderAmountPath =
        FocusPath.of(paymentResultLens)
            .some(Affines.<String, Payment>eitherRight())
            .via(amountLens);

    // SOLUTION: getOptional on success order returns the amount
    Optional<Integer> successAmount = orderAmountPath.getOptional(successOrder);

    // SOLUTION: getOptional on failed order returns empty
    Optional<Integer> failedAmount = orderAmountPath.getOptional(failedOrder);

    // SOLUTION: modify adds surcharge to the amount
    Order surcharged = orderAmountPath.modify(a -> a + 500, successOrder);

    assertThat(successAmount).contains(5000);
    assertThat(failedAmount).isEmpty();
    assertThat(orderAmountPath.getOptional(surcharged)).contains(5500);
  }

  // ===========================================================================
  // Exercise 4: Use some(Affine) with Validated
  // ===========================================================================

  @Test
  @DisplayName("Exercise 4: Navigate through Validated with validatedValid()")
  void exercise4_navigateValidated() {
    FormField validField = new FormField("email", Validated.valid("alice@example.com"));
    FormField invalidField =
        new FormField("email", Validated.invalid(List.of("must not be empty", "invalid format")));

    // SOLUTION: Create AffinePath using FocusPath.of(lens).some(Affines.validatedValid())
    AffinePath<FormField, String> validValuePath =
        FocusPath.of(valueLens).some(Affines.validatedValid());

    // SOLUTION: getOptional on Valid returns the value
    Optional<String> validValue = validValuePath.getOptional(validField);

    // SOLUTION: getOptional on Invalid returns empty
    Optional<String> invalidValue = validValuePath.getOptional(invalidField);

    // SOLUTION: modify lowercases the valid value
    FormField lowered = validValuePath.modify(String::toLowerCase, validField);

    assertThat(validValue).contains("alice@example.com");
    assertThat(invalidValue).isEmpty();
    assertThat(validValuePath.getOptional(lowered)).contains("alice@example.com");
  }
}
