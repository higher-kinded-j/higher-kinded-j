// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.util.Affines;

/**
 * Demonstrates navigating into custom container types with the Focus DSL.
 *
 * <p>Shows how to use {@code some(Affine)} to navigate into container types like {@link Either},
 * {@link Try}, and {@link Validated}. These types each hold zero or one value from the perspective
 * of the "success" channel, making {@link org.higherkindedj.optics.Affine} the appropriate optic
 * for navigating into them.
 *
 * <h2>Key Concepts</h2>
 *
 * <ul>
 *   <li>{@link Affines#eitherRight()} - focuses on the Right value of an Either
 *   <li>{@link Affines#trySuccess()} - focuses on the Success value of a Try
 *   <li>{@link Affines#validatedValid()} - focuses on the Valid value of a Validated
 *   <li>{@code some(Affine)} composition - widens a FocusPath into an AffinePath
 *   <li>Composing through multiple container types via chained {@code .via()} calls
 *   <li>Comparing manual composition with {@code @GenerateFocus} SPI auto-widening
 * </ul>
 */
public class ContainerNavigationExample {

  // ============= Domain Models =============

  /** A configuration entry with a name and a port that may be an error message or a valid port. */
  record Config(String name, Either<String, Integer> port) {}

  /** A service result containing the service name and a response that may have failed. */
  record ServiceResult(String service, Try<String> response) {}

  /** A form field with a label and a validated answer that may hold an error or a valid integer. */
  record FormField(String label, Validated<String, Integer> answer) {}

  /** A payment with an amount and currency. */
  record Payment(int amount, String currency) {}

  /** An order with an identifier and a result that is either an error message or a payment. */
  record Order(String id, Either<String, Payment> result) {}

  // ============= @GenerateFocus Records (SPI auto-widening) =============

  /**
   * Same Config model but with {@code @GenerateFocus}.
   *
   * <p>The SPI {@code EitherGenerator} recognises {@code Either<String, Integer>} as a ZERO_OR_ONE
   * container, so {@code AutoConfigFocus.port()} automatically returns {@code
   * AffinePath<AutoConfig, Integer>} — no manual {@code some(Affine)} needed.
   */
  @GenerateFocus
  record AutoConfig(String name, Either<String, Integer> port) {}

  /**
   * Same ServiceResult model but with {@code @GenerateFocus}.
   *
   * <p>The SPI {@code TryGenerator} recognises {@code Try<String>} as ZERO_OR_ONE, so {@code
   * AutoServiceResultFocus.response()} returns {@code AffinePath<AutoServiceResult, String>}.
   */
  @GenerateFocus
  record AutoServiceResult(String service, Try<String> response) {}

  /**
   * Same FormField model but with {@code @GenerateFocus}.
   *
   * <p>The SPI {@code ValidatedGenerator} recognises {@code Validated<String, Integer>} as
   * ZERO_OR_ONE, so {@code AutoFormFieldFocus.answer()} returns {@code AffinePath<AutoFormField,
   * Integer>}.
   */
  @GenerateFocus
  record AutoFormField(String label, Validated<String, Integer> answer) {}

  // ============= Manual Lenses =============

  static final Lens<Config, Either<String, Integer>> CONFIG_PORT =
      Lens.of(Config::port, (c, p) -> new Config(c.name(), p));

  static final Lens<ServiceResult, Try<String>> RESULT_RESPONSE =
      Lens.of(ServiceResult::response, (r, resp) -> new ServiceResult(r.service(), resp));

  static final Lens<FormField, Validated<String, Integer>> FIELD_ANSWER =
      Lens.of(FormField::answer, (f, a) -> new FormField(f.label(), a));

  static final Lens<Order, Either<String, Payment>> ORDER_RESULT =
      Lens.of(Order::result, (o, r) -> new Order(o.id(), r));

  static final Lens<Payment, Integer> PAYMENT_AMOUNT =
      Lens.of(Payment::amount, (p, a) -> new Payment(a, p.currency()));

  static final Lens<Payment, String> PAYMENT_CURRENCY =
      Lens.of(Payment::currency, (p, c) -> new Payment(p.amount(), c));

  // ============= Examples =============

  public static void main(String[] args) {
    System.out.println("=== Custom Container Navigation with the Focus DSL ===\n");

    eitherNavigationExample();
    tryNavigationExample();
    validatedNavigationExample();
    composedContainerPathExample();
    autoWideningComparisonExample();
  }

