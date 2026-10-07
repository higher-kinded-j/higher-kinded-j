// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.selfcheck;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fluent.OpticOps;

/**
 * The answers the Optics chapter's <a
 * href="https://higher-kinded-j.github.io/latest/optics/self_check.html">Check Your
 * Understanding</a> page shows as code. The page {@code {{#include}}}s the anchored regions, and
 * {@code SelfCheckBookTest} proves each answer.
 */
public final class SelfCheckBook {

  private SelfCheckBook() {}

  static Traversal<Order, BigDecimal> prices() {
    // ANCHOR: and_then_type
    Traversal<Order, BigDecimal> prices = OrderTraversals.lines().andThen(LineItemLenses.price());
    // ANCHOR_END: and_then_type
    return prices;
  }

  static Order oneOfEach(Order order) {
    // ANCHOR: one_of_each
    Order oneOfEach = OrderFocus.lines().via(LineItemFocus.quantity()).setAll(1, order);
    // ANCHOR_END: one_of_each
    return oneOfEach;
  }

  // ANCHOR: check_quantities
  static Validated<String, Integer> checkQuantity(Integer quantity) {
    return quantity >= 1 ? Validated.valid(quantity) : Validated.invalid("No items: " + quantity);
  }

  static Validated<List<String>, Order> acceptQuantities(Order order) {
    return OpticOps.modifyAllValidated(
        order,
        OrderFocus.lines().via(LineItemFocus.quantity()).toTraversal(),
        SelfCheckBook::checkQuantity);
  }

  // ANCHOR_END: check_quantities

  static Consignment shoutReturnReason(Consignment consignment) {
    // ANCHOR: returned_reason
    Consignment tidied =
        ConsignmentFocus.state()
            .via(ConsignmentStatePrisms.returned())
            .modify(
                returned ->
                    new ConsignmentState.Returned(returned.reason().toUpperCase(Locale.ROOT)),
                consignment);
    // ANCHOR_END: returned_reason
    return tidied;
  }

  static Lens<NormalisedEmail, String> normalisedValue() {
    // ANCHOR: normalised_lens
    Lens<NormalisedEmail, String> value =
        Lens.of(NormalisedEmail::value, (_, newValue) -> new NormalisedEmail(newValue));
    // ANCHOR_END: normalised_lens
    return value;
  }
}

// ANCHOR: normalised_email
// An email address whose constructor lowercases what it is given
record NormalisedEmail(String value) {
  NormalisedEmail {
    value = value.toLowerCase(Locale.ROOT);
  }
}
// ANCHOR_END: normalised_email
