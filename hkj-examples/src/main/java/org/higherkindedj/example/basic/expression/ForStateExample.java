// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.expression;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates ForState comprehensions for state-threaded workflows with lens-based updates.
 *
 * <p>This example shows a realistic order processing workflow using all ForState capabilities:
 *
 * <ul>
 *   <li>Basic state threading with update/modify/fromThen
 *   <li>Guards via when() with MonadZero
 *   <li>Pattern matching via matchThen() with Prism
 *   <li>Bulk operations via traverse()
 *   <li>State zooming via zoom()/endZoom()
 * </ul>
 */
public class ForStateExample {

  // --- Domain Model ---

  sealed interface OrderStatus {
    record Pending(String reason) implements OrderStatus {}

    record Confirmed(String confirmationId) implements OrderStatus {}

    record Cancelled(String reason) implements OrderStatus {}
  }

  record ShippingAddress(String street, String city, String zip) {}

  record OrderContext(
      String orderId,
      OrderStatus status,
      String extractedConfirmationId,
      ShippingAddress shippingAddress,
      List<String> tags,
      int totalCents) {}

  // --- Lenses ---

  static final Lens<OrderContext, OrderStatus> statusLens =
      Lens.of(
          OrderContext::status,
          (ctx, s) ->
              new OrderContext(
                  ctx.orderId(),
                  s,
                  ctx.extractedConfirmationId(),
                  ctx.shippingAddress(),
                  ctx.tags(),
                  ctx.totalCents()));

  static final Lens<OrderContext, String> extractedIdLens =
      Lens.of(
          OrderContext::extractedConfirmationId,
          (ctx, id) ->
              new OrderContext(
                  ctx.orderId(),
                  ctx.status(),
                  id,
                  ctx.shippingAddress(),
                  ctx.tags(),
                  ctx.totalCents()));

  static final Lens<OrderContext, ShippingAddress> addressLens =
      Lens.of(
          OrderContext::shippingAddress,
          (ctx, a) ->
              new OrderContext(
                  ctx.orderId(),
                  ctx.status(),
                  ctx.extractedConfirmationId(),
                  a,
                  ctx.tags(),
                  ctx.totalCents()));

  static final Lens<OrderContext, List<String>> tagsLens =
      Lens.of(
          OrderContext::tags,
          (ctx, t) ->
              new OrderContext(
                  ctx.orderId(),
                  ctx.status(),
                  ctx.extractedConfirmationId(),
                  ctx.shippingAddress(),
                  t,
                  ctx.totalCents()));

  static final Lens<OrderContext, Integer> totalLens =
      Lens.of(
          OrderContext::totalCents,
          (ctx, total) ->
              new OrderContext(
                  ctx.orderId(),
                  ctx.status(),
                  ctx.extractedConfirmationId(),
                  ctx.shippingAddress(),
                  ctx.tags(),
                  total));

  static final Lens<ShippingAddress, String> streetLens =
      Lens.of(ShippingAddress::street, (a, s) -> new ShippingAddress(s, a.city(), a.zip()));

  static final Lens<ShippingAddress, String> cityLens =
      Lens.of(ShippingAddress::city, (a, c) -> new ShippingAddress(a.street(), c, a.zip()));

  static final Lens<ShippingAddress, String> zipLens =
      Lens.of(ShippingAddress::zip, (a, z) -> new ShippingAddress(a.street(), a.city(), z));

  // --- Prisms ---

  static final Prism<OrderStatus, String> confirmedIdPrism =
      Prism.of(
          s ->
              s instanceof OrderStatus.Confirmed c
                  ? Optional.of(c.confirmationId())
                  : Optional.empty(),
          id -> new OrderStatus.Confirmed(id));

  // --- Main ---

  public static void main(String[] args) {
    System.out.println("=== ForState Example: Order Processing Workflow ===\n");

    basicWorkflow();
    guardWorkflow();
    matchThenWorkflow();
    traverseWorkflow();
    zoomWorkflow();
    combinedWorkflow();
  }

