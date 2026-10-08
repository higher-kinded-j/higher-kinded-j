// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.focus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.example.book.optics.JsonNodeOptics;
import org.higherkindedj.example.book.optics.cast.Card;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerFocus;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.CustomerProfileFocus;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.example.book.optics.cast.Payment;
import org.higherkindedj.example.book.optics.cast.PaymentPrisms;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/focus_dsl.html">Focus DSL</a> page. The
 * page {@code {{#include}}}s the anchored regions, and {@code FocusDslBookTest} holds the claims
 * the page makes about this code. The records are the chapter's cast, and {@link Basket} is a
 * supporting type holding one field of every kind the "Find your field" table covers.
 */
public final class FocusDslBook {

  private FocusDslBook() {}

  /** What the page's first use computes, so the test can read each value. */
  record FirstUse(String name, Order renamed, Order tidied) {}

  static FirstUse firstUse(Order order) {
    // ANCHOR: first_use
    String name = OrderFocus.customer().name().get(order);

    Order renamed = OrderFocus.customer().name().set("Ada Lovelace", order);

    Order tidied = OrderFocus.customer().name().modify(String::strip, order);
    // ANCHOR_END: first_use
    return new FirstUse(name, renamed, tidied);
  }

  /** The values the page's path-per-component block reads. */
  record PerComponent(OrderStatus status, List<LineItem> lines, Optional<String> nickname) {}

  static PerComponent perComponent(Order order, CustomerProfile profile) {
    // ANCHOR: per_component
    // A plain field: exactly one focus
    FocusPath<Order, OrderStatus> statusPath = OrderFocus.status();
    OrderStatus status = statusPath.get(order);

    // A List field: the processor has already stepped into the elements
    TraversalPath<Order, LineItem> linePath = OrderFocus.lines();
    List<LineItem> lines = linePath.getAll(order);

    // An Optional field: zero or one focus
    AffinePath<CustomerProfile, String> nicknamePath = CustomerProfileFocus.nickname();
    Optional<String> nickname = nicknamePath.getOptional(profile);
    // ANCHOR_END: per_component
    return new PerComponent(status, lines, nickname);
  }

  /** The values the page's chained path reads and writes. */
  record Chained(List<String> skus, Order lowered) {}

  static Chained chained(Order order) {
    // ANCHOR: chained
    TraversalPath<Order, String> skus = OrderFocus.lines().via(LineItemFocus.sku());

    // Read every one of them
    List<String> all = skus.getAll(order);

    // Or update every one of them
    Order lowered = skus.modifyAll(String::toLowerCase, order);
    // ANCHOR_END: chained
    return new Chained(all, lowered);
  }

  /** The values the page's FocusPath block computes. */
  record FocusOps(String name, Customer updated, Customer modified) {}

  static FocusOps focusOps(Customer customer) {
    // ANCHOR: focus_ops
    FocusPath<Customer, String> namePath = CustomerFocus.name();

    String name = namePath.get(customer); // always a value
    Customer updated = namePath.set("Grace", customer); // always succeeds
    Customer modified = namePath.modify(String::toUpperCase, customer);
    // ANCHOR_END: focus_ops
    return new FocusOps(name, updated, modified);
  }

  /** The values the page's AffinePath block computes. */
  record AffineOps(
      Optional<String> nickname,
      CustomerProfile updated,
      CustomerProfile modified,
      boolean hasNickname) {}

  static AffineOps affineOps(CustomerProfile profile) {
    // ANCHOR: affine_ops
    AffinePath<CustomerProfile, String> nicknamePath = CustomerProfileFocus.nickname();

    Optional<String> nickname = nicknamePath.getOptional(profile); // may be empty
    CustomerProfile updated = nicknamePath.set("Countess", profile); // writes even when absent
    CustomerProfile modified = nicknamePath.modify(String::toUpperCase, profile);
    boolean hasNickname = nicknamePath.matches(profile);
    // ANCHOR_END: affine_ops
    return new AffineOps(nickname, updated, modified, hasNickname);
  }

  /** The values the page's TraversalPath block computes. */
  record TraversalOps(List<LineItem> all, Order updated, Order modified, int lineCount) {}

  static TraversalOps traversalOps(Order order, LineItem replacement) {
    // ANCHOR: traversal_ops
    TraversalPath<Order, LineItem> linesPath = OrderFocus.lines();

    List<LineItem> all = linesPath.getAll(order);
    Order updated = linesPath.setAll(replacement, order);
    Order modified =
        linesPath.modifyAll(line -> LineItemLenses.quantity().modify(q -> q + 1, line), order);
    int lineCount = linesPath.count(order);
    // ANCHOR_END: traversal_ops
    return new TraversalOps(all, updated, modified, lineCount);
  }

  /** Every row of the page's "Find your field" table, each spelled as the row says. */
  record Fields(
      FocusPath<Basket, String> customerEmail,
      FocusPath<Basket, String> reference,
      TraversalPath<Basket, Integer> quantities,
      AffinePath<Basket, String> giftMessage,
      AffinePath<Basket, String> couponCode,
      AffinePath<Basket, String> legacyNote,
      AffinePath<Basket, String> channel,
      TraversalPath<Basket, String> tags,
      AffinePath<Basket, String> approvedBy,
      AffinePath<Basket, Card> card,
      AffinePath<Basket, Card> sameCard,
      AffinePath<Basket, ObjectNode> payloadObject) {}

  static Fields findYourField() {
    // ANCHOR: find_your_field
    // A record with @GenerateFocus: with navigators on, the next field chains straight on
    FocusPath<Basket, String> customerEmail = BasketFocus.customer().email().value();

    // A plain value: read and write it
    FocusPath<Basket, String> reference = BasketFocus.reference();

    // A List, Set or Collection: already on the elements, so the next hop is .via(...)
    TraversalPath<Basket, Integer> quantities = BasketFocus.lines().via(LineItemFocus.quantity());

    // An Optional, or a component with a recognised @Nullable: zero or one
    AffinePath<Basket, String> giftMessage = BasketFocus.giftMessage();
    AffinePath<Basket, String> couponCode = BasketFocus.couponCode();

    // A reference that may hold null, with no annotation: say so with .nullable()
    AffinePath<Basket, String> legacyNote = BasketFocus.legacyNote().nullable();

    // A Map: the path focuses the whole map, and .atKey(k) picks one value
    AffinePath<Basket, String> channel = BasketFocus.attributes().atKey("channel");

    // An array: the path focuses the whole array, and .each(...) steps into it
    TraversalPath<Basket, String> tags = BasketFocus.tags().each(EachInstances.arrayEach());

    // An Either, Maybe, Try or Validated: zero or one, on the success side
    AffinePath<Basket, String> approvedBy = BasketFocus.approvedBy();

    // A sealed type: a generated prism picks one variant, or instanceOf by runtime type
    AffinePath<Basket, Card> card = BasketFocus.payment().via(PaymentPrisms.card());
    AffinePath<Basket, Card> sameCard =
        BasketFocus.payment().via(AffinePath.instanceOf(Card.class));

    // A type you cannot annotate: compose the optics @ImportOptics generated for it
    AffinePath<Basket, ObjectNode> payloadObject =
        BasketFocus.payload().via(JsonNodeOptics.object());
    // ANCHOR_END: find_your_field
    return new Fields(
        customerEmail,
        reference,
        quantities,
        giftMessage,
        couponCode,
        legacyNote,
        channel,
        tags,
        approvedBy,
        card,
        sameCard,
        payloadObject);
  }
}

// ANCHOR: basket
// The basket a customer pays for before it becomes an Order, with a field of every kind
@GenerateFocus(generateNavigators = true)
record Basket(
    Customer customer,
    String reference,
    List<LineItem> lines,
    Optional<String> giftMessage,
    @Nullable String couponCode,
    String legacyNote,
    Map<String, String> attributes,
    String[] tags,
    Either<String, String> approvedBy,
    Payment payment,
    JsonNode payload) {}
// ANCHOR_END: basket
