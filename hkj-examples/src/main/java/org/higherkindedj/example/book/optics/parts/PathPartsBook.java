// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.parts;

import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.EmailAddressLenses;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/optics_intro.html">What a Path Is Made
 * Of</a> page. The page {@code {{#include}}}s the anchored regions, and {@code PathPartsBookTest}
 * holds the claims the page makes about this code. The records are the chapter's cast.
 */
public final class PathPartsBook {

  private PathPartsBook() {}

  /** Three optics, one for each result type: taken out of paths, or composed with andThen. */
  record Optics(
      Lens<Order, String> emailValue,
      Affine<Consignment, ConsignmentState.Dispatched> dispatched,
      Traversal<Order, Integer> quantities) {}

  static Optics toOptic() {
    // ANCHOR: to_optic
    FocusPath<Order, String> emailPath = OrderFocus.customer().email().value();
    Lens<Order, String> emailValue = emailPath.toLens();

    AffinePath<Consignment, ConsignmentState.Dispatched> dispatchedPath =
        ConsignmentFocus.state().via(ConsignmentStatePrisms.dispatched());
    Affine<Consignment, ConsignmentState.Dispatched> dispatched = dispatchedPath.toAffine();

    TraversalPath<Order, Integer> quantityPath = OrderFocus.lines().via(LineItemFocus.quantity());
    Traversal<Order, Integer> quantities = quantityPath.toTraversal();
    // ANCHOR_END: to_optic
    return new Optics(emailValue, dispatched, quantities);
  }

  static Lens<Customer, EmailAddress> emailByHand() {
    // ANCHOR: by_hand
    Lens<Customer, EmailAddress> email =
        Lens.of(Customer::email, (customer, newEmail) -> new Customer(customer.name(), newEmail));
    // ANCHOR_END: by_hand
    return email;
  }

  static Optics andThen() {
    // ANCHOR: and_then
    // exactly one, then exactly one: still exactly one
    Lens<Order, String> emailValue =
        OrderLenses.customer().andThen(CustomerLenses.email()).andThen(EmailAddressLenses.value());

    // exactly one, then one variant: zero or one
    Affine<Consignment, ConsignmentState.Dispatched> dispatched =
        ConsignmentLenses.state().andThen(ConsignmentStatePrisms.dispatched());

    // zero or more, then exactly one: zero or more
    Traversal<Order, Integer> quantities =
        OrderTraversals.lines().andThen(LineItemLenses.quantity());
    // ANCHOR_END: and_then
    return new Optics(emailValue, dispatched, quantities);
  }

  /** What the page's raw-optic block computes, so the test can read each value. */
  record Raw(Order doubled, int totalQuantity, Optional<ConsignmentState.Dispatched> dispatch) {}

  static Raw useRawOptics(Order order, Consignment consignment) {
    Optics composed = andThen();
    Traversal<Order, Integer> quantities = composed.quantities();
    Affine<Consignment, ConsignmentState.Dispatched> dispatched = composed.dispatched();
    // ANCHOR: raw_use
    Order doubled = Traversals.modify(quantities, quantity -> quantity * 2, order);

    int totalQuantity =
        quantities.asFold().foldMap(Monoids.integerAddition(), quantity -> quantity, order);

    Optional<ConsignmentState.Dispatched> dispatch = dispatched.getOptional(consignment);
    // ANCHOR_END: raw_use
    return new Raw(doubled, totalQuantity, dispatch);
  }
}