  /**
   * Demonstrates navigating into an Either field using {@link Affines#eitherRight()}.
   *
   * <p>The {@code some(Affine)} call widens the FocusPath into an AffinePath, reflecting the fact
   * that the Right value may or may not be present.
   */
  static void eitherNavigationExample() {
    System.out.println("--- Either Navigation with Affines.eitherRight() ---");

    FocusPath<Config, Either<String, Integer>> portPath = FocusPath.of(CONFIG_PORT);
    AffinePath<Config, Integer> portValue = portPath.some(Affines.eitherRight());

    Config validConfig = new Config("web-server", Either.right(8080));
    Config errorConfig = new Config("web-server", Either.left("PORT_NOT_SET"));

    // Get from Right
    Optional<Integer> fromRight = portValue.getOptional(validConfig);
    System.out.println("Get from Right: " + fromRight);

    // Get from Left (returns empty)
    Optional<Integer> fromLeft = portValue.getOptional(errorConfig);
    System.out.println("Get from Left:  " + fromLeft);

    // Set a new port value
    Config updated = portValue.set(9090, validConfig);
    System.out.println("After set(9090): " + updated.port());

    // Modify the port value
    Config modified = portValue.modify(p -> p + 1, validConfig);
    System.out.println("After modify(+1): " + modified.port());

    System.out.println();
  }

  /**
   * Demonstrates navigating into a Try field using {@link Affines#trySuccess()}.
   *
   * <p>When the Try holds a Failure, the AffinePath returns empty, leaving the structure unchanged
   * on modification.
   */
  static void tryNavigationExample() {
    System.out.println("--- Try Navigation with Affines.trySuccess() ---");

    FocusPath<ServiceResult, Try<String>> responsePath = FocusPath.of(RESULT_RESPONSE);
    AffinePath<ServiceResult, String> responseValue = responsePath.some(Affines.trySuccess());

    ServiceResult success = new ServiceResult("auth", Try.success("token-abc-123"));
    ServiceResult failure =
        new ServiceResult("auth", Try.failure(new RuntimeException("Connection refused")));

    // Get from Success
    Optional<String> fromSuccess = responseValue.getOptional(success);
    System.out.println("Get from Success: " + fromSuccess);

    // Get from Failure (returns empty)
    Optional<String> fromFailure = responseValue.getOptional(failure);
    System.out.println("Get from Failure: " + fromFailure);

    // Modify the success value
    ServiceResult modified = responseValue.modify(String::toUpperCase, success);
    System.out.println("After modify(toUpperCase): " + RESULT_RESPONSE.get(modified));

    // Modify on failure is a no-op
    ServiceResult unchanged = responseValue.modify(String::toUpperCase, failure);
    System.out.println("Modify on Failure (no-op): " + RESULT_RESPONSE.get(unchanged));

    System.out.println();
  }

  /**
   * Demonstrates navigating into a Validated field using {@link Affines#validatedValid()}.
   *
   * <p>Validated is commonly used for accumulating validation errors. The affine focuses only on
   * the Valid case.
   */
  static void validatedNavigationExample() {
    System.out.println("--- Validated Navigation with Affines.validatedValid() ---");

    FocusPath<FormField, Validated<String, Integer>> answerPath = FocusPath.of(FIELD_ANSWER);
    AffinePath<FormField, Integer> answerValue = answerPath.some(Affines.validatedValid());

    FormField validField = new FormField("Age", Validated.valid(25));
    FormField invalidField = new FormField("Age", Validated.invalid("Must be a number"));

    // Get from Valid
    Optional<Integer> fromValid = answerValue.getOptional(validField);
    System.out.println("Get from Valid:   " + fromValid);

    // Get from Invalid (returns empty)
    Optional<Integer> fromInvalid = answerValue.getOptional(invalidField);
    System.out.println("Get from Invalid: " + fromInvalid);

    System.out.println();
  }

