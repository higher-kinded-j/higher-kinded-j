// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.parts;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/optics_intro.html">What a Path Is Made
 * Of</a> page. The page {@code {{#include}}}s the anchored regions, and {@code PathPartsBookTest}
 * holds the claims the page makes about this code.
 */
public final class PathPartsBook {

  private PathPartsBook() {}

  /** Three optics, one for each result type: taken out of paths, or composed with andThen. */
  record Optics(
      Lens<User, String> streetName,
      Affine<Order, Payment.Card> card,
      Traversal<Order, Integer> quantities) {}

  static Optics toOptic() {
    // ANCHOR: to_optic
    FocusPath<User, String> streetPath = UserFocus.address().street().name();
    Lens<User, String> streetName = streetPath.toLens();

    AffinePath<Order, Payment.Card> cardPath = OrderFocus.payment().via(PaymentPrisms.card());
    Affine<Order, Payment.Card> card = cardPath.toAffine();

    TraversalPath<Order, Integer> quantityPath = OrderFocus.lines().via(LineItemFocus.quantity());
    Traversal<Order, Integer> quantities = quantityPath.toTraversal();
    // ANCHOR_END: to_optic
    return new Optics(streetName, card, quantities);
  }

  static Lens<User, Address> addressByHand() {
    // ANCHOR: by_hand
    Lens<User, Address> address =
        Lens.of(User::address, (user, newAddress) -> new User(user.name(), newAddress));
    // ANCHOR_END: by_hand
    return address;
  }

  static Optics andThen() {
    // ANCHOR: and_then
    // exactly one, then exactly one: still exactly one
    Lens<User, String> streetName =
        UserLenses.address().andThen(AddressLenses.street()).andThen(StreetLenses.name());

    // exactly one, then one variant: zero or one
    Affine<Order, Payment.Card> card = OrderLenses.payment().andThen(PaymentPrisms.card());

    // zero or more, then exactly one: zero or more
    Traversal<Order, Integer> quantities =
        OrderTraversals.lines().andThen(LineItemLenses.quantity());
    // ANCHOR_END: and_then
    return new Optics(streetName, card, quantities);
  }

  /** What the page's raw-optic block computes, so the test can read each value. */
  record Raw(Order doubled, int totalQuantity, Optional<Payment.Card> paidByCard) {}

  static Raw useRawOptics(Order order) {
    Optics composed = andThen();
    Traversal<Order, Integer> quantities = composed.quantities();
    Affine<Order, Payment.Card> card = composed.card();
    // ANCHOR: raw_use
    Order doubled = Traversals.modify(quantities, quantity -> quantity * 2, order);

    int totalQuantity =
        quantities.asFold().foldMap(Monoids.integerAddition(), quantity -> quantity, order);

    Optional<Payment.Card> paidByCard = card.getOptional(order);
    // ANCHOR_END: raw_use
    return new Raw(doubled, totalQuantity, paidByCard);
  }
}

// ANCHOR: records
@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Street(String name, int number) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Address(Street street, String city) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record User(String name, Address address) {}

// ANCHOR_END: records

// ANCHOR: order_records
@GeneratePrisms
sealed interface Payment permits Payment.Card, Payment.Invoice {
  record Card(String last4) implements Payment {}

  record Invoice(String terms) implements Payment {}
}

@GenerateLenses
@GenerateFocus
record LineItem(String sku, int quantity) {}

@GenerateLenses
@GenerateFocus
@GenerateTraversals
record Order(String id, Payment payment, List<LineItem> lines) {}
// ANCHOR_END: order_records