  /** Example 1: Basic state threading with update, modify, fromThen. */
  private static void basicWorkflow() {
    System.out.println("--- 1. Basic Workflow (Id Monad) ---");
    IdMonad idMonad = IdMonad.instance();

    OrderContext initial =
        new OrderContext(
            "ORD-001",
            new OrderStatus.Pending("new order"),
            null,
            new ShippingAddress("123 Main St", "Springfield", "62701"),
            List.of("rush"),
            5000);

    Kind<IdKind.Witness, String> result =
        ForState.withState(idMonad, Id.of(initial))
            .update(statusLens, new OrderStatus.Confirmed("CONF-ABC"))
            .modify(totalLens, total -> total + 500) // add shipping
            .fromThen(ctx -> Id.of("CONF-ABC"), extractedIdLens)
            .yield(ctx -> "Order " + ctx.orderId() + " total: $" + (ctx.totalCents() / 100.0));

    System.out.println("  Result: " + IdKindHelper.ID.unwrap(result));
    System.out.println();
  }

  /** Example 2: Guards with when() using Maybe monad. */
  private static void guardWorkflow() {
    System.out.println("--- 2. Guard Workflow (Maybe Monad) ---");
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    OrderContext validOrder =
        new OrderContext(
            "ORD-002",
            new OrderStatus.Confirmed("CONF-XYZ"),
            null,
            new ShippingAddress("456 Oak", "Shelbyville", "62702"),
            List.of(),
            10000);

    OrderContext invalidOrder =
        new OrderContext(
            "ORD-003",
            new OrderStatus.Pending("not confirmed"),
            null,
            new ShippingAddress("789 Elm", "Capital City", "62703"),
            List.of(),
            0); // zero total

    // Valid order passes both guards
    Kind<MaybeKind.Witness, OrderContext> validResult =
        ForState.withState(maybeMonad, MAYBE.just(validOrder))
            .when(ctx -> ctx.orderId().startsWith("ORD"))
            .when(ctx -> ctx.totalCents() > 0)
            .update(extractedIdLens, "processed")
            .yield();

    // Invalid order fails the total > 0 guard
    Kind<MaybeKind.Witness, OrderContext> invalidResult =
        ForState.withState(maybeMonad, MAYBE.just(invalidOrder))
            .when(ctx -> ctx.orderId().startsWith("ORD"))
            .when(ctx -> ctx.totalCents() > 0) // fails here -> Nothing
            .update(extractedIdLens, "processed")
            .yield();

    System.out.println("  Valid order: " + MAYBE.narrow(validResult));
    System.out.println("  Invalid order: " + MAYBE.narrow(invalidResult));
    System.out.println();
  }

  /** Example 3: Pattern matching with matchThen() using Prism. */
  private static void matchThenWorkflow() {
    System.out.println("--- 3. matchThen Workflow (Pattern Matching) ---");
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    OrderContext confirmedOrder =
        new OrderContext(
            "ORD-004",
            new OrderStatus.Confirmed("CONF-999"),
            null,
            new ShippingAddress("123 Main", "Springfield", "62701"),
            List.of(),
            7500);

    OrderContext pendingOrder =
        new OrderContext(
            "ORD-005",
            new OrderStatus.Pending("awaiting payment"),
            null,
            new ShippingAddress("456 Oak", "Shelbyville", "62702"),
            List.of(),
            3000);

    // Prism matches Confirmed -> extracts confirmation ID
    Kind<MaybeKind.Witness, OrderContext> confirmedResult =
        ForState.withState(maybeMonad, MAYBE.just(confirmedOrder))
            .matchThen(statusLens, confirmedIdPrism, extractedIdLens)
            .yield();

    // Prism does not match Pending -> Nothing
    Kind<MaybeKind.Witness, OrderContext> pendingResult =
        ForState.withState(maybeMonad, MAYBE.just(pendingOrder))
            .matchThen(statusLens, confirmedIdPrism, extractedIdLens)
            .yield();

    // Function-based variant
    Kind<MaybeKind.Witness, OrderContext> functionResult =
        ForState.withState(maybeMonad, MAYBE.just(confirmedOrder))
            .matchThen(
                ctx ->
                    ctx.status() instanceof OrderStatus.Confirmed c
                        ? Optional.of(c.confirmationId())
                        : Optional.empty(),
                extractedIdLens)
            .yield();

    Maybe<OrderContext> cr = MAYBE.narrow(confirmedResult);
    System.out.println(
        "  Confirmed order extractedId: "
            + (cr.isJust() ? cr.get().extractedConfirmationId() : "N/A"));
    System.out.println("  Pending order: " + MAYBE.narrow(pendingResult));
    Maybe<OrderContext> fr = MAYBE.narrow(functionResult);
    System.out.println(
        "  Function variant extractedId: "
            + (fr.isJust() ? fr.get().extractedConfirmationId() : "N/A"));
    System.out.println();
  }