  /**
   * Demonstrates composing container-widened paths to navigate through nested structures.
   *
   * <p>An Order contains an Either that, when Right, holds a Payment record. By composing {@code
   * some(Affines.eitherRight())} with a Lens into Payment fields, we can navigate the entire path
   * in a single expression.
   */
  static void composedContainerPathExample() {
    System.out.println("--- Composing Container-Widened Paths ---");

    // Build the composed paths: Order -> Either<String, Payment> -> Payment -> fields
    FocusPath<Order, Either<String, Payment>> resultPath = FocusPath.of(ORDER_RESULT);
    AffinePath<Order, Payment> paymentPath = resultPath.some(Affines.eitherRight());
    AffinePath<Order, Integer> amountPath = paymentPath.via(PAYMENT_AMOUNT);
    AffinePath<Order, String> currencyPath = paymentPath.via(PAYMENT_CURRENCY);

    Order successfulOrder = new Order("ORD-001", Either.right(new Payment(4999, "GBP")));
    Order failedOrder = new Order("ORD-002", Either.left("Payment declined"));

    // Navigate to amount through Either
    Optional<Integer> amount = amountPath.getOptional(successfulOrder);
    System.out.println("Amount from successful order: " + amount);

    Optional<Integer> noAmount = amountPath.getOptional(failedOrder);
    System.out.println("Amount from failed order:     " + noAmount);

    // Navigate to currency through Either
    Optional<String> currency = currencyPath.getOptional(successfulOrder);
    System.out.println("Currency from successful order: " + currency);

    // Modify amount through the composed path
    Order discounted = amountPath.modify(a -> a - 500, successfulOrder);
    System.out.println("After discount: " + amountPath.getOptional(discounted));

    // Modify on a failed order is a no-op
    Order unchangedFailed = amountPath.modify(a -> a - 500, failedOrder);
    System.out.println("Modify on failed order (no-op): " + unchangedFailed.result());

    System.out.println();
  }

  /**
   * Compares manual container navigation with {@code @GenerateFocus} SPI auto-widening.
   *
   * <p>The TraversableGenerator SPI allows the annotation processor to recognise container types
   * and automatically generate the correct path widening. This eliminates the boilerplate of
   * creating manual Lenses and calling {@code some(Affine)}.
   *
   * <p>Manual approach (above examples):
   *
   * <pre>{@code
   * Lens<Config, Either<String, Integer>> lens = Lens.of(Config::port, ...);
   * AffinePath<Config, Integer> path = FocusPath.of(lens).some(Affines.eitherRight());
   * }</pre>
   *
   * <p>Generated approach (SPI auto-widening):
   *
   * <pre>{@code
   * // @GenerateFocus on the record is all you need:
   * AffinePath<AutoConfig, Integer> path = AutoConfigFocus.port();
   * }</pre>
   */
  static void autoWideningComparisonExample() {
    System.out.println("--- @GenerateFocus SPI Auto-Widening Comparison ---");

    // --- Either: manual vs generated ---
    AutoConfig validConfig = new AutoConfig("web-server", Either.right(8080));
    AutoConfig errorConfig = new AutoConfig("web-server", Either.left("PORT_NOT_SET"));

    // Generated: AutoConfigFocus.port() returns AffinePath<AutoConfig, Integer> directly
    var portPath = AutoConfigFocus.port(); // AffinePath — auto-widened by EitherGenerator SPI
    System.out.println("Either (generated):");
    System.out.println("  port() type:     " + portPath.getClass().getSimpleName());
    System.out.println("  Get from Right:  " + portPath.getOptional(validConfig));
    System.out.println("  Get from Left:   " + portPath.getOptional(errorConfig));

    // --- Try: manual vs generated ---
    AutoServiceResult success = new AutoServiceResult("auth", Try.success("token-abc-123"));
    AutoServiceResult failure =
        new AutoServiceResult("auth", Try.failure(new RuntimeException("Connection refused")));

    // Generated: AutoServiceResultFocus.response() returns AffinePath — auto-widened by
    // TryGenerator SPI
    var responsePath = AutoServiceResultFocus.response();
    System.out.println("Try (generated):");
    System.out.println("  response() type: " + responsePath.getClass().getSimpleName());
    System.out.println("  Get from Success: " + responsePath.getOptional(success));
    System.out.println("  Get from Failure: " + responsePath.getOptional(failure));

    // --- Validated: manual vs generated ---
    AutoFormField validField = new AutoFormField("Age", Validated.valid(25));
    AutoFormField invalidField = new AutoFormField("Age", Validated.invalid("Must be a number"));

    // Generated: AutoFormFieldFocus.answer() returns AffinePath — auto-widened by
    // ValidatedGenerator SPI
    var answerPath = AutoFormFieldFocus.answer();
    System.out.println("Validated (generated):");
    System.out.println("  answer() type:   " + answerPath.getClass().getSimpleName());
    System.out.println("  Get from Valid:   " + answerPath.getOptional(validField));
    System.out.println("  Get from Invalid: " + answerPath.getOptional(invalidField));

    System.out.println();
    System.out.println("Summary: @GenerateFocus + SPI auto-widening eliminates manual Lens");
    System.out.println("creation and explicit some(Affine) calls. The generated Focus class");
    System.out.println("returns the correct path type (AffinePath or TraversalPath) based on");
    System.out.println("the container's Cardinality registered via TraversableGenerator SPI.");

    System.out.println();
  }
}
