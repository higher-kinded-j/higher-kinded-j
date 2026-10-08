// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.capstone;

import static org.higherkindedj.optics.edit.Edit.parseIfPresent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.jspecify.annotations.Nullable;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/capstone.html">Capstone</a> page: an order
 * desk built from the Optics chapter's first pages, over the chapter's cast. The page {@code
 * {{#include}}}s the anchored regions, and {@code OrderDeskTest} holds the claims it makes.
 */
public final class OrderDesk {

  private OrderDesk() {}

  // ANCHOR: paths
  // Paths are values: build each one once, name it, and every method below reuses it
  static final FocusPath<Order, String> EMAIL = OrderFocus.customer().email().value();

  static final FocusPath<Order, OrderStatus> STATUS = OrderFocus.status();

  static final TraversalPath<Order, BigDecimal> PRICES =
      OrderFocus.lines().via(LineItemFocus.price());

  static final TraversalPath<Order, BigDecimal> BULK_PRICES =
      OrderFocus.lines().filter(line -> line.quantity() >= 4).via(LineItemFocus.price());

  static final AffinePath<Consignment, ConsignmentState.Pending> PENDING =
      ConsignmentFocus.state().via(ConsignmentStatePrisms.pending());

  // ANCHOR_END: paths

  // ANCHOR: check_price
  static Validated<String, BigDecimal> checkPrice(BigDecimal price) {
    return price.signum() < 0
        ? Validated.invalid("Price cannot be negative: " + price)
        : Validated.valid(price);
  }

  // ANCHOR_END: check_price

  // ANCHOR: accept
  /** Accepts an order: every price checked, then a 10% discount on each bulk line. */
  static Validated<List<String>, Order> accept(Order order) {
    return OpticOps.modifyAllValidated(order, PRICES.toTraversal(), OrderDesk::checkPrice)
        .map(checked -> BULK_PRICES.modifyAll(p -> p.multiply(new BigDecimal("0.9")), checked));
  }

  // ANCHOR_END: accept

  // ANCHOR: amendment
  /** A change a customer asks for: each component is null when the request did not send it. */
  record OrderAmendment(@Nullable String email, @Nullable String status) {}

  static Validated<NonEmptyList<FieldError>, String> parseEmail(String raw) {
    String email = raw.strip().toLowerCase(Locale.ROOT);
    return email.contains("@")
        ? Validated.validNel(email)
        : Validated.invalidNel(FieldError.of("not an email"));
  }

  static Validated<NonEmptyList<FieldError>, OrderStatus> parseStatus(String raw) {
    try {
      return Validated.validNel(OrderStatus.valueOf(raw.strip().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Validated.invalidNel(FieldError.of("not a status"));
    }
  }

  // ANCHOR_END: amendment

  // ANCHOR: amend
  /** Amends an order: every field the request sent is checked, and all of them are written. */
  static Validated<NonEmptyList<FieldError>, Order> amend(Order order, OrderAmendment amendment) {
    return Edits.accumulate(
            parseIfPresent(EMAIL, amendment.email(), OrderDesk::parseEmail),
            parseIfPresent(STATUS, amendment.status(), OrderDesk::parseStatus))
        .apply(order);
  }

  // ANCHOR_END: amend

  // ANCHOR: amend_by_hand
  /** The same amendment written by hand: each field rebuilt, and the first bad one throws. */
  static Order amendByHand(Order order, OrderAmendment amendment) {
    Order amended = order;
    if (amendment.email() != null) {
      String email = amendment.email().strip().toLowerCase(Locale.ROOT);
      if (!email.contains("@")) {
        throw new IllegalArgumentException("not an email");
      }
      Customer customer = new Customer(amended.customer().name(), new EmailAddress(email));
      amended =
          new Order(
              amended.id(),
              customer,
              amended.lines(),
              amended.placedAt(),
              amended.currency(),
              amended.status());
    }
    if (amendment.status() != null) {
      OrderStatus status = OrderStatus.valueOf(amendment.status().strip().toUpperCase(Locale.ROOT));
      amended =
          new Order(
              amended.id(),
              amended.customer(),
              amended.lines(),
              amended.placedAt(),
              amended.currency(),
              status);
    }
    return amended;
  }

  // ANCHOR_END: amend_by_hand

  // ANCHOR: dispatch
  /** Dispatches a pending consignment; any other state is left as it is. */
  static Consignment dispatch(Consignment consignment, Instant at) {
    return PENDING.matches(consignment)
        ? ConsignmentFocus.state().set(new ConsignmentState.Dispatched(at), consignment)
        : consignment;
  }
  // ANCHOR_END: dispatch
}
