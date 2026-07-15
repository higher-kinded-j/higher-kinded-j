// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import static org.higherkindedj.optics.edit.Edit.modify;
import static org.higherkindedj.optics.edit.Edit.modifyIfPresent;
import static org.higherkindedj.optics.edit.Edit.parseIfPresent;
import static org.higherkindedj.optics.edit.Edit.setIfPresent;

import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.edit.Edit;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.focus.FocusPath;
import org.jspecify.annotations.Nullable;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/optics/multi_edit.html">Multi-Edit and Sparse Updates</a>
 * page. The page {@code {{#include}}}s the anchored regions, so it cannot drift from the API.
 *
 * <p>The paths come from the generated {@code OrderFocus} companion, not hand-rolled optics: their
 * labels are what let a failed parse locate itself, which is precisely what the page claims.
 */
public final class MultiEditBook {

  // What the page calls EMAIL, SKU, QUANTITY, ORDER_NUMBER: generated, and therefore labelled.
  static final FocusPath<Order, String> ORDER_NUMBER = OrderFocus.orderNumber();
  static final FocusPath<Order, String> EMAIL = OrderFocus.email();
  static final FocusPath<Order, String> SKU = OrderFocus.sku();
  static final FocusPath<Order, Integer> QUANTITY = OrderFocus.quantity();

  static final Update<Order> APPLY_DISCOUNT = o -> o;

  private MultiEditBook() {}

  public static void main(String[] args) {
    Order order = new Order("ORD-1", "A@B.COM", " sku ", 1);
    PatchRequest req = new PatchRequest("ORD-2", "not-an-address", null, 3);

    // ANCHOR: before
    Order updated = order;
    if (req.email() != null) {
      updated = updated.withEmail(req.email().toLowerCase()); // thread the result...
    }
    if (req.sku() != null) {
      updated = updated.withSku(req.sku().trim()); // ...through every step
    }
    if (req.qtyDelta() != null) {
      updated = updated.withQuantity(updated.quantity() + req.qtyDelta());
    }
    // And if the email was malformed? You throw on the first bad field and never see the rest.
    // ANCHOR_END: before
    System.out.println(updated);

    // ANCHOR: combine
    Update<Order> normalise =
        Edits.combine(modify(EMAIL, String::toLowerCase), modify(SKU, String::trim));

    Order orderA = new Order("ORD-1", "A@B.COM", " sku ", 1);
    Order orderB = new Order("ORD-2", "C@D.COM", " sku2 ", 2);

    Order a = normalise.apply(orderA);
    Order b = normalise.andThen(APPLY_DISCOUNT).apply(orderB); // Update composes further
    // ANCHOR_END: combine
    System.out.println(a + " / " + b);

    // ANCHOR: sparse
    Edit<Order> number = setIfPresent(ORDER_NUMBER, req.orderNumber()); // null -> no-op
    Edit<Order> quantity = modifyIfPresent(QUANTITY, req.qtyDelta(), (delta, qty) -> qty + delta);
    // ANCHOR_END: sparse
    System.out.println(Edits.combine(number, quantity).apply(order));

    // ANCHOR: accumulate
    Validated<NonEmptyList<FieldError>, Order> patched =
        Edits.accumulate(
                setIfPresent(ORDER_NUMBER, req.orderNumber()),
                parseIfPresent(EMAIL, req.email(), Email::parse),
                modifyIfPresent(QUANTITY, req.qtyDelta(), (delta, qty) -> qty + delta))
            .apply(order);
    // Invalid(NEL[ "email: not an address" ]), or Valid(order') with only the present fields
    // changed
    // ANCHOR_END: accumulate
    System.out.println(patched);
  }
}

@GenerateLenses
@GenerateFocus
record Order(String orderNumber, String email, String sku, int quantity) {
  Order withEmail(String email) {
    return new Order(orderNumber, email, sku, quantity);
  }

  Order withSku(String sku) {
    return new Order(orderNumber, email, sku, quantity);
  }

  Order withQuantity(int quantity) {
    return new Order(orderNumber, email, sku, quantity);
  }
}

/** A sparse PATCH request: a null component means "not supplied", not "set to null". */
record PatchRequest(
    @Nullable String orderNumber,
    @Nullable String email,
    @Nullable String sku,
    @Nullable Integer qtyDelta) {}

/** The boundary parser the page hands to {@code parseIfPresent}. */
final class Email {
  static Validated<NonEmptyList<FieldError>, String> parse(String raw) {
    return raw.contains("@")
        ? Validated.validNel(raw)
        : Validated.invalidNel(FieldError.of("not an address"));
  }

  private Email() {}
}
