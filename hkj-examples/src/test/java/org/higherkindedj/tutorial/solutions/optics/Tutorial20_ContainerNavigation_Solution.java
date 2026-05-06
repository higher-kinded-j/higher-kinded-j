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
 * Solution for Tutorial20 ContainerNavigation — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
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

  /**
   * Why this is idiomatic: {@code Affines.eitherRight()} is the canonical "Right side" affine for
   * {@code Either}. Combined with {@code .some(...)} on a lens, the path reads "if the Either is
   * Right, give me the value".
   *
   * <p>Alternative: {@code either.fold(left -> Optional.empty(), Optional::of)}. Same answer; the
   * affine makes the path composable for further navigation and writes.
   *
   * <p>Common wrong attempt: forget the type witness on {@code eitherRight()} and let inference
   * pick {@code Object}. Supply explicit type arguments when chaining further.
   */
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

  /**
   * Why this is idiomatic: {@code Affines.trySuccess()} navigates the success side of a {@code
   * Try}; failures are absent from the affine's view. {@code modify} doubles the present value and
   * leaves failures untouched.
   *
   * <p>Alternative: pattern-match the {@code Try} and rebuild manually. The affine wraps that
   * pattern as a value.
   *
   * <p>Common wrong attempt: try to read a {@code Failure} as if it were a success. The affine's
   * partiality says "no value here"; never call {@code get} on the empty optional.
   */
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

  /**
   * Why this is idiomatic: chain {@code FocusPath.of(lens) -> some(eitherRight) -> via(lens)} and
   * the SPI-widened affine composes with the regular lens. The full path reads Order →
   * payment-result → Right payment → amount.
   *
   * <p>Alternative: handle each step with bespoke {@code if/else} branches. Works once; the optic
   * chain is one expression and the corresponding {@code modify} is available without extra code.
   *
   * <p>Common wrong attempt: skip the type witness on {@code eitherRight}. The chain needs the
   * error type to be known so it can be preserved when the path widens.
   */
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

  /**
   * Why this is idiomatic: {@code Affines.validatedValid()} navigates the {@code Valid} side of
   * {@code Validated}. Invalid forms keep their error list; the affine simply does not match.
   *
   * <p>Alternative: pattern-match the {@code Validated} and rebuild manually. The affine is the
   * named, composable spelling.
   *
   * <p>Common wrong attempt: try to use {@code modify} to introduce errors. The affine only
   * modifies present values; to add an error, work with the whole {@code Validated} via the
   * underlying lens.
   */
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