  /** Example 4: Bulk operations with traverse(). */
  private static void traverseWorkflow() {
    System.out.println("--- 4. Traverse Workflow (Bulk Tag Operations) ---");
    IdMonad idMonad = IdMonad.instance();

    OrderContext order =
        new OrderContext(
            "ORD-006",
            new OrderStatus.Confirmed("CONF-111"),
            null,
            new ShippingAddress("123 Main", "Springfield", "62701"),
            List.of("rush", "fragile", "gift-wrap"),
            9500);

    Traversal<List<String>, String> listTraversal = Traversals.forList();

    Kind<IdKind.Witness, OrderContext> result =
        ForState.withState(idMonad, Id.of(order))
            .traverse(tagsLens, listTraversal, tag -> Id.of("[" + tag.toUpperCase() + "]"))
            .yield();

    OrderContext finalCtx = IdKindHelper.ID.unwrap(result);
    System.out.println("  Original tags: " + order.tags());
    System.out.println("  Transformed tags: " + finalCtx.tags());
    System.out.println();
  }

  /** Example 5: Zooming into nested state with zoom()/endZoom(). */
  private static void zoomWorkflow() {
    System.out.println("--- 5. Zoom Workflow (Nested Address Updates) ---");
    IdMonad idMonad = IdMonad.instance();

    OrderContext order =
        new OrderContext(
            "ORD-007",
            new OrderStatus.Confirmed("CONF-222"),
            "CONF-222",
            new ShippingAddress("123 Main St", "Springfield", "62701"),
            List.of(),
            12000);

    Kind<IdKind.Witness, OrderContext> result =
        ForState.withState(idMonad, Id.of(order))
            .zoom(addressLens)
            .update(streetLens, "456 Oak Avenue")
            .modify(zipLens, z -> z + "-5678")
            .fromThen(addr -> Id.of(addr.city().toUpperCase()), cityLens)
            .endZoom()
            .modify(totalLens, t -> t + 1500) // add insurance after zoom
            .yield();

    OrderContext finalCtx = IdKindHelper.ID.unwrap(result);
    System.out.println("  Address: " + finalCtx.shippingAddress());
    System.out.println("  Total: $" + (finalCtx.totalCents() / 100.0));
    System.out.println();
  }

  /** Example 6: Combined workflow using all features. */
  private static void combinedWorkflow() {
    System.out.println("--- 6. Combined Workflow (All Features) ---");
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    OrderContext order =
        new OrderContext(
            "ORD-008",
            new OrderStatus.Confirmed("CONF-FINAL"),
            null,
            new ShippingAddress("789 Elm Blvd", "Capital City", "62703"),
            List.of("priority", "insured"),
            25000);

    Traversal<List<String>, String> listTraversal = Traversals.forList();

    Kind<MaybeKind.Witness, String> result =
        ForState.withState(maybeMonad, MAYBE.just(order))
            .when(ctx -> ctx.orderId().startsWith("ORD")) // guard
            .matchThen(statusLens, confirmedIdPrism, extractedIdLens) // pattern match
            .traverse(tagsLens, listTraversal, tag -> MAYBE.just(tag.toUpperCase())) // bulk op
            .yield(
                ctx ->
                    String.format(
                        "Order %s confirmed (%s), tags: %s, total: $%.2f",
                        ctx.orderId(),
                        ctx.extractedConfirmationId(),
                        ctx.tags(),
                        ctx.totalCents() / 100.0));

    System.out.println("  Result: " + MAYBE.narrow(result));
    System.out.println();
  }
}
