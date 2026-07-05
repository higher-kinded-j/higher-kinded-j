// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.monoid;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Update;

/**
 * Demonstrates {@link Update} and the {@link Monoids#update()} monoid: named, reusable
 * transformations composed and folded into pipelines.
 *
 * <p>Where every other monoid combines <em>data</em>, {@code Monoids.update()} combines
 * <em>behaviour</em>: an {@code Update<S>} is a transformation of a value ({@code S -> S}), and the
 * monoid folds any number of them into one, applied left to right.
 *
 * <p>Key concepts demonstrated:
 *
 * <ul>
 *   <li>Naming updates and reusing them across many sources
 *   <li>Seamless composition with standard {@code UnaryOperator}s, lambdas, and method references
 *   <li>Folding a pipeline of updates with {@code combineAll}, and why order matters
 *   <li>Identity slots: absent steps contribute nothing, the basis for sparse partial updates
 * </ul>
 */
public class UpdateMonoidExample {

  /** A small immutable order with wither methods for single-field updates. */
  record Order(String email, String sku, int quantity, double total) {
    Order withEmail(String newEmail) {
      return new Order(newEmail, sku, quantity, total);
    }

    Order withSku(String newSku) {
      return new Order(email, newSku, quantity, total);
    }

    Order withQuantity(int newQuantity) {
      return new Order(email, sku, newQuantity, total);
    }

    Order withTotal(double newTotal) {
      return new Order(email, sku, quantity, newTotal);
    }
  }

  public static void main(String[] args) {
    demonstrateNamedReusableUpdates();
    demonstrateComposingWithStandardOperators();
    demonstrateFoldingAPipeline();
    demonstrateIdentitySlots();
  }

  /**
   * Demonstrates naming updates and reusing one composed transformation across many sources.
   *
   * <p>An {@code Update<S>} is a value: give it a name, apply it to as many inputs as you like, and
   * compose it with other updates via {@code andThen}.
   */
  private static void demonstrateNamedReusableUpdates() {
    System.out.println("=== Named, Reusable Updates Example ===");

    Update<Order> normaliseEmail = o -> o.withEmail(o.email().trim().toLowerCase());
    Update<Order> tidySku = o -> o.withSku(o.sku().trim().toUpperCase());

    // Compose once, reuse everywhere.
    Update<Order> tidy = normaliseEmail.andThen(tidySku);

    Order orderA = new Order("  Alice@Example.COM ", " ab-123 ", 2, 100.0);
    Order orderB = new Order("BOB@example.com", "cd-456", 1, 50.0);

    System.out.println("Tidied A: " + tidy.apply(orderA));
    System.out.println("Tidied B: " + tidy.apply(orderB));
    System.out.println("Expected: emails lower-cased and trimmed, SKUs upper-cased and trimmed\n");
  }

  /**
   * Demonstrates that updates compose seamlessly with standard Java operators.
   *
   * <p>{@code andThen} accepts any {@code UnaryOperator<S>}, so pre-existing operators, lambdas,
   * and method references join a pipeline with no wrapping, and the result stays an {@code
   * Update<S>}.
   */
  private static void demonstrateComposingWithStandardOperators() {
    System.out.println("=== Composing With Standard Operators Example ===");

    // A pre-existing, plain UnaryOperator, perhaps from another library or older code.
    UnaryOperator<Order> capQuantity = o -> o.quantity() > 10 ? o.withQuantity(10) : o;

    Update<Order> normaliseEmail = o -> o.withEmail(o.email().trim().toLowerCase());

    // No wrapping needed; the composition is still an Update<Order>.
    Update<Order> intake = normaliseEmail.andThen(capQuantity);

    Order bulkOrder = new Order(" WHOLESALE@Example.com ", "ef-789", 500, 9_950.0);

    System.out.println("Processed: " + intake.apply(bulkOrder));
    System.out.println("Expected: email normalised and quantity capped at 10\n");
  }

  /**
   * Demonstrates folding a whole pipeline of updates with {@code combineAll}.
   *
   * <p>The monoid's identity is the do-nothing update, and {@code combine(f, g)} applies {@code f}
   * first, then {@code g}, so a folded list of updates runs left to right. Function composition is
   * not commutative; the fold order is the application order.
   */
  private static void demonstrateFoldingAPipeline() {
    System.out.println("=== Folding a Pipeline Example ===");

    Update<Order> applyDiscount = o -> o.withTotal(o.total() * 0.9);
    Update<Order> addShipping = o -> o.withTotal(o.total() + 4.99);

    Monoid<Update<Order>> monoid = Monoids.update();

    Update<Order> checkout = monoid.combineAll(List.of(applyDiscount, addShipping));
    Update<Order> reversed = monoid.combineAll(List.of(addShipping, applyDiscount));

    Order order = new Order("carol@example.com", "gh-012", 3, 100.0);

    System.out.println("Discount then shipping: " + checkout.apply(order).total());
    System.out.println("Shipping then discount: " + reversed.apply(order).total());
    System.out.println("Expected: 94.99 vs 94.491 - the fold order is the application order\n");
  }

  /**
   * Demonstrates identity slots: an absent step contributes the monoid identity.
   *
   * <p>When a step is conditional, the absent case maps to {@link Update#identity()}, which changes
   * nothing. Folding then applies only the steps that are present, with no {@code if} ceremony at
   * the application site. This is the algebraic basis for sparse partial updates, where only the
   * fields supplied by a request are changed.
   */
  private static void demonstrateIdentitySlots() {
    System.out.println("=== Identity Slots Example ===");

    Order order = new Order("dave@example.com", "ij-345", 1, 25.0);

    System.out.println("Requested email change:    " + patch(Optional.of(" Dave@NEW.com "), order));
    System.out.println("No email change requested: " + patch(Optional.empty(), order));
    System.out.println("Expected: first order re-addressed, second returned unchanged");
  }

  /** Applies a sparse patch: each absent field contributes the identity update. */
  private static Order patch(Optional<String> requestedEmail, Order order) {
    Update<Order> emailStep =
        requestedEmail
            .<Update<Order>>map(email -> o -> o.withEmail(email.trim().toLowerCase()))
            .orElse(Update.identity());

    return emailStep.apply(order);
  }
}
